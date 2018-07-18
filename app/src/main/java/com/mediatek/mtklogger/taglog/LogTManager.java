package com.mediatek.mtklogger.taglog;

import android.os.Handler;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.taglog.TagLogUtils.LogInfoTreatmentEnum;
import com.mediatek.mtklogger.taglog.db.DBManager;
import com.mediatek.mtklogger.taglog.db.FileInfoTable;
import com.mediatek.mtklogger.taglog.db.MySQLiteHelper;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class LogTManager {
    private static final String TAG = TagLogUtils.TAGLOG_TAG + "/LogTManager";

    private List<LogInstanceForTaglog> mLogForTaglogList = new ArrayList<LogInstanceForTaglog>();
    private TagLogInformation mTagLogInformation;
    private Handler mTaglogManagerHandler;
    private int mLogTypeRestart = 0;

    /**
     * @param taglog TagLog
     */
    public LogTManager(TagLog taglog) {
        mTagLogInformation = taglog.getTaglogInformation();
        mTaglogManagerHandler = taglog.getTaglogManagerHandler();
        init();
    }
    private void init() {
        Utils.logd(TAG, "-->init start.");
        for (int logType : TagLogUtils.TAG_LOG_TYPE_SET) {
            LogInstanceForTaglog logForTaglog = getLogInstance(logType);
            if (logForTaglog != null) {
                mLogForTaglogList.add(logForTaglog);
            }
        }
        Utils.logi(TAG, "mLogForTaglogList.size() = " + mLogForTaglogList.size());
    }

    private LogInstanceForTaglog getLogInstance(int logType) {
        LogInstanceForTaglog logInstance = null;
        switch (logType) {
        case Utils.LOG_TYPE_MOBILE:
            logInstance = new MobileLogT(logType, mTagLogInformation);
            break;
        case Utils.LOG_TYPE_MODEM:
            logInstance = new ModemLogT(logType, mTagLogInformation);
            break;
        case Utils.LOG_TYPE_NETWORK:
            logInstance = new NetworkLogT(logType, mTagLogInformation);
            break;
        case Utils.LOG_TYPE_GPS:
            logInstance = new GPSLogT(logType, mTagLogInformation);
            break;
        case TagLogUtils.LOG_TYPE_BT:
            logInstance = new BTLogT(logType, mTagLogInformation);
            break;
        default :
            Utils.loge(TAG, "Unspported logType = " + logType + " for Taglog.");
            break;
        }
        return logInstance;
    }

    public List<LogInformation> getSavingLogInformation() {
        return getLogInformation(false);
    }

    public List<LogInformation> getSavingLogParentInformation() {
        return getLogInformation(true);
    }

    private List<LogInformation> getLogInformation(boolean isNeedParentPath) {
        Utils.logd(TAG, "-->getLogPath() isNeedParentPath = " + isNeedParentPath);
        List<LogInformation> logInformationList = new ArrayList<LogInformation>();
        for (LogInstanceForTaglog logInstanceForTaglog : mLogForTaglogList) {
            if (!logInstanceForTaglog.isNeedDoTag()) {
                continue;
            }
            int logType = logInstanceForTaglog.getLogType();
            String logPath = isNeedParentPath ? logInstanceForTaglog
                    .getSavingParentPath() : logInstanceForTaglog.getNeedTagPath();
            LogInfoTreatmentEnum logInfoTreatmentEnum = mTagLogInformation.isNeedZip()
                                                    ? LogInfoTreatmentEnum.ZIP_DELETE
                                                    : LogInfoTreatmentEnum.DO_NOTHING;

            String[] logPaths = logPath.split(";");
            for (String curLogPath : logPaths) {
                LogInformation logInformation = getLogInformationFromDB(curLogPath);
                if (logInformation != null && !isNeedParentPath) {
                    String targetFileNameTmp = logInformation.getTargetTagFolder() + File.separator
                             + logInformation.getTargetFileName().replace("TMP_", "");
                    Utils.logi(TAG, "-->getLogPath(), logPath = " + targetFileNameTmp);
                    String targetFileNameDone = targetFileNameTmp.replace("TMP_", "");
                    String sourcePath = logInformation.getFileInfo().getSourcePath();

                    String newSourcePath = targetFileNameTmp;
                    if (!newSourcePath.contains(targetFileNameDone)) {
                        newSourcePath += ";" + targetFileNameDone;
                    }
                    if (!newSourcePath.contains(sourcePath)) {
                        newSourcePath += ";" + sourcePath;
                    }
                    Utils.logi(TAG, "-->getLogPath(), setNewSourcePath = " + newSourcePath);
                    logInformation.getFileInfo().setOriginalPath(sourcePath);
                    logInformation.getFileInfo().setState(MySQLiteHelper.FILEINFO_STATE_PREPARE);
                    logInformation.getFileInfo().setSourcePath(newSourcePath);
                    logInformation.setTagFlag(false);
                    logInformation.setTreatMent(LogInfoTreatmentEnum.COPY);
                    logInformationList.add(logInformation);
                } else {
                    File curLogFile = new File(curLogPath);
                    if (curLogFile.exists()) {
                        logInformation = new LogInformation(logType, curLogFile,
                                             logInfoTreatmentEnum);
                        Utils.logi(TAG, "-->getLogPath(), logPath = " + curLogPath);
                        if (curLogPath.contains(ModemLogT.MODEM_LOG_NO_NEED_ZIP)) {
                            logInformation.setTreatMent(LogInfoTreatmentEnum.DO_NOTHING);
                        }
                        logInformationList.add(logInformation);
                        logInformation.setTagFlag(true);
                    }
                }
            }
        }
        return logInformationList;
    }
    /**
     * @return boolean
     */
    public boolean canDoTag() {
        for (LogInstanceForTaglog logInstanceForTaglog : mLogForTaglogList) {
            if (!logInstanceForTaglog.isNeedDoTag()) {
                continue;
            }
            if (!logInstanceForTaglog.canDoTag()) {
                Utils.logd(TAG, "LogType[" + logInstanceForTaglog.getLogType()
                                 + "] can't do tag now");
                return false;
            }
        }
        return true;
    }
    /**.
     * restartLogs
     */
    public void restartLogs() {
        Utils.logi(TAG, "-->restartLogs");
        int logTypeRestart = 0;
        for (LogInstanceForTaglog logInstanceForTaglog : mLogForTaglogList) {
            if (logInstanceForTaglog.isNeedRestart()) {
                logTypeRestart |= logInstanceForTaglog.getLogType();
            }
        }
        Utils.logi(TAG, "logTypeRestart = " + logTypeRestart);
        if (logTypeRestart != 0) {
            lockUI();
            try {
                MTKLoggerServiceManager.getInstance().getService().restartRecording(logTypeRestart,
                        Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
            } catch (ServiceNullException e1) {
                releaseUI();
                return;
            }

            int timeout = 0;
            while (true) {
                if (canDoTag()) {
                    break;
                }
                try {
                    Thread.sleep(TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                timeout += TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD;
                if (timeout >= TagLogUtils.LOG_STATUS_CHECK_TIME_OUT) {
                    Utils.logw(TAG, "check can do tag timeout!");
                    break;
                }
            }
            releaseUI();
        }
        Utils.logi(TAG, "<--restartLogs");
    }

    /**
     * void.
     */
    public void stopLogs() {
        mLogTypeRestart = 0;
        for (LogInstanceForTaglog logInstanceForTaglog : mLogForTaglogList) {
            if (logInstanceForTaglog.isNeedRestart()) {
                mLogTypeRestart |= logInstanceForTaglog.getLogType();
            }
        }
        if (mLogTypeRestart == 0) {
            Utils.logi(TAG, "no need stop logs for mLogTypeRestart = " + mLogTypeRestart);
            return;
        }
        try {
            MTKLoggerServiceManager.getInstance().getService().stopRecording(
                    mLogTypeRestart, Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
        } catch (ServiceNullException e1) {
            return;
        }

        int timeout = 0;
        boolean isStopDone = false;
        while (true) {
            isStopDone = true;
            for (int logType : Utils.LOG_TYPE_SET) {
                if ((logType & mLogTypeRestart) == 0) {
                    continue;
                }
                if (logType == Utils.LOG_TYPE_MODEM) {
                    try {
                        if (MTKLoggerServiceManager.getInstance().getService()
                                .isTypeLogRunning(logType)) {
                            isStopDone = false;
                            break;
                        } else if (Utils.isDenaliMd3Solution() &&
                                !MyApplication.getInstance().getSharedPreferences()
                                    .getString(Utils.KEY_C2K_MODEM_LOGGING_PATH, "").isEmpty()) {
                            isStopDone = false;
                            break;
                        }
                    } catch (ServiceNullException e) {
                        isStopDone = false;
                        break;
                    }
                }
                try {
                    if (MTKLoggerServiceManager.getInstance().getService()
                            .isTypeLogRunning(logType)) {
                        isStopDone = false;
                        break;
                    }
                } catch (ServiceNullException e) {
                    isStopDone = false;
                    break;
                }
            }
            if (isStopDone) {
                Utils.logi(TAG, "Type log[" + mLogTypeRestart + "] stop done!");
                break;
            }
            try {
                Thread.sleep(TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeout += TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD;
            if (timeout >= TagLogUtils.LOG_STATUS_CHECK_TIME_OUT) {
                Utils.logw(TAG, "Type log[" + mLogTypeRestart + "] stop timeout!");
                break;
            }
        }
    }

    /**
     * void.
     */
    public void startLogs() {
        if (mLogTypeRestart == 0) {
            Utils.logi(TAG, "no need start logs for mLogTypeRestart = " + mLogTypeRestart);
            return;
        }
        try {
            MTKLoggerServiceManager.getInstance().getService().startRecording(
                    mLogTypeRestart, Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
        } catch (ServiceNullException e1) {
            return;
        }
        int timeout = 0;
        boolean isStartDone = false;
        while (true) {
            isStartDone = true;
            for (int logType : Utils.LOG_TYPE_SET) {
                if ((logType & mLogTypeRestart) == 0) {
                    continue;
                }
                try {
                    if (!MTKLoggerServiceManager.getInstance().getService()
                            .isTypeLogRunning(logType)) {
                        isStartDone = false;
                        break;
                    }
                } catch (ServiceNullException e) {
                    isStartDone = false;
                    break;
                }
            }
            if (isStartDone) {
                Utils.logi(TAG, "Type log[" + mLogTypeRestart + "] start done!");
                break;
            }
            try {
                Thread.sleep(TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeout += TagLogUtils.LOG_STATUS_CHECK_TIME_PERIOD;
            if (timeout >= TagLogUtils.LOG_STATUS_CHECK_TIME_OUT) {
                Utils.logw(TAG, "Type log[" + mLogTypeRestart + "] start timeout!");
                restartLog(mLogTypeRestart);
                break;
            }
        }
    }

    private void restartLog(int restartlogType) {
        Utils.logd(TAG, "restartLogType : " + restartlogType);
        int logTypeNeedRestart = 0;
        for (int logType : Utils.LOG_TYPE_SET) {
            if ((logType & restartlogType) == 0) {
                continue;
            }
            try {
                if (MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(logType)) {
                    continue;
                }
            } catch (ServiceNullException e) {
                continue;
            }
            logTypeNeedRestart |= logType;
        }
        if (logTypeNeedRestart != 0) {
            try {
                MTKLoggerServiceManager.getInstance().getService().startRecording(
                        logTypeNeedRestart, Utils.LOG_START_STOP_REASON_FROM_TAGLOG);
            } catch (ServiceNullException e) {
                return;
            }
        }
    }

    private LogInformation getLogInformationFromDB(String logPath) {
        LogInformation logInformation = null;
        FileInfoTable fileInfo = DBManager.getInstance().getFileInfoByOriginalPath(logPath);
        if (fileInfo != null) {
            logInformation = new LogInformation(fileInfo);
            Utils.logi(TAG, "-->getLogInformationFromDB, logPath = " + logPath);
        }
        return logInformation;
    }

    private void lockUI() {
        Utils.logi(TAG, "-->lockUI");
        mTaglogManagerHandler.obtainMessage(TagLogUtils.MSG_UI_LOCK, this).sendToTarget();
    }

    private void releaseUI() {
        Utils.logi(TAG, "-->releaseUI");
        mTaglogManagerHandler.obtainMessage(TagLogUtils.MSG_UI_RELEASE, this).sendToTarget();
    }

}
