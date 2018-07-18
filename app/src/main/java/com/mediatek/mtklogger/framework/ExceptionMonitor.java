package com.mediatek.mtklogger.framework;

import android.content.Intent;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;

import com.mediatek.mtklogger.proxy.communicate.ProxyCommandInfo;
import com.mediatek.mtklogger.proxy.communicate.ProxyCommandManager;
import com.mediatek.mtklogger.taglog.TagLogManager;
import com.mediatek.mtklogger.utils.FileMonitor;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.util.List;
/**
 * @author MTK11515
 *
 */
public class ExceptionMonitor {
    private static final String TAG = Utils.TAG + "/ExceptionMonitor";
    private static ExceptionMonitor sInstance;
    private ExceptionMonitor() {
    }
    /**.
     * @return ExceptionMonitor
     */
    public static ExceptionMonitor getInstance() {
        if (sInstance == null) {
            synchronized (ExceptionMonitor.class) {
                if (sInstance == null) {
                    sInstance = new ExceptionMonitor();
                }
            }
        }
        return sInstance;
    }

    /**.
     * return void
     */
    public synchronized void startExceptionMonitor() {
        Utils.logd(TAG, "startExceptionMonitor");
        if (mDBHistorySystemMonitor == null
                || mDBHistoryCommonMonitor == null) {
            Utils.logd(TAG, "startExceptionMonitor is not running, start it!");
            startFileLisener();
        }
    }

    /**.
     * return void
     */
    public void stopExceptionMonitor() {
        Utils.logd(TAG, "stopExceptionMonitor");
        if (mDBHistorySystemMonitor != null) {
            mDBHistorySystemMonitor.stopWatching();
            mDBHistorySystemMonitor = null;
        }
        if (mDBHistoryCommonMonitor != null) {
            mDBHistoryCommonMonitor.stopWatching();
            mDBHistoryCommonMonitor = null;
        }
    }

    private FileMonitor mDBHistorySystemMonitor;
    private FileMonitor mDBHistoryCommonMonitor;

    private void startFileLisener() {
        final String dbHistorySystemPath = Utils.AEE_SYSTEM_PATH +  Utils.AEE_DB_HISTORY_FILE;
        Utils.logi(TAG, "Start file monitor for " + dbHistorySystemPath);
        mDBHistorySystemMonitor = new FileMonitor(dbHistorySystemPath,
                FileObserver.MODIFY) {
            @Override
            protected void notifyModified() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Utils.logd(TAG, "monitor file change : " + dbHistorySystemPath);
                notifyNewException(dbHistorySystemPath);
            }
        };
        mDBHistorySystemMonitor.startWatching();
        final String dbHistoryVendorPath = Utils.AEE_VENDOR_PATH + Utils.AEE_DB_HISTORY_FILE;
        boolean canAccessVendorData = new File(dbHistoryVendorPath).exists();

        String dbHistoryStoragePath = dbHistoryVendorPath;
        if (!canAccessVendorData) {
            File taglogFolder = new File(Utils.geMtkLogPath() + "taglog");
            if (!taglogFolder.exists()) {
                taglogFolder.mkdirs();
            }
            dbHistoryStoragePath = taglogFolder.getAbsolutePath()
                    + File.separator +  Utils.AEE_DB_HISTORY_FILE;
        }
        final String vendorMonitorPath = dbHistoryStoragePath;
        Utils.logi(TAG, "Start file monitor for " + vendorMonitorPath);
        mDBHistoryCommonMonitor = new FileMonitor(vendorMonitorPath, FileObserver.MODIFY) {
            @Override
            protected void notifyModified() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Utils.logd(TAG, "monitor file change : " + vendorMonitorPath);
                notifyNewException(vendorMonitorPath);
            }
        };
        mDBHistoryCommonMonitor.startWatching();
        if (!canAccessVendorData) {
            ProxyCommandInfo monitorProxyCommandInfo = ProxyCommandManager.getInstance()
                    .createProxyCommand(Utils.PROXY_CMD_OPERATE_MONITOR_FILE,
                            dbHistoryVendorPath, dbHistoryStoragePath);
            ProxyCommandManager.getInstance().sendOutCommand(monitorProxyCommandInfo);
        }
    }

    private void notifyNewException(String dbHistoryPath) {
        Intent exceptionIntent = getLastExceptionIntent(dbHistoryPath);
        if (exceptionIntent != null) {
            Message msg = new Message();
            msg.what = MSG_EXCEPTION_HAPPEND_SEND_BROADCAST;
            msg.obj = exceptionIntent;
            mMessageHandler.sendMessageDelayed(msg, 1000);

            if (Utils.isTaglogEnable()) {
                TagLogManager.getInstance().beginTagLog(exceptionIntent);
            }
        }
    }
    private Intent getLastExceptionIntent(String dbHistory) {
        Intent exceptionIntent = null;

        List<String> dbInfors = Utils.getLogFolderFromFileTree(new File(dbHistory));
        if (dbInfors == null || dbInfors.size() == 0) {
            Utils.logi(TAG, "no db infors in db_history");
            return null;
        }
        String lastException = dbInfors.get(dbInfors.size() - 1);

        String[] dbStr = lastException.split(",");
        if (dbStr.length < 2) {
            Utils.logi(TAG, "wrong db string format, length = " + dbStr.length);
            return null;
        }
        String dbPath = dbStr[0];
        String zzTime = dbStr[1].trim();
        String dbFolderPath = dbPath + File.separator;

        String dbFileName = dbPath.substring(dbPath.lastIndexOf(File.separator) + 1) + ".dbg";
        String zzFileName = "ZZ_INTERNAL";

        exceptionIntent = new Intent(Utils.ACTION_EXP_HAPPENED);
        exceptionIntent.putExtra(Utils.EXTRA_KEY_EXP_PATH, dbFolderPath);
        exceptionIntent.putExtra(Utils.EXTRA_KEY_EXP_NAME, dbFileName);
        exceptionIntent.putExtra(Utils.EXTRA_KEY_EXP_ZZ, zzFileName);
        exceptionIntent.putExtra(Utils.EXTRA_KEY_EXP_TIME, zzTime);

        Utils.logd(TAG, "new excepion from db_history, exp =" + lastException);
        Utils.logd(TAG, "new excepion from db_history, dbFolderPath =" + dbFolderPath
                + ", dbFileName = " + dbFileName + ", zzTime = " + zzTime);
        return exceptionIntent;
    }

    private static final int MSG_EXCEPTION_HAPPEND_SEND_BROADCAST = 1;
    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message message) {
            Utils.logi(TAG, " MyHandler handleMessage --> start " + message.what);
            switch (message.what) {
            case MSG_EXCEPTION_HAPPEND_SEND_BROADCAST :
                Intent exceptionIntent = (Intent) message.obj;
                Utils.sendBroadCast(exceptionIntent);
                Utils.logi(TAG, "send expception happened broadcast.");
                break;
            default:
                Utils.logw(TAG, "Not supported message: " + message.what);
                break;
            }
        }
    };
}
