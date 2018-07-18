package com.mediatek.mtklogger.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;

/**
 * @author MTK81255
 *
 */
public class NetworkLog extends LogInstance {
    private static final String TAG = Utils.TAG + "/NetworkLog";

    public static final String SOCKET_NAME = "netdiag";
    private static final String COMMAND_SD_START = "tcpdump_sdcard_start";
    private static final String COMMAND_SD_STOP = "tcpdump_sdcard_stop";
    private static final String COMMAND_SD_STOP_WITHOUT_PING = "tcpdump_sdcard_stop_noping";
    private static final String COMMAND_PHONE_START = "tcpdump_data_start";
    private static final String COMMAND_PHONE_STOP = "tcpdump_data_stop";
    private static final String COMMAND_PHONE_STOP_WITHOUT_PING = "tcpdump_data_stop_noping";
    private static final String COMMAND_SD_CHECK = "tcpdump_sdcard_check";
    private static final String COMMAND_SET_STORAGE_PATH = "set_storage_path,";

    /**
     * Response string type, which command this response is for.
     */
    private static final int RESP_MSG_BASE = 32;
    private static final int RESP_TYPE_START = 1;
    private static final int RESP_TYPE_STOP = 2;
    private static final int RESP_TYPE_TAG = 3;
    private static final int RESP_TYPE_CHECK = 4;
    private static final int RESP_TYPE_SHELL_START = 5;
    private static final int RESP_TYPE_SHELL_STOP = 6;
    private static final int RESP_TYPE_MONITOR = 7;
    private static final int RESP_TYPE_UNKNOWN = 8;

    private static final String STOP_REASON_LOG_FOLDER_LOST = "folder_lost";

    private SharedPreferences mDefaultSharedPreferences = null;

    /**
     * @param context Context
     * @param handler Handler
     */
    public NetworkLog(Context context, Handler handler) {
        super(context);
        HandlerThread handlerThread = new HandlerThread("netlogHandler");
        handlerThread.start();
        mHandler = new NetworkLogHandler(handlerThread.getLooper());
        mLogConnectionThread = new LogConnectionThread(
                new NetworkLogConnection(SOCKET_NAME, mHandler));
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
    class NetworkLogHandler extends LogHandler {
        NetworkLogHandler(Looper loop) {
            super(loop);
        }
        @Override
        public void handleMessage(Message msg) {
            Utils.logi(TAG, "Handle receive message, what=" + msg.what);
            // Check whether we need to connect to native layer at the very
            // begin
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
                                    Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                    break;
                }
                int logStorageStatus = getLogStorageState();
                if (logStorageStatus != Utils.STORAGE_STATE_OK) {
                    Utils.loge(TAG, "Log storage is not ready yet.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    if (logStorageStatus == Utils.STORAGE_STATE_NOT_READY) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                                Utils.REASON_STORAGE_NOT_READY);
                    } else if (logStorageStatus == Utils.STORAGE_STATE_FULL) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_STORAGE_FULL);
                    }
                } else {
                    mLogConnection.sendCmd(COMMAND_SET_STORAGE_PATH
                            + Utils.getCurrentLogPath(sContext));
                    // Get log size first
                    SharedPreferences defaultPreferences =
                            PreferenceManager.getDefaultSharedPreferences(sContext);
                    String logSizeStr =
                            defaultPreferences.getString(Utils.KEY_LOG_SIZE_NETWORK, "");
                    int logSize = Utils.DEFAULT_LOG_SIZE;
                    if (!TextUtils.isEmpty(logSizeStr)) {
                        try {
                            int tempSize = Integer.parseInt(logSizeStr);
                            if (tempSize > 0) {
                                logSize = tempSize;
                            }
                        } catch (NumberFormatException e) {
                            Utils.loge(TAG, "parser logSizeStr failed : " + logSizeStr);
                        }
                    }

                    String startCmd = "";
                    String logPathType = Utils.getLogPathType();
                    if (Utils.LOG_PATH_TYPE_PHONE.equals(logPathType)) {
                        startCmd = COMMAND_PHONE_START + "_" + logSize;
                    } else {
                        startCmd = COMMAND_SD_START + "_" + logSize;
                    }
                    int limitedPackageSize = getLimitedPackageSize();
                    Utils.logi(TAG, "limitedPackageSize=" + limitedPackageSize);
                    if (limitedPackageSize > 0) {
                        startCmd = startCmd + ",-s" + limitedPackageSize;
                    }
                    boolean cmdSuccess = mLogConnection.sendCmd(startCmd);
                    if (!cmdSuccess) {
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
                    String stopCmd = "";
                    String logPathType = Utils.getLogPathType();

                    SharedPreferences defaultPreferences =
                            PreferenceManager.getDefaultSharedPreferences(sContext);
                    boolean needPing = defaultPreferences.getBoolean("networklog_ping_flag", false);
                    Utils.logi(TAG, "NetworkLog ping at stop time flag = " + needPing);

                    if (Utils.LOG_PATH_TYPE_PHONE.equals(logPathType)) {
                        if (needPing) {
                            stopCmd = COMMAND_PHONE_STOP;
                        } else {
                            stopCmd = COMMAND_PHONE_STOP_WITHOUT_PING;
                        }
                    } else {
                        if (needPing) {
                            stopCmd = COMMAND_SD_STOP;
                        } else {
                            stopCmd = COMMAND_SD_STOP_WITHOUT_PING;
                        }
                    }
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logi(TAG, "Network log stop reason = " + cmdReason);
                        if (Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE.equals(cmdReason)
                                || Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT.equals(cmdReason)
                                || STOP_REASON_LOG_FOLDER_LOST.equals(cmdReason)) {
                            // need to stop network log because of bad
                            // storage or log folder lost,
                            // send down a check command
                            cmdSuccess = mLogConnection.sendCmd(COMMAND_SD_CHECK);
                            if (cmdSuccess) {
                                Utils.logd(TAG,
                                        "Native will not response to check command,"
                                        + " assum it to be successful.");
                                String stopReason = Utils.REASON_STORAGE_UNAVAILABLE;
                                if (STOP_REASON_LOG_FOLDER_LOST.equals(cmdReason)) {
                                    stopReason = Utils.REASON_LOG_FOLDER_DELETED;
                                } else if (Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT
                                        .equals(cmdReason)) {
                                    stopReason = Utils.REASON_WAIT_SD_TIMEOUT;
                                }
                                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, stopReason);
                            }
                        } else {
                            Utils.logw(TAG, "Unsupported stop reason, ignore it.");
                            cmdSuccess = mLogConnection.sendCmd(stopCmd);
                        }
                    } else {
                        Utils.logd(TAG, "Normally stop network log with stop command");
                        cmdSuccess = mLogConnection.sendCmd(stopCmd);
                    }
                    if (!cmdSuccess) {
                        Utils.loge(TAG,
                                "Send stop command to native layer fail,"
                                + " maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    }
                } else {
                    Utils.logw(TAG, "Have not connected to native layer, just ignore this command");
                    // Lost connection to native layer, treat it as stopped
                    // and report reason to UI
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                }
                break;
            case MSG_START_SHELL_CMD:
                Object cmdObj = msg.obj;
                if (cmdObj == null) {
                    Utils.loge(TAG, "Fail to get start shell command.");
                    return;
                }
                String cmdStr = (String) cmdObj;
                if (TextUtils.isEmpty(cmdStr)) {
                    Utils.logw(TAG, "Please give me a not null command to run in shell.");
                    return;
                }
                Utils.logd(TAG, "Send command[" + cmdStr + "] to shell now.");
                mLogConnection.sendCmd(Utils.START_CMD_PREFIX + cmdStr);
                break;
            case MSG_STOP_SHELL_CMD:
                Utils.logd(TAG, "Stop former sent shell command now.");
                mLogConnection.sendCmd(Utils.STOP_CMD_PREFIX);
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
                    mLogConnection.stop();
                    mLogConnection = null;
                }
                mBConnected = false;
                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_DIE);
                break;
            default:
                if (msg.what > RESP_MSG_BASE) {
                    int respType = msg.what - RESP_MSG_BASE;
                    String failReason = "";
                    Object failReasonObj = msg.obj;
                    if (failReasonObj != null && (failReasonObj instanceof String)) {
                        failReason = (String) failReasonObj;
                    }
                    Utils.logi(TAG, "Resp type: " + respType + ", failReason string: ["
                            + failReason + "]");
                    if (respType == RESP_TYPE_START || respType == RESP_TYPE_STOP
                            || respType == RESP_TYPE_CHECK || respType == RESP_TYPE_MONITOR) {

                        // If respType == RESP_TYPE_MONITOR, failReason will not
                        // be empty
                        if (!TextUtils.isEmpty(failReason)) {
                            notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, failReason);
                        } else {
                            if (respType == RESP_TYPE_START) {
                                notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");
                            } else if (respType == RESP_TYPE_STOP || respType == RESP_TYPE_CHECK) {
                                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, "");
                            }
                        }
                    } else {
                        Utils.logd(TAG, "Ignore response whose type = " + respType);
                    }
                } else {
                    Utils.logw(TAG, "Not supported message, tell father to deal with it: "
                                    + msg.what);
                    super.handleMessage(msg, Utils.LOG_TYPE_NETWORK);
                    break;
                }
                break;
            }
        }
    }

    /**
     * Notify current network log running status to other component.
     *
     * @param status
     * @param reason
     */
    protected void notifyServiceStatus(int status, String reason) {
        Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status +
                ",  reason=[" + reason + "]");

        if (Utils.VALUE_STATUS_RUNNING == status) {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_RUNNING).apply();
            }
            updateStatusBar(Utils.LOG_TYPE_NETWORK, R.string.notification_title_network, true);
        } else {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_STOPPED).apply();
            }
            updateStatusBar(Utils.LOG_TYPE_NETWORK, R.string.notification_title_network, false);
        }
        // Notify service about NetworkLog state change
        if (mIsNeedNotifyLogStatusChanged) {
            mCmdResHandler.obtainMessage(Utils.MSG_LOG_STATE_CHANGED, Utils.LOG_TYPE_NETWORK,
                    status, reason).sendToTarget();
        }
        mIsNeedNotifyLogStatusChanged = true;
        super.notifyServiceStatus(status, reason);
    }

    /**
     * @author MTK81255
     *
     */
    class NetworkLogConnection extends LogConnection {
        public NetworkLogConnection(String sockname, Handler handler) {
            super(sockname, handler);
        }

        @Override
        public void dealWithResponse(byte[] respBuffer, Handler handler) {
            super.dealWithResponse(respBuffer, handler);
            if (respBuffer == null || respBuffer.length < 2) {
                Utils.logw(TAG, "Get an invalid response from native, ignore it.");
                return;
            }
            Utils.logi(TAG, "-->dealWithResponse(), resp=" + new String(respBuffer));

            int respType = RESP_MSG_BASE;
            switch (respBuffer[0]) {
            case 't': // tcpdump
                respType += RESP_TYPE_START;
                break;
            case 'p': // stop tcpdump command
                respType += RESP_TYPE_STOP;
                break;
            case 'k': // check command
                respType += RESP_TYPE_CHECK;
                break;
            case 'r': // run shell command
                respType += RESP_TYPE_SHELL_START;
                break;
            case 's': // stop shell command
                respType += RESP_TYPE_SHELL_STOP;
                break;
            case 'g': // tag command
                respType += RESP_TYPE_TAG;
                break;
            case 'm': // monitor log running status, have something to report
                respType += RESP_TYPE_MONITOR;
                break;
            default:
                respType += RESP_TYPE_UNKNOWN;
                Utils.logw(TAG, "Unknown response type");
                break;
            }

            String failReason = null;
            switch (respBuffer[1]) {
            case 'o': // everything is ok
                failReason = "";
                break;
            case 'w': // something wrong has happened: wrong
                failReason = Utils.REASON_COMMON;
                break;
            case 'd': // could not find or create log folder: dir
                failReason = Utils.REASON_LOG_FOLDER_CREATE_FAIL;
                break;
            case 'l': // there is not enough storage for log saving: low
                failReason = Utils.REASON_STORAGE_FULL;
                break;
            case 'g': // Log folder was deleted by user: gone
                failReason = Utils.REASON_LOG_FOLDER_DELETED;
                break;
            case 'f':
                failReason = Utils.REASON_TCPDUMP_FAILED;
                break;
            default:
                Utils.loge(TAG, "Unkonwn response value: " + respBuffer[0]);
                break;
            }
            Utils.logd(TAG, "Response from native type=" + respType + ", failReason=" + failReason);
            if ((respType - RESP_MSG_BASE) == RESP_TYPE_START) {
                mCmdResHandler.obtainMessage(Utils.MSG_START_LOGS_DONE, Utils.LOG_TYPE_NETWORK,
                        (failReason == null || failReason.isEmpty()) ? 1 : 0).sendToTarget();
                mIsNeedNotifyLogStatusChanged = false;
            } else if ((respType - RESP_MSG_BASE) == RESP_TYPE_STOP
                    || (respType - RESP_MSG_BASE) == RESP_TYPE_CHECK) {
                mCmdResHandler.obtainMessage(Utils.MSG_STOP_LOGS_DONE, Utils.LOG_TYPE_NETWORK,
                        (failReason == null || failReason.isEmpty()) ? 1 : 0).sendToTarget();
                mIsNeedNotifyLogStatusChanged = false;
            }
            if (handler != null) {
                handler.obtainMessage(respType, failReason).sendToTarget();
            } else {
                Utils.loge(TAG, "Need to send message[" + respType + "], but handler is null");
            }
        }
    }

    @Override
    public void checkLogFolder() {
        super.checkLogFolder();
        String currentLogPathFromNative =
                SystemProperties.get(Utils.KEY_SYSTEM_PROPERTY_NETLOG_SAVING_PATH);
        File currentLogFolder = new File(currentLogPathFromNative);
        int runningStatus =
                mSharedPreferences.getInt(Utils.KEY_STATUS_NETWORK, Utils.VALUE_STATUS_STOPPED);
        if (runningStatus == Utils.VALUE_STATUS_RUNNING && !currentLogFolder.exists()) {
            Utils.logw(TAG, "NetworkLog is running, but could not found log folder("
                    + currentLogPathFromNative + ") from Java layer, just a remind.");
            // mHandler.obtainMessage(MSG_STOP,
            // STOP_REASON_LOG_FOLDER_LOST).sendToTarget();
        }
    }

    /**
     * Get each network log package limited size, 0 mean no limitation.
     *
     * @return
     */
    private int getLimitedPackageSize() {
        if (mDefaultSharedPreferences == null) {
            mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(sContext);
        }
        boolean limitPackageEnabled =
                mDefaultSharedPreferences.getBoolean(Utils.KEY_NT_LIMIT_PACKAGE_ENABLER, false);
        if (!limitPackageEnabled) {
            return 0;
        }
        String limitedPackageSizeStr =
                mDefaultSharedPreferences.getString(Utils.KEY_NT_LIMIT_PACKAGE_SIZE,
                        String.valueOf(Utils.VALUE_NT_LIMIT_PACKAGE_DEFAULT_SIZE));

        int limitedPackageSize = Utils.VALUE_NT_LIMIT_PACKAGE_DEFAULT_SIZE;
        if (!TextUtils.isEmpty(limitedPackageSizeStr)) {
            try {
                int tempSize = Integer.parseInt(limitedPackageSizeStr);
                if (tempSize >= 0) {
                    limitedPackageSize = tempSize;
                }
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "parser limitedPackageSizeStr failed : " + limitedPackageSizeStr);
            }
        }
        return limitedPackageSize;
    }
}
