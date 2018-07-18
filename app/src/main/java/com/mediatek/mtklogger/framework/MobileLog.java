package com.mediatek.mtklogger.framework;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.SparseArray;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.settings.MobileLogSettings;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class MobileLog extends LogInstance {
    private static final String TAG = Utils.TAG + "/MobileLog";
    public static final String SOCKET_NAME = "mobilelogd";

    private static final String COMMAND_START = "start";
    /**
     * Like start from UI, then need to change auto start value at the same time.
     */
    private static final String COMMAND_START_WITH_CONFIG = "deep_start";
    private static final String COMMAND_STOP = "stop";
    private static final String COMMAND_STOP_WITH_CONFIG = "deep_stop";
    private static final String COMMAND_BOOT = "copy";
    private static final String COMMAND_SET_STORAGE_PATH = "set_storage_path,";

    /**
     * Each control command to native will have a executing response, use this
     * to update command executing status.
     */
    private static final String RESPONSE_COMMAND_RESULT_SUCCESS = "1";
    private static final String RESPONSE_COMMAND_RESULT_FAIL = "0";
    /**
     * When mobile log native status changed itself, we will be notified by this
     * type message.
     */
    private static final String RESPONSE_UPDATE_LOG_RUNNING = "mblog_running";
    private static final String RESPONSE_UPDATE_LOG_STOPPED = "mblog_stopped";
    private static final String RESPONSE_UPDATE_STORAGE_FULL = "storage_full";
    private static final String RESPONSE_UPDATE_LOG_FOLDER_LOST = "log_folder_lost";

    /**
     * Sub type of Mobile log, can set some to be on and some to be off.
     */
    public static final int SUB_LOG_TYPE_ANDROID = 1;
    public static final int SUB_LOG_TYPE_KERNEL = 2;
    public static final int SUB_LOG_TYPE_SCP = 3;
    public static final int SUB_LOG_TYPE_ATF = 4;
    public static final int SUB_LOG_TYPE_BSP = 5;
    public static final int SUB_LOG_TYPE_MMEDIA = 6;
    public static final int SUB_LOG_TYPE_SSPM = 7;

    public static final SparseArray<String> KEY_SUB_LOG_TYPE_MAP = new SparseArray<String>();
    static {
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_ANDROID, "AndroidLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_KERNEL, "KernelLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_SCP, "SCPLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_ATF, "ATFLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_BSP, "BSPLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_MMEDIA, "MmediaLog");
        KEY_SUB_LOG_TYPE_MAP.put(SUB_LOG_TYPE_SSPM, "SSPMLog");
    }

    public static final SparseArray<String> KEY_SUB_LOG_TYPE_PREFERENCE_MAP =
            new SparseArray<String>();
    static {
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_ANDROID,
                MobileLogSettings.KEY_MB_ANDROID_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_KERNEL,
                MobileLogSettings.KEY_MB_KERNEL_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_SCP, MobileLogSettings.KEY_MB_SCP_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_ATF, MobileLogSettings.KEY_MB_ATF_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_BSP, MobileLogSettings.KEY_MB_BSP_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_MMEDIA,
                MobileLogSettings.KEY_MB_MMEDIA_LOG);
        KEY_SUB_LOG_TYPE_PREFERENCE_MAP.put(SUB_LOG_TYPE_SSPM, MobileLogSettings.KEY_MB_SSPM_LOG);

    }

    /**
     * @param context Context
     * @param handler Handler
     */
    public MobileLog(Context context, Handler handler) {
        super(context);
        HandlerThread handlerThread = new HandlerThread("mobileHandler");
        handlerThread.start();
        mHandler = new MobileLogHandler(handlerThread.getLooper());
        mLogConnectionThread = new LogConnectionThread(
                new MobileLogConnection(SOCKET_NAME, mHandler));
        mLogConnectionThread.start();

        synchronized (mLogConnectionLock) {
            try {
                mLogConnectionLock.wait(Utils.DURATION_WAIT_LOG_INSTANCE_READY);
            } catch (InterruptedException e) {
                Utils.logi(TAG, "Wait gps log sub thread initialization, but was interrupted");
            }
        }
        mCmdResHandler = handler;

    }

    /**
     * @author MTK81255
     *
     */
    class MobileLogHandler extends LogHandler {
        MobileLogHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            Utils.logi(TAG, "Handle receive message, what=" + msg.what);

            // Check whether we need to connect to native layer at the very
            // begin
            // For stop message, since Service may be killed unfortunately while
            // native is still running,
            // then we need to re-initialize connection to native layer if
            // connection is lost
            if (mLogConnectionThread != null && mLogConnectionThread.isAlive()) {
                try {
                    mLogConnectionThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (!mBConnected) {
                mBConnected = initLogConnection();
            }
            // This object may contain detail reason for this command
            // For different trigger reason, different command is needed
            Object cmdReasonObj = msg.obj;
            String cmdReason = null;
            if ((cmdReasonObj != null) && (cmdReasonObj instanceof String)) {
                cmdReason = (String) cmdReasonObj;
            }
            switch (msg.what) {
            case MSG_INIT:
                if (!mBConnected) {
                    Utils.loge(TAG, "Fail to establish connection to native layer.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                    break;
                }
                if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(cmdReason)) {
                    // First start command after boot, re-set log size to
                    // confirm info sync
                    String sizeStr =
                            MyApplication.getInstance().getDefaultSharedPreferences().getString(
                                    Utils.KEY_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MOBILE), "");
                    String totalSizeStr =
                            MyApplication.getInstance().getDefaultSharedPreferences().getString(
                                    Utils.KEY_TOTAL_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MOBILE), "");
                    Utils.logi(TAG, "At first start time after boot,"
                            + " re-set log size to confirm info sync, sizeStr = " + sizeStr
                            + ", totalSizeStr = " + totalSizeStr);
                    if (!TextUtils.isEmpty(sizeStr)) {
                        mLogConnection.sendCmd(PREFIX_CONFIG_LOG_SIZE + sizeStr);
                    } else {
                        Utils.loge(TAG,
                                "Mobile log size in default shared preference is empty.");
                    }
                    if (!TextUtils.isEmpty(totalSizeStr)) {
                        mLogConnection.sendCmd(PREFIX_CONFIG_TOTAL_LOG_SIZE + totalSizeStr);
                    } else {
                        Utils.loge(TAG,
                                "Mobile total log size in default shared preference is empty.");
                    }
                }
                int logStorageStatus = getLogStorageState();
                if (logStorageStatus != Utils.STORAGE_STATE_OK) {
                    Utils.loge(TAG, "Log storage is not ready yet.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    // At start time, SD card is not ready, like no enough
                    // storage,
                    // in this case, manually stop mobile log for safety
                    // Utils.logi(TAG,
                    // "Going to start mobile log, but SD card not ready yet, status="
                    // +logStorageStatus+", just send out a stop command to native.");
                    // mLogConnection.sendCmd(COMMAND_STOP);
                    if (logStorageStatus == Utils.STORAGE_STATE_NOT_READY) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                                Utils.REASON_STORAGE_NOT_READY);
                    } else if (logStorageStatus == Utils.STORAGE_STATE_FULL) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_STORAGE_FULL);
                    }
                } else {
                    boolean cmdSuccess = false;
                    mLogConnection.sendCmd(COMMAND_SET_STORAGE_PATH
                            + Utils.getCurrentLogPath(sContext));
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "Mobile log initialization reason = " + cmdReason);
                        if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(cmdReason)) {
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_BOOT);
                        } else if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG,
                                    "Start command come from UI,"
                                    + " change log auto start to true at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_START_WITH_CONFIG);
                        } else {
                            Utils.logw(TAG, "Unsupported initialization reason, ignore it.");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_START);
                        }
                    } else {
                        Utils.logd(TAG,
                                "No valid start up reason was found when init. Just start up.");
                        cmdSuccess = mLogConnection.sendCmd(COMMAND_START);
                    }
                    if (cmdSuccess) {
                        // sendEmptyMessageDelayed(MSG_CHECK,
                        // Utils.CHECK_CMD_DURATION);
                        // notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");
                        Utils.logd(TAG,
                                "After sending start command, wait native's resp,"
                                + " not treat it as successfully directly");
                    } else {
                        Utils.loge(TAG,
                                "Send start command to native layer fail,"
                                + " maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    }
                }
                break;
            case MSG_STOP:
                if (mBConnected) {
                    boolean cmdSuccess = false;
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "Mobile log stop reason = " + cmdReason);
                       if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG,
                                    "Stop command come from UI,"
                                    + " change log auto start to false at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                        } else {
                            Utils.logw(TAG, "Unsupported stop reason, ignore it.");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP);
                        }
                    } else {
                        Utils.logd(TAG, "Normally stop mobile log with stop command");
                        cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP);
                    }

                    if (!cmdSuccess) {
                        Utils.loge(TAG,
                                "Send stop command to native layer fail,"
                                + " maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                        // }else{
                        // String stopReason = "";
                        // if(Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT.equals(cmdReason)){
                        // stopReason = Utils.REASON_WAIT_SD_TIMEOUT;
                        // }
                        // notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                        // stopReason);
                    }
                    // Stop check command
                    removeMessages(MSG_CHECK);
                } else {
                    Utils.logw(TAG, "Have not connected to native layer, just ignore this command");
                    // Lost connection to native layer, treat it as stopped and
                    // report reason to UI
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                }
                break;
            case MSG_CHECK:
                if (mBConnected) {
                    Utils.logd(TAG, "Receive a check command. Ignore it.");
                    // mLogConnection.sendCmd(COMMAND_CHECK);
                    // sendEmptyMessageDelayed(MSG_CHECK,
                    // Utils.CHECK_CMD_DURATION);
                } else {
                    Utils.logw(TAG, "lost connection to native layer, stop check.");
                    removeMessages(MSG_CHECK);
                }
                break;
            case MSG_CONFIG:
                if (mBConnected) {
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logd(TAG, "Receive config command, parameter=" + cmdReason);
                        if (cmdReason.startsWith(PREFIX_CONFIG_LOG_SIZE)
                                || cmdReason.startsWith(PREFIX_CONFIG_AUTO_START)
                                || cmdReason.startsWith(PREFIX_CONFIG_TOTAL_LOG_SIZE)) {
                            mLogConnection.sendCmd(cmdReason);
                        } else if (cmdReason.startsWith(LogInstance.PREFIX_CONFIG_SUB_LOG)) {
                            int subType = msg.arg1;
                            int enableValue = msg.arg2;
                            Utils.logv(TAG, "Try to set mobile sub log enable state, subType="
                                    + subType + ", enable?" + enableValue);
                            String subLogStr = KEY_SUB_LOG_TYPE_MAP.get(subType);
                            if (subLogStr == null) {
                                Utils.loge(TAG, "Unsupported sub mobile log type");
                            } else {
                                boolean cmdRs =
                                        mLogConnection.sendCmd(LogInstance.PREFIX_CONFIG_SUB_LOG
                                                + subLogStr + "=" + enableValue);
                                if (cmdRs) {
                                    sDefaultSharedPreferences
                                            .edit()
                                            .putBoolean(
                                                    KEY_SUB_LOG_TYPE_PREFERENCE_MAP.get(subType),
                                                    enableValue == 1).apply();
                                } else {
                                    Utils.loge(TAG, "Send cmd : "
                                            + LogInstance.PREFIX_CONFIG_SUB_LOG + subLogStr + "="
                                            + enableValue + " failed!");
                                }
                            }
                        } else {
                            Utils.logw(TAG, "Unsupported config command");
                        }
                    } else {
                        Utils.loge(TAG, "Receive config command, but parameter is null");
                    }
                } else {
                    Utils.logw(TAG, "Fail to config native parameter because of lost connection.");
                }
                break;
            case MSG_ADB_CMD:
                Utils.logd(TAG, "Receive adb command[" + cmdReason + "]");
                break;
            case MSG_RESTORE_CONN:
                if (!mBConnected) {
                    Utils.logw(TAG, "Reconnect to native layer fail! Mark log status as stopped.");
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                }
                break;
            case MSG_DIE:
                if (mLogConnection != null) {
                    // mLogConnection.sendCmd(COMMAND_DIE);
                    mLogConnection.stop();
                    mLogConnection = null;
                }
                removeMessages(MSG_CHECK);
                mBConnected = false;
                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_DIE);
                break;
            case MSG_ALIVE:
                int formerStatus =
                        mSharedPreferences.getInt(Utils.KEY_STATUS_MOBILE,
                                Utils.VALUE_STATUS_STOPPED);
                if (formerStatus != Utils.VALUE_STATUS_RUNNING) {
                    notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");
                } else {
                    Utils.logv(TAG, "Still running, ignore alive message.");
                }
                break;
            case MSG_STATUS_UPDATE:
                int newState = msg.arg1;
                int oldState =
                        mSharedPreferences.getInt(Utils.KEY_STATUS_MOBILE,
                                Utils.VALUE_STATUS_STOPPED);
                Utils.logd(TAG, "Got an update event, new state = " + newState + ", reason="
                        + cmdReason + ", oldState=" + oldState);
                notifyServiceStatus(newState, TextUtils.isEmpty(cmdReason) ? "" : cmdReason);
                // if(newState != oldState){
                // } else {
                // Utils.logd(TAG,
                // "Mobile status not change, ignore new coming state change event");
                // }
                break;
            default:
                Utils.logw(TAG, "Not supported message, tell father to deal with it: " + msg.what);
                super.handleMessage(msg, Utils.LOG_TYPE_MOBILE);
                break;
            }
        }
    }

    /**
     * Notify current mobile log running status to other component.
     *
     * @param status
     * @param reason
     */
    protected void notifyServiceStatus(int status, String reason) {
        Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status
                + ",  reason=[" + reason + "]");

        if (Utils.VALUE_STATUS_RUNNING == status) {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_RUNNING).apply();
            }
            updateStatusBar(Utils.LOG_TYPE_MOBILE, R.string.notification_title_mobile, true);
        } else {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_STOPPED).apply();
            }
            updateStatusBar(Utils.LOG_TYPE_MOBILE, R.string.notification_title_mobile, false);
        }

        if (mIsNeedNotifyLogStatusChanged) {
            // Notify service about MobileLog state change
            mCmdResHandler.obtainMessage(Utils.MSG_LOG_STATE_CHANGED, Utils.LOG_TYPE_MOBILE,
                    status, reason).sendToTarget();
        }
        mIsNeedNotifyLogStatusChanged = true;
        super.notifyServiceStatus(status, reason);
    }

    /**
     * @author MTK81255
     *
     */
    class MobileLogConnection extends LogConnection {
        public MobileLogConnection(String socketName, Handler handler) {
            super(socketName, handler);
        }

        @Override
        public void dealWithResponse(byte[] respBuffer, Handler handler) {
            super.dealWithResponse(respBuffer, handler);
            if (respBuffer == null || respBuffer.length == 0) {
                Utils.logw(TAG, "Get an empty response from native, ignore it.");
                return;
            }
            String respStr = new String(respBuffer);
            Utils.logi(TAG, "-->dealWithResponse(), resp=" + respStr);

            int formerStatus =
                    mSharedPreferences.getInt(Utils.KEY_STATUS_MOBILE, Utils.VALUE_STATUS_STOPPED);

            int msgType = MSG_UNKNOWN;
            int msgArg1 = -1;
            String extraStr = null; // Detail info, if any
            if (respStr.startsWith(COMMAND_BOOT) || respStr.startsWith(COMMAND_START)
                    || respStr.startsWith(COMMAND_START_WITH_CONFIG)) {
                msgType = MSG_STATUS_UPDATE;
                if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                    msgArg1 = 1; // Stand for mobile log new running status
                } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                    msgArg1 = 0;
                    extraStr = Utils.REASON_SEND_CMD_FAIL;
                }
                mCmdResHandler.obtainMessage(Utils.MSG_START_LOGS_DONE,
                        Utils.LOG_TYPE_MOBILE, extraStr == null ? 1 : 0).sendToTarget();
                mIsNeedNotifyLogStatusChanged = false;
            } else if (respStr.startsWith(COMMAND_STOP)
                    || respStr.startsWith(COMMAND_STOP_WITH_CONFIG)) {
                msgType = MSG_STATUS_UPDATE;
                if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                    msgArg1 = 0; // Stand for mobile log new running status
                } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                    msgArg1 = 0;
                    extraStr = Utils.REASON_SEND_CMD_FAIL;
                }
                mCmdResHandler.obtainMessage(Utils.MSG_STOP_LOGS_DONE,
                        Utils.LOG_TYPE_MOBILE, extraStr == null ? 1 : 0).sendToTarget();
                mIsNeedNotifyLogStatusChanged = false;
            } else if (respStr.startsWith(RESPONSE_UPDATE_LOG_RUNNING)) { // Log
                                                                          // begin
                                                                          // to
                                                                          // running
                if (formerStatus == Utils.VALUE_STATUS_STOPPED) {
                    msgType = MSG_STATUS_UPDATE;
                    msgArg1 = 1;
                } else {
                    Utils.logd(TAG,
                            "Log already in running status, ingore new running report from native");
                    return;
                }
            } else if (respStr.startsWith(RESPONSE_UPDATE_LOG_STOPPED)) { // Log
                                                                          // begin
                                                                          // to
                                                                          // running
                if (formerStatus == Utils.VALUE_STATUS_RUNNING) {
                    msgType = MSG_STATUS_UPDATE;
                    msgArg1 = 0;
                } else {
                    Utils.logd(TAG,
                            "Log alraedy in stop status, ingore new stopped report from native");
                    return;
                }
            } else if (respStr.startsWith("die")) {
                Utils.loge(TAG, "Got a die message from native");
                msgType = MSG_DIE;
            } else if (respStr.startsWith(RESPONSE_UPDATE_STORAGE_FULL)) {
                msgType = MSG_STATUS_UPDATE;
                msgArg1 = 0;
                extraStr = Utils.REASON_STORAGE_FULL;
            } else if (respStr.startsWith(RESPONSE_UPDATE_LOG_FOLDER_LOST)) {
                msgType = MSG_STATUS_UPDATE;
                msgArg1 = 0;
                extraStr = Utils.REASON_LOG_FOLDER_DELETED;
            }

            if (msgType == 0) {
                Utils.logd(TAG,
                        "No need deal with this response for msgType = MSG_UNKNOWN");
                return;
            }
            handler.obtainMessage(msgType, msgArg1, -1, extraStr).sendToTarget();
        }
    }
}
