package com.mediatek.mtklogger.framework;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.SparseArray;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.settings.MetLogSettings;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class MetLog extends LogInstance {
    private static final String TAG = Utils.TAG + "/MetLog";
    public static final String SOCKET_NAME = "metlogd";
    private static final String RESPONSE_COMMADM_RESULT_STOP_STATUS = "stop_status";
    /**
     * Like start from UI, then need to change auto start value at the same time.
     */
    private static final String COMMAND_START_WITH_CONFIG = "deep_start";
    private static final String COMMAND_STOP_WITH_CONFIG = "deep_stop";
    private static final String COMMAND_SET_STORAGE_PATH = "set_storage_path,";

    /**
     * Flag for configuring met log period.
     */
    public static final String PREFIX_MET_LOG_PERIOD = "get_period=";

    public static final String PREFIX_MET_LOG_CPU_BUFFER = "get_cpu_buff_size=";
    public static final String PREFIX_MET_LOG_SIZE = "get_logsize=";

    public static final String PREFIX_MAX_MET_LOG = "get_mx_logsize=";
    public static final String PREFIX_MIN_MET_LOG = "get_min_logsize=";

    public static final String PREFIX_MAX_PERIOD = "get_mx_period=";
    public static final String PREFIX_MIN_PERIOD = "get_min_period=";

    public static final String PREFIX_MAX_CPU_BUFFER = "get_mx_cpu_buff_size=";
    public static final String PREFIX_MIN_CPU_BUFFER = "get_min_cpu_buff_size=";

    public static final SparseArray<String> GET_VALUES_MAP = new SparseArray<String>();
    static {
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MAX_PERIOD, PREFIX_MAX_PERIOD);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MIN_PERIOD, PREFIX_MIN_PERIOD);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MAX_CPU_BUFFER, PREFIX_MAX_CPU_BUFFER);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MIN_CPU_BUFFER, PREFIX_MIN_CPU_BUFFER);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MAX_LOG_SIZE, PREFIX_MAX_MET_LOG);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_MIN_LOG_SIZE, PREFIX_MIN_MET_LOG);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_CURRENT_CPU_BUFFER,
                PREFIX_MET_LOG_CPU_BUFFER);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_CURRENT_PERIOD, PREFIX_MET_LOG_SIZE);
        GET_VALUES_MAP.put(MetLogSettings.KEY_MET_LOG_CURRENT_LOG_SIZE, PREFIX_MET_LOG_PERIOD);
    }
    public static final String PREFIX_IS_METLOG_SUPPORT = "is_metlog_support";
    private static int sValue = 0;

    @Override
    public String getRunningLogPath(int logType) {
        // MET log is not managed by MTKLogger
        return null;
    }

    /**
     * @return int
     */
    public static int getValue() {
        int retrynum = 3;
        if (sValue == 0 && retrynum > 0) {
            try {
                Thread.sleep(100);
                retrynum--;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Utils.logd(TAG, "getValue : retry" + retrynum);
        return sValue;
    }

    public static void setValue(int value) {
        sValue = value;
    }

    /**
     * Each control command to native will have a executing response, use this
     * to update command executing status.
     */
    private static final String RESPONSE_COMMAND_RESULT_SUCCESS = "1";
    private static final String RESPONSE_COMMAND_RESULT_FAIL = "0";
    private static final String RESPONSE_COMMAND_RESULT_START_FAIL = "-1";

    /**
     * @param context Context
     * @param handler Handler
     */
    public MetLog(Context context, Handler handler) {
        super(context);
        HandlerThread handlerThread = new HandlerThread("metlogHandler");
        handlerThread.start();
        mHandler = new MetLogHandler(handlerThread.getLooper());
        mLogConnectionThread = new LogConnectionThread(
                new MetLogConnection(SOCKET_NAME, mHandler));
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

    @Override
    protected void getLogDefaultValues() {
        /**
         * Get Value of IS_METLOG_SUPPORT.
         * 1 means METLOG SUPPORT
         * 0 means METLOG not support
         */
        mHandler.obtainMessage(MSG_GET_VALUE_FROM_NATIVE,
                PREFIX_IS_METLOG_SUPPORT).sendToTarget();
    }

    /**
     * @author MTK81255
     *
     */
    class MetLogHandler extends LogHandler {
        MetLogHandler(Looper loop) {
            super(loop);
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
                                    Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_STOPPED).apply();
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                    break;
                }
                int logStorageStatus = getLogStorageState();
                if (logStorageStatus != Utils.STORAGE_STATE_OK) {
                    Utils.loge(TAG, "Log storage is not ready yet.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_STOPPED).apply();
                    }
                    // At start time, SD card is not ready, like no enough
                    // storage,
                    // in this case, manually stop met log for safety
                    Utils.logi(TAG, "Going to start met log, but SD card not ready yet, status="
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
                    boolean cmdSuccess = false;
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "MET log initialization reason = " + cmdReason);

                        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG,
                                    "Start command come from UI,"
                                    + " change log auto start to true at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_START_WITH_CONFIG);
                        } else {
                            Utils.logw(TAG, "Unsupported initialization reason, ignore it.");
                        }
                    } else {
                        Utils.logd(TAG,
                                "No valid start up reason was found when init. Just start up.");
                    }
                    if (cmdSuccess) {
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
                        Utils.logi(TAG, "MET log stop reason = " + cmdReason);

                        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                            Utils.logw(TAG,
                                    "Stop command come from UI,"
                                    + " change log auto start to false at the same time");
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_STOP_WITH_CONFIG);
                        } else {
                            Utils.logw(TAG, "Unsupported stop reason, ignore it.");
                        }
                    }

                    if (!cmdSuccess) {
                        Utils.loge(TAG,
                                "Send stop command to native layer fail,"
                                + " maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    } else {
                        String stopReason = "";
                        if (Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT.equals(cmdReason)) {
                            stopReason = Utils.REASON_WAIT_SD_TIMEOUT;
                        }
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, stopReason);
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
            case MSG_GET_VALUE_FROM_NATIVE:
                if (mBConnected) {
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logd(TAG, "Receive config command, parameter=" + cmdReason);
                        mLogConnection.sendCmd(cmdReason);
                    } else {
                        Utils.loge(TAG, "Receive config command, but parameter is null");
                    }
                } else {
                    Utils.logw(TAG, "Fail to config native parameter because of lost connection.");
                }
                break;
            case MSG_CONFIG:
                if (mBConnected) {
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logd(TAG, "Receive config command, parameter=" + cmdReason);
                        if (cmdReason.startsWith(PREFIX_CONFIG_LOG_SIZE)
                                || cmdReason.startsWith(PREFIX_CONFIG_AUTO_START)
                                || cmdReason.startsWith(PREFIX_CONFIG_TOTAL_LOG_SIZE)
                                || cmdReason.startsWith(PREFIX_PEROID_SIZE)
                                || cmdReason.startsWith(PREFIX_CPU_BUFFER_SIZE)
                                || cmdReason.startsWith(PREFIX_MET_HEAVY_RECORD)) {
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
            case MSG_NATIVE_ERROR:
                cmdReason = (String) cmdReasonObj;
                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, cmdReason);
                break;
            case MSG_STATUS_UPDATE:
                int newState = msg.arg1;
                int oldState =
                        mSharedPreferences.getInt(Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_STOPPED);
                Utils.logd(TAG, "Got an update event, new state = " + newState + ", reason="
                        + cmdReason + ", oldState=" + oldState);
                notifyServiceStatus(newState, TextUtils.isEmpty(cmdReason) ? "" : cmdReason);

                break;
            default:
                Utils.logw(TAG, "Not supported message, tell father to deal with it: " + msg.what);
                super.handleMessage(msg, Utils.LOG_TYPE_MET);
                break;
            }
        }
    }

    protected void notifyServiceStatus(int status, String reason) {
        Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status
                + ",  reason=[" + reason + "]");

        if (Utils.VALUE_STATUS_RUNNING == status) {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit().putInt(Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_RUNNING)
                        .apply();
            }
            updateStatusBar(Utils.LOG_TYPE_MET, R.string.notification_title_met, true);
        } else {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit().putInt(Utils.KEY_STATUS_MET, Utils.VALUE_STATUS_STOPPED)
                        .apply();
            }
            updateStatusBar(Utils.LOG_TYPE_MET, R.string.notification_title_met, false);
        }

        if (mIsNeedNotifyLogStatusChanged) {
            // Notify service about MetLog state change
            mCmdResHandler.obtainMessage(Utils.MSG_LOG_STATE_CHANGED, Utils.LOG_TYPE_MET,
                    status, reason).sendToTarget();
        }
        mIsNeedNotifyLogStatusChanged = true;
        super.notifyServiceStatus(status, reason);
    }

    /**
     * @author MTK81255
     *
     */
    class MetLogConnection extends LogConnection {
        public MetLogConnection(String socketName, Handler handler) {
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

            int msgType = MSG_UNKNOWN;
            int msgArg1 = -1;
            String extraStr = null; // Detail info, if any
            try {
                if (respStr.startsWith(COMMAND_START_WITH_CONFIG)) {
                    msgType = MSG_STATUS_UPDATE;
                    if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                        msgArg1 = 1; // Stand for start successfully
                    } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                        msgArg1 = 0;
                        extraStr = Utils.REASON_SEND_CMD_FAIL;
                    } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_START_FAIL)) {
                        msgArg1 = 0; // start failed from native
                        String splited[] = respStr.split("=");
                        extraStr = splited[1].split(",")[0].trim();
                        Utils.logd(TAG, "start failed messsage:" + extraStr);
                    }
                    mCmdResHandler.obtainMessage(Utils.MSG_START_LOGS_DONE,
                            Utils.LOG_TYPE_MET, extraStr == null ? 1 : 0).sendToTarget();
                    mIsNeedNotifyLogStatusChanged = false;
                } else if (respStr.startsWith(RESPONSE_COMMADM_RESULT_STOP_STATUS)) {
                    msgType = MSG_STATUS_UPDATE;
                    if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                        msgArg1 = 0;
                        String splited[] = respStr.split("=");
                        extraStr = splited[1].split(",")[0].trim();
                        Utils.logd(TAG, "stop  messsage:" + extraStr);
                    }
                    mCmdResHandler.obtainMessage(Utils.MSG_STOP_LOGS_DONE,
                            Utils.LOG_TYPE_MET, msgArg1 == 0 ? 1 : 0).sendToTarget();
                    mIsNeedNotifyLogStatusChanged = false;
                } else if (respStr.startsWith(COMMAND_STOP_WITH_CONFIG)) {
                    Utils.logd(TAG,
                            "At present, ignore stop command response, just stop log directly");
                    return;
                } else if (respStr.startsWith("die")) {
                    Utils.loge(TAG, "Got a die message from native");
                    msgType = MSG_DIE;
                } else if (respStr.startsWith(PREFIX_RESPONSE_ERROR)) {
                    Utils.logw(TAG, "Got a error message from native");
                    msgType = MSG_NATIVE_ERROR;
                    extraStr = respStr.substring(PREFIX_RESPONSE_ERROR.length());
                } else if (respStr.startsWith(PREFIX_IS_METLOG_SUPPORT)) {
                    String[] splited = respStr.split(",");
                    if (splited.length >= 2) {
                        extraStr = splited[1].trim();
                    } else {
                        extraStr = "";
                    }
                    setSpecialFeatureSupport(extraStr);
                    return;
                } else if (isGetValueResp(respStr)) {
                    String[] command = respStr.split("=");
                    String[] result = command[1].split(",");
                    setValue(Integer.parseInt(result[0].trim()));
                    return;
                }
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "unexpected response!");

            }

            handler.obtainMessage(msgType, msgArg1, -1, extraStr).sendToTarget();
        }

        private boolean isGetValueResp(String respStr) {
            boolean rs = false;
            for (int i = 0; i < GET_VALUES_MAP.size(); i++) {
                if (respStr.startsWith(GET_VALUES_MAP.valueAt(i))) {
                    rs = true;
                    break;
                }
            }
            return rs;
        }
    }
}
