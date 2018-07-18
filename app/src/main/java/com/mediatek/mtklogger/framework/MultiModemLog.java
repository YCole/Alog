package com.mediatek.mtklogger.framework;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Window;
import android.view.WindowManager;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.settings.ModemLogSettings;
import com.mediatek.mtklogger.utils.Utils;

import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class can be used to control multiple modem instance. If any modem
 * instance happens unexpected event like die, then we need to re-initiate all
 * instance again.
 */
public class MultiModemLog extends LogInstance {
    private static final String TAG = Utils.TAG + "/MultiModemLog";

    /**
     * This segment will be used to support multiple modem instance.
     */
    // need modify for K2 project mdlogger rename
    public static final String SOCKET_NAME_MD = "com.mediatek.mdlogger.socket";
    public static final String SOCKET_NAME_MD1 = "com.mediatek.mdlogger.socket";

    public static final String SOCKET_NAME_MD2 = "com.mediatek.dualmdlogger.socket";

    public static final String SOCKET_NAME_MD_LTE = "com.mediatek.emcsmdlogger.socket";

    public static final SparseArray<String> MODEM_INDEX_SOCKET_MAP = new SparseArray<String>();
    static {
        MODEM_INDEX_SOCKET_MAP.put(Utils.MODEM_LOG_TYPE_DEFAULT, SOCKET_NAME_MD1);
        MODEM_INDEX_SOCKET_MAP.put(Utils.MODEM_LOG_TYPE_DUAL, SOCKET_NAME_MD2);
        MODEM_INDEX_SOCKET_MAP.put(Utils.MODEM_LOG_TYPE_EXT, SOCKET_NAME_MD_LTE);
        for (int i = 1; i <= Utils.MODEM_MAX_COUNT; i++) {
            if (Utils.isTypeMDEnable(i)) {
                MODEM_INDEX_SOCKET_MAP.put(Utils.MODEM_LOG_K2_INDEX + i, SOCKET_NAME_MD + i);
                Utils.logd(TAG + "/Utils", "MODEM_INDEX_SOCKET_MAP added index: " + i);
            }
        }
    }

    private static final int C2KLOGGER_INDEX = 3;

    private static final String RESPONSE_START_MEMORY_DUMP = "MEMORYDUMP_START";

    private static final String RESPONSE_FINISH_MEMORY_DUMP = "MEMORYDUMP_DONE";

    private static final String RESPONSE_START_LOG_FLUSH = "LOG_FLUSH_START";

    private static final String RESPONSE_FINISH_LOG_FLUSH = "LOG_FLUSH_DONE";

    private static final String RESPONSE_LOGGING_FILE_NOTEXIST = "LOGFILE_NOTEXIST";

    private static final String RESPONSE_FAIL_SEND_FILTER = "FAIL_SENDFILTER";

    private static final String RESPONSE_FAIL_WRITE_FILE = "FAIL_WRITEFILE";

    private static final String RESPONSE_SDCARD_FULL = "SDCARD_FULL";

    private static final String RESPONSE_ANOTHER_MD_POWER_OFF = "ANOTHER_MD_POWER_OFF";

    private static final String RESPONSE_FINISH_MEMORY_DUMP_FILE = "MEMORYDUMP_FILE";
    private static final String RESPONSE_NEED_DUMP_FILE = "need_dump_file";

    /**
     * ADB shell command Broadcast for ModemLog.
     */
    private static final String ADB_COMMAND_EXIT = "exit";

    private static final String ADB_COMMAND_PAUSE = "pause";

    private static final String ADB_COMMAND_RESUME = "resume";

    public static final String ADB_COMMAND_FORCE_MODEM_ASSERT = "force_modem_assert";

    private static final String ADB_COMMAND_AUTO_TEST_START_MDLOGGER = "auto_test_start_mdlogger";

    public static final String ADB_COMMAND_MODEM_AUTO_RESET = "modem_auto_reset_";

    private static final String COMMAND_START = "start,";

    // for customize PLS mode

    private static final String ADB_COMMAND_SILENT_START = "silent_start";

    private static final String ADB_COMMAND_SILENT_STOP = "silent_stop";

    private static final String ADB_COMMAND_FLUSH = "log_flush";

    private static final String ADB_COMMAND_CUSTOM_PATH = "silent_log_path";
    /**
     * Like start from UI, then need to change auto start value at the same
     * time.
     */
    private static final String COMMAND_START_WITH_CONFIG = "deep_start,";

    private static final String COMMAND_PAUSE = "pause";
    private static final String COMMAND_PAUSE_WITH_CONFIG = "deep_pause";

    private static final String COMMAND_RESUME = "resume";

    private static final String COMMAND_SETAUTO = "setauto,";

    private static final String COMMAND_LOG_FLUSH = "log_flush,";

    private static final String COMMAND_STOP = "stop";

    private static final String COMMAND_SET_LOGSIZE = "setlogsize,";
    private static final String COMMAND_SET_STORAGE_PATH = "set_storage_path,";

    private static final String COMMAND_NOTIFY_TETHER_CHANGE = "usbtethering";

    /**
     * Command to query whether modem is still running.
     */
    private static final String COMMAND_ISPAUSED = "ispaused";

    /**
     * Command for begin memory dump.
     */
    private static final String COMMAND_POLLING = "polling";

    /**
     * After memory dump done, use this to reset modem.
     */
    private static final String COMMAND_RESET = "resetmd";

    /**
     * Query modem log current status.
     * 0:pause, 1:common running, 2:polling, 3:copying.
     */
    public static final String COMMAND_GET_STATUS = "getstatus";
    /**
     * Query modem log filter file information.
     * Response format: get_filter_info,filterpath;modifiedtime;filesize;
     */
    public static final String COMMAND_GET_FILTER_INFO = "get_filter_info";

    public static final String PREFIX_IS_GPS_SUPPORT = "is_gps_support";
    public static final String PREFIX_GET_CCB_GEAR_ID = "get_ccb_gear_id";
    public static final String PREFIX_SET_CCB_GEAR_ID = "set_ccb_gear_id";

    /**
     * If PLS is enabled, disable modem silent reboot IF PLS is disabled, enable
     * modem silent reboot.
     */
//    private static final String COMMAND_MDSILRBT = "mdsilrbt,";

    /**
     * Each control command to native will have a executing response, use this
     * to update command. executing status
     */
    private static final String RESPONSE_COMMAND_RESULT_SUCCESS = "1";
    private static final String RESPONSE_COMMAND_RESULT_FAIL = "0";
    /**
     * In flight mode, start modem log will be failed.
     */
    private static final String RESPONSE_COMMAND_RESULT_FAIL_IN_FLIGHT_MODE = "19";

    private static final String SHOW_TOAST = "showToast";
    private static final String SHOW_DIALOG = "showDialog";

    /**
     * Tell modem begin to dump memory.
     */
    private static final int MSG_BEGIN_DUMP = MSG_SPECIAL_BASE + 1;

    /**
     * Tell modem begin to reset.
     */
    private static final int MSG_BEGIN_RESET = MSG_SPECIAL_BASE + 2;

    /**
     * Show reset modem progress dialog
     */
    // private static final int MSG_SHOW_RESET_DIALOG = MSG_SPECIAL_BASE + 3;

    /**
     * Dismiss reset modem progress dialog.
     */
    private static final int MSG_DISMISS_RESET_DIALOG = MSG_SPECIAL_BASE + 4;

    /**
     * Response message of memory dump begin.
     */
    private static final int MSG_MEMORY_DUMP_START = MSG_SPECIAL_BASE + 20;

    /**
     * Response message of memory dump done.
     */
    private static final int MSG_MEMORY_DUMP_FINISH = MSG_SPECIAL_BASE + 21;

    private static final int MSG_NO_LOGGING_FILE = MSG_SPECIAL_BASE + 22;

    private static final int MSG_SEND_FILTER_FAIL = MSG_SPECIAL_BASE + 23;

    private static final int MSG_WRITE_FILE_FAIL = MSG_SPECIAL_BASE + 24;

    private static final int MSG_SDCARD_FULL = MSG_SPECIAL_BASE + 25;

    private static final int MSG_QUERY_PAUSE_STATUS = MSG_SPECIAL_BASE + 26;

    /**
     * Response message of log flush begin.
     */
    private static final int MSG_LOG_FLUSH_START = MSG_SPECIAL_BASE + 27;

    /**
     * Response message of log flush done.
     */
    private static final int MSG_LOG_FLUSH_FINISH = MSG_SPECIAL_BASE + 28;

    /**
     * Notify modem_log to do flush.
     */
    public static final int MSG_NOTIFY_LOG_FLUSH_START = MSG_SPECIAL_BASE + 29;

    public static final int MSG_MEMORYDUMP_START_TIMEOUT = MSG_SPECIAL_BASE + 31;
    public static final int MSG_MEMORYDUMP_DONE_TIMEOUT = MSG_SPECIAL_BASE + 32;
    private static final int MSG_QUERY_NEED_DUMP_FILE = MSG_SPECIAL_BASE + 34;
    // Use this to notify user when modem reset done
    // private Ringtone assertRingtone = null;

    // private Uri alertRingUri = null;

    private SharedPreferences mDefaultSharedPreferences;

//    private boolean mConnected = false;

    private ModemManager mModemManager = null;

    private int mCurrentStage = Utils.RUNNING_STAGE_IDLE;

    /**
     * Flag to show which MD is running at present, 0 for all stopped, 1 for
     * MD1, 2 for MD2 and 3 for both.
     */
    private int mCurrentStatus = Utils.LOG_RUNNING_STATUS_UNKNOWN;

    /**
     * Whether modem reset dialog is showing.
     */
    private boolean mIsModemResetDialogShowing = false;

    private ProgressDialog mResetModemDialog;
    private Timer mTimer;
    private int mResetTimeout = 0;

    private String mMdDumpPath = "";
    private boolean mIsNeedOtherDump = false;
    /**
     * add feature 2015.8.12:
     * use this flag to resolve :when it is false(in dumping not finish),
     * reject all command from UI.
     */
    private boolean mIsAllMemoryDumpDone = true;
    private int mMsgArg1ForReset = -1;

    private String mMdFlushPath = "";
    private boolean mIsAllFlushDone = false;

    /**
     * @param context
     *            Context
     * @param handler
     *            Handler
     */
    public MultiModemLog(Context context, Handler handler) {
        super(context);
        Utils.logd(TAG, "-->new MultiModemLog start");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mCmdResHandler = handler;
        HandlerThread handlerThread = new HandlerThread("modemlogHandler");
        handlerThread.start();
        mHandler = new ModemLogHandler(handlerThread.getLooper());
        mLogConnectionThread = new ModemLogThread(
                new ModemLogConnection(Utils.MODEM_LOG_TYPE_DEFAULT, SOCKET_NAME_MD1, mHandler));
        mLogConnectionThread.start();
        synchronized (mLogConnectionLock) {
            try {
                mLogConnectionLock.wait(Utils.DURATION_WAIT_LOG_INSTANCE_READY);
            } catch (InterruptedException e) {
                Utils.logi(TAG, "Wait gps log sub thread initialization, but was interrupted");
            }
        }
        Utils.logd(TAG, "<--new MultiModemLog done!");
    }

    /**
     * Let each log instance run in a separate thread, to avoid blocking main
     * thread.
     */
    class ModemLogThread extends LogConnectionThread {
        public ModemLogThread(LogConnection logConnection) {
            super(logConnection);
        }

        @Override
        public void run() {
            mModemManager = new ModemManager(mHandler);
            synchronized (mLogConnectionLock) {
                mLogConnectionLock.notify();
            }
        }
    }

    @Override
    public int getGlobalRunningStage() {
        return mCurrentStage;
    }

    @Override
    public int getLogRunningStatus() {
        return mCurrentStatus;
    }

    @Override
    public boolean isLogRunning(int logType) {
        boolean isLogRunning = super.isLogRunning(logType);
        if (Utils.isDenaliMd3Solution()) {
            isLogRunning |= isC2KLoggerRunning();
        }
        if (isLogRunning) {
            if (mModemManager != null) {
                isLogRunning =  mModemManager.isAnyModemLogConnect();
            }
        }
        Utils.logd(TAG, "Is modm log running ? " + isLogRunning);
        return isLogRunning;
    }

    @Override
    public String getRunningLogPath(int logType) {
        Utils.logd(TAG, "-->getRunningLogPath(), logType = " + logType);
        if (!isLogRunning(logType)) {
            return null;
        }
        if (Utils.sAvailableModemList.size() == 0) {
            return super.getRunningLogPath(logType);
        }
        String runningLogPath = null;
        for (int modemIndex : Utils.sAvailableModemList) {
            if (modemIndex == (Utils.MODEM_LOG_K2_INDEX + Utils.C2KLOGGER_INDEX)
                    && Utils.isDenaliMd3Solution()) {
                String c2kModemPath =
                        mSharedPreferences.getString(
                                Utils.KEY_C2K_MODEM_LOGGING_PATH, "");
                Utils.logd(TAG, "c2kModemExceptionPath = " + c2kModemPath);
                File c2kModemFile = new File(c2kModemPath);
                if (null != c2kModemFile && c2kModemFile.exists()) {
                    if (runningLogPath != null) {
                        runningLogPath += ";" + c2kModemFile.getAbsolutePath();
                    } else {
                        runningLogPath = c2kModemFile.getAbsolutePath();
                    }
                }
                continue;
            }
            String modemLogFolder = Utils.MODEM_INDEX_FOLDER_MAP
                    .get(modemIndex + Utils.MODEM_LOG_K2_INDEX);
            String logPath = Utils.getCurrentLogPath(sContext)
                    + Utils.MTKLOG_PATH + modemLogFolder;

            File fileTree = new File(logPath + File.separator + Utils.LOG_TREE_FILE);
            File logFile = Utils.getLogFolderFromFileTree(fileTree, false);
            if (null != logFile && logFile.exists()) {
                if (runningLogPath != null) {
                    runningLogPath += ";" + logFile.getAbsolutePath();
                } else {
                    runningLogPath = logFile.getAbsolutePath();
                }
            }
        }
        Utils.logi(TAG, "<--getRunningLogPath(), runningLogPath = " + runningLogPath);
        return runningLogPath;
    }

    /**
     * Whether need to start modem log at boot time.
     *
     * @return boolean
     */
    private boolean getAutoStartValue() {
        boolean defaultValue = Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(Utils.LOG_TYPE_MODEM);
        if (mDefaultSharedPreferences != null) {
            defaultValue =
                    mDefaultSharedPreferences.getBoolean(Utils.KEY_START_AUTOMATIC_MODEM,
                            defaultValue);
        }
        Utils.logv(TAG, " getAutoStartValue(), value=" + defaultValue);
        return defaultValue;
    }

    private String getCurrentMode() {
        return getCurrentModeMd1();
    }

    private String getCurrentModeMd1() {
        String mode = Utils.MODEM_MODE_SD;
        if (mDefaultSharedPreferences != null) {
            mode = mDefaultSharedPreferences.getString(Utils.KEY_MD_MODE_1, Utils.MODEM_MODE_SD);
        }
        return mode;
    }

    private String getCurrentModeMd2() {
        String mode = Utils.MODEM_MODE_SD;
        if (mDefaultSharedPreferences != null) {
            mode = mDefaultSharedPreferences.getString(Utils.KEY_MD_MODE_2, Utils.MODEM_MODE_SD);
        }
        return mode;
    }

    private boolean setCurrentMode(String newMode, String modemType) {
        Utils.logi(TAG, "-->setCurrentMode(), newMode=" + newMode + "; modemType=" + modemType);
        if (Utils.MODEM_MODE_IDLE.equals(newMode) || Utils.MODEM_MODE_USB.equals(newMode)
                || Utils.MODEM_MODE_SD.equals(newMode) || Utils.MODEM_MODE_PLS.equals(newMode)) {
            if (mDefaultSharedPreferences != null) {
                Utils.logv(TAG, "Persist new modem log mode");
                if (Utils.sAvailableModemList.size() == 1 || modemType.equals("1")) {
                    mDefaultSharedPreferences.edit().putString(Utils.KEY_MD_MODE_1, newMode)
                            .apply();
                } else {
                    mDefaultSharedPreferences.edit().putString(Utils.KEY_MD_MODE_2, newMode)
                            .apply();
                }
                //not send mdsilrbt to mdlog, request from HTC-16.4.26
//                mModemManager.sendCmd(COMMAND_MDSILRBT
//                        + (Utils.MODEM_MODE_PLS.equals(newMode) ? "0" : "1"));
                return true;
            } else {
                Utils.loge(TAG, "mDefaultSharedPreferences is null");
            }
        } else {
            Utils.logw(TAG, "Unsupported log mode");
        }
        return false;
    }

    private void setLogSize(String newSize, String modemIndex) {
        Utils.logi(TAG, "-->setLogSize(), newSize=" + newSize + "; modemIndex="
                + modemIndex);
        if (Utils.sAvailableModemList.size() == 1 || modemIndex.equals("1")) {
            mModemManager.sendCmd(Utils.sAvailableModemList.get(0)
                    + Utils.MODEM_LOG_K2_INDEX, COMMAND_SET_LOGSIZE + newSize);
            mDefaultSharedPreferences.edit()
                    .putString(Utils.KEY_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MODEM), newSize)
                    .apply();
        } else {
            mModemManager.sendCmd(Utils.sAvailableModemList.get(1)
                    + Utils.MODEM_LOG_K2_INDEX, COMMAND_SET_LOGSIZE + newSize);
        }
    }

    /**
     * @author MTK81255
     *
     */
    class ModemLogHandler extends LogHandler {
        ModemLogHandler(Looper loop) {
            super(loop);
        }
        @Override
        public void handleMessage(Message msg) {
            Utils.logi(TAG, "Handle receive message, what=" + msg.what + ", msg.arg1=[" + msg.arg1
                    + "]");
            // Check whether we need to connect to native layer at the very
            // beginning
            // For stop message, since Service may be killed unfortunately while
            // native is still running,
            // then we need to re-initialize connection to native layer if
            // connection is lost

            if (mLogConnectionThread != null && mLogConnectionThread.isAlive()) {
                try {
                    mLogConnectionThread.join();
                    mCurrentStatus = Utils.LOG_RUNNING_STATUS_UNKNOWN;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (!mModemManager.isAnyModemLogConnect()) {
                mModemManager.initLogConnection();
                // Connection just re-connected, need to query log running
                // status
                mCurrentStatus = Utils.LOG_RUNNING_STATUS_UNKNOWN;
            }
            // This object may contain detail reason for this command
            // For different trigger reason, different command is needed
            Object cmdReasonObj = msg.obj;
            String cmdReason = null;
            if ((cmdReasonObj != null) && (cmdReasonObj instanceof String)) {
                cmdReason = (String) cmdReasonObj;
            }
            // After reconnect to modem, check whether need to show modem reset
            // dialog,
            // since the former one maybe interrupted by system process killer
            if (mModemManager.isAnyModemLogConnect()) {
                String assertFileStr =
                        mSharedPreferences.getString(Utils.KEY_MODEM_ASSERT_FILE_PATH, "");
                if (!TextUtils.isEmpty(assertFileStr) && (msg.what != MSG_MEMORY_DUMP_FINISH)
                        && mIsAllMemoryDumpDone) {
                    int index = assertFileStr.indexOf(";");
                    if (index >= 1) {
                        try {
                            int modemIndex = Integer.parseInt(assertFileStr.substring(0, index));
                            String path = assertFileStr.substring(index + 1);
                            showMemoryDumpDoneDialog(modemIndex, path);
                        } catch (NumberFormatException e) {
                            Utils.loge(TAG, "Cached modem assert file format invalid");
                        }
                    }
                }
            }
            switch (msg.what) {
            case MSG_INIT:
                if (!mModemManager.isAnyModemLogConnect()) {
                    Utils.loge(TAG, "Fail to establish connection to native layer.");
                    if (mSharedPreferences != null
                            && (Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_RUNNING))) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                    break;
                }
                int logStorageStatus = getLogStorageState();
                String currentMode = getCurrentMode();
                // For USB mode, do not need to care storage status
                if (!Utils.MODEM_MODE_USB.equals(currentMode)
                        && logStorageStatus != Utils.STORAGE_STATE_OK) {
                    Utils.loge(TAG, "Log storage is not ready yet.");
                    if (mSharedPreferences != null
                            && Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                                    Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_RUNNING)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                    }
                    if (logStorageStatus == Utils.STORAGE_STATE_NOT_READY) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                                Utils.REASON_STORAGE_NOT_READY);
                    } else if (logStorageStatus == Utils.STORAGE_STATE_FULL) {
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_STORAGE_FULL);
                    }
                } else {
                    boolean cmdSuccess = false;
                    mModemManager.sendCmd(COMMAND_SET_STORAGE_PATH
                            + Utils.getModemLogPath(sContext));
                    String commandStr = COMMAND_START;
                    if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                        Utils.logw(TAG, "Start command come from UI,"
                                + " change log auto start to true at the same time");
                        commandStr = COMMAND_START_WITH_CONFIG;
                    }
                    if (Utils.isDenaliMd3Solution()) {
                        mModemManager.sendCmd(Utils.sAvailableModemList.get(1)
                                + Utils.MODEM_LOG_K2_INDEX,
                                COMMAND_SET_STORAGE_PATH + Utils.getModemLogPath(sContext));
                        cmdSuccess = mModemManager.sendCmd(commandStr + currentMode);
                        mStartStopModemTimes = 1;
                    } else {
                        Utils.logd(TAG, "Utils.getModemSize(), "
                                + Utils.sAvailableModemList.size());
                        if (Utils.sAvailableModemList.size() == 2) {
                            cmdSuccess =
                                    mModemManager.sendCmd(Utils.sAvailableModemList.get(0)
                                            + Utils.MODEM_LOG_K2_INDEX, commandStr
                                            + getCurrentModeMd1() + "," + getCurrentModeMd2());
                            cmdSuccess =
                                    mModemManager.sendCmd(Utils.sAvailableModemList.get(1)
                                            + Utils.MODEM_LOG_K2_INDEX, commandStr
                                            + getCurrentModeMd2() + "," + getCurrentModeMd1());
                            mStartStopModemTimes = 2;
                        } else {
                            cmdSuccess =
                                    mModemManager.sendCmd(commandStr + getCurrentModeMd1() + ","
                                            + getCurrentModeMd2());
                            mStartStopModemTimes = 1;
                        }
                    }
                    if (cmdSuccess) {
                        Utils.logd(TAG, "After sending start command, wait native's resp,"
                                + " not treat it as successfully directly");
                    } else {
                        Utils.loge(TAG, "Send start command to native layer fail,"
                                + " maybe connection has already been lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    }
                    startStopC2KLoggerService(true);

                    if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(cmdReason)) {
                        // First start command after boot, re-set log size to
                        // confirm info sync
                        String sizeStr =
                                mDefaultSharedPreferences.getString(
                                        Utils.KEY_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MODEM), "");
                        Utils.logi(TAG, "At first start time after boot,"
                                + " re-set log size to confirm info sync, sizeStr=" + sizeStr);
                        if (!TextUtils.isEmpty(sizeStr)) {
                            // Add a time sleep to avoid too frequent command to
                            // native
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Utils.loge(TAG,
                                        "Waiting set modem log size command was interrupted..");
                            }
                            mModemManager.sendCmd(COMMAND_SET_LOGSIZE + sizeStr);
                        } else {
                            Utils.loge(TAG,
                                    "Modem log size in default shared preference is empty.");
                        }
                    }
                }
                break;
            case MSG_STOP:
                if (mModemManager.isAnyModemLogConnect()) {
                    boolean cmdSuccess = false;
                    Utils.logd(TAG, "Receive stop command, stop reason=" + cmdReason);
                    String commandStr = COMMAND_PAUSE;

                    if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(cmdReason)) {
                        Utils.logw(TAG, "Stop command come from UI,"
                                + " change log auto start to false at the same time");
                        commandStr = COMMAND_PAUSE_WITH_CONFIG;
                    }
                    if (Utils.sAvailableModemList.size() == 2 && !Utils.isDenaliMd3Solution()) {
                        mStartStopModemTimes = 2;
                    } else {
                        mStartStopModemTimes = 1;
                    }
                    cmdSuccess = mModemManager.sendCmd(commandStr);
                    if (!cmdSuccess) {
                        Utils.loge(TAG, "Send stop command to native layer fail, "
                                + "maybe connection has already be lost.");
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_SEND_CMD_FAIL);
                    } else {
                        // } else {
                        // String stopReason = "";
                        // if(Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT.equals(cmdReason)){
                        // stopReason = Utils.REASON_WAIT_SD_TIMEOUT;
                        // } else
                        // if(Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE.equals(cmdReason)){
                        // stopReason = Utils.REASON_STORAGE_UNAVAILABLE;
                        // }
                        // notifyServiceStatus(Utils.VALUE_STATUS_STOPPED,
                        // stopReason);
                    }
                    startStopC2KLoggerService(false);
                } else {
                    Utils.logw(TAG, "Have not connected to native layer, just ignore this command");
                    // Lost connection to native layer, treat it as stopped and
                    // report reason to UI
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                }
                break;
            case MSG_CONFIG:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (!TextUtils.isEmpty(cmdReason)) {
                        Utils.logd(TAG, "Receive config command, parameter=" + cmdReason);
                        int arg1 = msg.arg1;
                        if (cmdReason.startsWith(PREFIX_CONFIG_AUTO_START)) {
                            if (cmdReason.endsWith("1")) {
                                if (mIsAllMemoryDumpDone) {
                                    mModemManager.sendCmdToOneInstance(
                                        COMMAND_SETAUTO + getCurrentModeMd1());
                                }
                                if (Utils.sAvailableModemList.size() == 2) {
                                    mModemManager.sendCmd(Utils.sAvailableModemList.get(1)
                                            + Utils.MODEM_LOG_K2_INDEX,
                                            COMMAND_SETAUTO + getCurrentModeMd2());
                                }
                            } else {
                                if (mIsAllMemoryDumpDone) {
                                    mModemManager.sendCmdToOneInstance(
                                        COMMAND_SETAUTO + Utils.MODEM_MODE_IDLE);
                                }
                                if (Utils.sAvailableModemList.size() == 2) {
                                    mModemManager.sendCmd(Utils.sAvailableModemList.get(1)
                                            + Utils.MODEM_LOG_K2_INDEX,
                                            COMMAND_SETAUTO + Utils.MODEM_MODE_IDLE);
                                }
                            }
                        } else if (cmdReason.startsWith(PREFIX_CONFIG_LOG_SIZE)) {
                            String sizeStr = cmdReason.substring(PREFIX_CONFIG_LOG_SIZE.length());
                            mModemManager.sendCmd(COMMAND_SET_LOGSIZE + sizeStr);
                        } else if (cmdReason.startsWith(PREFIX_TETHER_CHANGE_FLAG)) {
                            mModemManager.sendCmd(COMMAND_NOTIFY_TETHER_CHANGE);
                        } else if (cmdReason.startsWith(PREFIX_ENABLE_GPS_LOCATION)
                                || cmdReason.startsWith(PREFIX_DISABLE_GPS_LOCATION)) {
                            mModemManager.sendCmd(arg1, cmdReason);
                        } else {
                            mModemManager.sendCmdToOneInstance(cmdReason);
                        }
                    } else {
                        Utils.loge(TAG, "Receive config command, but parameter is null");
                    }
                } else {
                    Utils.logw(TAG, "Fail to config native parameter because of lost connection.");
                }
                break;
            case MSG_RESTORE_CONN:
                if (!mModemManager.isAnyModemLogConnect()) {
                    Utils.logw(TAG, "Reconnect to native layer fail! Mark log status as stopped.");
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_UNKNOWN);
                } else if (isLogRunning(Utils.LOG_TYPE_MODEM)) {
                    Utils.logw(TAG, "Modem log reconected, update this to user.");
                    int resetModemIndex = msg.arg1;
                    if (resetModemIndex != 0) {
                        boolean isConnected = mModemManager.logConnect(
                                Utils.MODEM_LOG_K2_INDEX + resetModemIndex);
                        Utils.logw(TAG, "Modem[" + resetModemIndex + "] is connected ? "
                                + isConnected);
                    }
                    notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");
                    initModemPargram();
                }
                break;
            case MSG_ADB_CMD:
                Utils.logd(TAG, "Receive adb command[" + cmdReason + "]");
                if (!TextUtils.isEmpty(cmdReason)) {
                    dealWithADBCommand(cmdReason);
                }
                break;
            case MSG_DIE:
                Utils.logw(TAG, "Modemlog [" + msg.arg1
                        + "] lost, do not stop other instance, just update status as stopped");
                if (mModemManager != null) {
                    mModemManager.stop(msg.arg1);
                }
                notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_DAEMON_DIE);
                break;
            case MSG_BEGIN_DUMP:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (isLogRunning(Utils.LOG_TYPE_MODEM)) {
                        mModemManager.sendCmdToOneInstance(COMMAND_POLLING);
                    } else {
                        Utils.logw(TAG, "Modem log is stopped, no dumping is allowed.");
                    }
                } else {
                    Utils.loge(TAG, "Lost connection to native, so ignore polling command.");
                }
                break;
            case MSG_BEGIN_RESET:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (Utils.isMTKSvlteSupport()) {
                        startStopC2KLoggerService(true);
                    }
                    // Only need send reset command to md1 for md1 & md3 can auto reset together.
                    // This is changed by modem team from 2016/06.(All N version).
                    mModemManager.sendCmd(msg.arg1, COMMAND_RESET);
                    showResetModemDialog();
                } else {
                    Utils.loge(TAG, "Lost connection to native,"
                            + " reset command will have no effect.");
                }
                break;
            case MSG_MEMORY_DUMP_START:
                mIsAllMemoryDumpDone = false;
                Utils.logi(TAG, "dump start " + "mIsAllMemoryDumpDone=" + mIsAllMemoryDumpDone);
                mCurrentStage = Utils.RUNNING_STAGE_POLLING_LOG;
                mCmdResHandler.obtainMessage(Utils.MSG_RUNNING_STAGE_CHANGE, mCurrentStage)
                        .sendToTarget();
                // Notify Taglog modem log dump is already on the way, so just
                // keep waiting
                mSharedPreferences.edit()
                        .putString(Utils.KEY_MODEM_EXCEPTION_PATH, Utils.FLAG_MDLOGGER_POLLING)
                        .apply();

                //create timer wait 10m until dump finish
                mMessageHandler
                    .sendMessageDelayed(
                    mMessageHandler.obtainMessage(MSG_MEMORYDUMP_DONE_TIMEOUT, msg.arg1),
                        Utils.MEMORYDUMP_DONE_TIMEOUT);
                if (Utils.isSupportC2KModem() && Utils.isMTKSvlteSupport()
                        && !Utils.MODEM_MODE_USB.equals(getCurrentModeMd1())
                        && !Utils.MODEM_MODE_USB.equals(getCurrentModeMd2())) {
                    // Waiting another modem memory dump start message.
                    if (mMessageHandler.hasMessages(MSG_MEMORYDUMP_START_TIMEOUT)) {
                        mMessageHandler.removeMessages(MSG_MEMORYDUMP_START_TIMEOUT);
                    } else {
                        if (Utils.sAvailableModemList.size() == 2) {
                            int otherModemIndex = Utils.sAvailableModemList.get(0)
                                                + Utils.MODEM_LOG_K2_INDEX;
                            if (msg.arg1 == otherModemIndex) {
                                otherModemIndex = Utils.sAvailableModemList.get(1)
                                                + Utils.MODEM_LOG_K2_INDEX;
                            }
                            mMessageHandler
                            .sendMessageDelayed(
                                    mMessageHandler.obtainMessage(
                                            MSG_MEMORYDUMP_START_TIMEOUT, otherModemIndex),
                                            Utils.MEMORYDUMP_START_TIMEOUT);
                        }
                    }
                }
                break;
             case MSG_MEMORY_DUMP_FINISH:
                mMessageHandler.removeMessages(MSG_MEMORYDUMP_START_TIMEOUT, msg.arg1);
                mMessageHandler.removeMessages(MSG_MEMORYDUMP_DONE_TIMEOUT, msg.arg1);
                Object pathObj = msg.obj;
                if (Utils.isSupportC2KModem() && Utils.isMTKSvlteSupport()
                        && !Utils.MODEM_MODE_USB.equals(getCurrentModeMd1())
                        && !Utils.MODEM_MODE_USB.equals(getCurrentModeMd2())) {
                    if (msg.arg1 != (Utils.MODEM_LOG_K2_INDEX + C2KLOGGER_INDEX)) {
                        mMsgArg1ForReset = msg.arg1;
                    }
                    Utils.logd(TAG, "C2KModem is support. mIsNeedOtherDump = "
                                + mIsNeedOtherDump);
                    if (mIsNeedOtherDump) {
                        mIsNeedOtherDump = false;
                        if (pathObj != null && pathObj instanceof String) {
                            mMdDumpPath += ";";
                        }
                    } else {
                        mIsNeedOtherDump = true;
                        if (pathObj != null && pathObj instanceof String) {
                            mMdDumpPath = (String) pathObj;
                        }
                        break;
                    }
                } else {
                    mMdDumpPath = "";
                }
                if ((msg.arg1 == (Utils.MODEM_LOG_K2_INDEX + C2KLOGGER_INDEX)
                        || (Utils.isSupportC2KModem() && Utils.isMTKSvlteSupport()))
                        && Utils.isDenaliMd3Solution()) {
                    mSharedPreferences.edit().putString(Utils.KEY_C2K_MODEM_EXCEPTIONG_PATH,
                            mSharedPreferences.getString(Utils.KEY_C2K_MODEM_LOGGING_PATH, ""))
                            .apply();
                    startStopC2KLoggerService(false);
                }
                mCurrentStage = Utils.RUNNING_STAGE_IDLE;
                mCmdResHandler.obtainMessage(Utils.MSG_RUNNING_STAGE_CHANGE, mCurrentStage)
                        .sendToTarget();
                if (pathObj != null && pathObj instanceof String) {
                    mMdDumpPath += (String) pathObj;
                }
                if (Utils.isMultiLogFeatureOpen()) {
                    String modemLogPath = Utils.getCurrentLogPath(sContext)
                            + Utils.MTKLOG_PATH + Utils.MODEM_LOG_PATH;
                    File fileTree = new File(modemLogPath + "/" + Utils.LOG_TREE_FILE);
                    if (fileTree.exists()) {
                        File mdDumpFile = Utils.getLogFolderFromFileTree(fileTree, false);
                        if (mdDumpFile != null) {
                            mMdDumpPath = mdDumpFile.getAbsolutePath();
                        }
                    }
                }
                String dumpDialogShowStr = mMdDumpPath;
                if (Utils.isSupportC2KModem() && Utils.isDenaliMd3Solution()) {
                    mMdDumpPath += ";" + mSharedPreferences.getString(
                            Utils.KEY_C2K_MODEM_LOGGING_PATH, "");
                }
                // For notify taglog, can begin to compress log now
                mSharedPreferences.edit().putString(Utils.KEY_MODEM_EXCEPTION_PATH,
                        mMdDumpPath).apply();
                Utils.logi(TAG, "dump finished " + "mMdDumpPath=" + mMdDumpPath);
                Utils.logd(TAG, "mMsgArg1ForReset = " + mMsgArg1ForReset);
                mIsAllMemoryDumpDone = true;
                showMemoryDumpDoneDialog(
                        mMsgArg1ForReset == -1 ? msg.arg1 : mMsgArg1ForReset, dumpDialogShowStr);
                break;
            case MSG_LOG_FLUSH_START:
                mCurrentStage = Utils.RUNNING_STAGE_FLUSHING_LOG;
                mCmdResHandler.obtainMessage(Utils.MSG_RUNNING_STAGE_CHANGE, mCurrentStage)
                        .sendToTarget();
                // Notify Taglog modem log flush is already on the way, so just
                // keep waiting
                mSharedPreferences.edit()
                        .putString(Utils.KEY_MODEM_LOG_FLUSH_PATH, Utils.FLAG_MDLOGGER_FLUSHING)
                        .apply();
                break;
            case MSG_LOG_FLUSH_FINISH:
                Object flushLogPathObj = msg.obj;
                if (Utils.isSupportC2KModem()) {
                    Utils.logd(TAG, "C2KModem is support. mIsAllFlushDone = " + mIsAllFlushDone);
                    if (mIsAllFlushDone) {
                        mIsAllFlushDone = false;
                        mMdFlushPath += ";";
                    } else {
                        mIsAllFlushDone = true;
                        if (flushLogPathObj != null && flushLogPathObj instanceof String) {
                            mMdFlushPath = (String) flushLogPathObj;
                        }
                        break;
                    }
                } else {
                    mMdFlushPath = "";
                }
                mCurrentStage = Utils.RUNNING_STAGE_IDLE;
                mCmdResHandler.obtainMessage(Utils.MSG_RUNNING_STAGE_CHANGE, mCurrentStage)
                        .sendToTarget();
                if (flushLogPathObj != null && flushLogPathObj instanceof String) {
                    mMdFlushPath += (String) flushLogPathObj;
                    // For notify taglog, can begin to compress log now
                    mSharedPreferences.edit()
                            .putString(Utils.KEY_MODEM_LOG_FLUSH_PATH, mMdFlushPath).apply();
                }
                break;
            case MSG_NOTIFY_LOG_FLUSH_START:
                if (mModemManager.isAnyModemLogConnect()) {
                    mModemManager.sendCmd(COMMAND_LOG_FLUSH);
                } else {
                    Utils.loge(TAG,
                            "Lost connection to native, log flush command will have no effect.");
                }
                break;
            case MSG_NO_LOGGING_FILE:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (mModemManager.sendCmd(COMMAND_PAUSE)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, "");
                    }
                }
                break;
            case MSG_SEND_FILTER_FAIL:
                Builder builder = new AlertDialog.Builder(sContext);
                builder.setTitle(R.string.send_filter_fail_title).setMessage(
                        R.string.send_filter_fail_msg);
                builder.setPositiveButton(android.R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.show();
                break;
            case MSG_WRITE_FILE_FAIL:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (mModemManager.sendCmd(COMMAND_PAUSE)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, "");
                    }
                }
                break;
            case MSG_SDCARD_FULL:
                if (mModemManager.isAnyModemLogConnect()) {
                    if (mModemManager.sendCmd(COMMAND_PAUSE)) {
                        mSharedPreferences.edit()
                                .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED)
                                .apply();
                        notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, Utils.REASON_STORAGE_FULL);
                    }
                }
                break;
            case MSG_QUERY_PAUSE_STATUS:
                int modemIndex = msg.arg1;
                Object statusObj = msg.obj;
                if (mCurrentStatus == Utils.LOG_RUNNING_STATUS_UNKNOWN) {
                    mCurrentStatus = Utils.LOG_RUNNING_STATUS_STOPPED;
                }
                if ("0".equals(statusObj)) {
                    mCurrentStatus |= modemIndex;
                } else if ("1".equals(statusObj)) {
                    mCurrentStatus |= modemIndex;
                    mCurrentStatus ^= modemIndex;
                } else {
                    Utils.loge(TAG, "Invalid pause status value.");
                }
                Utils.logd(TAG, "Query MD pause status return, modemIndex=" + modemIndex
                        + ", new pause status=" + statusObj + ", mCurrentStatus=" + mCurrentStatus);
                boolean isLogRunning = isLogRunning(Utils.LOG_TYPE_MODEM);
                if (!isLogRunning && mCurrentStatus > 0) {
                    Utils.logw(TAG, "Modem log runing status from system property is 0, "
                            + "but new query value is 1, update this to user.");
                    notifyServiceStatus(Utils.VALUE_STATUS_RUNNING, "");

                } else if (isLogRunning && mCurrentStatus == 0) {
                    Utils.logw(TAG, "Modem log runing status from system property is 1, "
                            + "but new query value is 0, update this to user.");
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, "");
                }
                break;
            case MSG_STATUS_UPDATE:
                int newState = msg.arg2;
                // int oldState =
                // mSharedPreferences.getInt(Utils.KEY_STATUS_MODEM,
                // Utils.VALUE_STATUS_STOPPED);

                Utils.logd(TAG, "Got an update event, new state = " + newState + ", reason="
                        + cmdReason);
                // if(newState != oldState){
                notifyServiceStatus(newState, TextUtils.isEmpty(cmdReason) ? "" : cmdReason);
                if (newState == Utils.VALUE_STATUS_RUNNING) {
                    // -1 | 1 = -1
                    if (mCurrentStatus == Utils.LOG_RUNNING_STATUS_UNKNOWN) {
                        mCurrentStatus = Utils.LOG_RUNNING_STATUS_STOPPED;
                    }
                    mCurrentStatus |= msg.arg1;
                    // runMonitoringLogSizeThread();
                } else {
                    Utils.logd(TAG, "Get a modem log stop signal, stop monitor log folder status");
                }
                // } else {
                // Utils.logd(TAG,
                // "Modem status not change, ignore new coming state change event");
                // }
                break;
            case MSG_MDLOG_SHOW_TOAST:

                Utils.logi(TAG, "MSG_MDLOG_SHOW_TOAST" + (String) msg.obj);

                break;
            case MSG_MDLOG_SHOW_DIALOG:

                builder =
                        new AlertDialog.Builder(sContext)
                                .setTitle(sContext.getText(R.string.modem_log).toString())
                                .setMessage((String) msg.obj)
                                .setPositiveButton(android.R.string.yes, null);

                dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

                dialog.show();

                Utils.logi(TAG, "MSG_MDLOG_SHOW_DIALOG" + (String) msg.obj);

                break;
            case MSG_GET_VALUE_FROM_NATIVE:
                if (!mModemManager.isAnyModemLogConnect()) {
                    Utils.loge(TAG, "Fail to establish connection to native layer.");
                    break;
                }
                if (!TextUtils.isEmpty(cmdReason)) {
                    mModemManager.sendCmdToOneInstance(cmdReason);
                }
                break;
            case MSG_QUERY_NEED_DUMP_FILE:
                // need to do check wheter need dump file and then send to mdlogger.
                // mdlogger will wait this message 2 seconds, if time out. not send dump file.
                //String cmd = RESPONSE_NEED_DUMP_FILE + ",1"; // need dump file
                //cmd = PREFIX_NEED_DUMP_FILE + ",0" // not need dump file
               // mModemManager.sendCmdToOneInstance(cmd);
                break;
            default:
                Utils.logw(TAG, "Not supported message, tell father to deal with it: " + msg.what);
                super.handleMessage(msg, Utils.LOG_TYPE_MODEM);
                break;
            }
        }
    }
    private void initModemPargram() {
        mCurrentStage = Utils.RUNNING_STAGE_IDLE;

        if (Utils.VALUE_START_AUTOMATIC_ON == mDefaultSharedPreferences
                .getBoolean(Utils.KEY_START_AUTOMATIC_MAP.get(Utils.LOG_TYPE_MODEM),
                 Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(Utils.LOG_TYPE_MODEM))) {
            Utils.logw(TAG, "Modem is re-connected, need auto start,"
                    + "so send init message. ");
            mHandler.sendEmptyMessage(MSG_INIT);
        }
    }
    private void dealWithADBCommand(String cmd) {
        if (ADB_COMMAND_EXIT.equalsIgnoreCase(cmd)) {
            if (mModemManager.isAnyModemLogConnect()) {
                if (mModemManager.sendCmd(COMMAND_STOP)) {
                    mSharedPreferences.edit()
                            .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED).apply();
                    notifyServiceStatus(Utils.VALUE_STATUS_STOPPED, "");
                }
            }
        } else if (ADB_COMMAND_PAUSE.equalsIgnoreCase(cmd)) {
            mHandler.sendEmptyMessage(MSG_STOP);
        } else if (ADB_COMMAND_RESUME.equalsIgnoreCase(cmd)) {
            mHandler.sendEmptyMessage(MSG_INIT);
        } else if (ADB_COMMAND_FORCE_MODEM_ASSERT.equalsIgnoreCase(cmd)) {
            mHandler.sendEmptyMessage(MSG_BEGIN_DUMP);
        } else if (ADB_COMMAND_AUTO_TEST_START_MDLOGGER.equalsIgnoreCase(cmd)) {
            if (getCurrentMode().equals(Utils.MODEM_MODE_SD)
                    || getCurrentMode().equals(Utils.MODEM_MODE_PLS)) {
                mHandler.sendEmptyMessage(MSG_INIT);
            }
        } else if (cmd.startsWith(Utils.ADB_COMMAND_SWITCH_MODEM_LOG_MODE)) {
            String[] tmpArray = cmd.split(",");
            if (tmpArray.length == 2) {
                String newModeStr = "";
                boolean setModeSuccess = false;
                if (tmpArray[0].startsWith(Utils.ADB_COMMAND_SWITCH_MODEM_LOG_MODE + "_")) {
                    newModeStr = tmpArray[0].substring(Utils.ADB_COMMAND_SWITCH_MODEM_LOG_MODE
                            .length() + 1);
                    setModeSuccess = setCurrentMode(newModeStr, tmpArray[1]);
                } else {
                    newModeStr = tmpArray[1];
                    setModeSuccess = setCurrentMode(newModeStr, "1");
                    if (Utils.sAvailableModemList.size() == 2) {
                        setModeSuccess = setCurrentMode(newModeStr, "3");
                    }
                }
                if (setModeSuccess && getAutoStartValue()) {
                    mModemManager.sendCmd(COMMAND_SETAUTO + newModeStr);
                }
            } else {
                Utils.loge(TAG, "Invalid configuration from adb command");
            }
        } else if (cmd.startsWith(Utils.ADB_COMMAND_SET_MODEM_LOG_SIZE)) {
            String[] tmpArray = cmd.split(",");
            if (tmpArray.length == 2) {
                String newlogSize = tmpArray[0].substring(Utils.ADB_COMMAND_SET_MODEM_LOG_SIZE
                        .length() + 1);
                setLogSize(newlogSize, tmpArray[1]);
            } else {
                Utils.loge(TAG, "Invalid mdlog size configuration from adb command");
            }
        } else if (cmd.startsWith(ADB_COMMAND_FORCE_MODEM_ASSERT)) {
            String[] tmpArray = cmd.split(":");
            Utils.logd(TAG, "tmpArray:" + tmpArray[0] + "   " + tmpArray[1]);
            String logname = tmpArray[1];
            for (int modemIndex : Utils.MODEM_INDEX_SET) {
                Utils.logd(TAG, "modemIndex:" + modemIndex);
                String socketname = MODEM_INDEX_SOCKET_MAP.get(modemIndex);
                Utils.logd(TAG, "MODEM_INDEX_SOCKET_MAP:" + socketname);
                // String[] splited = socketname.split("\\.");

                // Utils.logd(TAG, "MODEM_INDEX_SOCKET_MAP:"+
                // splited[3]+" "+logname);
                if (socketname.contains(logname)) {
                    mModemManager.sendCmd(modemIndex, COMMAND_POLLING);
                    break;
                }
            }
        } else if (cmd.startsWith(ADB_COMMAND_MODEM_AUTO_RESET)) {
            String newValue = cmd.substring(cmd.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(ModemLogSettings.KEY_MD_AUTORESET, newValue.equals("1"))
                        .apply();
            } else {
                Utils.logw(TAG, "Unsupported adb command modem_auto_reset value");
            }
        } else if (cmd.startsWith(ADB_COMMAND_FLUSH) ||
                cmd.startsWith(ADB_COMMAND_SILENT_STOP) ||
                cmd.startsWith(ADB_COMMAND_SILENT_START) ||
                        cmd.startsWith(ADB_COMMAND_CUSTOM_PATH)) {
            mModemManager.sendCmd(cmd);
            Utils.logw(TAG, "customize adb command:" + cmd);
        } else {
            mModemManager.sendCmd(cmd);
            Utils.logw(TAG, "not official adb command:" + cmd);
        }
    }

    private int mStartStopModemTimes = 1;
    /**
     * Notify current modem log running status to other component.
     *
     * @param status
     * @param reason
     */
    protected void notifyServiceStatus(int status, String reason) {
        Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status
                + ",  reason=[" + reason + "]"
                + ", mStartStopModemTimes = " + mStartStopModemTimes);

        if (mStartStopModemTimes >= 1) {
            mStartStopModemTimes -= 1;
        }
        if (mStartStopModemTimes > 0) {
            Utils.logd(TAG, "Waiting another modem log response.");
            return;
        }
        if (Utils.VALUE_STATUS_RUNNING == status) {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_RUNNING).apply();
            }
            updateStatusBar(Utils.LOG_TYPE_MODEM, R.string.notification_title_modem, true);
        } else {
            if (mSharedPreferences != null) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MODEM, Utils.VALUE_STATUS_STOPPED).apply();
                // If one modem was stopped, just treat them all stopped for
                // simple
                mCurrentStatus = Utils.LOG_RUNNING_STATUS_STOPPED;
            }
            updateStatusBar(Utils.LOG_TYPE_MODEM, R.string.notification_title_modem, false);
        }
        // If status != -1, the status change must be from start/stop command response
        if (!mIsNeedNotifyLogStatusChanged) {
            if (reason != null && !reason.isEmpty()) {
                mCmdResHandler.obtainMessage(Utils.MSG_START_LOGS_DONE,
                        Utils.LOG_TYPE_MODEM, 0).sendToTarget();
                mCmdResHandler.obtainMessage(Utils.MSG_STOP_LOGS_DONE,
                        Utils.LOG_TYPE_MODEM, 0).sendToTarget();
            } else {
                mCmdResHandler.obtainMessage(
                        Utils.VALUE_STATUS_RUNNING == status ?
                                Utils.MSG_START_LOGS_DONE : Utils.MSG_STOP_LOGS_DONE,
                                Utils.LOG_TYPE_MODEM, 1).sendToTarget();
            }
        } else {
            // Notify service about Modem log state change
            mCmdResHandler.obtainMessage(Utils.MSG_LOG_STATE_CHANGED, Utils.LOG_TYPE_MODEM, status,
                    reason).sendToTarget();
        }
        mIsNeedNotifyLogStatusChanged = true;
        super.notifyServiceStatus(status, reason);
    }

    private synchronized void startStopC2KLoggerService(boolean isStart) {
        Utils.logd(TAG, "StartStopC2KLoggerService isStart ? " + isStart);
        if (!Utils.isSupportC2KModem()) {
            Utils.logd(TAG, "The md3 is not support, so it is not support C2KModem!");
            return;
        }
        if (!Utils.isDenaliMd3Solution()) {
            Utils.logd(TAG, "It is not denali modem solutin,"
                    + " so no need start/stop c2klogger service!");
            return;
        }
        String currentMode = getCurrentMode();
        if (Utils.MODEM_MODE_USB.equals(currentMode)) {
            Utils.logd(TAG, "Modem mode is USB, ignore C2KLogger!");
            return;
        }
        final Intent intent = new Intent();
        intent.setClass(sContext, C2KLoggerService.class);
        if (isStart) {
            mSharedPreferences
            .edit()
            .putString("InternalLogPath",
                    Utils.getLogPath(sContext, Utils.LOG_PATH_TYPE_INTERNAL_SD)).apply();
            mSharedPreferences
            .edit()
            .putString("ExternalLogPath",
                    Utils.getLogPath(sContext, Utils.LOG_PATH_TYPE_INTERNAL_SD)).apply();
            mSharedPreferences.edit().putString("ModemLogPath", Utils.getModemLogPath(sContext))
            .apply();
            if (isC2KLoggerRunning()) {
                Utils.logw(TAG, "C2KLoggerService has been running, try later for waiting 15s!");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (int i = 0; i < 15; i++) {
                                Thread.sleep(1000);
                                if (!isC2KLoggerRunning()) {
                                    Utils.logd(TAG, "Waiting stop C2KLoggerService for " + (i + 1)
                                            + " seconds");
                                    sContext.startService(intent);
                                    startStopC2KLoggerLibService(true);
                                    return;
                                }
                            }
                            Utils.loge(TAG, "Waiting C2KLoggerService stop timeout!");
                            if (!isC2KLoggerRunning()) {
                                sContext.startService(intent);
                                startStopC2KLoggerLibService(true);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            } else {
                sContext.startService(intent);
                startStopC2KLoggerLibService(true);
            }
        } else {
            if (!isC2KLoggerRunning()) {
                Utils.logw(TAG, "C2KLoggerService has been destroyed");
            } else {
                sContext.stopService(intent);
                startStopC2KLoggerLibService(false);
            }
        }
    }

    private Class<?> mC2kLogService;
    /**
     * @param isStart boolean
     */
    private void startStopC2KLoggerLibService(boolean isStart) {
        Utils.logd(TAG, "startStopC2KLoggerLibService isStart ? " + isStart);
        try {
            if (mC2kLogService == null) {
                mC2kLogService = getC2KLogService();
                if (mC2kLogService == null) {
                    Utils.loge(TAG, "Load c2kloggerService failed!");
                    return;
                }
            }
            Class<?>[] parameterTypes = new Class[] { Class.forName("android.app.Service") };
            Method getVolumeStateMethod = mC2kLogService.getDeclaredMethod(isStart ? "startService"
                    : "stopService", parameterTypes);
            int i = 0;
            while (C2KLoggerService.sService == null) {
                try {
                    Utils.logw(TAG, "C2KLogService.sService is null,"
                            + " waiting it onCreate finished!");
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i ++;
                if (i >= 100) {
                    Utils.loge(TAG, "Start C2KLogService failed in 20s!");
                    return;
                }
            }
            Object[] args = new Object[] { C2KLoggerService.sService };
            getVolumeStateMethod.invoke(null, args);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Class<?>
     */
    private Class<?> getC2KLogService() {
        try {
            Utils.logd(TAG, "getC2KLoggerService Start!");
            String path = "/system/vendor/framework/via-plugin.jar";
            File c2kloggerLibFile = new File(path);
            if (!c2kloggerLibFile.exists()) {
                return null;
            }
            String className = "com.mediatek.mtklogger.c2klogger.C2KLoggerProxy";
            PathClassLoader classLoader = new PathClassLoader(path,
                    null, ClassLoader.getSystemClassLoader());
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @return boolean
     */
    private boolean isC2KLoggerRunning() {
        return Utils.isServiceRunning(sContext, C2KLoggerService.class.getName())
                || !mSharedPreferences.getString(Utils.KEY_C2K_MODEM_LOGGING_PATH, "")
                        .isEmpty();
    }

    private void showMemoryDumpDoneDialog(final int instanceIndex, String logFolderPath) {
        Utils.logd(TAG, "-->showMemoryDumpDone(), logFolderPath=" + logFolderPath
                + ", isModemResetDialogShowing=" + mIsModemResetDialogShowing);
        if (mDefaultSharedPreferences.getBoolean(ModemLogSettings.KEY_MD_AUTORESET, false)) {
            Utils.logd(TAG, "Auto reset modem, does not need showMemoryDumpDoneDialog!");
            mSharedPreferences.edit().remove(Utils.KEY_MODEM_ASSERT_FILE_PATH).apply();
            mHandler.obtainMessage(MSG_BEGIN_RESET, instanceIndex, 0).sendToTarget();
            mIsModemResetDialogShowing = false;
            return;
        }
        if (mIsModemResetDialogShowing) {
            Utils.logd(TAG, "Modem reset dialog is already showing, just return");
            return;
        }
        // Modem dump done, need to show a reset dialog to user in all
        // case,
        // so store this status to file, if this process was killed,
        // then re-show
        // this dialog when process was restarted by system
        mSharedPreferences.edit()
                .putString(Utils.KEY_MODEM_ASSERT_FILE_PATH, instanceIndex + ";" + logFolderPath)
                .apply();

        Utils.logi(TAG, "Show memory dump done dialog.");
        String message = sContext.getText(R.string.memorydump_done).toString() + logFolderPath;
        Builder builder =
                new AlertDialog.Builder(sContext)
                        .setTitle(sContext.getText(R.string.dump_warning).toString())
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Utils.logd(TAG, "Click OK in memory dump done dialog");
                                mHandler.obtainMessage(MSG_BEGIN_RESET, instanceIndex, 0)
                                        .sendToTarget();
                                // if (assertRingtone != null) {
                                // assertRingtone.stop();
                                // }
                                Utils.logd(TAG,
                                        "After confirm, no need to show reset dialog next time");
                                mSharedPreferences.edit().remove(Utils.KEY_MODEM_ASSERT_FILE_PATH)
                                        .apply();
                                mIsModemResetDialogShowing = false;
                            }
                        });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Utils.logd(TAG, "Press cancel in memory dump done dialog");
                mHandler.obtainMessage(MSG_BEGIN_RESET, instanceIndex, 0).sendToTarget();
                // if (assertRingtone != null) {
                // assertRingtone.stop();
                // }
                Utils.logd(TAG, "After cancel, no need to show reset dialog next time");
                mSharedPreferences.edit().remove(Utils.KEY_MODEM_ASSERT_FILE_PATH).apply();
                mIsModemResetDialogShowing = false;
            }
        });
        mIsModemResetDialogShowing = true;
        dialog.show();
    }

    private void setDefaultCcbGearId(String gearIdValue) {
        Utils.logi(TAG,
                "setDefaultCcbGearId() gearIdValue = " + gearIdValue);
        mDefaultSharedPreferences.edit().putString(ModemLogSettings.KEY_CCB_GEAR,
                gearIdValue).apply();
    }

    private Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message message) {
            Utils.logi(TAG, " MyHandler handleMessage --> start " + message.what);
            switch (message.what) {
            case MSG_DISMISS_RESET_DIALOG:
                dismissResetModemDialog();
                break;
            case MSG_MEMORYDUMP_DONE_TIMEOUT:
            case MSG_MEMORYDUMP_START_TIMEOUT:
                int mdIndex = 9;
                if (message.obj != null && message.obj instanceof Integer) {
                    mdIndex = (Integer) message.obj;
                }
                Message msg = mHandler.obtainMessage(MSG_MEMORY_DUMP_FINISH);
                msg.arg1 = mdIndex;
                msg.sendToTarget();
                Utils.logd(TAG, "Send out an empty memory dump finish message for modem "
                        + mdIndex);
                break;
            default:
                Utils.logw(TAG, "Not supported message: " + message.what);
                break;
            }
        }
    };

    private void showResetModemDialog() {
        if (mDefaultSharedPreferences.getBoolean(ModemLogSettings.KEY_MD_AUTORESET, false)) {
            Utils.logd(TAG, "Auto reset modem, does not need showResetModemDialog!");
            return;
        }
        mResetModemDialog = new ProgressDialog(sContext);
        mResetModemDialog.setTitle(sContext.getText(R.string.reset_modem_title).toString());
        mResetModemDialog.setMessage(sContext.getText(R.string.reset_modem_msg).toString());
        mResetModemDialog.setCancelable(false);
        Window win = mResetModemDialog.getWindow();
        win.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mResetModemDialog.show();

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mMessageHandler.obtainMessage(MSG_DISMISS_RESET_DIALOG).sendToTarget();
            }
        }, 1000, 1000);
    }

    private void dismissResetModemDialog() {
        mResetTimeout += 1000;
        if (mResetTimeout >= 10000) {
            Utils.logw(TAG, "Reset modem timeout!");
            mResetTimeout = 0;
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            if (mResetModemDialog != null) {
                mResetModemDialog.cancel();
                mResetModemDialog = null;
            }
            return;
        }
        boolean isResetDone =
                (Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(Utils.KEY_STATUS_MODEM,
                        Utils.VALUE_STATUS_RUNNING));
        Utils.loge(TAG, "dismissResetModemDialog()-> isResetDone ? " + isResetDone);
        if (isResetDone) {
            mResetTimeout = 0;
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            if (mResetModemDialog != null) {
                mResetModemDialog.cancel();
                mResetModemDialog = null;
            }
        }
    }

    /**
     * @author MTK81255
     *
     */
    class ModemLogConnection extends LogConnection {
        /**
         * For multiple modem, this flag show which one this connection was
         * connected to.
         */
        private int mConnType = 0;

        public ModemLogConnection(int connType, String socketName, Handler handler) {
            super(connType, socketName, LocalSocketAddress.Namespace.ABSTRACT, handler);
            mConnType = connType;
        }

        @Override
        public void dealWithResponse(byte[] respBuffer, Handler handler) {
            super.dealWithResponse(respBuffer, handler);
            if (respBuffer == null || respBuffer.length == 0) {
                Utils.logw(TAG, "Get an empty response from native, ignore it.");
                return;
            }
            String respStr = new String(respBuffer);
            Utils.logi(TAG, "-->dealWithResponse(), resp=" + new String(respBuffer));
            int msgType = MSG_UNKNOWN;
            String extraStr = null; // Detail info, if any
            int newLogStatus = -1;

            if (respStr.startsWith(COMMAND_START)
                    || respStr.startsWith(COMMAND_START_WITH_CONFIG)) {
                msgType = MSG_STATUS_UPDATE;
                // respStr = "start,2,1..." / respStr = "deep_start,2,1..."
                int startLength =
                        respStr.startsWith(COMMAND_START) ? COMMAND_START.length()
                                : COMMAND_START_WITH_CONFIG.length();
                if (!Utils.isDenaliMd3Solution()) {
                    startLength += 2;
                }
                if (respStr.length() >= startLength + 4
                        && respStr.substring(startLength + 2, startLength + 4).equals(
                                RESPONSE_COMMAND_RESULT_FAIL_IN_FLIGHT_MODE)) {
                    newLogStatus = 0;
                    extraStr = Utils.REASON_MODEM_LOG_IN_FLIGHT_MODE;
                } else if (respStr.substring(startLength + 2, startLength + 3).equals(
                        RESPONSE_COMMAND_RESULT_SUCCESS)) {
                    newLogStatus = 1; // Stand for modem log new running status
                } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                    newLogStatus = 0;
                    extraStr = Utils.REASON_SEND_CMD_FAIL;
                }
                mIsNeedNotifyLogStatusChanged = false;
            } else if (respStr.startsWith(COMMAND_PAUSE) || respStr.startsWith(COMMAND_STOP)
                    || respStr.startsWith(COMMAND_PAUSE_WITH_CONFIG)) {
                msgType = MSG_STATUS_UPDATE;
                if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_SUCCESS)) {
                    newLogStatus = 0; // Stand for modem log new running status
                } else if (respStr.endsWith("," + RESPONSE_COMMAND_RESULT_FAIL)) {
                    newLogStatus = 0;
                    extraStr = Utils.REASON_SEND_CMD_FAIL;
                }
                mIsNeedNotifyLogStatusChanged = false;
                if (Utils.isSupportC2KModem() && Utils.isDenaliMd3Solution()
                        && isC2KLoggerRunning()) {
                    waitingC2KLoggerStop(handler, msgType, mConnType, extraStr, newLogStatus);
                    return;
                }
            } else if (respStr.startsWith(RESPONSE_ANOTHER_MD_POWER_OFF)) {
                msgType = MSG_MEMORY_DUMP_FINISH;
            } else if (respStr.startsWith(RESPONSE_START_MEMORY_DUMP)) {
                msgType = MSG_MEMORY_DUMP_START;
            } else if (respStr.startsWith(RESPONSE_FINISH_MEMORY_DUMP)) {
                msgType = MSG_MEMORY_DUMP_FINISH;
                // Extra dump folder name now
                int start = RESPONSE_FINISH_MEMORY_DUMP.length() + 1;
                int end = respStr.length() - 1;
                if (start < end) {
                    extraStr = respStr.substring(start, end);
                } else {
                    Utils.loge(TAG, "Invalid dump done message from native.");
                }
            } else if (respStr.startsWith(RESPONSE_START_LOG_FLUSH)) {
                msgType = MSG_LOG_FLUSH_START;
            } else if (respStr.startsWith(RESPONSE_FINISH_LOG_FLUSH)) {
                msgType = MSG_LOG_FLUSH_FINISH;
                // Extra dump folder name now
                int start = RESPONSE_FINISH_LOG_FLUSH.length() + 1;
                int end = respStr.length() - 1;
                if (start < end) {
                    extraStr = respStr.substring(start, end);
                } else {
                    Utils.loge(TAG, "Invalid log flush done message from native.");
                }
            } else if (respStr.startsWith(RESPONSE_LOGGING_FILE_NOTEXIST)) {
                msgType = MSG_NO_LOGGING_FILE;
            } else if (respStr.startsWith(RESPONSE_FAIL_SEND_FILTER)) {
                msgType = MSG_SEND_FILTER_FAIL;
            } else if (respStr.startsWith(RESPONSE_FAIL_WRITE_FILE)) {
                msgType = MSG_WRITE_FILE_FAIL;
            } else if (respStr.startsWith(RESPONSE_SDCARD_FULL)) {
                msgType = MSG_SDCARD_FULL;
            } else if (respStr.startsWith(COMMAND_ISPAUSED)) {
                msgType = MSG_QUERY_PAUSE_STATUS;
                // Extra pause status value
                int start = COMMAND_ISPAUSED.length() + 1;
                int end = respStr.length();
                if (start < end) {
                    extraStr = respStr.substring(start, start + 1);
                } else {
                    Utils.loge(TAG, "Invalid puase status response from native.");
                }
            } else if (respStr.startsWith(SHOW_TOAST)) {
                msgType = MSG_MDLOG_SHOW_TOAST;
                String[] splited = respStr.split(":");
                if (splited.length >= 2) {
                    extraStr = splited[1].trim();
                } else {
                    extraStr = "";
                }

            } else if (respStr.startsWith(SHOW_DIALOG)) {
                msgType = MSG_MDLOG_SHOW_DIALOG;
                String[] splited = respStr.split(":");
                if (splited.length >= 2) {
                    extraStr = splited[1].trim();
                } else {
                    extraStr = "";
                }
            } else if (respStr.startsWith(PREFIX_IS_GPS_SUPPORT)) {
                String[] splited = respStr.split(",");
                if (splited.length >= 2) {
                    extraStr = splited[1].trim();
                } else {
                    extraStr = "";
                }
                setSpecialFeatureSupport(extraStr);
                return;
            } else if (respStr.startsWith(PREFIX_GET_CCB_GEAR_ID)) {
                String[] splited = respStr.split(",");
                if (splited.length >= 2) {
                    extraStr = splited[1].trim();
                } else {
                    extraStr = "";
                }
                setDefaultCcbGearId(extraStr);
                return;
            } else if (mCommandValue != null && respStr.startsWith(mCommandValue)) {
                String[] splited = respStr.split(",");
                if (splited.length >= 2) {
                    extraStr = splited[1].trim();
                } else {
                    extraStr = "";
                }
                mCommandResult = extraStr;
                return;
            } else if (respStr.startsWith(RESPONSE_FINISH_MEMORY_DUMP_FILE)) {
               // for customize
                Utils.logd(TAG, "RESPONSE_FINISH_MEMORY_DUMP_FILE:" + respStr);

            } else if (respStr.startsWith(RESPONSE_NEED_DUMP_FILE)) {
              // for customize
                Utils.logd(TAG, "receive need dump file query");
                msgType = MSG_QUERY_NEED_DUMP_FILE;
            }

            if (msgType == 0) {
                Utils.logd(TAG,
                        "No need deal with this response for msgType = MSG_UNKNOWN");
                return;
            }
            Message msg = handler.obtainMessage(msgType);
            msg.arg1 = mConnType;
            if (!TextUtils.isEmpty(extraStr)) {
                msg.obj = extraStr;
            }
            msg.arg2 = newLogStatus;
            msg.sendToTarget();
        }
    }

    /**
     * @param handler Handler
     * @param msgType int
     * @param mConnType int
     * @param extraStr String
     * @param newLogStatus int
     */
    private void waitingC2KLoggerStop(final Handler handler, final int msgType,
            final int mConnType, final String extraStr, final int newLogStatus) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 12; i++) {
                        Thread.sleep(1000);
                        if (!isC2KLoggerRunning()) {
                            Utils.logd(TAG, "Waiting stop C2KLoggerService for " + (i + 1)
                                    + " seconds");
                            Message msg = handler.obtainMessage(msgType);
                            msg.arg1 = mConnType;
                            if (!TextUtils.isEmpty(extraStr)) {
                                msg.obj = extraStr;
                            }
                            msg.arg2 = newLogStatus;
                            msg.sendToTarget();
                            return;
                        }
                    }
                    Utils.loge(TAG, "Waiting C2KLoggerService stop timeout!");
                    Message msg = handler.obtainMessage(msgType);
                    msg.arg1 = mConnType;
                    if (!TextUtils.isEmpty(extraStr)) {
                        msg.obj = extraStr;
                    }
                    msg.arg2 = newLogStatus;
                    msg.sendToTarget();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * This class is used to manager multiple modem log connection instance.
     */
    class ModemManager {
        public static final int MSG_SEND_CMD_TIMEOUT = 1;
        private SparseArray<LogConnection> mConnectionMap =
                new SparseArray<LogConnection>();

        private SparseArray<String> mLogFolderMap = new SparseArray<String>();

        public ModemManager(Handler handler) {
            initLogConnection();
            getLogDefaultValues();
        }

        /**
         * Initiate connection to each modem. If connect to any one success,
         * return true, else false which means we need to re-initiate connection
         * next time
         */
        private boolean initLogConnection() {
            for (int modemIndex : Utils.MODEM_INDEX_SET) {
                if (null == mConnectionMap.get(modemIndex)
                    || null == mConnectionMap.get(modemIndex).getSocket()) {
                    Utils.logi(TAG, "Add modem[" + modemIndex + "] at initLogConnection");
                    mConnectionMap.put(modemIndex, new ModemLogConnection(modemIndex,
                            MODEM_INDEX_SOCKET_MAP.get(modemIndex), mHandler));
                }
            }
            boolean connected = false;
            for (int i = 0; i < mConnectionMap.size(); i++) {
                int key = mConnectionMap.keyAt(i);
                if (logConnect(key)) {
                    if (key == (Utils.MODEM_LOG_K2_INDEX + C2KLOGGER_INDEX)
                            && Utils.isDenaliMd3Solution()) {
                        continue;
                    }
                    connected = true;
                } else {
                    Utils.loge(TAG + "/ModemManager", "Fail to init connection for modem [" + key
                            + "]");
                }
            }
            Utils.logd(TAG + "/ModemManager", "-->initLogConnection(), connected=" + connected);
            return connected;
        }

        private boolean logConnect(int modemIndex) {
            boolean connected = false;
            LogConnection connection = mConnectionMap.get(modemIndex);
            if (connection == null || null == connection.getSocket()) {
                connection = new ModemLogConnection(modemIndex,
                        MODEM_INDEX_SOCKET_MAP.get(modemIndex), mHandler);
                mConnectionMap.put(modemIndex, connection);
            }
            if (connection != null && connection.isConnected()) {
                Utils.loge(TAG + "/ModemManager", "Modem [" + modemIndex
                        + "] has been connected, do not need connect again!");
                connected = true;
            } else {
                connected = LogInstance.initLogConnection(connection);
            }
            Utils.logd(TAG + "/ModemManager", "-->logConnect(), modemIndex = " + modemIndex
                    + ", connected = " + connected);
            return connected;
        }

        /**
         * Get log default values from native daemons.
         */
        private void getLogDefaultValues() {
            /**
             * Get Value of if ModemSpecialFeatureSupport.
             * 1 means gps_location support
             * 2 means CCB_feature support
             * 3 is 1 + 2 means gps_location & CCB_feature all support
             */
            mHandler.obtainMessage(MSG_GET_VALUE_FROM_NATIVE,
                    PREFIX_IS_GPS_SUPPORT).sendToTarget();
            mHandler.obtainMessage(MSG_GET_VALUE_FROM_NATIVE,
                    PREFIX_GET_CCB_GEAR_ID).sendToTarget();
        }

        private boolean isAnyModemLogConnect() {
            boolean isAnyModemLogConnect = false;
            for (int i = 0; i < mConnectionMap.size(); i++) {
                LogConnection logConnection = mConnectionMap.valueAt(i);
                if (logConnection == null) {
                    continue;
                }
                if (logConnection.isConnected()) {
                    isAnyModemLogConnect = true;
                    break;
                }
            }
            Utils.logd(TAG + "/ModemManager", "isAnyModemLogConnect ? " + isAnyModemLogConnect);
            return isAnyModemLogConnect;
        }
        private boolean sendCmd(String cmd) {
            Utils.logd(TAG + "/ModemManager", "-->receive cmd:" + cmd);
            boolean result = false;
            if (!mIsAllMemoryDumpDone || mIsModemResetDialogShowing) {
                if (cmd != null && (!cmd.startsWith(COMMAND_START) &&
                        !cmd.startsWith(COMMAND_START_WITH_CONFIG))) {
                    Utils.logw(TAG + "/ModemManager", "-->sendCmd but mIsAllMemoryDumpDone=0,"
                            + "return false");
                    return false;
                }
            }
            if (COMMAND_RESUME.equals(cmd)) {
                mCurrentStatus = Utils.LOG_RUNNING_STATUS_STOPPED;
            }
            for (int i = 0; i < mConnectionMap.size(); i++) {
                LogConnection connection = mConnectionMap.valueAt(i);
                int key = mConnectionMap.keyAt(i);
                if (connection != null && connection.isConnected()
                        && !(key == (Utils.MODEM_LOG_K2_INDEX + C2KLOGGER_INDEX)
                        && Utils.isDenaliMd3Solution())
                        && sendCmd(key, cmd)) {
                    result = true;
                    if (COMMAND_RESUME.equals(cmd)) {
                        Utils.logd(TAG, "Send resume command to MD" + key + ", mark it as running");
                        mCurrentStatus |= key;
                    }
                }
            }
            Utils.logd(TAG + "/ModemManager", "<--sendCmd(), cmd=" + cmd + ", result=" + result);
            return result;
        }

        private synchronized boolean sendCmd(int instanceIndex, String cmd) {
            Utils.logd(TAG + "/ModemManager" , "-->receive cmd:" + cmd
                    + " instanceIndex = " + instanceIndex);
            boolean result = false;
            if (!mIsAllMemoryDumpDone) {
                if (cmd != null && (!cmd.startsWith(COMMAND_START) &&
                        !cmd.startsWith(COMMAND_START_WITH_CONFIG))) {
                    Utils.logw(TAG + "/ModemManager", "-->sendCmd but is dumping , return false");
                    return false;
                }
            }
            LogConnection connection = mConnectionMap.get(instanceIndex);
            if (connection == null || !connection.isConnected()) {
                Utils.loge(TAG, "Send command to instance [" + instanceIndex
                        + "] fail, instance have not be initialized or already lost connection.");
                return false;
            }
            mModemManagerMessageHandler.sendMessageDelayed(
                    mModemManagerMessageHandler.obtainMessage(
                            MSG_SEND_CMD_TIMEOUT, instanceIndex, -1, cmd),
                    15000);
            if (connection.sendCmd(cmd)) {
                result = true;
            }
            mModemManagerMessageHandler.removeMessages(MSG_SEND_CMD_TIMEOUT);
            Utils.logd(TAG + "/ModemManager", "-->sendCmd(), instanceIndex=" + instanceIndex
                    + "cmd=" + cmd + ", result=" + result);
            return result;
        }

        /**
         * Some command like dump, configure not need to send to all instance,
         * so at this situation just send such command to any one instance.
         *
         * @return
         */
        private boolean sendCmdToOneInstance(String cmd) {
            boolean result = false;
            for (int i = 0; i < mConnectionMap.size(); i ++) {
                LogConnection connection = mConnectionMap.valueAt(i);
                int key = mConnectionMap.keyAt(i);
                if (connection != null && connection.isConnected()
                        && key != (Utils.MODEM_LOG_K2_INDEX + C2KLOGGER_INDEX)
                        && connection.sendCmd(cmd)) {
                    result = true;
                    break;
                }
            }
            Utils.logd(TAG + "/ModemManager", "-->sendCmdToOneInstance(), cmd=" + cmd + ", result="
                    + result);
            return result;
        }

        private Handler mModemManagerMessageHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message message) {
                Utils.logd(TAG, " mModemManagerMessageHandler --> start " + message.what);
                switch (message.what) {
                case MSG_SEND_CMD_TIMEOUT:
                    int modemIndex = message.arg1;
                    String cmd = "";
                    if (message.obj != null && message.obj instanceof String) {
                        cmd = (String) message.obj;
                    }
                    Utils.logd(TAG, " modemIndex = " + modemIndex
                            + "cmd" + cmd);
                    mConnectionMap.get(modemIndex).stop();
                    mConnectionMap.remove(modemIndex);
                    mConnectionMap.put(modemIndex, new ModemLogConnection(modemIndex,
                            MODEM_INDEX_SOCKET_MAP.get(modemIndex), mHandler));
                    mLogFolderMap.put(modemIndex, Utils.MODEM_INDEX_FOLDER_MAP.get(modemIndex));
                    logConnect(modemIndex);
                    sendCmd(modemIndex, cmd);
                    Utils.loge(TAG, "Re-send command to instance [" + modemIndex + "]");
                    break;
                default:
                    Utils.logw(TAG, "Not supported message: " + message.what);
                    break;
                }
            }
        };

        private void stop(int modemIndex) {
            Utils.logi(TAG + "/ModemManager", "-->stop(), modemIndex = " + modemIndex);
            LogConnection connection = mConnectionMap.get(modemIndex);
            connection.stop();
            mConnectionMap.remove(modemIndex);
        }
    }
}
