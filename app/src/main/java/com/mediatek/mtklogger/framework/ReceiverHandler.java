package com.mediatek.mtklogger.framework;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;


/**
 * @author MTK81255
 *
 */
public class ReceiverHandler extends Handler {
    private static final String TAG = Utils.TAG + "/ReceiverHandler";

    public static final int MSG_KILL_SELF = 1;
    public static final int MSG_RECEIVER_BOOT_COMPLETED = 2;
    public static final int MSG_RECEIVER_ADB_CMD = 3;
    public static final int MSG_RECEIVER_MDLOGGER_RESTART_DONE = 4;
    public static final int MSG_RECEIVER_EXP_HAPPENED = 5;
    public static final int MSG_RECEIVER_FROM_BYPASS = 6;
    // After receiver a kill self command, wait this time(ms) to avoid
    // duplicated kill in short time
    public static final int DELAY_KILL_SELF = 2000;
    public static final int DELAY_WAITING_BOOT_COMPLETE_DONE = 5000;

    private static ReceiverHandler sDefaultInstance;

    /**
     * @param looper Looper
     */
    public ReceiverHandler(Looper looper) {
        super(looper);
    }

    /**
     * @return ReceiverHandler
     */
    public static ReceiverHandler getDefaultInstance() {
        if (sDefaultInstance == null) {
            synchronized (ReceiverHandler.class) {
                if (sDefaultInstance == null) {
                    HandlerThread handlerThread = new HandlerThread("ReceiverHandlerThread");
                    handlerThread.start();
                    sDefaultInstance = new ReceiverHandler(handlerThread.getLooper());
                }
            }
        }
        return sDefaultInstance;
    }

    private SharedPreferences mSharedPreferences =
            MyApplication.getInstance().getSharedPreferences();
    private SharedPreferences mDefaultSharedPreferences =
            MyApplication.getInstance().getDefaultSharedPreferences();

    @Override
    public void handleMessage(Message msg) {
        int what = msg.what;
        if (what == MSG_KILL_SELF) {
            Utils.logi(TAG, "Get a self-kill command. Need to kill me now");
            if (!MTKLoggerServiceManager.getInstance().isServiceUsed()) {
                Process.killProcess(Process.myPid());
            } else {
                Utils.logi(TAG, "But Log service was started already, maybe user enter UI."
                        + "Do not kill self any more.");
            }
            return;
        }
        try {
            Intent intent = new Intent();
            if (msg.obj instanceof Intent) {
                intent = (Intent) msg.obj;
            }
            if (what == MSG_RECEIVER_BOOT_COMPLETED) {
                dealWithBootcomplete(intent);
                return;
            }
            if (!Utils.isBootCompleteDone()) {
                getDefaultInstance().sendMessageDelayed(
                        getDefaultInstance().obtainMessage(what, intent),
                        DELAY_WAITING_BOOT_COMPLETE_DONE);
                return;
            }
            if (what == MSG_RECEIVER_ADB_CMD) {
                if (!Utils.isDeviceOwner()) {
                    Utils.logi(TAG, "It is not device owner, ignore dealWithADBCommand()");
                    return;
                }
                dealWithADBCommand(intent);
            } else if (what == MSG_RECEIVER_MDLOGGER_RESTART_DONE) {
                dealWithMDLoggerRestart(intent);
            } else if (what == MSG_RECEIVER_EXP_HAPPENED) {
                dealWithExcptionHappend(intent);
            } else if (what == MSG_RECEIVER_FROM_BYPASS) {
                dealWithBypassAction(intent);
            }
        } catch (ServiceNullException e) {
            return;
        }
    };

    private void dealWithBootcomplete(Intent intent) throws ServiceNullException {
        LogConfig.getInstance().checkConfig();
        initLogStatus();
        // Now start log service or just remove log process manually
        if (Utils.isTaglogEnable() || needStartLogAtBootTime()) {
            MTKLoggerServiceManager.getInstance().getService().initLogsForBootup();
        } else {
            Utils.setBootCompleteDone();
            getDefaultInstance().removeMessages(MSG_KILL_SELF);
            getDefaultInstance().sendMessageDelayed(
                    getDefaultInstance().obtainMessage(MSG_KILL_SELF), DELAY_KILL_SELF);
        }
    }

    private void dealWithADBCommand(Intent intent) throws ServiceNullException {
        MTKLoggerServiceManager.getInstance().getService().daelWithADBCommand(intent);
    }

    private void dealWithMDLoggerRestart(Intent intent) throws ServiceNullException {
        MTKLoggerServiceManager.getInstance().getService().dealWithMDLoggerRestart(intent);
    }

    private void dealWithExcptionHappend(Intent intent) throws ServiceNullException {
        if (Utils.isTaglogEnable()) {
            MTKLoggerServiceManager.getInstance().getService().doTagLogForManually(intent);
        }
    }

    private void dealWithBypassAction(Intent intent) throws ServiceNullException {
        MTKLoggerServiceManager.getInstance().getService().dealWithBypassAction(intent);
    }

    private void initLogStatus() {
        Utils.logd(TAG, "-->initLogStatus()");
        for (Integer logType : Utils.LOG_TYPE_SET) {
            // At boot up time, assume all log is stopped
            if (Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                    Utils.KEY_STATUS_MAP.get(logType), Utils.VALUE_STATUS_STOPPED)) {
                Utils.logw(TAG, "Boot up, set " +
                    Utils.KEY_STATUS_MAP.get(logType) + " to stopped");
                mSharedPreferences.edit()
                        .putInt(Utils.KEY_STATUS_MAP.get(logType), Utils.VALUE_STATUS_STOPPED)
                        .apply();
            }

            // Reset need recovery log running status flag
            if (mSharedPreferences.getBoolean(Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType),
                    Utils.DEFAULT_VALUE_NEED_RECOVER_RUNNING)) {
                mSharedPreferences
                        .edit()
                        .putBoolean(Utils.KEY_NEED_RECOVER_RUNNING_MAP.get(logType),
                                Utils.DEFAULT_VALUE_NEED_RECOVER_RUNNING).apply();
            }
        }
        // Reset log start up time
        mSharedPreferences.edit()
                .putLong(Utils.KEY_BEGIN_RECORDING_TIME,
                        Utils.VALUE_BEGIN_RECORDING_TIME_DEFAULT)
                .apply();

        // For Debug mode default status.
        mSharedPreferences.edit().putBoolean(
                Utils.SETTINGS_HAS_STARTED_DEBUG_MODE, false).apply();

        // Reset modem assert file path
        mSharedPreferences.edit().remove(Utils.KEY_MODEM_ASSERT_FILE_PATH)
                .remove(Utils.KEY_WAITING_SD_READY_REASON) // clear waiting sd
                                                           // status
                .remove(Utils.KEY_MODEM_EXCEPTION_PATH) // clear modem assert
                                                        // information
                .remove(Utils.KEY_USB_MODE_VALUE) // After reboot, usb tether
                                                  // will be reset
                .remove(Utils.KEY_USB_CONNECTED_VALUE) // After reboot, assume
                                                       // usb not connected
                .apply();

        if (Utils.isDenaliMd3Solution()) {
            mSharedPreferences.edit().putString(Utils.KEY_C2K_MODEM_LOGGING_PATH, "").apply();
        }
    }

    /**
     * Judge whether need to start up MTKLogger service at boot time.
     * If none log instance was set to start automatically when boot up,
     * just remove this process to avoid confuse user
     * @return
     */
    private boolean needStartLogAtBootTime() {
        boolean needStart = false;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            if (logType == Utils.LOG_TYPE_MET) {
                continue;
            }
            if (Utils.VALUE_START_AUTOMATIC_ON == mDefaultSharedPreferences.getBoolean(
                    Utils.KEY_START_AUTOMATIC_MAP.get(logType),
                    Utils.DEFAULT_CONFIG_LOG_AUTO_START_MAP.get(logType))) {
                needStart = true;
                break;
            }
        }
        Utils.logd(TAG, "-->needStartLogAtBootTime(), needStart=" + needStart);
        return needStart;
    }
}
