package com.mediatek.mtklogger.taglog;

import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
/**
 * @author MTK81255
 *
 */
public class LogInstanceForTaglog {
    protected static final String TAG = TagLogUtils.TAGLOG_TAG + "/LogInstanceForTaglog";

    protected int mLogType;
    protected TagLogInformation mTagLogInformation;

    /**
     * @param logType int
     * @param tagLogInformation TagLogInformation
     */
    public LogInstanceForTaglog(int logType, TagLogInformation tagLogInformation) {
        mLogType = logType;
        mTagLogInformation = tagLogInformation;
    }

    /**
     * @return boolean
     */
    public boolean isNeedDoTag() {
        int needLogType = mTagLogInformation.getNeedLogType();
        if (needLogType != 0 && (needLogType & mLogType) == 0) {
            Utils.logi(TAG, "isNeedDoTag ? false. mLogType = " + mLogType + ", needLogType = "
                    + needLogType);
            return false;
        }
        Utils.logi(TAG, "isNeedDoTag ? true. mLogType = " + mLogType);
        return true;
    }

    /**
     * @return boolean
     */
    public boolean isNeedRestart() {
        if (!isNeedDoTag()) {
            Utils.logi(TAG, "isNeedRestart ? false. No need do tag, so no need restart."
                    + " mLogType = " + mLogType);
            return false;
        }
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                Utils.logi(TAG, "isNeedRestart ? false. Log is stopped before do tag."
                        + " mLogType = " + mLogType);
                return false;
            }
        } catch (ServiceNullException e) {
            return false;
        }
        String needTagPath = getNeedTagPath();
        if (needTagPath.isEmpty()) {
            Utils.logi(TAG, "isNeedRestart ? false. getNeedTagPath  is empty.");
            return false;
        }
        if (!needTagPath.contains(getSavingPath())) {
            String[] savingPaths = getSavingPath().split(";");
            boolean isRealContain = false;
            for (String path : savingPaths) {
                if (needTagPath.contains(path)) {
                    isRealContain = true;
                    break;
                }
            }
            if (!isRealContain) {
                Utils.logi(TAG, "isNeedRestart ? false. "
                        + "getNeedTagPath not contains subSavingPath!");
                return false;
            }
        }
        return true;
    }
    /**
     * @return boolean
     */
    public boolean canDoTag() {
        String needTagPath = getNeedTagPath();
        return needTagPath.isEmpty() || getSavingPath().isEmpty()
                || !needTagPath.contains(getSavingPath());
    }

    protected String mNeedTagPath = null;
    /**
     * @return log need be tagged folder path like : /mtklog/mobilelog/APLog_***
     */
    public String getNeedTagPath() {
        if (mNeedTagPath != null) {
            Utils.logd(TAG, "getNeedTagPath() mNeedTagPath is not null, no need reinit it!"
                    + " mNeedTagPath = " + mNeedTagPath);
            return mNeedTagPath;
        }
        String needTagPath = "";
        File fileTree = new File(
                getSavingParentPath() + File.separator + Utils.LOG_TREE_FILE);
        String logFolderPath = Utils.getLogFolderFromFileTree(fileTree,
                         mTagLogInformation.getExpTime());
        if (logFolderPath != null) {
            needTagPath = logFolderPath;
        }
        Utils.logi(TAG, "getNeedTagPath() needTagPath = " + needTagPath
                    + ", for logtype = " + mLogType);
        mNeedTagPath = needTagPath;
        return needTagPath;
    }

    /**
     * @return log saving folder path like : /mtklog/mobilelog/APLog_***
     */
    public String getSavingPath() {
        String savingPath = "";
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                Utils.logw(TAG, "Log mLogType = " + mLogType + " is stopped,"
                        + " just return null string for saving path!");
                return savingPath;
            }
        } catch (ServiceNullException e) {
            return savingPath;
        }
        File fileTree = new File(getSavingParentPath() + File.separator + Utils.LOG_TREE_FILE);
        String logFolderPath = Utils.getLogFolderFromFileTree(fileTree, "");
        if (logFolderPath != null) {
            savingPath = logFolderPath;
        }
        Utils.logi(TAG, "getSavingPath() savingPath = " + savingPath);
        return savingPath;
    }

    /**
     * @return type log parent path like : /mtklog/mobilelog
     */
    public String getSavingParentPath() {
        String savingParentPath = Utils.geMtkLogPath() + Utils.LOG_PATH_MAP.get(mLogType);
        Utils.logd(TAG, "savingParentPath = " + savingParentPath);
        return savingParentPath;
    }

    /**
     * void.
     */
    public void start() {
        try {
            MTKLoggerServiceManager.getInstance().getService().startRecording(
                    mLogType, Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
        } catch (ServiceNullException e1) {
            return;
        }
        int timeout = 0;
        try {
            while (!MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                try {
                    Thread.sleep(TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                timeout += TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD;
                if (timeout >= TagLogUtils.LOG_STATUS_CHECK_TIME_OUT) {
                    Utils.logw(TAG, "Type log[" + mLogType + "] start timeout!");
                    break;
                }
            }
        } catch (ServiceNullException e) {
            return;
        }
    }

    /**
     * void.
     */
    public void stop() {
        try {
            MTKLoggerServiceManager.getInstance().getService().stopRecording(
                    mLogType, Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
        } catch (ServiceNullException e1) {
            return;
        }
        int timeout = 0;
        try {
            while (MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                try {
                    Thread.sleep(TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                timeout += TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD;
                if (timeout >= TagLogUtils.LOG_STATUS_CHECK_TIME_OUT) {
                    Utils.logw(TAG, "Type log[" + mLogType + "] stop timeout!");
                    break;
                }
            }
        } catch (ServiceNullException e) {
            return;
        }
    }

    public int getLogType() {
        return mLogType;
    }
}
