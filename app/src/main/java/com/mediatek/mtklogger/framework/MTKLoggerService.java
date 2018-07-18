package com.mediatek.mtklogger.framework;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.WindowManager;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.LogInstance.LogHandler;
import com.mediatek.mtklogger.proxy.communicate.ProxyReceiver;
import com.mediatek.mtklogger.settings.GPSLogSettings;
import com.mediatek.mtklogger.settings.ModemLogSettings;
import com.mediatek.mtklogger.settings.SettingsActivity;
import com.mediatek.mtklogger.taglog.TagLogManager;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author MTK81255
 *
 */
public class MTKLoggerService extends Service {
    private static final String TAG = Utils.TAG + "/MTKLoggerService";

    /**
     * Map for storing each current running log instance. Key: logType
     */
    private SparseArray<LogInstance> mLogInstanceMap = new SparseArray<LogInstance>();
    private SharedPreferences mSharedPreferences = null;
    /**
     * Some settings like log auto start status will be stored in default
     * preference, so UI do not need to take care these values manually.
     */
    private SharedPreferences mDefaultSharedPreferences = null;

    /**
     * Which stage this service is running in, like starting/stopping log,
     * memory dumping.
     */
    private int mGlobalRunningStage = Utils.RUNNING_STAGE_IDLE;

    /**
     * Current log storing path type, one of phone, internal SD card and
     * external SD card.
     */

    /**
     * When service destroy, or log stopped, need to stop monitor thread. true:
     * should stop monitor thread; false: monitor thread can be running
     */
    private boolean mLogFolderMonitorThreadStopFlag = true;
    LogFolderMonitor mMonitorLogFolderThread = null;

    private NotificationManager mNM = null;

    /**
     * Monitor remaining log storage, remember it. When it become too less, give
     * a notification
     */
    private int mRemainingStorage = 0;

    /**
     * When sd card unmount, if log stopped by itself before MTKLogger receive
     * unmount broadcast in SD_STATUS_CHANGE_CHECK_TIME ms, still recover the
     * log when sd card mount.
     */
    private final static long SD_STATUS_CHANGE_CHECK_TIME = 15000;

    private long mLastSDStatusChangedTime = 0;
    private Handler mServiceHandler;
    private static final int MSG_SHOW_LOW_MEMORY_DIALOG = 1;
    private BroadcastReceiver mProxyReceiver = new ProxyReceiver();

    @Override
    public void onCreate() {
        Utils.logi(TAG, "-->onCreate()");
        super.onCreate();
        this.setTheme(android.R.style.Theme_Holo_Light);
        mSharedPreferences = getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Monitor USB status
        mUSBStatusIntentFilter = new IntentFilter();
        mUSBStatusIntentFilter.addAction(Utils.ACTION_USB_STATE_CHANGED);
        registerReceiver(mUSBStatusReceiver, mUSBStatusIntentFilter,
                "android.permission.DUMP", null);

        // Monitor SD card storage status
        mSDStatusIntentFilter = new IntentFilter();
        mSDStatusIntentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        mSDStatusIntentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        mSDStatusIntentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        mSDStatusIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mSDStatusIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mSDStatusIntentFilter.addDataScheme("file");
        registerReceiver(mStorageStatusReceiver, mSDStatusIntentFilter,
                "android.permission.DUMP", null);

        // Monitor Phone internal storage status change
        mPhoneStorageIntentFilter = new IntentFilter();
        mPhoneStorageIntentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        mPhoneStorageIntentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        registerReceiver(mStorageStatusReceiver, mPhoneStorageIntentFilter,
                "android.permission.DUMP", null);

        // Monitor shutdown event
        IntentFilter shutdownIntentFilter = new IntentFilter();
        shutdownIntentFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mShutdonwReceiver, shutdownIntentFilter,
                "android.permission.DUMP", null);

        // Monitor time changed
        IntentFilter timeChangedIntentFilter = new IntentFilter();
        timeChangedIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        timeChangedIntentFilter.addAction(Intent.ACTION_DATE_CHANGED);
        timeChangedIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(mTimeChangedReceiver, timeChangedIntentFilter,
                "android.permission.DUMP", null);

        // Monitor MTKLogger Proxy result
        IntentFilter proxyIntentFilter = new IntentFilter();
        proxyIntentFilter.addAction(Utils.PROXY_ACTION_RECEIVER_RETURN);
        registerReceiver(mProxyReceiver, proxyIntentFilter,
                "android.permission.DUMP", null);

        IntentFilter userFilter = new IntentFilter();
        userFilter.addAction(Intent.ACTION_USER_SWITCHED);
        registerReceiver(mUserSwitchReceiver, userFilter,
                "android.permission.DUMP", null);

        HandlerThread handlerThread = new HandlerThread(this.getClass().getName());
        handlerThread.start();
        mServiceHandler = new ServiceHandler(handlerThread.getLooper());

        // Print version info
        try {
            String versionName =
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            Utils.logi(TAG, "Version name=" + versionName);
        } catch (NameNotFoundException e) {
            Utils.loge(TAG, "Fail to get application version name.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.logi(TAG, "-->onStartCommand()");
        showNotificationBar();
        if (!Utils.isDeviceOwner()) {
            Utils.logi(TAG, "It is not device owner, do nothing!");
            return Service.START_NOT_STICKY;
        }
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
        } catch (IllegalArgumentException iae) {
            Utils.logw(TAG, "Set mtklogger service to foreground failed!");
        } catch (SecurityException se) {
            Utils.logw(TAG, "Set mtklogger service to foreground failed!");
        }
        startSocketService();
        startExceptionMonitor();
        if (Utils.isTaglogEnable() && Utils.isBootCompleteDone()) {
            // Restarted by system, check whether need to resume TagLog process
            Utils.logd(TAG, "Service is first started,"
                    + " check whether need to resume TagLog process");
            TagLogManager.getInstance().startTagLogManager();
        }
        if (Utils.isBootCompleteReceived() && !Utils.isBootCompleteDone()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!Utils.isBootCompleteDone()) {
                        initLogsForBootup();
                    }
                }
            }).start();
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Utils.logi(TAG, "-->onDestroy()");
        String isMonkeyRunning = SystemProperties.get(Utils.PROP_MONKEY, "false");
        if ("true".equalsIgnoreCase(isMonkeyRunning)) {
            Utils.logi(TAG, "Monkey is running, MTKLoggerService destroy failed!");
            return;
        }

        unregisterReceiver(mUSBStatusReceiver);
        unregisterReceiver(mStorageStatusReceiver);
        unregisterReceiver(mShutdonwReceiver);
        unregisterReceiver(mUserSwitchReceiver);
        unregisterReceiver(mTimeChangedReceiver);
        unregisterReceiver(mProxyReceiver);
        mLogFolderMonitorThreadStopFlag = true;

        stopExceptionMonitor();
        super.onDestroy();
    }

    /**
     * return void.
     */
    private void startSocketService() {
        AEEConnection.getInstance().startSocketServer();
    }
    /**
     * return void.
     */
    private void startExceptionMonitor() {
        ExceptionMonitor.getInstance().startExceptionMonitor();
    }
    /**
     * return void.
     */
    private void stopExceptionMonitor() {
        ExceptionMonitor.getInstance().stopExceptionMonitor();
    }
    private boolean mIsDoingInitLogsForBootup = false;
    /**
     * return void.
     */
    synchronized public void initLogsForBootup() {
        Utils.logi(TAG, "initLogsForBootup(), "
                + "mIsDoingInitLogsForBootup = " + mIsDoingInitLogsForBootup);
        if (mIsDoingInitLogsForBootup) {
            return;
        }
        mIsDoingInitLogsForBootup = true;
        initLogStatus(Utils.SERVICE_STARTUP_TYPE_BOOT);
        String currentLogPath = Utils.getCurrentLogPath(MTKLoggerService.this);
        long timeout = 15000;
        while (currentLogPath == null || currentLogPath.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeout -= 1000;
            if (timeout <= 0) {
                Utils.logw(TAG, "Waiting currentLogPath timeout for 15s!");
                break;
            }
            currentLogPath = Utils.getCurrentLogPath(MTKLoggerService.this);
        }

        changeLogRunningStatus(true, Utils.SERVICE_STARTUP_TYPE_BOOT);
        Utils.logd(TAG, "Service is first started,"
                + " check whether need to resume TagLog process");
        TagLogManager.getInstance().startTagLogManager();
        Utils.setBootCompleteDone();
    }

    /**
     * @param intent Intent
     */
    public void doTagLogForManually(Intent intent) {
        String dbPath = intent.getStringExtra(Utils.EXTRA_KEY_EXP_PATH);
        if (Utils.MANUAL_SAVE_LOG.equalsIgnoreCase(dbPath)) {
            TagLogManager.getInstance().beginTagLog(intent);
        }
    }

    /**
     * @param dbPath String
     */
    public void doCopyDbToMTKLog(String dbPath) {
        Utils.logi(TAG, "doCopyDbToMTKLog(), dbPath = " + dbPath);
        mServiceHandler.obtainMessage(Utils.MSG_COPY_DB_TO_MTKLOG, dbPath).sendToTarget();
    }

    /**
     * @param intent Intent
     */
    public void dealWithBypassAction(Intent intent) {
        Intent intentToBypass = new Intent(Utils.ACTION_TO_BYPASS);
        String cmdName = intent.getStringExtra(Utils.EXTRA_ADB_CMD_NAME);
        Utils.logi(TAG, "dealWithBypassAction(), cmdName = " + cmdName);
        if (cmdName != null && cmdName.length() > 0
                && Utils.VALUE_BYPASS_GET_STATUS.equalsIgnoreCase(cmdName)) {
            intentToBypass.putExtras(intent.getExtras());
            int returnValue = mSharedPreferences.getInt(
                    Utils.KEY_STATUS_MAP.get(Utils.LOG_TYPE_MODEM), Utils.VALUE_STATUS_DEFAULT);
            intentToBypass.putExtra(Utils.EXTRA_CMD_RESULT, returnValue);
            Utils.logi(TAG, " returnValue = " + returnValue);
            Utils.sendBroadCast(intentToBypass);
        } else {
            Utils.loge(TAG, "The intent from " +
                    Utils.ACTION_FROM_BYPASS + " is not support!");
        }
    }

    /**
     * @param intent Intent
     */
    public void daelWithADBCommand(Intent intent) {
        int logCluster = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET,
                Utils.DEFAULT_ADB_CMD_TARGET);
        String command = intent.getStringExtra(Utils.EXTRA_ADB_CMD_NAME);
        String logname = intent.getStringExtra(Utils.EXTRA_ADB_CMD_LOGNAME);
        Utils.logd(TAG, "Receive adb command, logCluster=" + logCluster + ", command="
                + command + ", logname" + logname);
        if (mDefaultSharedPreferences != null
                && Utils.MODEM_MODE_USB.equals(mDefaultSharedPreferences.getString(
                        Utils.KEY_MD_MODE_1, Utils.MODEM_MODE_SD))
                && MultiModemLog.ADB_COMMAND_FORCE_MODEM_ASSERT.equals(command)) {
            Utils.logw(TAG, "In USB mode, force modem assert command is not supported");
            return;
        }
        if (Utils.ADB_COMMAND_SWITCH_TAGLOG.equals(command)) {
            int iTagLogEnabled = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET,
                    Utils.TAGLOG_CONFIG_VALUE_INVALID);
            Utils.logd(TAG, "Receive a Taglog configuration broadcast, target value="
                    + iTagLogEnabled);
            if (Utils.TAGLOG_CONFIG_VALUE_ENABLE == iTagLogEnabled) {
                mSharedPreferences.edit().putBoolean(Utils.TAG_LOG_ENABLE, true).apply();
            } else if (Utils.TAGLOG_CONFIG_VALUE_DISABLE == iTagLogEnabled) {
                mSharedPreferences.edit().putBoolean(Utils.TAG_LOG_ENABLE, false).apply();
            } else {
                Utils.logw(TAG, "Configure taglog value invalid: " + iTagLogEnabled);
            }
            return;
        } else if (Utils.ADB_COMMAND_ALWAYS_TAG_MODEMLOG.equals(command)) {
            int iAlwaysTagModemLogEnabled = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET,
                    Utils.TAGLOG_CONFIG_VALUE_INVALID);
            Utils.logd(TAG, "Receive a Always Tag ModemLog configuration broadcast,"
                    + " target value = " + iAlwaysTagModemLogEnabled);
            if (iAlwaysTagModemLogEnabled == 1) {
                mDefaultSharedPreferences.edit().putBoolean(
                        SettingsActivity.KEY_ALWAYS_TAG_MODEM_LOG_ENABLE, true).apply();
            } else if (iAlwaysTagModemLogEnabled == 0) {
                mDefaultSharedPreferences.edit().putBoolean(
                        SettingsActivity.KEY_ALWAYS_TAG_MODEM_LOG_ENABLE, false).apply();
            } else {
                Utils.logw(TAG, "Configure Always Tag ModemLog value invalid: "
                        + iAlwaysTagModemLogEnabled);
            }
            return;
        } else if (Utils.ADB_COMMAND_MONITOR_ABNORMAL_EVENT.equals(command)) {
            int iMonitor = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET,
                    Utils.TAGLOG_CONFIG_VALUE_INVALID);
            Utils.logd(TAG, "Receive a monitor abnormal event broadcast, iMonitor value="
                    + iMonitor);
            if (1 == iMonitor) {
                mDefaultSharedPreferences.edit().putBoolean(
                        ModemLogSettings.KEY_MD_MONITOR_MODEM_ABNORMAL_EVENT, true).apply();
            } else if (0 == iMonitor) {
                mDefaultSharedPreferences.edit().putBoolean(
                        ModemLogSettings.KEY_MD_MONITOR_MODEM_ABNORMAL_EVENT, false).apply();
            } else {
                Utils.logw(TAG, "Configure monitor abnormal event value invalid: "
                           + iMonitor);
            }
            return;
        } else if (Utils.ADB_COMMAND_SWITCH_LOGPATH.equals(command)) {
            String targetLogpath = intent.getStringExtra(Utils.EXTRA_ADB_CMD_TARGET);
            Utils.logd(TAG, "Receive a log path swithc configuration broadcast, target value="
                    + targetLogpath);
            // Let modem log instance to handle this command
            command = command + "," + targetLogpath;
            logCluster = Utils.LOG_TYPE_NETWORK;
        } else if (command.startsWith(Utils.ADB_COMMAND_SWITCH_MODEM_LOG_MODE)) {
            int targetLogMode = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET, 0);
            Utils.logd(TAG, "Receive a modem log mode configuration broadcast, target value="
                    + targetLogMode);
            // Let modem log instance to handle this command
            command = command + "," + targetLogMode;
            logCluster = Utils.LOG_TYPE_MODEM;
        } else if (command.startsWith(Utils.ADB_COMMAND_SET_MODEM_LOG_SIZE)) {
            int targetMDLogIndex = intent.getIntExtra(Utils.EXTRA_ADB_CMD_TARGET, 0);
            Utils.logd(TAG, "Receive a set modem log size configuration broadcast,"
                    + " target modemindex=" + targetMDLogIndex);
            command = command + "," + targetMDLogIndex;
            logCluster = Utils.LOG_TYPE_MODEM;
        }
        if (logname != null && command.equals(MultiModemLog.ADB_COMMAND_FORCE_MODEM_ASSERT)) {
            command += ":" + logname;
        }
        dealWithAdbCommand(logCluster, command);
    }

    /**
     * @param intent Intent
     */
    public void dealWithMDLoggerRestart(Intent intent) {
        String resetModemIndexStr = intent.getStringExtra(Utils.EXTRA_RESET_MD_INDEX);
        int resetModemIndex = 0;
        if (resetModemIndexStr != null && resetModemIndexStr.length() != 0) {
            try {
                resetModemIndex = Integer.parseInt(resetModemIndexStr);
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "Reset modem log instance index format is error!");
            }
        }
        Utils.logi(TAG, "reset modem log instance index=" + resetModemIndex);
        // Receive update command from native, just update log running
        // status.

        LogInstance logInstance = getLogInstance(Utils.LOG_TYPE_MODEM);
        if (logInstance.mHandler != null) {
            Message message = logInstance.mHandler.obtainMessage(LogInstance.MSG_RESTORE_CONN);
            message.arg1 = resetModemIndex;
            message.sendToTarget();
        } else {
            Utils.loge(TAG, "When updateLogStatus(), fail to get log instance handler of "
                    + "log [" + Utils.LOG_TYPE_MODEM + "]");
        }
    }

    private void showNotificationBar() {
        Utils.logd(TAG, "-->showNotificationBar()");
        // Use this temp instance to udpate status bar
        LogInstance instance = new LogInstance(this);
        for (Integer logType : Utils.LOG_TYPE_SET) {
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
            boolean isLogRunning = instance.isLogRunning(logType);
            if (isLogRunning) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MAP.get(logType), Utils.VALUE_STATUS_RUNNING)
                        .apply();
                instance.updateStatusBar(logType,
                        Utils.KEY_LOG_TITLE_RES_IN_STSTUSBAR_MAP.get(logType), true);
            }
        }
    }
    /**
     * Since service maybe killed, when service is restarted, update current log
     * running status according to native flag.
     */
    private void initLogStatus(String reason) {
        Utils.logd(TAG, "-->initLogStatus()");
        // Use this temp instance to udpate status bar
        LogInstance instance = new LogInstance(this);
        Set<Integer> localLogSet = new HashSet<Integer>();
        for (Integer logType : Utils.LOG_TYPE_SET) {
            localLogSet.add(logType);
        }
        for (Integer logType : localLogSet) {
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
            boolean isLogRunning = isTypeLogRunning(logType);
            if (isLogRunning) {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MAP.get(logType), Utils.VALUE_STATUS_RUNNING)
                        .apply();
                instance.updateStatusBar(logType,
                        Utils.KEY_LOG_TITLE_RES_IN_STSTUSBAR_MAP.get(logType), true);
                if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(reason)) {
                    continue;
                }
                // Try to reconnect to native layer since native is still
                // running
                LogInstance logInstance = getLogInstance(logType);
                if (logInstance.mHandler != null) {
                    logInstance.mHandler.obtainMessage(LogInstance.MSG_RESTORE_CONN).sendToTarget();
                } else {
                    Utils.loge(TAG, "When updateLogStatus(), fail to get log instance handler of "
                            + "log [" + logType + "]");
                }
            } else {
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MAP.get(logType), Utils.VALUE_STATUS_STOPPED)
                        .apply();
                instance.updateStatusBar(logType,
                        Utils.KEY_LOG_TITLE_RES_IN_STSTUSBAR_MAP.get(logType), false);
                // Check whether need to restore modem assert dialog
                String assertFileStr = mSharedPreferences.getString(
                        Utils.KEY_MODEM_ASSERT_FILE_PATH, "");
                boolean needReconnectModemLog = false;
                if (!TextUtils.isEmpty(assertFileStr)) {
                    Utils.logw(TAG, " Modem assert file path is not null,"
                            + " need to re-show assert dialog");
                    needReconnectModemLog = true;
                }
                // Even modem log was stopped, if need to show reset dialog,
                // reconnect it
                if (logType == Utils.LOG_TYPE_MODEM && needReconnectModemLog) {
                    // Try to reconnect to native layer since need to show reset
                    // dialog
                    LogInstance logInstance = getLogInstance(logType);
                    if (logInstance.mHandler != null) {
                        logInstance.mHandler.obtainMessage(LogInstance.MSG_RESTORE_CONN)
                                .sendToTarget();
                    } else {
                        Utils.loge(TAG, "When try to reconnect to modem log,"
                                + " fail to get log instance handler");
                    }
                }
            }
        }
    }

    /**
     * Update log folder monitor's running state, when log started, start
     * monitor, when log stopped, stop the former monitor thread.
     */
    private void updateLogFolderMonitor() {
        boolean isLogRunning = isAnyLogRunning();

        Utils.logd(TAG, "-->updateLogFolderMonitor(), isLogRunning=" + isLogRunning
                + ", mLogFolderMonitorThreadStopFlag=" + mLogFolderMonitorThreadStopFlag);
        synchronized (mLock) {
            if (isLogRunning && mLogFolderMonitorThreadStopFlag) {
                mMonitorLogFolderThread = new LogFolderMonitor();
                mMonitorLogFolderThread.start();
                mLogFolderMonitorThreadStopFlag = false;
                Utils.logv(TAG, "Log is running, so start monitor log folder");
            } else if (!isLogRunning && !mLogFolderMonitorThreadStopFlag) {
                Utils.logv(TAG, "Log is stopped,"
                        + " so need to stop log folder monitor if any exist.");
                mLogFolderMonitorThreadStopFlag = true;
                if (mMonitorLogFolderThread != null) {
                    mMonitorLogFolderThread.interrupt();
                    mMonitorLogFolderThread = null;
                }
                // Since log were all stopped, reset storage monitor status
                if (mNM == null) {
                    mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                }
                if (mDefaultSharedPreferences.getBoolean(Utils.KEY_PREFERENCE_NOTIFICATION_ENABLED,
                        true)) {
                    mNM.cancel(R.drawable.ic_notification_low_storage);
                }
                mRemainingStorage = 0;
            }
        }
    }

    private ServiceBinder mServiceBinder = new ServiceBinder();
    @Override
    public IBinder onBind(Intent intent) {
        Utils.logi(TAG, "-->onBind()");
        return mServiceBinder;
    }

    /**
     * @author MTK81255
     *
     */
    public class ServiceBinder extends Binder {
        public MTKLoggerService getMTKLoggerService() {
            return MTKLoggerService.this;
        }
    }

    private IntentFilter mUSBStatusIntentFilter = null;
    private BroadcastReceiver mUSBStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Utils.ACTION_USB_STATE_CHANGED.equals(action)) {
                boolean isInited = Utils.isBootCompleteDone();
                Utils.logi(TAG, " Current init status is " + isInited);
                if (!isInited) {
                    Utils.logi(TAG, "The service is not inited,"
                            + " ignore ACTION_USB_STATE_CHANGED broadcast!");
                    return;
                }
                boolean isModemLogRunning = isTypeLogRunning(Utils.LOG_TYPE_MODEM);
                String modemLogRunningMode =
                        mDefaultSharedPreferences.getString(Utils.KEY_MD_MODE_1,
                                Utils.MODEM_MODE_SD);
                Utils.logv(TAG, "isModemLogRunning?" + isModemLogRunning + ", modemLogRunningMode="
                        + modemLogRunningMode);
                if (!isModemLogRunning || !Utils.MODEM_MODE_USB.equals(modemLogRunningMode)) {
                    return;
                }

                boolean usbConfigured = intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false);
                boolean newUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                int newUsbMode = Utils.getCurrentUsbMode(intent);
                int oldUsbModeValue =
                        mSharedPreferences.getInt(Utils.KEY_USB_MODE_VALUE,
                                Utils.VALUE_USB_MODE_UNKNOWN);
                boolean oldUsbConnected =
                        mSharedPreferences.getBoolean(Utils.KEY_USB_CONNECTED_VALUE, false);
                boolean needNotifyModemLog =
                        (newUsbMode != oldUsbModeValue) || (newUsbConnected != oldUsbConnected);

                Utils.logi(TAG, " usbConfigured?" + usbConfigured + ", newUsbConnected="
                        + newUsbConnected + ", oldUsbConnected=" + oldUsbConnected
                        + ", newUsbMode=" + newUsbMode + ", oldUSBModeValue="
                        + oldUsbModeValue + ", needNotifyModemLog=" + needNotifyModemLog);
                if (needNotifyModemLog) {
                    mSharedPreferences.edit().putInt(Utils.KEY_USB_MODE_VALUE, newUsbMode)
                            .putBoolean(Utils.KEY_USB_CONNECTED_VALUE, newUsbConnected).apply();
                    Utils.logv(TAG,
                            "Modem log is running in USB mode, need to send down switch command.");
                    notifyUSBModeChange();
                } else {
                    Utils.logv(TAG,
                            "Modem log is not running in USB mode or USB status not change, "
                                    + "do not need to send down switch command. newUsbMode="
                                    + newUsbMode + ", usbConnected=" + newUsbConnected);
                }
            }
        }
    };

    private IntentFilter mSDStatusIntentFilter = null;
    private IntentFilter mPhoneStorageIntentFilter = null;
    private BroadcastReceiver mStorageStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String currentLogPathType = Utils.getLogPathType();
            Utils.logi(TAG, "Storage status changed, action=" + action + ", current logPathType="
                    + currentLogPathType);

            if (!Utils.isBootCompleteDone()) {
                Utils.logd(TAG, "MTKLoggerservice is not inited just return!");
                return;
            }
            if (Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(currentLogPathType)
                    || Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(currentLogPathType)) {
                Uri data = intent.getData();
                String affectedPath = null;
                if (data != null) {
                    affectedPath = data.getPath();
                }

                Utils.logd(TAG, "AffectedPath=" + affectedPath);
                currentStorageStatusChange(action, affectedPath);

            } else {
                if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                    Utils.logw(TAG, "Phone storage is low now. What should I do? ");
                    // Stop all running log now because log storage is
                    // unavailable
                    changeLogRunningStatus(false, Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE);
                }
            }
        }
    };

    private void currentStorageStatusChange(final String action, final String affectedPath) {
        new Thread() {
            @Override
            public void run() {
                if (affectedPath == null) {
                    Utils.logi(TAG, "affectedPath, ignore.");
                    return;
                }
                if (!isAffectCurrentLogType(affectedPath)) {
                    Utils.logi(TAG, "isAffectCurrentLogType = false, ignore.");
                    return;
                }
                if (Intent.ACTION_MEDIA_BAD_REMOVAL.equals(action)
                        || Intent.ACTION_MEDIA_EJECT.equals(action)
                        || Intent.ACTION_MEDIA_REMOVED.equals(action)
                        || Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                    long currentTime = System.currentTimeMillis();
                    long intervalTime = currentTime - mLastSDStatusChangedTime;
                    Utils.logi(TAG, "The SD card status changed time is currentTime = "
                            + currentTime + ", mLastSDStatusChangedTime = "
                            + mLastSDStatusChangedTime + ", intervalTime = " + intervalTime);
                    mLastSDStatusChangedTime = currentTime;
                    if (intervalTime >= 0 && intervalTime <= SD_STATUS_CHANGE_CHECK_TIME) {
                        Utils.logw(TAG, "The SD card status changed time is < "
                                + SD_STATUS_CHANGE_CHECK_TIME + ". Ignore this changed!");
                        return;
                    }
                    // Stop all running log now because log storage is
                    // unavailable
                    changeLogRunningStatus(false, Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE);
                } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                    mServiceHandler.removeMessages(Utils.MSG_SD_TIMEOUT);
                    String waitSDStatuStr =
                            mSharedPreferences.getString(Utils.KEY_WAITING_SD_READY_REASON, "");
                    mSharedPreferences.edit().remove(Utils.KEY_WAITING_SD_READY_REASON).apply();
                    Utils.logv(TAG, "Got storage mounted event, "
                            + ", waitSDStatuStr=" + waitSDStatuStr);
                    // When restore after killed by system, isWaitingSDReady
                    // will be false
                    if (!TextUtils.isEmpty(waitSDStatuStr)) {
                        Utils.logd(TAG, "Got storage mounted event, cached waitSDStatuStr="
                                + waitSDStatuStr);
                        changeLogRunningStatus(true, waitSDStatuStr);
                    } else { // Storage is available again, try to restart
                             // former stopped log
                        changeLogRunningStatus(true, Utils.SERVICE_STARTUP_TYPE_STORAGE_RECOVERY);
                    }
                } else {
                    Utils.loge(TAG, "Unsupported broadcast action for SD card. action=" + action);
                }
            }
        }.start();
    }

    private boolean isAffectCurrentLogType(String affectLogPath) {
        String internalLogPath = Utils.getInternalSdPath(MTKLoggerService.this);
        String externalLogPath = Utils.getExternalSdPath(MTKLoggerService.this);
        String currentLogPath = Utils.getCurrentLogPath(MTKLoggerService.this);

        Utils.logd(TAG, "affectLogPath:" + affectLogPath +
                ", internalLogPath :" + internalLogPath +
                ", externalLogPath :" + externalLogPath +
                ", currentLogPath :" + currentLogPath);

        String affectLogPathType = "";
        if (internalLogPath != null &&
                affectLogPath.startsWith(internalLogPath)) {
            affectLogPathType = Utils.LOG_PATH_TYPE_INTERNAL_SD;
        }
        if ((externalLogPath != null &&
                affectLogPath.startsWith(externalLogPath))) {
            affectLogPathType = Utils.LOG_PATH_TYPE_EXTERNAL_SD;
        }
        boolean isUnmountEvent = affectLogPathType.isEmpty();
        String currentLogPathType = Utils.getLogPathType();
        Utils.logd(TAG, "affectLogPath:" + affectLogPath +
                ", affectLogPathType :" + affectLogPathType +
                ", isUnmountEvent :" + isUnmountEvent +
                ", currentLogPathType :" + currentLogPathType);
        if (!isUnmountEvent) {
            return affectLogPathType.equals(currentLogPathType);
        } else {
            // If unmount event && current log path is null, return true.
            if (currentLogPath == null || currentLogPath.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Broadcastreceiver for ACTION_SHUTDOWN
    private BroadcastReceiver mShutdonwReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String action = arg1.getAction();
            Utils.logi(TAG, " mShutdonwReceiver intent action: " + action
                    + ", mIsAlreadySendShutDown ?" + Utils.getAlreadySendShutDown());
            if (Intent.ACTION_SHUTDOWN.equals(action)) {
                Utils.logd(TAG, "Get a Normal SHUTDOWN event!");

                if (Utils.getAlreadySendShutDown()) {
                    Utils.logd(TAG, "Already send stop to network for normal shutdown,return!");
                    return;
                }
                Utils.setAlreadySendShutDown(true);
                if (!Utils.isDeviceOwner()) {
                    // Do not need stop network log for not device owner.
                    return;
                }
                LogInstance logInstance = getLogInstance(Utils.LOG_TYPE_NETWORK);
                LogHandler handler = logInstance.mHandler;
                if (handler != null) {
                    handler.obtainMessage(LogInstance.MSG_STOP,
                            Intent.ACTION_SHUTDOWN).sendToTarget();
                }
            }
        }
    };

    /**
     * Broadcast receiver for monitor Time settings changed.
     */
    private BroadcastReceiver mTimeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Utils.logd(TAG, "TimeChangedReceiver, action = " + action);
        }
    };

    /**
     * Broadcast receiver for monitor user switch event Note: this event can not
     * be received by static receiver.
     */
    private BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int newUserId = intent.getIntExtra(Utils.EXTRA_NEW_USER_ID, -1);
            Utils.loge(TAG, "Monitor a user switch event. New user id = " + newUserId);
            if (newUserId == Utils.USER_ID) {
                Utils.logi(TAG, "Switch current user to fore ground, sync info with native now");
                initLogStatus("");
            } else {
                Utils.logi(TAG, "Current user is set to background,"
                        + " just ignore and let me free some time, thank you.");
            }
        }
    };

    /**
     * Start or stop all log at the same time when environment has changed, or
     * at bootup/shutdown time.
     *
     * @param enable
     *            true for enable, false for disable
     * @param reason
     *            Why this method is called, boot up, or nothing stand
     *            for user's operation
     */
    private void changeLogRunningStatus(boolean enable, String reason) {
        Utils.logd(TAG, "-->changeLogRunningStatus(), enable?" + enable + ", reason=[" + reason
                + "]");
        if (mSharedPreferences == null) {
            Utils.loge(TAG, "SharedPreference instance is null");
            return;
        }
        // Which log type will be affected in this operation
        int affectedLog = 0;
        if (enable) {
            // If this method is called because of boot up, try to enable all
            // boot-automatic log instance
            // If just because of storage recovery, restart former interrupted
            // log
            if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(reason)) {
                for (Integer logType : Utils.LOG_TYPE_SET) {
                    if (logType == Utils.LOG_TYPE_MET) {
                        continue;
                    }
                    if (Utils.VALUE_START_AUTOMATIC_ON == mDefaultSharedPreferences.getBoolean(
                            Utils.KEY_START_AUTOMATIC_MAP.get(logType),
                            Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(logType))) {
                        // At boot time, no matter native status, just send down
                        // start command
                        // /**Need to enable log at boot time*/
                        // && Utils.VALUE_STATUS_STOPPED ==
                        // mSharedPreferences.getInt(
                        // Utils.KEY_STATUS_MAP.get(logType),
                        // Utils.VALUE_STATUS_STOPPED)/**not be on yet*/){
                        affectedLog += logType;
                    }
                }
            } else if (Utils.SERVICE_STARTUP_TYPE_STORAGE_RECOVERY.equals(reason)) {
                for (Integer logType : Utils.LOG_TYPE_SET) {
                    if (logType == Utils.LOG_TYPE_MET) {
                        continue;
                    }
                    boolean needRecovery =
                            mSharedPreferences.getBoolean(
                                    Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType),
                                    Utils.DEFAULT_VALUE_NEED_RECOVER_RUNNING);
                    int runningState =
                            mSharedPreferences.getInt(Utils.KEY_STATUS_MAP.get(logType),
                                    Utils.VALUE_STATUS_STOPPED);
                    boolean enableStatus =
                            mDefaultSharedPreferences.getBoolean(
                                    SettingsActivity.KEY_LOG_SWITCH_MAP.get(logType), true);
                    // autostart/stop
                    boolean autostart =
                            mDefaultSharedPreferences.getBoolean(
                                    Utils.KEY_START_AUTOMATIC_MAP.get(logType),
                                    Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(logType));
                    Utils.logd(TAG, "For log[" + logType + "], needRecovery?" + needRecovery
                            + ", runningState=" + runningState + ",enablestatus=" + enableStatus
                            + ", autostart = " + autostart);
                    if (needRecovery || autostart/**
                     * Need to recover log running
                     * status
                     */
                    ) {
                        if (Utils.VALUE_STATUS_STOPPED == runningState/**
                         * not be
                         * on yet
                         */
                        && enableStatus) {
                            affectedLog += logType;
                        }
                        mSharedPreferences.edit()
                                .putBoolean(Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType), false)
                                .apply();
                    }
                }
            }
            Utils.logv(TAG, " affectedLog=" + affectedLog);
            if (affectedLog > 0) {
                startRecording(affectedLog, reason);
            }
        } else { // Try to disable all running log instance
            for (Integer logType : Utils.LOG_TYPE_SET) {
                if (logType == Utils.LOG_TYPE_MET) {
                    continue;
                }
                boolean isRunning = isTypeLogRunning(logType);
                /** Be running right now */
                boolean shouldAutoStarted =
                        (Utils.VALUE_START_AUTOMATIC_ON == mDefaultSharedPreferences.getBoolean(
                                Utils.KEY_START_AUTOMATIC_MAP.get(logType),
                                Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(logType)));

                boolean isStoppedInShortTime = isStoppedInShortTime(logType);
                if (isRunning
                        || isStoppedInShortTime
                        || (shouldAutoStarted && Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT
                                .equals(reason))) {
                    // Should be on at boot time, but SD time out, since native
                    // layer may already running, need to stop
                    // but for USB mode modem log, ignore this timeout
                    if (logType == Utils.LOG_TYPE_MODEM
                            && Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT.equals(reason)) {
                        String currentMDLogMode =
                                mDefaultSharedPreferences.getString(Utils.KEY_MD_MODE_1,
                                        Utils.MODEM_MODE_SD);
                        if (Utils.MODEM_MODE_USB.equals(currentMDLogMode)) {
                            Utils.logd(TAG, "For USB mode modem log, ignore SD timeout event.");
                            continue;
                        }
                    }
                    affectedLog += logType;
                    if ((isRunning || isStoppedInShortTime)
                            && Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE.equals(reason)) {
                        // Storage become unavailable, need to recovery log when
                        // storage is available again
                        // set a flag for this
                        mSharedPreferences.edit()
                                .putBoolean(Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType), true)
                                .apply();
                    }
                }
            }
            Utils.logv(TAG, " affectedLog=" + affectedLog);
            if (affectedLog > 0) {
                stopRecording(affectedLog, reason);
            }
        }
    }

    private boolean isStoppedInShortTime(int logType) {
        boolean rs = false;
        long currentTime = System.currentTimeMillis();
        long stopTime = mSharedPreferences.getLong(Utils.KEY_SELF_STOP_TIME_MAP.get(logType), 0);
        long intervalTime = currentTime - stopTime;
        if (intervalTime >= 0 && intervalTime <= SD_STATUS_CHANGE_CHECK_TIME) {
            rs = true;
        }
        Utils.logi(TAG, "isStoppedInShortTime() logType = " + logType + ". Rs = " + rs);
        return rs;
    }

    /**
     * Check whether storage(SD or internal) is ready for log recording.
     *
     * @return
     */
    private boolean isStorageReady(String currentLogPath) {
        // Utils.logd(TAG, "-->isStorageReady()");
        String currentLogPathType = Utils.getLogPathType();
        if (Utils.LOG_PATH_TYPE_PHONE.equals(currentLogPathType)) {
            Utils.logv(TAG, "For phone internal storage, assume it's already ready");
            return true;
        }
        String status = Utils.getVolumeState(this, currentLogPath);
        if (Environment.MEDIA_MOUNTED.equals(status)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * At running time, log folder may be deleted, so we need to monitor each
     * log related log folder. If the folder is deleted, we should consider
     * whether stop that log manually(For Network Log)
     */
    class LogFolderMonitor extends Thread {
        @Override
        public void run() {
            Utils.logd(TAG, "Begin to monitor log folder status...");
            while (!mLogFolderMonitorThreadStopFlag) {
                String currentLogPath = Utils.getCurrentLogPath(MTKLoggerService.this);
                boolean isStorageReady = isStorageReady(currentLogPath);
                if (isStorageReady) {
                    checkRemainingStorage(currentLogPath);

                    for (Integer logType : Utils.LOG_TYPE_SET) {
                        int currentState =
                                mSharedPreferences.getInt(Utils.KEY_STATUS_MAP.get(logType),
                                        Utils.VALUE_STATUS_STOPPED);
                        if (currentState == Utils.VALUE_STATUS_RUNNING) {
                            LogInstance instance = getLogInstance(logType);
                            if (instance != null) {
                                instance.checkLogFolder();
                            } else {
                                Utils.loge(TAG,
                                        "Fail to get log instance when checking log folder.");
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(Utils.DURATION_CHECK_LOG_FOLDER);
                } catch (InterruptedException e) {
                    Utils.logw(TAG, "Waiting check log folder been interrupted.");
                    continue;
                }
            }

            Utils.logd(TAG, "End monitor log folder status.");
        }
    }

    Notification.Builder mNotificationBuilder;
    /**
     * We will set a storage water level, when not too much storage is
     * remaining, give user a notification to delete old logs.
     */
    private void checkRemainingStorage(String currentLogPath) {
        int remainingSize = Utils.getAvailableStorageSize(currentLogPath);
        // Utils.logv(TAG,
        // "-->checkRemainingStorage(), remainingSize="+remainingSize+
        // ", former storage="+mRemainingStorage);
        if (remainingSize < Utils.RESERVED_STORAGE_SIZE + 2) {
            int currentState =
                    mSharedPreferences.getInt(Utils.KEY_STATUS_MAP.get(Utils.LOG_TYPE_GPS),
                            Utils.VALUE_STATUS_STOPPED);
            if (currentState == Utils.VALUE_STATUS_RUNNING) {
                stopRecording(Utils.LOG_TYPE_GPS, Utils.SERVICE_SHUTDOWN_TYPE_BAD_STORAGE);
                Utils.logd(TAG, "stop GPS log,for SD card is almost full");

            }
            Intent intent = new Intent();
            intent.setAction(Utils.ACTION_REMAINING_STORAGE_LOW);
            intent.putExtra(Utils.EXTRA_REMAINING_STORAGE, remainingSize);
            Utils.sendBroadCast(intent);
        }
        if (remainingSize < Utils.DEFAULT_STORAGE_WATER_LEVEL
                && (mRemainingStorage == 0
                || mRemainingStorage >= Utils.DEFAULT_STORAGE_WATER_LEVEL)) {
            Utils.logi(TAG, "Remaining log storage drop below water level,"
                    + " give a notification now");
            if (mNM == null) {
                mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            }
            Utils.logd(TAG, "Log storage drop down below water level, give out a notification");

            Intent backIntent = new Intent();
            backIntent.setComponent(new ComponentName("com.mediatek.mtklogger",
                    "com.mediatek.mtklogger.MainActivity"));
            backIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, backIntent, 0);
            if (mNotificationBuilder == null) {
                mNotificationBuilder = new Notification.Builder(MTKLoggerService.this);
            }
            mNotificationBuilder
                .setContentText(getText(R.string.notification_out_of_storage_summary))
                .setTicker(getText(R.string.notification_nearly_out_of_storage))
                .setContentTitle(getText(R.string.notification_nearly_out_of_storage))
                .setSmallIcon(R.drawable.ic_notification_low_storage)
                .setContentIntent(pendingIntent);
            if (mDefaultSharedPreferences.getBoolean(Utils.KEY_PREFERENCE_NOTIFICATION_ENABLED,
                    true)) {
                mNM.notify(R.drawable.ic_notification_low_storage, mNotificationBuilder.build());
            } else {
                Utils.logw(TAG, "Notification is disabled, does not show any notification.");
            }
            Intent intent = new Intent();
            intent.setAction(Utils.ACTION_REMAINING_STORAGE_LOW);
            intent.putExtra(Utils.EXTRA_REMAINING_STORAGE, remainingSize);
            Utils.sendBroadCast(intent);

            // Intent intent = new Intent();
            // intent.setAction(Utils.ACTION_REMAINING_STORAGE_LOW);
            // intent.putExtra(Utils.EXTRA_REMAINING_STORAGE, remainingSize);
            // sendBroadcast(intent);
            if (Utils.isReleaseToCustomer1()) {
                mUIHandler.sendEmptyMessage(MSG_SHOW_LOW_MEMORY_DIALOG);
            }
        } else if (mRemainingStorage > 0 && mRemainingStorage < Utils.DEFAULT_STORAGE_WATER_LEVEL
                && remainingSize >= Utils.DEFAULT_STORAGE_WATER_LEVEL) {
            if (mNM == null) {
                mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            }
            Utils.logd(TAG, "Log storage resume upto water level, clear former notification");
            if (mDefaultSharedPreferences.getBoolean(Utils.KEY_PREFERENCE_NOTIFICATION_ENABLED,
                    true)) {
                mNM.cancel(R.drawable.ic_notification_low_storage);
            }
        }

        mRemainingStorage = remainingSize;
    }

    private Handler mUIHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SHOW_LOW_MEMORY_DIALOG:
                showLowStorageDialog();
                break;
            default:
                break;
            }
        };
    };
    private void showLowStorageDialog() {
        Utils.logd(TAG, "showLowStorageDialog");
                Context sContext = MTKLoggerService.this;
                String message =
                        sContext.getString(R.string.low_storage_warning_dialog_msg,
                                        Utils.DEFAULT_STORAGE_WATER_LEVEL);
                Builder builder =
                        new AlertDialog.Builder(sContext)
                                .setTitle(sContext.getText(
                                        R.string.taglog_title_no_space).toString())
                                .setMessage(message)
                                .setPositiveButton(android.R.string.yes, new OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.setCancelable(false);
                dialog.setInverseBackgroundForced(true);
                dialog.show();
    }
    private void dealWithAdbCommand(int logTypeCluster, String command) {
        Utils.logi(TAG, "-->dealWithAdbCommand(), logTypeCluster=" + logTypeCluster + ", command="
                + command);
        if (Utils.ADB_COMMAND_START.equals(command)) { // start log command
            startRecording(logTypeCluster, Utils.SERVICE_STARTUP_TYPE_ADB);
        } else if (Utils.ADB_COMMAND_STOP.equals(command)) {
            stopRecording(logTypeCluster, Utils.SERVICE_STARTUP_TYPE_ADB);
        } else if (Utils.ADB_COMMAND_RESTART.equals(command)) {
            restartRecording(logTypeCluster, Utils.SERVICE_STARTUP_TYPE_ADB);
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_GPS_LOCATION_ENABLE)) {
            String newValue = command.substring(command.length() - 1);
            mDefaultSharedPreferences.edit()
                                     .putBoolean(Utils.KEY_MD_SAVE_LOCATIN_IN_LOG,
                                       newValue.equals("1")).apply();
            setSaveLocationInModemLog(newValue.equals("1"));
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_LOG_SAVE_PATH_PREFIX)) {
            // Set GPS Log save path.
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("1") || newValue.equals("2")) {
                mDefaultSharedPreferences.edit()
                    .putString(GPSLogSettings.KEY_SAVE_LOG_PATH, newValue).apply();
            } else {
                Utils.logw(TAG, "Unsupported GPS Log save path value!");
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_LOG_AUTO_START_PREFIX)) {
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                setLogAutoStart(logTypeCluster, newValue.equals("1"));
            } else {
                Utils.logw(TAG, "Unsupported auto start value");
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_LOG_UI_ENABLED_PREFIX)) {
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                for (Integer logType : Utils.LOG_TYPE_SET) {
                    if ((logType & logTypeCluster) == 0) {
                        continue;
                    }
                    if (logType == Utils.LOG_TYPE_MET) {
                        continue;
                    }
                    mDefaultSharedPreferences
                    .edit()
                    .putBoolean(SettingsActivity.KEY_LOG_SWITCH_MAP.get(logType),
                            newValue.equals("1")).apply();
                }
            } else {
                Utils.logw(TAG, "Unsupported auto start value");
            }
        } else if (command != null && command.startsWith(Utils.ADB_COMMAND_SET_SUBLOG_PREFIX)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name set_sublog_1_2_..._6_0 --ei cmd_target 1
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                String subTypeStr =
                        command.substring(Utils.ADB_COMMAND_SET_SUBLOG_PREFIX.length(),
                                command.length() - 2);
                String[] subTypes = subTypeStr.split("_");
                if (subTypes == null || subTypes.length == 0) {
                    Utils.logw(TAG, "Unsupported set sublog value");
                    return;
                }
                for (String subType : subTypes) {
                    int subTypeInt = 1;
                    try {
                        subTypeInt = Integer.parseInt(subType);
                    } catch (NumberFormatException e) {
                        Utils.loge(TAG, "Invalid sub log type parameter: " + subType);
                        continue;
                    }
                    for (Integer logType : Utils.LOG_TYPE_SET) {
                        if ((logType & logTypeCluster) == 0) {
                            continue;
                        }
                        if (logType == Utils.LOG_TYPE_MET) {
                            continue;
                        }
                        setSubLogEnable(logType, subTypeInt, newValue.equals("1"));
                    }
                }
            } else {
                Utils.logw(TAG, "Unsupported set sublog value");
            }
        } else if (command != null && command.startsWith(Utils.ADB_COMMAND_SWITCH_LOGPATH)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e switch_logpath -e cmd_target external_sd
            String targetLogpath = command.substring(Utils.ADB_COMMAND_SWITCH_LOGPATH.length() + 1);

            String newValue = Utils.LOG_PATH_TYPE_INTERNAL_SD;
            String selfInternalPath = "";
            String selfExternalPath = "";
            if (Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(targetLogpath)) {
                newValue = Utils.LOG_PATH_TYPE_EXTERNAL_SD;
            } else if (Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(targetLogpath)) {
                newValue = Utils.LOG_PATH_TYPE_INTERNAL_SD;
            } else {
                if (!new File(targetLogpath).exists()) {
                    Utils.logw(TAG, "self log path is not exist : " + targetLogpath);
                    boolean creatResult = new File(targetLogpath).mkdirs();
                    if (!creatResult) {
                        Utils.logw(TAG, "creatSuccess ? " + creatResult);
                        return;
                    }
                }
                try {
                    String newPath = new File(targetLogpath).getCanonicalPath();
                    Utils.logd(TAG, "self new Path :" + newPath);
                    String internalSDPath = Utils.getDefalueInternalSdPath(this);
                    String externalSDPath = Utils.getDefaultExternalSdPath(this);

                    boolean isinternalType = newPath.startsWith(internalSDPath);
                    if (isinternalType) {
                        newValue = Utils.LOG_PATH_TYPE_INTERNAL_SD;
                        selfInternalPath = targetLogpath;
                    } else if (newPath.startsWith(externalSDPath)) {
                        newValue = Utils.LOG_PATH_TYPE_EXTERNAL_SD;
                        selfExternalPath = targetLogpath;
                    }
                } catch (IOException e) {
                    Utils.logw(TAG, "set net log path error");
                }
            }

            Utils.setLogPathType(newValue);
            Utils.setModemLogPathType(newValue);
            if (!selfInternalPath.isEmpty()) {
                mSharedPreferences.edit()
                .putString(Utils.SELF_DEF_INTERNAL_LOG_PATH, selfInternalPath)
                .apply();
                Utils.logd(TAG, " set internal self define: " + selfInternalPath );
            } else if (!selfExternalPath.isEmpty()) {
                mSharedPreferences.edit()
                .putString(Utils.SELF_DEF_EXTERNAL_LOG_PATH, selfExternalPath)
                .apply();
                Utils.logd(TAG, " set external self define: " + selfExternalPath );
            }
        } else if (command != null && command.startsWith(Utils.ADB_COMMAND_SET_LOG_SIZE_PREFIX)) {
            String newValueStr = command.substring(Utils.ADB_COMMAND_SET_LOG_SIZE_PREFIX.length());

            int newLogSize = 0;
            try {
                newLogSize = Integer.parseInt(newValueStr);
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "Invalid set log size parameter: " + newValueStr);
                return;
            }
            if (newLogSize <= 0) {
                Utils.loge(TAG, "Given log size should bigger than zero, but got " + newValueStr);
                return;
            }

            for (Integer logType : Utils.LOG_TYPE_SET) {
                if ((logType & logTypeCluster) == 0) {
                    continue;
                }
                if (logType == Utils.LOG_TYPE_MET) {
                    continue;
                }
                mDefaultSharedPreferences.edit()
                        .putString(Utils.KEY_LOG_SIZE_MAP.get(logType), newValueStr).apply();
                setEachLogSize(logType, newLogSize);
            }

        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_TOTAL_LOG_SIZE_PREFIX)) {
            String newValueStr =
                    command.substring(Utils.ADB_COMMAND_SET_TOTAL_LOG_SIZE_PREFIX.length());

            int newLogSize = 0;
            try {
                newLogSize = Integer.parseInt(newValueStr);
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "Invalid set total log size parameter: " + newValueStr);
                return;
            }
            if (newLogSize <= 0) {
                Utils.loge(TAG, "Given total log size should bigger than zero, but got "
                        + newValueStr);
                return;
            }

            for (Integer logType : Utils.LOG_TYPE_SET) {
                if ((logType & logTypeCluster) == 0) {
                    continue;
                }
                if (logType == Utils.LOG_TYPE_MET) {
                    continue;
                }
                mDefaultSharedPreferences.edit()
                        .putString(Utils.KEY_TOTAL_LOG_SIZE_MAP.get(logType), newValueStr).apply();
                setEachTotalLogSize(logType, newLogSize);
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_NOTIFICATION_ENABLE)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name show_notification_1 --ei cmd_target -1
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(Utils.KEY_PREFERENCE_NOTIFICATION_ENABLED, newValue.equals("1"))
                        .apply();
            } else {
                Utils.logw(TAG, "Unsupported set NOTIFICATION value");
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_ENVIRONMENT_CHECK_PREFIX)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name environment_check_value --ei cmd_target 4
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                mDefaultSharedPreferences.edit()
                        .putBoolean("networklog_ping_flag", newValue.equals("1")).apply();
            } else {
                Utils.logw(TAG, "Unsupported set ENVIRONMENT CHECK value!");
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_PACKAGE_LIMITATION_PREFIX)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name package_limitation_value --ei cmd_target 4
            String newValue = command.substring(command.length() - 1);
            if (newValue.equals("0") || newValue.equals("1")) {
                mDefaultSharedPreferences.edit()
                .putBoolean(Utils.KEY_NT_LIMIT_PACKAGE_ENABLER, newValue.equals("1")).apply();
            } else {
                Utils.logw(TAG, "Unsupported set PACKAGE LIMITATION value!");
            }
        } else if (command != null
                && command.startsWith(Utils.ADB_COMMAND_SET_NETWORK_PACKAGE_SIZE_PREFIX)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            //  -e cmd_name set_network_package_size_128 --ei cmd_target 4
            String newValueStr = command.substring(
                    Utils.ADB_COMMAND_SET_NETWORK_PACKAGE_SIZE_PREFIX.length());
            int newLogSize = 0;
            try {
                newLogSize = Integer.parseInt(newValueStr);
            } catch (NumberFormatException e) {
                Utils.loge(TAG, "Invalid set network package size parameter: " + newValueStr);
                return;
            }
            if (newLogSize <= 0) {
                Utils.loge(TAG, "Given network package size should bigger than zero, but got "
                        + newValueStr);
                return;
            }
            mDefaultSharedPreferences.edit().putBoolean(
                    Utils.KEY_NT_LIMIT_PACKAGE_ENABLER, true).apply();
            mDefaultSharedPreferences.edit().putString(
                    Utils.KEY_NT_LIMIT_PACKAGE_SIZE, newValueStr).apply();
        } else if (command != null
                && command.equalsIgnoreCase(Utils.ADB_COMMAND_GET_MTKLOG_PATH_NAME)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            //  -e cmd_name get_mtklog_path
            String mtklogPath = Utils.getCurrentLogPath(this) + Utils.MTKLOG_PATH;
            Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
            intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_GET_MTKLOG_PATH_NAME);
            intent.putExtra(Utils.EXTRA_RESULT_VALUE, mtklogPath);
            Utils.sendBroadCast(intent);
            Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                    + " is sent out with extra :"
                    + Utils.EXTRA_RESULT_NAME + " = " + Utils.ADB_COMMAND_GET_MTKLOG_PATH_NAME
                    + ", " + Utils.EXTRA_RESULT_VALUE + " = " + mtklogPath);
        } else if (command != null
                && command.equalsIgnoreCase(Utils.ADB_COMMAND_GET_LOG_RECYCLE_SIZE_NAME)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name get_log_recycle_size --ei cmd_target typeLog
            String logRecycleSizeStr = "0";
            if (logTypeCluster == Utils.LOG_TYPE_MOBILE) {
                logRecycleSizeStr = mDefaultSharedPreferences.getString(
                        Utils.KEY_TOTAL_LOG_SIZE_MAP.get(logTypeCluster),
                        String.valueOf(Utils.DEFAULT_CONFIG_LOG_SIZE_MAP.get(logTypeCluster) * 2));
            } else {
                logRecycleSizeStr = mDefaultSharedPreferences.getString(
                        Utils.KEY_LOG_SIZE_MAP.get(logTypeCluster),
                        String.valueOf(Utils.DEFAULT_CONFIG_LOG_SIZE_MAP.get(logTypeCluster)));
            }
            Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
            intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_GET_LOG_RECYCLE_SIZE_NAME);
            intent.putExtra(Utils.EXTRA_RESULT_VALUE, logRecycleSizeStr);
            Utils.sendBroadCast(intent);
            Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                    + " is sent out with extra :"
                    + Utils.EXTRA_RESULT_NAME + " = " + Utils.ADB_COMMAND_GET_LOG_RECYCLE_SIZE_NAME
                    + ", " + Utils.EXTRA_RESULT_VALUE + " = " + logRecycleSizeStr);
        } else if (command != null
                && command.equalsIgnoreCase(Utils.ADB_COMMAND_GET_TAGLOG_STATUS_NAME)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name get_taglog_status
            boolean isTaglogEnable = mSharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false);
            Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
            intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_GET_TAGLOG_STATUS_NAME);
            intent.putExtra(Utils.EXTRA_RESULT_VALUE, isTaglogEnable);
            Utils.sendBroadCast(intent);
            Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                    + " is sent out with extra :"
                    + Utils.EXTRA_RESULT_NAME + " = " + Utils.ADB_COMMAND_GET_TAGLOG_STATUS_NAME
                    + ", " + Utils.EXTRA_RESULT_VALUE + " = " + isTaglogEnable);
        } else if (command != null
                && command.equalsIgnoreCase(Utils.ADB_COMMAND_GET_LOG_AUTO_STATUS_NAME)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            // -e cmd_name get_log_auto_status --ei cmd_target typeLog
            boolean isLogAutoStart = mDefaultSharedPreferences.getBoolean(
                    Utils.KEY_START_AUTOMATIC_MAP.get(logTypeCluster), false);
            Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
            intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_GET_LOG_AUTO_STATUS_NAME);
            intent.putExtra(Utils.EXTRA_RESULT_VALUE, isLogAutoStart);
            Utils.sendBroadCast(intent);
            Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                    + " is sent out with extra :"
                    + Utils.EXTRA_RESULT_NAME + " = " + Utils.ADB_COMMAND_GET_LOG_AUTO_STATUS_NAME
                    + ", " + Utils.EXTRA_RESULT_VALUE + " = " + isLogAutoStart);
        } else if (command != null
                && command.equalsIgnoreCase(Utils.ADB_COMMAND_CLEAR_ALL_LOGS_NAME)) {
            // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
            //  -e cmd_name clear_all_logs
            clearAllLogs();
        } else {
            // Other case-special command, let each log instance to handle it
            for (int logType : Utils.LOG_TYPE_SET) {
                if ((logType & logTypeCluster) == 0) {
                    continue;
                }
                if (logType == Utils.LOG_TYPE_MET) {
                    continue;
                }
                LogInstance logInstance = getLogInstance(logType);
                if (logInstance != null) {
                    Utils.logd(TAG, "Send adb command [" + command + "] to log " + logType);
                    LogHandler handler = logInstance.mHandler;
                    if (handler != null) {
                        handler.obtainMessage(LogInstance.MSG_ADB_CMD, command).sendToTarget();
                    } else {
                        Utils.loge(TAG, "When dealWithAdbCommand(), "
                                + "fail to get log instance handler of log [" + logType + "]");
                    }
                } else {
                    Utils.loge(TAG, "Fail to get log instance(" + logType
                            + ") when dealing with adb command");
                }
            }
        }
    }

    private void clearAllLogs() {
        new Thread() {
            @Override
            public void run() {
                Utils.logd(TAG, "-->clearAllLogs() Start");
                String logStoragePath = Utils.getCurrentLogPath(MTKLoggerService.this);
                File logFile = new File(logStoragePath + Utils.MTKLOG_PATH);
                if (!logFile.exists()) {
                    mServiceHandler.obtainMessage(
                            Utils.MSG_CLEAR_ALL_LOGS_DONE, 1, -1).sendToTarget();
                    return;
                }
                if (!logFile.isDirectory()) {
                    mServiceHandler.obtainMessage(
                            Utils.MSG_CLEAR_ALL_LOGS_DONE, 1, -1).sendToTarget();
                    return;
                }
                File[] files = logFile.listFiles();
                if (files == null) {
                    mServiceHandler.obtainMessage(
                            Utils.MSG_CLEAR_ALL_LOGS_DONE, 1, -1).sendToTarget();
                    return;
                }

                Set<String> filters = getFileFilters();
                for (File file : files) {
                    // Do check for folder sdcard/mtklog/
                    if (isNeedClear(file, Utils.CLEAR_LOG_FILES_LIST)
                            && !isFileFilter(file, filters)) {
                        Utils.logd(TAG, "clearAllLogs() file = " + file.getAbsolutePath());
                        File[] subFiles = file.listFiles();
                        if (subFiles != null) {
                            for (File subFile : subFiles) {
                                // Do filter for folder sdcard/mtklog/subfiles
                                if (!isFileFilter(subFile, filters)) {
                                    Utils.deleteFile(subFile);
                                }
                            }
                        }
                    }
                }
                mServiceHandler.obtainMessage(
                        Utils.MSG_CLEAR_ALL_LOGS_DONE, 1, -1).sendToTarget();
            }
        }.start();
    }

    private Set<String> getFileFilters() {
        Utils.logd(TAG, "-->getFileFilters() Start");
        Set<String> fileFilters = new HashSet<String>();
        fileFilters.addAll(Utils.CLEAR_LOG_PRE_FIX_FILTERS);
        for (Integer logType : Utils.LOG_TYPE_SET) {
            LogInstance logInstance = getLogInstance(logType);
            if (logInstance != null) {
                String runningLogPath = logInstance.getRunningLogPath(logType);
                if (runningLogPath != null && runningLogPath.length() > 0) {
                    String[] logPaths = runningLogPath.split(";");
                    if (logPaths != null) {
                        for (String logPath : logPaths) {
                            File logFile = new File(logPath);
                            if (logFile.exists()) {
                                Utils.logd(TAG, "fileFilters.add = " + logFile.getName());
                                fileFilters.add(logFile.getName());
                            }
                        }
                    }
                }
            }
        }
        Utils.logd(TAG, "<--getFileFilters() fileFilters.size() = " + fileFilters.size());
        return fileFilters;
    }

    private boolean isFileFilter(File file, Set<String> filters) {
        if (!file.exists()) {
            return true;
        }
        boolean isFiltered = false;
        if (!isFiltered) {
            for (String filter : filters) {
                if (file.getName().indexOf(filter) >= 0) {
                    isFiltered = true;
                    Utils.logd(TAG, "File: " + file.getAbsolutePath() + " is filtered!");
                    break;
                }
            }
        }
        return isFiltered;
    }

    private boolean isNeedClear(File file, Set<String> needClearList) {
        if (!file.exists()) {
            return false;
        }
        boolean isNeedCleared = false;
        for (String clearLogFile : needClearList ) {
            if (file.getName().indexOf(clearLogFile) >= 0) {
                isNeedCleared = true;
                Utils.logd(TAG, "File: " + file.getAbsolutePath() + " is need cleared!");
                break;
            }
        }
        return isNeedCleared;
    }

    /**
     * For start/stop command, user can operate more than one log with just one
     * API call. Like a parameter 7 standing for
     * networkLog(1)+mobileLog(2)+modemLog(4) will take effect on all of these
     * three log instances.
     *
     * This field will store the log instance types affected by current
     * API(start/stop) call. After all logs finish their on/off operations, this
     * field will be passed to other components to tell them which log's state
     * have been modified.
     */
//    private int mCurrentAffectedLogType = 0;

    /**
     * Since each log's operation may take a little while, this field will
     * remember all unfinished commands. that is waiting for response from
     * native layer.
     *
     * After receiving response from native or when timeout signal come up, the
     * related log flag will be reset to 0.
     *
     * This field is the sum of log types which will be operated with. 0 mean
     * all commands have been finished and service is in idle state, other
     * modules can call start/stop API again, else, meaning this service is
     * still busy, any other API request will be ignored.
     */
//    private int mCachedStartStopCmd = 0;

    /**
     * This field is used to store response from native layer. It is a cluster
     * of each log type, each bit stand for one log's state, 0 mean off, and 1
     * mean on.
     *
     * e.g. 6 mean mobileLog(2) and modemLog(4) are both running, but
     * networkLog(1) is stopped
     */
//    private int mCurrentRunningStatus = 0;

    // Fail reason for all log instance, if exist
//    private String mFailReasonStr = "";

    /**
     * After send a command to native layer, this handler will monitor response
     * from native, or timeout signal. It will also monitor each log instance's
     * self-driven change from native
     */
    class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            Utils.logi(TAG, " mNativeStateHandler receive message," + " what=" + what
                    + ", arg1=" + msg.arg1 + ", arg2=" + msg.arg2);

            if (what == Utils.MSG_SD_TIMEOUT) {
                String waitSDStatuStr =
                        mSharedPreferences.getString(Utils.KEY_WAITING_SD_READY_REASON, "");
                // We may miss storage ready broadcast, so re-check it
                // at the
                // last time
                boolean isSdReady = isStorageReady(
                        Utils.getCurrentLogPath(MTKLoggerService.this));
                Utils.logw(TAG, "At boot up time, waiting SD card time out,"
                        + " wait reason=" + waitSDStatuStr + ", isSdReady=" + isSdReady);
                mSharedPreferences.edit().remove(Utils.KEY_WAITING_SD_READY_REASON).apply();
                if (isSdReady && !TextUtils.isEmpty(waitSDStatuStr)) {
                    Utils.logw(TAG, "Wait storage ready timeout,"
                            + " but storage is already OK.");
                    changeLogRunningStatus(true, waitSDStatuStr);
                } else {
                    Utils.logw(TAG, "Stop log instances since waiting storage timeout");
                    changeLogRunningStatus(false, Utils.SERVICE_SHUTDOWN_TYPE_SD_TIMEOUT);
                }
                return;
            }
            if (what == Utils.MSG_LOG_STATE_CHANGED) {
                // start/stop command finish or self state change event
                handleLogStateChangeMsg(mServiceHandler, msg);
            } else if (what == Utils.MSG_RUNNING_STAGE_CHANGE) {
                int stageEvent = msg.arg1;
                handleGlobalRunningStageChange(stageEvent);
            } else if (what == Utils.MSG_START_LOGS_DONE) {
                int logType = msg.arg1;
                mStartLogCluster = mStartLogCluster ^ logType;
            } else if (what == Utils.MSG_STOP_LOGS_DONE) {
                int logType = msg.arg1;
                mStopLogCluster = mStopLogCluster ^ logType;
            } else if (what == Utils.MSG_RESTART_DONE) {
                int logType = msg.arg1;
                mRestartLogCluster = mRestartLogCluster ^ logType;
            } else if (what == Utils.MSG_CLEAR_ALL_LOGS_DONE) {
                int result = msg.arg1;
                Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
                intent.putExtra(Utils.EXTRA_RESULT_NAME,
                        Utils.ADB_COMMAND_CLEAR_ALL_LOGS_NAME);
                intent.putExtra(Utils.EXTRA_RESULT_VALUE, result);
                Utils.sendBroadCast(intent);
                Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                        + " is sent out with extra :"
                        + Utils.EXTRA_RESULT_NAME + " = "
                        + Utils.ADB_COMMAND_CLEAR_ALL_LOGS_NAME
                        + ", " + Utils.EXTRA_RESULT_VALUE + " = " + result);
            } else if (what == Utils.MSG_COPY_DB_TO_MTKLOG) {
                if (Utils.isTaglogEnable()) {
                    Utils.logd(TAG,
                            "The taglog is enable and will do taglog," +
                            " do not need COPY_DB_TO_MTKLOG");
                    return;
                }
                final String dbFolderPath = (String) msg.obj;
                if (dbFolderPath == null || !new File(dbFolderPath).exists()) {
                    Utils.logw(TAG, "The dbFolderPath get from broadcast is null or not exists!");
                    return;
                }
                String mtklogPath = Utils.getCurrentLogPath(MTKLoggerService.this) + File.separator
                        + "mtklog";
                if (!new File(mtklogPath).exists()) {
                    Utils.logi(TAG, "/mtklog floder not exists, no need to do copy" );
                    return;
                } else {
                    Utils.loge(TAG, "/mtklog floder is exists, do copy" );
                }
                Utils.logi(TAG, "Copy db file from " + dbFolderPath + " to " + mtklogPath);
                try {
                    String dbFolderCanonicalPath = new File(dbFolderPath)
                    .getCanonicalPath();
                    String mtklogCanonicalpath = new File(mtklogPath).getCanonicalPath();
                    Utils.logi(TAG, "After getCanonicalPath() : write Log to tag folder "
                            + dbFolderCanonicalPath + "--> " + mtklogCanonicalpath);
                    if (dbFolderCanonicalPath.startsWith(mtklogCanonicalpath)) {
                        Utils.logd(TAG,
                                "1) The db file has been in mtklog," +
                                " do not need copy anymore!");
                        return;
                    }
                    String emulatedPath = "/storage/emulated/";
                    // /storage/emulated/legacy/mtklog ==
                    // /storage/emulated/0/mtklog
                    if (dbFolderCanonicalPath.startsWith(emulatedPath)
                            && mtklogCanonicalpath.startsWith(emulatedPath)) {
                        Utils.logd(TAG, "Path is all startsWith /storage/emulated/ !");
                        String[] dbFolderStrs = dbFolderCanonicalPath.split("/");
                        String[] mtklogFolderStrs = mtklogCanonicalpath.split("/");
                        if (dbFolderStrs.length >= 4 && mtklogFolderStrs.length >= 4) {
                            if ((dbFolderStrs[3].equalsIgnoreCase("legacy")
                                    && isNumeric(mtklogFolderStrs[3]))
                                    || (mtklogFolderStrs[3].equalsIgnoreCase("legacy")
                                            && isNumeric(dbFolderStrs[3]))) {
                                Utils.logd(TAG,
                                        "2) The db file has been in mtklog," +
                                        " do not need copy anymore!");
                                return;
                            }
                        }
                    }
                    String aeeExpBackupPath = mtklogCanonicalpath + File.separator
                            + "aee_exp_backup";
                    File aeeExpBackupFile = new File(aeeExpBackupPath);
                    if (!aeeExpBackupFile.exists()) {
                        aeeExpBackupFile.mkdirs();
                    }
                    Utils.doCopy(
                            dbFolderCanonicalPath,
                            aeeExpBackupPath + File.separator
                            + new File(dbFolderCanonicalPath).getName());
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                Utils.loge(TAG, "Unknown message");
                return;
            }
        }
    }

    private boolean isNumeric(String str) {
        Utils.logd(TAG, "isNumeric(), str = " + str);
        for (int i = str.length(); --i >= 0; ) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private Object mLock = new Object();

    /**
     * @param handler
     * @param msg
     */
    private void handleLogStateChangeMsg(Handler handler, Message msg) {
        int logType = msg.arg1;
        int logRunningStatus = msg.arg2;
        Object reason = msg.obj;
        Utils.logd(TAG, "-->handleLogStateChangeMsg(), logType = " + logType
                + ", logRunningStatus = " + logRunningStatus
                + ", reason = " + reason);
        if (logRunningStatus != 1) {
            mSharedPreferences.edit().putLong(Utils.KEY_SELF_STOP_TIME_MAP.get(logType),
                    System.currentTimeMillis()).apply();
        } else {
            if (mSharedPreferences.getLong(Utils.KEY_SELF_STOP_TIME_MAP.get(logType), 0) != 0) {
                Utils.logi(TAG, "Log become enable again, logType=" + logType);
                mSharedPreferences.edit().remove(Utils.KEY_SELF_STOP_TIME_MAP.get(logType))
                        .apply();
            }
        }

        int timeOut = 5000;
        long currentTime = System.currentTimeMillis();
        while (timeOut >= 0) {
            long intervalTime = Math.abs(currentTime - mLastSDStatusChangedTime);
            if (intervalTime <= SD_STATUS_CHANGE_CHECK_TIME) {
                Utils.logw(TAG, "The SD card status changed time is < "
                        + SD_STATUS_CHANGE_CHECK_TIME + ". Ignore this changed!");
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeOut -= 500;
        }

        Intent intent = new Intent(Utils.ACTION_LOG_STATE_CHANGED);
        intent.putExtra(Utils.EXTRA_AFFECTED_LOG_TYPE, logType);
        intent.putExtra(Utils.EXTRA_LOG_NEW_STATE, logRunningStatus);
        String failedReasonStr = "";
        if (reason != null && !"".equals((String) reason)) {
            failedReasonStr = failedReasonStr + logType + ":" + (String) reason + ";";
        }
        if (!TextUtils.isEmpty(failedReasonStr)) {
            intent.putExtra(Utils.EXTRA_FAIL_REASON, failedReasonStr);
        }
        Utils.sendBroadCast(intent);

        if (isNeedUpdateStartRecordingTime(logType)) {
            updateStartRecordingTime(logRunningStatus == 1 ? SystemClock.elapsedRealtime()
                    : Utils.VALUE_BEGIN_RECORDING_TIME_DEFAULT);
        }
        updateLogFolderMonitor();
            // update current status to the latest value
//            if (result > 0) { // This log is enabled
//                boolean needRecovery =
//                        mSharedPreferences.getBoolean(
//                                Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType),
//                                Utils.DEFAULT_VALUE_NEED_RECOVER_RUNNING);
//                if (needRecovery) {
//                    Utils.logw(TAG, "Need recovery log[" + logType
//                            + "] running status, but now it's already on, clear former cache");
//                    mSharedPreferences.edit()
//                            .putBoolean(Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType), false)
//                            .apply();
//                }
//            }

    }

    /**
     * The service's global running stage have changed, like try to start/stop
     * log, begin memory dump.
     */
    private void handleGlobalRunningStageChange(int stageEvent) {
        Utils.logd(TAG, "-->handleGlobalRunningStageChange(), stageEvent=" + stageEvent
                + ", 1:start; 2:stop; 3:polling; 4:polling done.");
        mGlobalRunningStage = stageEvent;
        Intent intent = new Intent(Utils.EXTRA_RUNNING_STAGE_CHANGE_EVENT);
        intent.putExtra(Utils.EXTRA_RUNNING_STAGE_CHANGE_VALUE, stageEvent);
        Utils.sendBroadCast(intent);
    }

    /**
     * @return int
     */
    public int getCurrentRunningStage() {
        int stage = mGlobalRunningStage;
        for (int logType : Utils.LOG_TYPE_SET) {
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
            LogInstance logInstance = getLogInstance(logType);
            if (logInstance != null) {
                int subStage = logInstance.getGlobalRunningStage();
                if (subStage > stage) {
                    stage = subStage;
                }
            }
        }
        Utils.logd(TAG, "<--getGlobalRunningStage(), current stage=" + stage);
        return stage;
    }

    /**
     * @param logType
     *            The type of log.
     * @return int
     */
    public int getLogRunningStatus(int logType) {
        Utils.logd(TAG, "-->getLogInstanceRunningStatus(), logType=" + logType);
        LogInstance logInstance = getLogInstance(logType);
        int status = Utils.LOG_RUNNING_STATUS_UNKNOWN;
        if (logInstance != null) {
            status = logInstance.getLogRunningStatus();
        }
        Utils.logd(TAG, "<--getLogInstanceRunningStatus(), status=" + status);
        return status;
    }

    private boolean isNeedUpdateStartRecordingTime(int affectLogType) {
        boolean isNeedDoUpdate = true;
        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & affectLogType) == 0 && isTypeLogRunning(logType)) {
                isNeedDoUpdate = false;
                break;
            }
        }
        Utils.logd(TAG, "-->isNeedUpdateStartRecordingTime(), affectLogType = " + affectLogType
                + ", isNeedDoUpdate ? " + isNeedDoUpdate);
        return isNeedDoUpdate;
    }

    /**
     * Mark MTKLogger start time which is stored in shared preference, 0 or -1
     * mean stopped.
     */
    private void updateStartRecordingTime(long time) {
        Utils.logd(TAG, "-->updateStartRecordingTime(), time=" + time);
        mSharedPreferences.edit().putLong(
                Utils.KEY_BEGIN_RECORDING_TIME, time).apply();
        LogInstance.updateNotificationTime();
    }

    /**
     * Function for judging is any log being running right now.
     *
     * @return boolean
     */
    public boolean isAnyLogRunning() {
        boolean isRunning = false;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            if (isTypeLogRunning(logType)) {
                isRunning = true;
                break;
            }
        }
        Utils.logv(TAG, "<--isAnyLogRunning()? " + isRunning);
        return isRunning;
    }

    /**
     * @return all logs which is running.
     */
    public int getRunningLogType() {
        int runningLogTypeCluster = 0;
        for (int logType : Utils.LOG_TYPE_SET) {
            if (isTypeLogRunning(logType)) {
                runningLogTypeCluster |= logType;
            }
        }
        Utils.logv(TAG, "<--getRunningLogType() : " + runningLogTypeCluster);
        return runningLogTypeCluster;
    }

    /**
     * Trigger tag log process, called from UI, so treat this as user trigger.
     *
     * @param tagString
     *            String
     * @return boolean
     */
    public boolean beginTagLog(String tagString) {
        Utils.logi(TAG, "-->beginTagLog(), tagString=" + tagString);
        Intent intent = new Intent();
        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, tagString);
        TagLogManager.getInstance().beginTagLog(intent);
        return true;
    }

    /**
     * @param logTypeCluster
     *            The type of log.
     * @param autoStart
     *            Set log autoStart.
     */
    public void setLogAutoStart(int logTypeCluster, boolean autoStart) {
        Utils.logi(TAG, "-->setLogAutoStart(), logTypeCluster=" + logTypeCluster
                + ", autoStart?" + autoStart);
        Set<Integer> localLogSet = new HashSet<Integer>();
        for (Integer logType : Utils.LOG_TYPE_SET) {
            localLogSet.add(logType);
        }
        for (Integer logType : localLogSet) {
            if ((logType & logTypeCluster) == 0) {
                continue;
            }
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
          //filter gps log to reduce communication with proxy
            if (logType == Utils.LOG_TYPE_GPS) {
                boolean oldAutoStartValue = mDefaultSharedPreferences.getBoolean(
                        Utils.KEY_START_AUTOMATIC_MAP.get(logType), false);
                if (oldAutoStartValue == autoStart) {
                    continue;
                }
            }
            mDefaultSharedPreferences.edit()
                .putBoolean(Utils.KEY_START_AUTOMATIC_MAP.get(logType), autoStart).apply();
            LogInstance instance = getLogInstance(logType);
            if (instance == null) {
                Utils.loge(TAG, "Fail to get log instance for config auto start value.");
                continue;
            }
            if (instance.mHandler != null) {
                instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                        LogInstance.PREFIX_CONFIG_AUTO_START + (autoStart ? "1" : "0"))
                            .sendToTarget();
            } else {
                Utils.loge(TAG, "When setAutoStart(), fail to get log instance handler "
                        + " of log [" + logType + "].");
            }
        }
    }

    /**
     * @param logType
     *            int
     * @param subType
     *            int
     * @param enable
     *            boolean
     * @return boolean
     */
    public boolean setSubLogEnable(int logType, int subType, boolean enable) {
        Utils.logi(TAG, "-->setSubLogEnable(), logType=" + logType + ", subType=" + subType
                + ", enable?" + enable);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config sub log enable state.");
            return false;
        }

        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG, subType, (enable ? 1 : 0),
                    LogInstance.PREFIX_CONFIG_SUB_LOG).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setSubLogEnable(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }
    }

    /**
     * @param logType
     *            The type of log.
     * @param logSize
     *            The size of log.
     * @return boolean
     */
    public boolean setEachLogSize(int logType, int logSize) {
        Utils.logi(TAG, "-->setEachLogSize(), logType=" + logType + ", size=" + logSize);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log size.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_CONFIG_LOG_SIZE + logSize).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setLogSize(), fail to get log instance handler " + " of log ["
                    + logType + "].");
            return false;
        }
    }

    /**
     * @param logType
     *            The type of log.
     * @param logSize
     *            The size of log.
     * @return boolean
     */
    public boolean setEachTotalLogSize(int logType, int logSize) {
        Utils.logi(TAG, "-->setEachTotalLogSize(), logType=" + logType + ", size=" + logSize);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log size.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_CONFIG_TOTAL_LOG_SIZE + logSize).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setTotalLogSize(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }
    }

    private boolean notifyUSBModeChange() {
        Utils.logi(TAG, "-->notifyUSBModeChange()");
        LogInstance instance = getLogInstance(Utils.LOG_TYPE_MODEM);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get modem log instance for notify tether state change.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_TETHER_CHANGE_FLAG).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When notifyModemLogTetherChange(), fail to get log instance handler.");
            return false;
        }
    }

    /**
     * @param key int
     * @return int
     */
    public int getMetLogValues(int key) {
        Utils.logd(TAG, "-->getMetLogValues(), key = " + key);
        LogInstance instance = getLogInstance(Utils.LOG_TYPE_MET);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log getMetLogValues.");
            return 0;
        }
        if (instance.mHandler != null) {
            MetLog.setValue(0);
            instance.mHandler.obtainMessage(LogInstance.MSG_GET_VALUE_FROM_NATIVE,
                    MetLog.GET_VALUES_MAP.get(key)).sendToTarget();
            return MetLog.getValue();
        } else {
            Utils.loge(TAG, "When getMetLogValues(), fail to get log instance handler .");
            return 0;
        }
    }

    /**
     * @param logType int
     * @param period int
     * @return boolean
     */
    public boolean setLogPeriod(int logType, int period) {
        Utils.logd(TAG, "-->setLogPeriod(), logType=" + logType + ", period: " + period);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log period.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_PEROID_SIZE + period).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setLogPeriod(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }

    }

    /**
     * @param logType int
     * @param buffersize int
     * @return boolean
     */
    public boolean setLogCPUBuffer(int logType, int buffersize) {
        Utils.logd(TAG, "-->setLogCPUBuffer(), logType=" + logType + ", buffersize: "
                + buffersize);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log period.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_CPU_BUFFER_SIZE + buffersize).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setLogCPUBuffer(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }
    }

    /**
     * @param logType int
     * @param isEnable boolean
     * @return boolean
     */
    public boolean setHeavyLoadRecording(int logType, boolean isEnable) {
        Utils.logd(TAG, "-->setHeavyLoadRecording(), logType = " + logType
                + ", isEnable = " + isEnable);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config HeavyLoadRecording.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG,
                    LogInstance.PREFIX_MET_HEAVY_RECORD + (isEnable ? "1" : "0")).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setHeavyLoadRecording(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }
    }

    /**
     * @return boolean
     */
    public boolean udpateStatusBarForAllLogs() {
        for (Integer logType : Utils.LOG_TYPE_SET) {
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
            boolean isLogRunning = isTypeLogRunning(logType);
            LogInstance instance = getLogInstance(logType);
            if (instance == null) {
                Utils.loge(TAG, "Fail to get log instance for logType = ." + logType);
                continue;
            }
            instance.updateStatusBar(logType,
                    Utils.KEY_LOG_TITLE_RES_IN_STSTUSBAR_MAP.get(logType), isLogRunning);
        }
        return true;
    }

    /**
     * @param logType int
     * @return boolean
     */
    public boolean isTypeLogRunning(int logType) {
        boolean isLogRunning = false;
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for logType = ." + logType);
            return false;
        }
        isLogRunning = instance.isLogRunning(logType);
        Utils.logv(TAG, "<--isLogRunning()? " + isLogRunning + ", logType = " + logType);
        return isLogRunning;
    }

    /**
     * @param logType int
     * @param commandValue String
     * @return String
     */
    public String getValueFromNative(int logType, String commandValue) {
        Utils.logv(TAG, "-->getValueFromNative() ");
        String commandResult = "";
        LogInstance instance = getLogInstance(logType);
        if (instance != null) {
            commandResult = instance.getValueFromNative(commandValue);
        }
        Utils.logi(TAG, "<--getValueFromNative(), logType = " + logType
                + ", commandValue = " + commandValue + ", commandResult = " + commandResult);
        return commandResult;
    }

    /**
     * @param logType int
     * @return int
     */
    public int getSpecialFeatureSupport(int logType) {
        Utils.logv(TAG, "-->getSpecialFeatureSupport() logType = " + logType);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to getSpecialFeatureSupport.");
            return 0;
        }
        return instance.getSpecialFeatureSupport();
    }

    /**
     * @param enable boolean
     * @return boolean
     */
    public boolean setSaveLocationInModemLog(boolean enable) {
        Utils.logi(TAG, "-->setSaveLocationInLog(), enable? " + enable);
        LogInstance instance = getLogInstance(Utils.LOG_TYPE_MODEM);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get taglog status listener when setSaveLocationInLog.");
            return false;
        }
        if (instance.mHandler != null) {
            String setCommand = enable ? LogInstance.PREFIX_ENABLE_GPS_LOCATION
                                       : LogInstance.PREFIX_DISABLE_GPS_LOCATION;
            Message message =
                    instance.mHandler.obtainMessage(LogInstance.MSG_CONFIG, setCommand);
            if (Utils.sAvailableModemList.size() == 0) {
                Utils.loge(TAG, "Fail to get taglog status listener when setSaveLocationInLog.");
                return false;
            }
            message.arg1 = Utils.sAvailableModemList.get(0) + Utils.MODEM_LOG_K2_INDEX;
            message.sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When setSaveLocationInLog(), "
                    + "fail to get log instance handler of log[2].");
            return false;
        }
    }

    private int mStartLogCluster = 0;
    /**
     * @param logTypeCluster The type of log.
     * @param reason The reason for start of log.
     * @return boolean
     */
    synchronized public boolean startRecording(int logTypeCluster, String reason) {
        Utils.logi(TAG, "-->startRecording(), logTypeCluster=" + logTypeCluster + ", reason="
                + reason);
        if (!Utils.isDeviceOwner()) {
            Utils.logi(TAG, "It is not device owner, do not start logs!");
            return true;
        }
        if (Utils.LOG_TYPE_ALL == logTypeCluster) {
            logTypeCluster = Utils.getAllEnabledLog();
        }
        boolean result = true;
        mStartLogCluster = logTypeCluster;
        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_STARTING_LOG);
        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(reason)
                || Utils.SERVICE_STARTUP_TYPE_ADB.equals(reason)) {
            setLogAutoStart(logTypeCluster, true);
        }

        // For is need restart record time
        int logTypeAffect = 0;
        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & mStartLogCluster) == 0) {
                continue;
            }
            if (!isTypeLogRunning(logType)) {
                logTypeAffect |= logType;
            }
        }
        Utils.logd(TAG, "startRecording(), logTypeAffect=" + logTypeAffect);

        // First start mobile log
        if ((Utils.LOG_TYPE_MOBILE & mStartLogCluster) != 0) {
            if (!sendCommandToSingleNative(Utils.LOG_TYPE_MOBILE, LogInstance.MSG_INIT, reason)) {
                mStartLogCluster ^= Utils.LOG_TYPE_MOBILE;
            }
        }
        int startMobileLogTimeout = 5000;
        while ((Utils.LOG_TYPE_MOBILE & mStartLogCluster) != 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            startMobileLogTimeout -= 200;
            if (startMobileLogTimeout <= 0) {
                break;
            }
        }

        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & mStartLogCluster) == 0) {
                continue;
            }
            if (logType == Utils.LOG_TYPE_MOBILE) {
                continue;
            }
            if (logType == Utils.LOG_TYPE_GPS
                    && isTypeLogRunning(logType)) {
                mStartLogCluster ^= logType;
                continue;
            }
            if (!sendCommandToSingleNative(logType, LogInstance.MSG_INIT, reason)) {
                mStartLogCluster ^= logType;
            }
        }
        int startTimeout = 15000;
        while (mStartLogCluster != 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            startTimeout -= 200;
            if (startTimeout <= 0) {
                result = false;
                for (int logType : Utils.LOG_TYPE_SET) {
                    if ((logType & mStartLogCluster) == 0) {
                        continue;
                    }
                    if (logType == Utils.LOG_TYPE_MOBILE) {
                        continue;
                    }
                    Utils.logw(TAG, "Start Recording failed in 15000s, re-send start command!");
                    sendCommandToSingleNative(logType, LogInstance.MSG_INIT, reason);
                }
                mStartLogCluster = 0;
                break;
            }
        }
        if (Utils.SERVICE_STARTUP_TYPE_BOOT.equals(reason) || logTypeAffect != 0) {
            // During boot start, system performance will be Poor,
            // So sleep 3 seconds to wait log status really ready.
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            updateStartRecordingTime(SystemClock.elapsedRealtime());
        }
        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_IDLE);
        Intent intent = new Intent(Utils.ACTION_LOG_STATE_CHANGED);
        intent.putExtra(Utils.EXTRA_AFFECTED_LOG_TYPE, logTypeCluster);
        intent.putExtra(Utils.EXTRA_LOG_NEW_STATE, (result ? 1 : 0));
        Utils.sendBroadCast(intent);
        updateLogFolderMonitor();
        Utils.logi(TAG, "<--startRecordingDone(), startTime=" + startTimeout
                + ", result=" + result);
        if (logTypeAffect != 0) {
            TagLogManager.getInstance().checkNewException();
        }
        return result;
    }

    private boolean sendCommandToSingleNative(int logType, int commandType, String reason) {
        Utils.logi(TAG, "-->sendCommandToSingleNative(), logType = " + logType
                + ", commandType = " + commandType
                + ", reason = " + reason);
        boolean result = false;
        LogInstance logInstance = getLogInstance(logType);
        if (logInstance != null) {
            LogHandler handler = logInstance.mHandler;
            if (handler != null) {
                handler.obtainMessage(commandType, reason).sendToTarget();
                result = true;
            } else { // Fail to get log instance handler, so no where to
                     // send control command
                Utils.loge(TAG, "When startRecording(), fail to get log instance handler "
                        + " of log [" + logType + "].");
            }
        } else {
            Utils.loge(TAG, "Fail to get log instance of type: " + logType);
        }
        return result;
    }

    private int mStopLogCluster = 0;
    /**
     * @param logTypeCluster
     *            The type of log.
     * @param reason
     *            The reason for stop of log.
     * @return boolean
     */
    synchronized public boolean stopRecording(int logTypeCluster, String reason) {
        Utils.logi(TAG, "-->stopRecording(), logTypeCluster=" + logTypeCluster + ", reason="
                + reason);
        if (!Utils.isDeviceOwner()) {
            Utils.logi(TAG, "It is not device owner, do not stop logs!");
            return true;
        }
        if (Utils.LOG_TYPE_ALL == logTypeCluster) {
            logTypeCluster = Utils.getAllFeatureSupportLog();
        }
        boolean result = true;
        mStopLogCluster = logTypeCluster;
        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_STOPPING_LOG);
        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(reason)
                || Utils.SERVICE_STARTUP_TYPE_ADB.equals(reason)) {
            setLogAutoStart(logTypeCluster, false);
        }

        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & mStopLogCluster) == 0) {
                continue;
            }
            if (logType == Utils.LOG_TYPE_MOBILE) {
                // Last stop mobile log
                continue;
            }
            if (logType == Utils.LOG_TYPE_GPS
                    && !isTypeLogRunning(logType)) {
                mStopLogCluster ^= logType;
                continue;
            }
            if (!sendCommandToSingleNative(logType, LogInstance.MSG_STOP, reason)) {
                mStopLogCluster ^= logType;
            }
        }

        int stopOtherLogTimeout = 10000;
        while (mStopLogCluster != 0 && mStopLogCluster != Utils.LOG_TYPE_MOBILE) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stopOtherLogTimeout -= 200;
            if (stopOtherLogTimeout <= 0) {
                break;
            }
        }
        if ((Utils.LOG_TYPE_MOBILE & mStopLogCluster) != 0) {
            if (!sendCommandToSingleNative(Utils.LOG_TYPE_MOBILE, LogInstance.MSG_STOP, reason)) {
                mStopLogCluster ^= Utils.LOG_TYPE_MOBILE;
            }
        }

        int stopTimeout = 15000;
        while (mStopLogCluster != 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stopTimeout -= 200;
            if (stopTimeout <= 0) {
                result = false;
                mStopLogCluster = 0;
                break;
            }
        }
        if (!isAnyLogRunning()) {
            updateStartRecordingTime(Utils.VALUE_BEGIN_RECORDING_TIME_DEFAULT);
        }
        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_IDLE);
        Intent intent = new Intent(Utils.ACTION_LOG_STATE_CHANGED);
        intent.putExtra(Utils.EXTRA_AFFECTED_LOG_TYPE, logTypeCluster);
        intent.putExtra(Utils.EXTRA_LOG_NEW_STATE, (result ? 0 : 1));
        Utils.sendBroadCast(intent);
        updateLogFolderMonitor();
        if (Utils.LOG_START_STOP_REASON_FROM_UI.equals(reason)) {
            Utils.updateLogFilesInMediaProvider(this);
        }
        Utils.logi(TAG, "<--stopRecording(), stopTime=" + stopTimeout
                + ", result=" + result);
        return result;
    }

    private int mRestartLogCluster = 0;
    /**
     * @param logTypeCluster
     *            The type of log.
     * @param reason
     *            The reason for stop of log.
     * @return boolean
     */
    synchronized public boolean restartRecording(int logTypeCluster, String reason) {
        Utils.logi(TAG, "-->restartRecording(), logTypeCluster=" + logTypeCluster + ", reason="
                + reason);
        boolean result = true;
        int runningLogType = getRunningLogType();
        if (logTypeCluster == -1) {
            logTypeCluster = runningLogType;
        }
        mRestartLogCluster = logTypeCluster;
        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & mRestartLogCluster) == 0) {
                continue;
            }
            if ((logType & runningLogType) == 0) {
                mRestartLogCluster ^= logType;
            }
        }
        Utils.logd(TAG, "restartRecording() affectLogCluster = " + mRestartLogCluster);
        if (mRestartLogCluster == 0) {
            Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
            intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_RESTART);
            intent.putExtra(Utils.EXTRA_RESULT_VALUE, result ? 1 : 0);
            Utils.sendBroadCast(intent);
            return true;
        }

        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_RESTARTING_LOG);
        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & mRestartLogCluster) == 0) {
                continue;
            }
            LogInstance logInstance = getLogInstance(logType);
            if (logInstance != null) {
                LogHandler handler = logInstance.mHandler;
                if (handler != null) {
                    handler.obtainMessage(LogInstance.MSG_RESTART, reason).sendToTarget();
                }
            }
        }

        int restartTimeout = 30000;
        while (mRestartLogCluster != 0) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            restartTimeout -= 500;
            if (restartTimeout <= 0) {
                result = false;
                mRestartLogCluster = 0;
                break;
            }
        }
        Intent intent = new Intent(Utils.ACTION_MTKLOGGER_BROADCAST_RESULT);
        intent.putExtra(Utils.EXTRA_RESULT_NAME, Utils.ADB_COMMAND_RESTART);
        intent.putExtra(Utils.EXTRA_RESULT_VALUE, result ? 1 : 0);
        Utils.sendBroadCast(intent);
        Utils.logd(TAG, "Broadcast " + Utils.ACTION_MTKLOGGER_BROADCAST_RESULT
                + " is sent out with extra :"
                + Utils.EXTRA_RESULT_NAME + " = " + Utils.ADB_COMMAND_RESTART
                + ", " + Utils.EXTRA_RESULT_VALUE + " = " + (result ? 1 : 0));

        handleGlobalRunningStageChange(Utils.RUNNING_STAGE_IDLE);
        updateStartRecordingTime(SystemClock.elapsedRealtime());
        Intent logStateChangedIntent = new Intent(Utils.ACTION_LOG_STATE_CHANGED);
        logStateChangedIntent.putExtra(Utils.EXTRA_AFFECTED_LOG_TYPE, logTypeCluster);
        logStateChangedIntent.putExtra(Utils.EXTRA_LOG_NEW_STATE, (result ? 1 : 0));
        Utils.sendBroadCast(logStateChangedIntent);
        Utils.logd(TAG, "Broadcast " + Utils.ACTION_LOG_STATE_CHANGED
                + " is sent out with extra :"
                + Utils.EXTRA_AFFECTED_LOG_TYPE + " = " + logTypeCluster
                + ", " + Utils.EXTRA_LOG_NEW_STATE + " = " + (result ? 1 : 0));
        return result;
    }

    /**
     * Return a single-instance log instance.
     *
     * @return
     */
    private LogInstance getLogInstance(int logType) {
        // Utils.logv(TAG, "-->getLogInstance(), logType="+logType);
        if (mLogInstanceMap.indexOfKey(logType) < 0) {
            LogInstance instance =
                    LogInstance.getInstance(logType, MTKLoggerService.this, mServiceHandler);
            if (instance != null) {
                mLogInstanceMap.put(logType, instance);
            }
        }
        return mLogInstanceMap.get(logType);
    }

    /**
     * @param logType
     *            The type of log.
     * @param messageType
     *            The type of message.
     * @param message
     *            The type of message.
     * @return boolean
     */
    public boolean sentMessageToLog(int logType, int messageType, String message) {
        Utils.logi(TAG, "-->sentMessageToLog(), " + "logType=" + logType + ", messageType="
                + messageType + ", message=" + message);
        LogInstance instance = getLogInstance(logType);
        if (instance == null) {
            Utils.loge(TAG, "Fail to get log instance for config log size.");
            return false;
        }
        if (instance.mHandler != null) {
            instance.mHandler.obtainMessage(messageType, message).sendToTarget();
            return true;
        } else {
            Utils.loge(TAG, "When sentMessageToLog(), fail to get log instance handler "
                    + " of log [" + logType + "].");
            return false;
        }
    }
}
