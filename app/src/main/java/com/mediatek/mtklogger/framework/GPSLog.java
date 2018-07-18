package com.mediatek.mtklogger.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.utils.Utils;

/**
 * log instance for GPS Log.
 */
/**
 * @author mtk80130
 *
 */
public class GPSLog extends LogInstance {
    /**
     * init function for GPSLog.
     * @param context Context
     */
    public GPSLog(Context context) {
        super(context);
    }

    private static final String TAG = Utils.TAG + "/GPSLog";
    public static final String SOCKET_NAME = "gpslogd";

    private static final String COMMAND_START_WITH_CONFIG = "deep_start,";
    // private static final String COMMAND_STOP = "stop";
    private static final String COMMAND_STOP_WITH_CONFIG = "deep_stop";
    private static final String COMMAND_SET_STORAGE_PATH = "set_storage_path,";
    /**
     * Each control command to native will have a executing response, use this
     * to update command executing status.
     */
    private static final String RESPONSE_COMMAND_RESULT_SUCCESS = "1";
    private static final String RESPONSE_COMMAND_RESULT_FAIL = "0";
    private SharedPreferences mDefaultSharedPreferences;
    public static final String KEY_SAVE_LOG_PATH = "save_log_path";

    /**
     * @param context Context
     * @param handler Handler
     */
    public GPSLog(Context context, Handler handler) {
        super(context);
        HandlerThread handlerThread = new HandlerThread("gpslogHandler");
        handlerThread.start();
        mHandler = new GPSLogHandler(handlerThread.getLooper());
        mLogConnectionThread = new LogConnectionThread(
                new GPSLogConnection(SOCKET_NAME, mHandler));
        mLogConnectionThread.start();

        synchronized (mLogConnectionLock) {
            try {
                mLogConnectionLock.wait(Utils.DURATION_WAIT_LOG_INSTANCE_READY);
            } catch (InterruptedException e) {
                Utils.logi(TAG, "Wait gps log sub thread initialization, but was interrupted");
            }
        }
        mCmdResHandler = handler;
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

    }

    @Override
    public String getRunningLogPath(int logType) {
        // GPS log is not managed by MTKLogger
        return null;
    }

    /**
     * @author mtk80130
     *
     */
    class GPSLogHandler extends LogHandler {
        GPSLogHandler(Looper loop) {
            super(loop);
        }
        @Override
        public void handleMessage(Message msg) {
            Utils.logi(TAG, "Handle receive message, what=" + msg.what);

            // Check whether we need to connect to native layer at the very
            // begin
            // For stop message, since Service may be killed unfortunately
            // while native is still running,
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
                                    Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_STOPPED).apply();
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                    break;
                }
                int logStorageStatus = getLogStorageState();
                if (logStorageStatus != Utils.STORAGE_STATE_OK) {
                    Utils.loge(TAG, "Log storage is not ready yet.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_STOPPED).apply();
                    }
                    // At start time, SD card is not ready, like no enough
                    // storage,
                    // in this case, manually stop gps log for safety
                    Utils.logi(TAG, "Going to start gps log, but SD card not ready yet, status="
                            + logStorageStatus + ", just send out a stop command to native.");
                    mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                    if (logStorageStatus == Utils.STORAGE_STATE_NOT_READY) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                                Utils.REASON_STORAGE_NOT_READY);
                    } else if (logStorageStatus == Utils.STORAGE_STATE_FULL) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_STORAGE_FULL);
                    }
                } else {
                    mLogConnection.sendCmd(COMMAND_SET_STORAGE_PATH
                            + Utils.getCurrentLogPath(sContext));
                    String currentPath = getCurrentPath();
                    boolean cmdSuccess = false;
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "GPS log initialization reason = " + cmdReason);

                        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG, "Start command come from UI, "
                                    + "change log auto start to true at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_START_WITH_CONFIG
                                    + currentPath);
                        } else {
                            Utils.logw(TAG, "Unsupported initialization reason, ignore it.");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_START_WITH_CONFIG
                                    + currentPath);
                        }
                    } else {
                        Utils.logd(TAG,
                                "No valid start up reason was found when init. Just start up.");
                        cmdSuccess = mLogConnection
                                .sendCmd(COMMAND_START_WITH_CONFIG + currentPath);
                    }
                    if (cmdSuccess) {
                        // sendEmptyMessageDelayed(MSG_CHECK,
                        // Utils.CHECK_CMD_DURATION);
                        // notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");
                        Utils.logd(TAG, "After sending start command, wait native's resp, "
                                + "not treat it as successfully directly");
                    } else {
                        Utils.loge(TAG, "Send start command to native layer fail, "
                                + "maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    }
                }
                break;
            case MSG_STOP:
                if (mBConnected) {
                    boolean cmdSuccess = false;
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "gps log stop reason = " + cmdReason);

                        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG, "Stop command come from UI, "
                                    + "change log auto start to false at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                        } else {
                            Utils.logw(TAG, "Unsupported stop reason, ignore it.");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                        }
                    } else {
                        Utils.logd(TAG, "Normally stop gps log with stop command");
                        cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                    }

                    if (!cmdSuccess) {
                        Utils.loge(TAG, "Send stop command to native layer fail, "
                                + "maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
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

            case MSG_CONFIG:
                if (mBConnected) {
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logd(TAG, "Receive config command, parameter=" + cmdReason);
                        if (cmdReason.startsWith(PREFIX_CONFIG_LOG_SIZE)
                                || cmdReason.startsWith(PREFIX_CONFIG_AUTO_START)
                                || cmdReason.startsWith(PREFIX_CONFIG_TOTAL_LOG_SIZE)

                        ) {
                            mLogConnection.sendCmd(cmdReason);
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

            case MSG_RESTORE_CONN:
                if (!mBConnected) {
                    Utils.logw(TAG, "Reconnect to native layer fail! Mark log status as stopped.");
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                }
                break;
            case MSG_STATUS_UPDATE:
                int newState = msg.arg1;
                int oldState = mSharedPreferences.getInt(Utils.KEY_STATUS_GPS,
                        Utils.VALUE_STATUS_STOPPED);
                Utils.logd(TAG, "Got an update event, new state = " + newState + ", reason="
                        + cmdReason + ", oldState=" + oldState);
                notifyServiceStatus(newState, TextUtils.isEmpty(cmdReason) ? "" : cmdReason);

                break;
            case MSG_DIE:
                if (mLogConnection != null) {
                    mLogConnection.stop();
                }
                mBConnected = false;
                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_DIE);
                break;
            default:
                Utils.logw(TAG, "Not supported message, tell father to deal with it: " + msg.what);
                super.handleMessage(msg, Utils.LOG_TYPE_GPS);
                break;
            }
        }
    }

    private String getCurrentPath() {
        // TODO Auto-generated method stub
        String gpsLogpath = "1"; // MTKLogger path
        if (mDefaultSharedPreferences != null) {
            gpsLogpath = mDefaultSharedPreferences.getString(KEY_SAVE_LOG_PATH, "1");
        }
        return gpsLogpath;

    }

    protected void notifyServiceStatus(int status, String reason) {
        Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status + "," +
        "  reason=[" + reason + "]");

        if (Utils.VALUE_STATUS_RUNNING == status) {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit().putInt(Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_RUNNING)
                        .apply();
            }
            updateStatusBar(Utils.LOG_TYPE_GPS,
                    R.string.notification_title_gps, true);
        } else {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit().putInt(Utils.KEY_STATUS_GPS, Utils.VALUE_STATUS_STOPPED)
                        .apply();
            }
            updateStatusBar(Utils.LOG_TYPE_GPS,
                    R.string.notification_title_gps, false);
        }

        if (mIsNeedNotifyLogStatusChanged) {
            // Notify service about GPSLog state change
            mCmdResHandler.obtainMessage(Utils.MSG_LOG_STATE_CHANGED, Utils.LOG_TYPE_GPS,
                    status, reason).sendToTarget();
        }
        mIsNeedNotifyLogStatusChanged = true;
        super.notifyServiceStatus(status, reason);
    }

    /**
     * @author mtk80130
     *
     */
    class GPSLogConnection extends LogConnection {
        public GPSLogConnection(String socketName, Handler handler) {
            super(socketName, handler);
        }

        @Override
        public void dealWithResponse(byte[] respBuffer, Handler handler) {
            super.dealWithResponse(respBuffer, handler);
            String respStr = new String(respBuffer);
            Utils.logi(TAG, "-->dealWithResponse(), resp=" + respStr);
            if (respBuffer == null || respBuffer.length == 0) {
                Utils.logw(TAG, "Get an empty response from native, ignore it.");
                return;
            }

            // int formerStatus =
            // mSharedPreferences.getInt(Utils.KEY_STATUS_GPS,
            // Utils.VALUE_STATUS_STOPPED);

            int msgType = MSG_UNKNOWN;
            int msgArg1 = -1;
            String extraStr = null; // Detail info, if any
            try {
                if (respStr.contains(COMMAND_START_WITH_CONFIG)) {
                    msgType = MSG_STATUS_UPDATE;
                    if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                        msgArg1 = 1; // Stand for start successfully
                    } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                        msgArg1 = 0;
                        extraStr = Utils.REASON_SEND_CMD_FAIL;
                    }
                    mCmdResHandler.obtainMessage(Utils.MSG_START_LOGS_DONE,
                            Utils.LOG_TYPE_GPS, extraStr == null ? 1 : 0).sendToTarget();
                    mIsNeedNotifyLogStatusChanged = false;
                } else if (respStr.startsWith(COMMAND_STOP_WITH_CONFIG)) {
                    msgType = MSG_STATUS_UPDATE;
                    if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                        msgArg1 = 0; // Stand for log new running status
                    } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                        msgArg1 = 0;
                        extraStr = Utils.REASON_SEND_CMD_FAIL;
                    }
                    mCmdResHandler.obtainMessage(Utils.MSG_STOP_LOGS_DONE,
                            Utils.LOG_TYPE_GPS, extraStr == null ? 1 : 0).sendToTarget();
                    mIsNeedNotifyLogStatusChanged = false;
                } else if (respStr.startsWith("die")) {
                    Utils.loge(TAG, "Got a die message from native");
                    msgType = MSG_DIE;
                }
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "unexpected response!");

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
