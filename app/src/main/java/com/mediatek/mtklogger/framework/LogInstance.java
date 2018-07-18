package com.mediatek.mtklogger.framework;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseIntArray;

import com.mediatek.mtklogger.MainActivity;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;

/**
 * Supper class for each log instance, like network log, mobile log and modem.
 * log
 */
public class LogInstance {
    private static final String TAG = Utils.TAG + "/LogInstance";

    /**
     * These messages will be used by handler defined in each log instance 1~19.
     * will be used to send command from up to down 20~ will be used to send
     * response from down to up
     */
    public static final int MSG_UNKNOWN = 0;
    public static final int MSG_INIT = 1;
    public static final int MSG_START = 2;
    public static final int MSG_STOP = 3;
    public static final int MSG_CHECK = 4; // For network log, check tcpdump
                                           // running and kill it, merge it with
                                           // stop
    public static final int MSG_START_SHELL_CMD = 5;
    public static final int MSG_STOP_SHELL_CMD = 6;
    public static final int MSG_CONFIG = 7;
    public static final int MSG_ADB_CMD = 8;
    /**
     * When Java service was killed and the restarted, connection to native must
     * already lost, another reconnection operation will be needed.
     */
    public static final int MSG_RESTORE_CONN = 9;

    public static final int MSG_RESTART = 10;
    public static final int MSG_RESTART_STOP_DONE = 11;
    public static final int MSG_RESTART_STOP_TIMEOUT = 12;
    public static final int MSG_RESTART_DONE = 13;
    public static final int MSG_RESTART_TIMEOUT = 14;
    public static final int START_STOP_TIMEOUT = 20000;

    // Message from native to framework, For mobile log instance
    public static final int MSG_CONN_FAIL = 21;
    public static final int MSG_DIE = 22;
    public static final int MSG_ALIVE = 23;
    public static final int MSG_SD_ALMOST_FULL = 24;
    // Update new status to log instance normally, maybe with detail reason
    public static final int MSG_STATUS_UPDATE = 25;
    public static final int MSG_MDLOG_SHOW_TOAST = 26;
    public static final int MSG_MDLOG_SHOW_DIALOG = 27;
    public static final int MSG_GET_VALUE_FROM_NATIVE = 28;
    // public static final int MSG_RECEIVED_VALUE_FROM_NATIVE = 29;
    public static final int MSG_GET_GPS_SUPPORT_FROM_NATIVE = 29;
    // To deal with PREFIX_RESPONSE_ERROR
    public static final int MSG_NATIVE_ERROR = 30;
    /**
     * Each log instance may have its own special message, use this to identify.
     * If message is larger the this base number, then treat it as a log special
     * message
     */
    public static final int MSG_SPECIAL_BASE = 50;

    /**
     * Flag for configuring native log size parameter from Java.
     */
    public static final String PREFIX_CONFIG_LOG_SIZE = "logsize=";

    /**
     * Flag for configuring native total log size parameter from Java.
     */
    public static final String PREFIX_CONFIG_TOTAL_LOG_SIZE = "totallogsize=";

    /**
     * Flag for configuring native log whether need to auto start when boot up.
     * from Java
     */
    public static final String PREFIX_CONFIG_AUTO_START = "autostart=";

    /**
     * Flag for configuring mobile log whether to record this kind of log, like.
     * AndroidLog, KernelLog
     */
    public static final String PREFIX_CONFIG_SUB_LOG = "sublog_";

    /**
     * Flag for notifying native layer that device tether state changed, applied.
     * to modem log at present
     */
    public static final String PREFIX_TETHER_CHANGE_FLAG = "tether_change";

    /**
     * Flag for notifying native layer that device tether state changed, applied.
     * to modem log at present
     */
//    public static final String PREFIX_SET_GPS_LOCATION_FLAG = "set_gps_location,";
    public static final String PREFIX_ENABLE_GPS_LOCATION = "enable_gps_location";
    public static final String PREFIX_DISABLE_GPS_LOCATION = "disable_gps_location";
    /**
     * Flag to indicate whether log service has foreground priority at present.
     */
    public static boolean sHasForegroundPriority = false;

    // public static final String PREFIX_SET_MET_LOG_SIZE="log_size=";
    /**
     * Not is only support for MET log.
     */
    public static final String PREFIX_CPU_BUFFER_SIZE = "cpu_buff_size=";
    public static final String PREFIX_MET_HEAVY_RECORD = "met_heavy_record=";
    public static final String PREFIX_PEROID_SIZE = "period=";

    /**
     * The message after "error," will be shown as toast.
     */
    public static final String PREFIX_RESPONSE_ERROR = "error,";

    protected static Context sContext;
    private static NotificationManager sNM = null;
    private static Notification.Builder sNotificationBuilder;
    protected LogHandler mHandler;
    /**
     * After command execution finish(get response from native), notify service
     * by this handler.
     */
    protected Handler mCmdResHandler = null;
    protected boolean mBConnected = false;
    protected LogConnection mLogConnection;
    /**
     * Use this lock to sync main thread and mobile log thread. Main thread need
     * to wait mobile log thread to start up first, in order to initialize
     * mobile log handler instance
     */
    protected Object mLogConnectionLock = new Object();
    protected Thread mLogConnectionThread;
    protected SharedPreferences mSharedPreferences;
    protected static SharedPreferences sDefaultSharedPreferences;
    /**
     * Current running log instance will be identified in status bar key: log
     * type, value: log identification string resource.
     */
    protected static final SparseIntArray RUNNING_NOTIFICATION_MAP =
            new SparseIntArray();

    /**
     * Each log's state may change separately, every change should be shown in
     * notification bar. To avoid too frequently update status bar, cache them
     * first, and update them in a given period.
     */
    private static final SparseIntArray PENDING_ON_NOTIFICATION_MAP =
            new SparseIntArray();
    private static final SparseIntArray PENDING_OFF_NOTIFICATION_MAP =
            new SparseIntArray();

    /**
     * @param context Context
     */
    public LogInstance(Context context) {
        sContext = context;
        mSharedPreferences =
                context.getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        sDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Get a log instance according its type.
     * @param type int
     * @param context Context
     * @param handler Handler
     * @return LogInstance
     */
    public static LogInstance getInstance(int type, Context context, Handler handler) {
        LogInstance instance = null;
        switch (type) {
        case Utils.LOG_TYPE_NETWORK:
            instance = new NetworkLog(context, handler);
            break;
        case Utils.LOG_TYPE_MOBILE:
            instance = new MobileLog(context, handler);
            break;
        case Utils.LOG_TYPE_MODEM:
            // instance = new ModemLog(context, handler);
            instance = new MultiModemLog(context, handler);
            break;
        case Utils.LOG_TYPE_MET:
            instance = new MetLog(context, handler);
            break;
        case Utils.LOG_TYPE_GPS:
            instance = new GPSLog(context, handler);
            break;
        default:
            Utils.loge(TAG, "Unsported tag instance type[" + type + "] till now.");
            break;
        }
        return instance;
    }

    /**
     * @author MTK81255
     *
     */
    class LogConnectionThread extends Thread {

        public LogConnectionThread(LogConnection logConnection) {
            mLogConnection = logConnection;
        }

        @Override
        public void run() {
            mBConnected = initLogConnection();
            getLogDefaultValues();
            synchronized (mLogConnectionLock) {
                mLogConnectionLock.notify();
            }
        }
    }

    /**
     * Get log default values from native daemons.
     */
    protected void getLogDefaultValues() {
    }

    /**
     * Initialize log connection. If connect to native layer success, return
     * true, else, false.
     *
     * Call this method in then end of sub class constructor
     *
     * @return boolean
     */
    public boolean initLogConnection() {
        return initLogConnection(mLogConnection);
    }

    /**
     * @param logConnection LogConnection
     * @return boolean
     */
    public static boolean initLogConnection(LogConnection logConnection) {
        Utils.logd(TAG, "-->initLogConnection() with parameter");
        if (logConnection == null) {
            Utils.loge(TAG, "LogConnection is null");
            return false;
        }
        return logConnection.connect();
    }

    /**
     * Get current SD card or internal storage status, whether is ready for log
     * capture, and have enough space.
     *
     * @return int
     */
    public int getLogStorageState() {

        // First check whether storage is mounted
        String currentLogPathType = Utils.getLogPathType();
        Utils.logd(TAG, "-->getStorageState(), currentLogPathType=" + currentLogPathType);
        if (Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(currentLogPathType)
                || Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(currentLogPathType)) {
            String status = Utils.getCurrentVolumeState(sContext);
            if (!Environment.MEDIA_MOUNTED.equals(status)) {
                Utils.logw(TAG, "Log storage is not ready yet, state=" + status);
                return Utils.STORAGE_STATE_NOT_READY;
            }
        }

        int availableLogStorageSize = getAvailableLogMemorySize();
        if (availableLogStorageSize < Utils.RESERVED_STORAGE_SIZE) {
            Utils.logw(TAG, "Not enough storage for log, current available value="
                    + availableLogStorageSize + "MB");
            return Utils.STORAGE_STATE_FULL;
        }
        Utils.logd(TAG, "<--getStorageState(), storage is ready for log capture.");
        return Utils.STORAGE_STATE_OK;
    }

    /**
     * Get current used storage remaining space.
     * @return
     */
    private int getAvailableLogMemorySize() {
        String path = null;
        String logPathType = Utils.getLogPathType();

        if (Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(logPathType)
                || Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(logPathType)) {
            path = Utils.getCurrentLogPath(sContext);
            return Utils.getAvailableStorageSize(path);
        } else if (Utils.LOG_PATH_TYPE_PHONE.equals(logPathType)) { // internal
                                                                    // storage
            File pathFile = Environment.getDataDirectory();
            return Utils.getAvailableStorageSize(pathFile.getPath());
        } else {
            Utils.loge(TAG, "Unknown log path type: " + logPathType);
            return 0;
        }
    }

    private static boolean sIsnotLaunchMainUI = false;
    /**
     * Update this log instance's status into status bar.
     * @param logType int
     * @param titleRes int
     * @param enable boolean
     */
    public void updateStatusBar(int logType, int titleRes, boolean enable) {
        Utils.logd(TAG, "-->updateStatusBar(), logType=" + logType + ", titleRes=" + titleRes
                + ", enable=" + enable);
        synchronized (RUNNING_NOTIFICATION_MAP) {
            if (isLogRunning(logType)) {
                if (RUNNING_NOTIFICATION_MAP.indexOfKey(logType) >= 0) {
                    Utils.logd(TAG, "The log running status has been showing, igore new update!"
                            + " logType = " + logType);
                    if (Utils.isAutoTest() == sIsnotLaunchMainUI) {
                        return;
                    }
                }
                RUNNING_NOTIFICATION_MAP.put(logType, titleRes);
                PENDING_ON_NOTIFICATION_MAP.put(logType, titleRes);
                PENDING_OFF_NOTIFICATION_MAP.delete(logType);
            } else {
                if (RUNNING_NOTIFICATION_MAP.indexOfKey(logType) < 0) {
                    Utils.logd(TAG,
                            "The log running status does not been showing, igore new update!"
                            + " logType = " + logType);
                    return;
                }
                RUNNING_NOTIFICATION_MAP.delete(logType);
                PENDING_OFF_NOTIFICATION_MAP.put(logType, titleRes);
                PENDING_ON_NOTIFICATION_MAP.delete(logType);
            }
        }

        int icon = R.drawable.notification;
        if (sNotificationHandler.hasMessages(icon)) {
            // Remove old cached one
            Utils.logw(TAG, "Too frequent status bar update request, slow down.");
            sNotificationHandler.removeMessages(icon);
        }
        sNotificationHandler.sendEmptyMessageDelayed(icon, INTERNAL_UPDATE_DURATION);
    }

    /**
     * The up limitation of update system status bar.
     */
    private static final int INTERNAL_UPDATE_DURATION = 1000;
    /**
     * Use this global object to avoid too frequent status bar update. Two
     * update in 1000ms will be treated as one
     */
    private static Handler sNotificationHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            if (msg != null) {
                synchronized (RUNNING_NOTIFICATION_MAP) {
                    int icon = msg.what;
                    if (RUNNING_NOTIFICATION_MAP.size() <= 0) { // All log types
                                                                // are off, just
                                                                // clear
                                                                // notification
                                                                // bar
                        showLogStatusInNotificationBar(false, null, null, icon, null);
                    } else {
                        String notificationTitle = sContext.getString(R.string.notification_title);
                        String notificationSummary =
                                getLogStateDescStr(RUNNING_NOTIFICATION_MAP,
                                        R.string.notification_on_summary_suffix,
                                        R.string.notification_on_summary_suffixes);
                        String onLogStr =
                                getLogStateDescStr(PENDING_ON_NOTIFICATION_MAP,
                                        R.string.notification_on_summary_suffix,
                                        R.string.notification_on_summary_suffixes);
                        String offLogStr =
                                getLogStateDescStr(PENDING_OFF_NOTIFICATION_MAP,
                                        R.string.notification_off_summary_suffix,
                                        R.string.notification_off_summary_suffixes);
                        String tickerText = "";
                        if (!TextUtils.isEmpty(onLogStr) && !TextUtils.isEmpty(offLogStr)) {
                            tickerText = onLogStr + " " + offLogStr;
                        } else {
                            tickerText = onLogStr + offLogStr;
                        }

                        showLogStatusInNotificationBar(true, notificationTitle,
                                notificationSummary, icon, tickerText);
                    }
                    PENDING_ON_NOTIFICATION_MAP.clear();
                    PENDING_OFF_NOTIFICATION_MAP.clear();
                }
            }
        };
    };

    /**
     * Get a description string of logs in logStateMap. For example, if
     * logStateMap mean running log, and contains both MobileLog and NetworkLog,
     * then it will return "MobleLog,NetworkLog are on", else if this map only
     * contains MobileLog, it will return "MobileLog is on"
     * @param logStateMap Map<Integer, Integer>
     * @param singleRes int
     * @param multiRes int
     * @return
     */
    private static String getLogStateDescStr(SparseIntArray logStateMap, int singleRes,
            int multiRes) {
        if ((logStateMap == null) || (logStateMap.size() == 0)) {
            return "";
        }
        String descSuffix = "";
        if (logStateMap.size() == 1) {
            descSuffix = sContext.getString(singleRes);
        } else {
            descSuffix = sContext.getString(multiRes);
        }

        String descStr = "";
        for (int i = 0; i < logStateMap.size(); i++) {
            if (!"".equals(descStr)) {
                descStr += ", ";
            }
            descStr += sContext.getString(logStateMap.valueAt(i));
        }
        descStr = descStr + " " + descSuffix;
        return descStr;
    }

    private int mIsSpecialSupportValue = 0;

    /**
     * @param supportValue String
     */
    public void setSpecialFeatureSupport(String supportValue) {
        Utils.logi(TAG,
                "setSpecialFeatureSupport() supportValue = " + supportValue);
        try {
            mIsSpecialSupportValue = Integer.parseInt(supportValue);
        } catch (NumberFormatException nfe) {
            Utils.logw(TAG, "The format for supportValue " + supportValue + " is error!");
        }
    }

    /**
     * @return int
     */
    public int getSpecialFeatureSupport() {
        return mIsSpecialSupportValue;
    }

    /**
     * void.
     */
    public static void updateNotificationTime() {
        if (sNotificationBuilder != null) {
            sNotificationBuilder.setWhen(System.currentTimeMillis());
        }
    }

    /**
     * Update log instance status flag in notification bar.
     *
     * @param enable
     *            show or hide status bar notification
     * @param title String
     * @param summary String
     * @param icon
     *            the icon in notification, and this value will be the id of
     *            this notification
     * @param tickerText
     *            the string pop up when show this notification in status bar
     */
    public static void showLogStatusInNotificationBar(boolean enable, String title, String summary,
            int icon, String tickerText) {
        Utils.logi(TAG, "-->showLogStatusInNotificationBar(), enable?" + enable + ", title="
                + title + ", summary=" + summary + ", tickerText=" + tickerText + ", icon=" + icon);
        if (!sDefaultSharedPreferences.getBoolean(
                Utils.KEY_PREFERENCE_NOTIFICATION_ENABLED, true)) {
            Utils.logw(TAG, "Notification is disabled, does not show any notification.");
            return;
        }
        if (sNM == null) {
            sNM = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        sIsnotLaunchMainUI = Utils.isAutoTest();
        if (enable) {
            PendingIntent contentIntent = null;
            Intent backIntent = new Intent(sContext, MainActivity.class);
            backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (sContext.getPackageManager().resolveActivity(backIntent, 0) != null
                    && !Utils.isAutoTest()) {
                contentIntent = PendingIntent.getActivity(sContext, 0, backIntent, 0);
            } else {
                Utils.loge(TAG, "Could not find MTKLogger settings page.");
            }
            if (sNotificationBuilder == null) {
                sNotificationBuilder = new Notification.Builder(sContext);
                sNotificationBuilder
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(title)
                    .setSmallIcon(icon);
            }
            sNotificationBuilder.setContentIntent(contentIntent);
            sNotificationBuilder
                .setContentText(summary)
                .setTicker(tickerText);

            if (sContext instanceof MTKLoggerService) {
                Utils.logi(TAG, "Give service foreground priority since log is on");
                if (!sHasForegroundPriority) {
                    // Make Service start in foreground.
                    Utils.logi(TAG, "Set service to have foreground priority!");
                    ((MTKLoggerService) sContext).startForeground(icon,
                            sNotificationBuilder.build());
                    sHasForegroundPriority = true;
                }
                try {
                    sNM.notify(icon, sNotificationBuilder.build());
                } catch (ClassCastException e) {
                    Utils.loge(TAG, "ClassCastException mNotificationManager error!");
                } catch (ArrayIndexOutOfBoundsException e) {
                    Utils.loge(TAG, "ArrayIndexOutOfBoundsException mNotificationManager error!");
                }
            }

        } else {
            if (sContext instanceof MTKLoggerService) {
                Utils.logi(TAG,
                        "Resume service's priority since log is off, hasForegroundPriority="
                                + sHasForegroundPriority);
                ((MTKLoggerService) sContext).stopForeground(true);
                sHasForegroundPriority = false;
                if (sNM != null) {
                    sNM.cancel(icon);
                    sNotificationBuilder = null;
                }
            }
        }
    }

    protected boolean mIsRestarting = false;
    /**
     * Special handle for each log instance, used to deal with each instance's
     * special message
     *
     * Must be initialized in main thread.
     *
     * Some common message can be deal here
     */
    class LogHandler extends Handler {
        public LogHandler(Looper loop) {
            super(loop);
        }
        public void handleMessage(Message msg, int logType) {
            Utils.logi(TAG, "Logtype[" + logType + "] Handle receive message, what=" + msg.what);
            switch (msg.what) {
            case MSG_RESTART:
                mIsRestarting = true;
                mHandler.obtainMessage(LogInstance.MSG_STOP,
                        Utils.ADB_COMMAND_RESTART).sendToTarget();
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_RESTART_STOP_TIMEOUT), START_STOP_TIMEOUT);
                break;
            case MSG_RESTART_STOP_TIMEOUT:
                mHandler.obtainMessage(LogInstance.MSG_INIT,
                        Utils.ADB_COMMAND_RESTART).sendToTarget();
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_RESTART_TIMEOUT), START_STOP_TIMEOUT);
                break;
            case MSG_RESTART_STOP_DONE:
                mHandler.removeMessages(MSG_RESTART_STOP_TIMEOUT);
                mHandler.obtainMessage(LogInstance.MSG_INIT,
                        Utils.ADB_COMMAND_RESTART).sendToTarget();
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_RESTART_TIMEOUT), START_STOP_TIMEOUT);
                break;
            case MSG_RESTART_TIMEOUT:
                mIsRestarting = false;
                mCmdResHandler.obtainMessage(Utils.MSG_RESTART_DONE, logType, 0).sendToTarget();
                break;
            case MSG_RESTART_DONE:
                mHandler.removeMessages(MSG_RESTART_TIMEOUT);
                mIsRestarting = false;
                mCmdResHandler.obtainMessage(Utils.MSG_RESTART_DONE, logType, 1).sendToTarget();
                break;
            default:
                Utils.logw(TAG, "Logtype[" + logType + "] Not supported message: " + msg.what);
                break;
            }
        }
    }

    protected boolean mIsNeedNotifyLogStatusChanged = true;
    /**
     * Notify current modem log running status to other component.
     *
     * @param status
     * @param reason
     */
    protected void notifyServiceStatus(int status, String reason) {
        if (mIsRestarting) {
            Utils.logi(TAG, "-->notifyServiceStatus(), status=" + status
                    + ",  reason=[" + reason + "]"
                    + ", mIsRestarting = " + mIsRestarting);
            int what = MSG_RESTART_STOP_DONE;
            if (Utils.VALUE_STATUS_RUNNING == status) {
                what = MSG_RESTART_DONE;
            } else if (mHandler.hasMessages(MSG_RESTART_TIMEOUT)) {
                what = MSG_RESTART_TIMEOUT;
            }
            mHandler.obtainMessage(what, 1).sendToTarget();
        }
    }

    /**
     * Check whether the current running log folder still exist, if not, may
     * need to stop log manually(For NetworkLog).
     */
    public void checkLogFolder() {
    }

    /**
     * Return the current running stage which may affect UI display behaviors,
     * for example, if modem log is dumping, the whole UI should be disabled. By
     * default, detail log instance will not affect the global UI
     * @return int
     */
    public int getGlobalRunningStage() {
        return Utils.RUNNING_STAGE_IDLE;
    }

    public int getLogRunningStatus() {
        return Utils.LOG_RUNNING_STATUS_UNKNOWN;
    }

    protected String mCommandValue;
    protected String mCommandResult;
    /**
     * @param commandValue String.
     * @return String.
     */
    synchronized public String getValueFromNative(String commandValue) {
        mCommandValue = commandValue;
        mCommandResult = null;
        mHandler.obtainMessage(LogInstance.MSG_GET_VALUE_FROM_NATIVE,
                commandValue).sendToTarget();
        long timeout = 5000;
        long checkPeriod = 100;
        while (mCommandResult == null && timeout > 0) {
            try {
                Thread.sleep(checkPeriod);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeout -= checkPeriod;
        }
        return mCommandResult;
    }

    /**
     * @param logType int
     * @return boolean
     */
    public boolean isLogRunning(int logType) {
        boolean isLogRunning = false;
        String key = Utils.KEY_LOG_RUNNING_STATUS_IN_SYSPROP_MAP.get(logType);
        if (key == null) {
            isLogRunning = (Utils.VALUE_STATUS_RUNNING == mSharedPreferences
                    .getInt(Utils.KEY_STATUS_MAP.get(logType),
                            Utils.VALUE_STATUS_DEFAULT));
            Utils.logd(TAG, " SharedPreferences log(" + logType + ")"
                    + " running status=" + isLogRunning);
            return isLogRunning;
        }
        String nativeStatus = SystemProperties.get(key,
                Utils.VALUE_LOG_RUNNING_STATUS_IN_SYSPROP_OFF);
        isLogRunning = Utils.VALUE_LOG_RUNNING_STATUS_IN_SYSPROP_ON.equals(nativeStatus);
        Utils.logd(TAG, " Native log(" + logType + ") running status="
                + isLogRunning);
        return isLogRunning;
    };

    /**
     * @param logType int
     * @return String
     */
    public String getRunningLogPath(int logType) {
        Utils.logd(TAG, "-->getRunningLogPath(), logType = " + logType);
        String runningLogPath = null;
        if (!isLogRunning(logType)) {
            return runningLogPath;
        }
        String parentPath = Utils.getCurrentLogPath(sContext) + Utils.MTKLOG_PATH
                + Utils.LOG_PATH_MAP.get(logType);
        File fileTree = new File(parentPath + File.separator + Utils.LOG_TREE_FILE);
        File logFile = Utils.getLogFolderFromFileTree(fileTree, false);
        if (null != logFile && logFile.exists()) {
            runningLogPath = logFile.getAbsolutePath();
        }
        Utils.logi(TAG, "<--getRunningLogPath(), runningLogPath = " + runningLogPath);
        return runningLogPath;
    }
}
