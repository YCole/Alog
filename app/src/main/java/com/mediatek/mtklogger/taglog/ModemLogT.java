package com.mediatek.mtklogger.taglog;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.framework.MultiModemLog;
import com.mediatek.mtklogger.settings.SettingsActivity;
import com.mediatek.mtklogger.utils.ExceptionInfo;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author MTK81255
 *
 */
public class ModemLogT extends LogInstanceForTaglog {

    private static final int WAIT_MODEM_INTENT = 1000;
    private boolean mIsModemEE = false;
    private boolean mIsModemFlush = false;
    private String mCurrentMode = Utils.MODEM_MODE_SD;
    private boolean mIsSpecialModemExp = false;
    private SharedPreferences mSharedPreferences;

    private static int sMaxZipNormalLogNum = 3;
    public static final String MODEM_LOG_NO_NEED_ZIP = "_NO_NEED_ZIP";
    /**
     * @param logType int
     * @param tagLogInformation TagLogInformation
     */
    public ModemLogT(int logType, TagLogInformation tagLogInformation) {
        super(logType, tagLogInformation);
        init();
    }

    private void init() {
        mSharedPreferences = MyApplication.getInstance().getSharedPreferences();
        ExceptionInfo expInfo = new ExceptionInfo();
        try {
            expInfo.initFieldsFromZZ(mTagLogInformation.getExpPath() + File.separator
                    + Utils.EXTRA_VALUE_EXP_ZZ);
            mIsModemEE = expInfo.getmDiscription()
                        .toLowerCase(Locale.getDefault()).contains("modem");
            mIsSpecialModemExp = isSpecialMdExp(expInfo);
        } catch (IOException e) {
            Utils.loge(TAG, "fail to init exception info:" + e.getMessage());
        }
        if (mIsModemEE) {
            sMaxZipNormalLogNum = 4;
        } else {
            sMaxZipNormalLogNum = 3;
        }
        mCurrentMode =
                MyApplication.getInstance().getDefaultSharedPreferences()
                        .getString(Utils.KEY_MD_MODE_1, Utils.MODEM_MODE_SD);
        mIsModemFlush = Utils.MODEM_MODE_PLS.equals(mCurrentMode);
        Utils.logd(TAG, "mIsModemEE = " + mIsModemEE + ", mIsModemFlush =" + mIsModemFlush);
    }

    @Override
    public boolean isNeedDoTag() {
        if (mCurrentMode.equals(Utils.MODEM_MODE_USB)) {
            // For md log is in usb mode
            Utils.logi(TAG, "isNeedZipModemLog ? false. Modem is in USB mode.");
            return false;
        }
        if (MyApplication.getInstance().getDefaultSharedPreferences()
                .getBoolean(SettingsActivity.KEY_ALWAYS_TAG_MODEM_LOG_ENABLE, false)) {
            return true;
        }
        if (mTagLogInformation.isFromException() && !mIsModemEE && !mIsSpecialModemExp) {
            // For normal exception, no need tag modem log
            Utils.logi(TAG, "isNeedZipModemLog ? false." + " mIsModemEE = " + mIsModemEE
                    + ", mIsSpecialModemExp = " + mIsSpecialModemExp);
            return false;
        }
        return super.isNeedDoTag();
    }

    private boolean isSpecialMdExp(ExceptionInfo expInfo) {
        if (!mTagLogInformation.isFromException()) {
            return false;
        }
        boolean isModemException = false;
        String expType = "";
        String expProcess = "";
        expType = expInfo.getmType();
        expProcess = expInfo.getmProcess();

        if (expType == null) {
            isModemException = false;
        } else if (expType.endsWith("Native (NE)")
                && (expProcess.contains("volte_") || expProcess.contains("mtkmal") || expProcess
                        .contains("mtkrild") || expProcess.contains("mtkfusionrild"))) {
            isModemException = true;
        } else if (expType.endsWith("System API Dump")
                && expProcess.contains("AT command pending too long")) {
            isModemException = true;
            sMaxZipNormalLogNum = 5;
        } else if (expType.endsWith("Modem Warning")) {
            isModemException = true;
        } else if (expType.endsWith("ANR")
                && (expProcess.contains("com.android.phone")
                        || expProcess.contains("com.android.mms"))) {
            isModemException = true;
        } else if (expType.endsWith("Java (JE)")
                && expProcess.contains("com.android.mms")) {
            isModemException = true;
        }
        Utils.logi(TAG, "isSpecialMdExp ? " + isModemException);
        return isModemException;
    }

    @Override
    public boolean isNeedRestart() {
        if (Utils.MODEM_MODE_USB.equals(mCurrentMode)) {
            // For md log is in usb mode
            Utils.logi(TAG, "isNeedRestartModemLog ? false. Modem is in USB mode." + " mLogType = "
                    + mLogType);
            return false;
        }
        if (Utils.MODEM_MODE_PLS.equals(mCurrentMode)) {
            // For md log is in PLS mode
            Utils.logi(TAG, "isNeedRestartModemLog ? false. Modem is in PLS mode." + " mLogType = "
                    + mLogType);
            return false;
        }
        return super.isNeedRestart();
    }

    private String getModemLogPath(String beforTime) {
        waitPollingDone();
        String allModemLogPath = "";
        String[] modemLogParentPaths = getSavingParentPath().split(";");
        for (String modemParentLogPath : modemLogParentPaths) {
            String modemLogPath = "";
            File fileTree = new File(modemParentLogPath + File.separator + Utils.LOG_TREE_FILE);
            String logFolderPath = Utils.getLogFolderFromFileTree(fileTree, beforTime);
            //no need check file exist here, because LogTManager.getLogInformation
            //will first from db
            if (logFolderPath != null) {
                modemLogPath = logFolderPath;
            }
            if (!modemLogPath.isEmpty()) {
                allModemLogPath += modemLogPath + ";";
            }
        }
        if (Utils.isSupportC2KModem() && Utils.isDenaliMd3Solution()) {
            allModemLogPath += getC2kLogPath(beforTime);
        }
        if (allModemLogPath.endsWith(";")) {
            // Remove ";" from modemLogSavingPath
            allModemLogPath = allModemLogPath.substring(0, allModemLogPath.length() - 1);
        }
        Utils.logi(TAG, "getModemLogPath() beforTime = " + beforTime
                + ", allModemLogPath = " + allModemLogPath);
        return allModemLogPath;
    }

    @Override
    public String getNeedTagPath() {
        if (mNeedTagPath != null) {
            Utils.logd(TAG, "getNeedTagPath() mNeedTagPath is not null, no need reinit it!"
                    + " mNeedTagPath = " + mNeedTagPath);
            return mNeedTagPath;
        }
        String modemNeedTagPath = "";
        if (mIsModemFlush) {
            modemNeedTagPath = getFlushPath();
        } else if (mIsModemEE) {
            modemNeedTagPath = getEEPath();
        } else {
            modemNeedTagPath = getModemLogPath(mTagLogInformation.getExpTime());
        }
        modemNeedTagPath = doFilter(modemNeedTagPath);
        Utils.logi(TAG, "getNeedTagPath() modemNeedTagPath = " + modemNeedTagPath);
        mNeedTagPath = modemNeedTagPath;
        return modemNeedTagPath;
    }
/**
 * @param path String
 * @return String
 */
    public String copyDumpFiles(String path) {
        Utils.logi(TAG, "copyDumpFiles--> path =  " + path);
        String newPath = "";
        File logPath = new File(path);
        File[] fileList = null;
        if (logPath != null && logPath.exists()) {
            fileList = logPath.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    if (pathname.getName().contains("_data")) {
                        return true;
                    }
                    return false;
                }
            });
        }
        if (fileList != null && fileList.length >= 1) {
            newPath = mTagLogInformation.getTaglogTargetFolder() + File.separator
                                  + "data";
            File newFolder = new File(newPath);
            if (!newFolder.exists()) {
                newFolder.mkdirs();
            }
            for (File dataFile : fileList) {
//                dataFile.renameTo(new File(newPath + File.separator + dataFile.getName()));
                Utils.doCopy(dataFile.getAbsolutePath(), newPath + File.separator
                        + dataFile.getName());
            }
        }
        Utils.logi(TAG, "<--copyDumpFiles " + newPath);
        return newPath;
    }

    @Override
    public String getSavingPath() {
        String modemSavingPath = "";
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                Utils.logw(TAG, "Log mLogType = " + mLogType + " is stopped,"
                        + " just return null string for saving path!");
                return modemSavingPath;
            }
        } catch (ServiceNullException e) {
            return modemSavingPath;
        }
        if (mIsModemFlush) {
            Utils.logi(TAG, "getModemLogPath() = null,for modelog in PLS mode.");
            return modemSavingPath;
        }
        modemSavingPath = getModemLogPath("");
        Utils.logi(TAG, "getSavingPath() modemSavingPath = " + modemSavingPath);
        return modemSavingPath;
    }

    @Override
    public String getSavingParentPath() {
        Set<String> mdLogPathSets = new HashSet<String>();
        String mtkLogPath = Utils.geMtkLogPath();
        for (int modemIndex : Utils.MODEM_INDEX_SET) {
            String modemLogFolder = Utils.MODEM_INDEX_FOLDER_MAP.get(modemIndex);
            String logFilePath = mtkLogPath + modemLogFolder;
            Utils.logv(TAG, "Modem [" + modemIndex + "] root folder=" + logFilePath);
            File logFile = new File(logFilePath);
            if (null != logFile && logFile.exists()) {
                mdLogPathSets.add(logFilePath);
            }
        }

        String mdParentPath = "";
        for (String mdLogPath : mdLogPathSets) {
            mdParentPath += mdLogPath + ";";
        }
        if (mdParentPath.endsWith(";")) {
            // Remove ";" from mdParentPath
            mdParentPath = mdParentPath.substring(0, mdParentPath.length() - 1);
        }
        Utils.logi(TAG, "getSavingParentPath() mdParentPath = " + mdParentPath);
        return mdParentPath;
    }

    private void waitPollingDone() {
        String currentRunningStatus;
        try {
            currentRunningStatus = MTKLoggerServiceManager.getInstance().getService()
                    .getValueFromNative(Utils.LOG_TYPE_MODEM, MultiModemLog.COMMAND_GET_STATUS);
        } catch (ServiceNullException e1) {
            currentRunningStatus = "";
        }
        String pollingValue = "2";
        String flushingVaue = "3";
        boolean isModemLogReady = !pollingValue.equals(currentRunningStatus)
                               && !flushingVaue.equals(currentRunningStatus);
        int i = 0; // For printout log every 10s
        while (!isModemLogReady && i < 600) { // Wait at most 10min
            try {
                Thread.sleep(WAIT_MODEM_INTENT);
                i++;
                if (i % 5 == 0) {
                    Utils.logd(TAG, "Modem Log is not Ready , wait for 5s");
                }
                try {
                    currentRunningStatus = MTKLoggerServiceManager.getInstance().getService()
                           .getValueFromNative(Utils.LOG_TYPE_MODEM,
                                   MultiModemLog.COMMAND_GET_STATUS);
                } catch (ServiceNullException e) {
                    currentRunningStatus = "";
                }
                isModemLogReady = !pollingValue.equals(currentRunningStatus)
                               && !flushingVaue.equals(currentRunningStatus);;
            } catch (InterruptedException e) {
                Utils.loge(TAG, "Catch InterruptedException");
            }
        }
        if (i >= 600) {
            Utils.loge(TAG, " Modem dump cost too much time, currentRunningStage = "
                           + currentRunningStatus);
        }
    }
    private String getEEPath() {
        String eePath = "";
        eePath = getModemLogPath(mTagLogInformation.getExpTime());
        String[] paths = eePath.split(";");
        for (String logPath : paths) {
            if (logPath.contains(Utils.C2K_MODEM_LOG_PATH)) {
                Utils.logd(TAG, "c2k log need contain normal log.");
                continue;
            }
            if (!logPath.contains("_EE")) {
                String newPath = "";
                newPath = copyDumpFiles(logPath);
                if (!newPath.isEmpty()) {
                    eePath = eePath.replace(logPath, newPath);
                    continue;
                }
                File fileTree = new File(new File(logPath).getParent()
                        + File.separator + Utils.LOG_TREE_FILE);
                String nextFolderPath = Utils.getNextLogFolderFromFileTree(fileTree, logPath);
                if (nextFolderPath == null || nextFolderPath.isEmpty()) {
                    eePath = eePath.replace(logPath, "");
                    continue;
                }
                String[] nextFolders = nextFolderPath.split(";");
                for (String nextFolder : nextFolders) {
                    if (nextFolder.contains("_EE")) {
                        newPath = nextFolder;
                        break;
                    } else {
                        newPath = copyDumpFiles(nextFolder);
                        if (!newPath.isEmpty()) {
                            break;
                        }
                    }
                }
                Utils.logd(TAG, "newPath = " + newPath);
                eePath = eePath.replace(logPath, newPath);
            }
        }
        if (eePath.endsWith(";")) {
            eePath = eePath.substring(0, eePath.length() - 1);
        }
        return eePath;
    }
    private String getFlushPath() {
        boolean isFlushTimeout = false;
        try {
            MTKLoggerServiceManager.getInstance().getService()
                    .sentMessageToLog(Utils.LOG_TYPE_MODEM,
                            MultiModemLog.MSG_NOTIFY_LOG_FLUSH_START, "");
        } catch (ServiceNullException e1) {
            return "";
        }
        String flushModemLogPath = mSharedPreferences.getString(
                Utils.KEY_MODEM_LOG_FLUSH_PATH, "");
        boolean isModemLogReady =
                (!TextUtils.isEmpty(flushModemLogPath) && !Utils.FLAG_MDLOGGER_FLUSHING
                        .equals(flushModemLogPath));
        int i = 0; // For printout log every 10s
        while (!isModemLogReady && i < 600) { // Wait at most 10min
            try {
                String eeModemLogPath =
                        mSharedPreferences.getString(Utils.KEY_MODEM_EXCEPTION_PATH, "");
                boolean isEEComes = !eeModemLogPath.isEmpty();
                if (isEEComes) {
                    Utils.logw(TAG, "During log flushing, EE happend!");
                    mSharedPreferences.edit().putString(Utils.KEY_MODEM_LOG_FLUSH_PATH, "")
                            .apply();
                    return getModemLogPath(mTagLogInformation.getExpTime());
                }
                Thread.sleep(WAIT_MODEM_INTENT);
                i++;
                if (i % 5 == 0) {
                    Utils.logd(TAG, "Flush Modem Log is not Ready , wait for 5s");
                }
                flushModemLogPath =
                        mSharedPreferences.getString(Utils.KEY_MODEM_LOG_FLUSH_PATH, "");
                isModemLogReady =
                        (!TextUtils.isEmpty(flushModemLogPath) && !Utils.FLAG_MDLOGGER_FLUSHING
                                .equals(flushModemLogPath));
                // Wait modem log flush for 180s
                if (i >= 180 && TextUtils.isEmpty(flushModemLogPath)) {
                    Utils.logw(TAG, "Wait 180s but still no modem log flush begin information,"
                            + " treat it as timeout log flush!");
                    isFlushTimeout = true;
                    break;
                }
            } catch (InterruptedException e) {
                Utils.loge(TAG, "Catch InterruptedException");
            }
        }
        if (i >= 600) {
            Utils.loge(TAG, " Modem log flush cost too much time, flushModemLogPath = "
                    + flushModemLogPath);
            isFlushTimeout = true;
        }
        mSharedPreferences.edit().putString(Utils.KEY_MODEM_LOG_FLUSH_PATH, "").apply();
        Utils.logi(TAG, "MODEM_LOG_FLUSH_PATH : " + flushModemLogPath + ", mIsTimeoutFlushLog="
                + isFlushTimeout);
        return flushModemLogPath;
    }

    /**
     * @param savingLogPath String
     * @return String
     */
    public String doFilter(String savingLogPath) {
        Utils.logi(TAG, "doFilter for " + savingLogPath);
        String newLogPath = savingLogPath;
        String[] modemLogPaths = savingLogPath.split(";");
        for (String modemLogPath : modemLogPaths) {
            List<File> needRenameList = getNeedRenameLogList(modemLogPath);
            if (needRenameList == null || needRenameList.size() < 1) {
                continue;
            }
            doRename(needRenameList);
        }
        return newLogPath;
    }

    /**
     * @param mdlogPath
     *            String
     * @return List<File>
     */
    private List<File> getNeedRenameLogList(String mdlogPath) {
        File modemLogFile = new File(mdlogPath);
        if (!modemLogFile.exists()) {
            Utils.logi(TAG, "mdlog not exist :" + mdlogPath);
            return null;
        }
        List<String> logInFileTreeList = (Utils.getLogFolderFromFileTree(new File(
                modemLogFile.getAbsolutePath() + File.separator + Utils.LOG_TREE_FILE)));
        if (logInFileTreeList == null || logInFileTreeList.size() < 1) {
            Utils.logi(TAG, "getLogFolderFromFileTree = null or size < 1" );
            return null;
        }
        String[] modemLogStrArray = logInFileTreeList.get(0).split("\\.");
        Utils.logi(TAG, "modemLogStrArray.length = " + modemLogStrArray.length);
        if (modemLogStrArray.length < 3) {
            return null;
        }
        List<String> logFileExistInTree = new ArrayList<String>();
        String treeSuffix = modemLogStrArray[modemLogStrArray.length - 1];
        for (String filePath : logInFileTreeList) {
            filePath = filePath.replace("." + treeSuffix, "");
            String[] logPathStrArray = filePath.split("\\/");
            String newPath = mdlogPath + File.separator +
                          logPathStrArray[logPathStrArray.length - 1];
            if (new File(newPath).exists()) {
                logFileExistInTree.add(newPath);
            }
        }
        Utils.logi(TAG, "new file in tree = " + logFileExistInTree.toString());

        if (logFileExistInTree.size() < sMaxZipNormalLogNum) {
            Utils.logi(TAG, "return. file in tree < " + sMaxZipNormalLogNum);
            return null;
        }
        List<File> needRenameLog = new ArrayList<File>();
        for (int i = 0; i <= logFileExistInTree.size() - sMaxZipNormalLogNum; i++) {
            Utils.logd(TAG, "need rename file: " + logFileExistInTree.get(i));
            needRenameLog.add(new File(logFileExistInTree.get(i)));
        }
        return needRenameLog;
    }
    private void doRename(List<File> fileList) {
        if (fileList == null || fileList.size() < 1) {
            return;
        }
        String mdlogFolder = new File(fileList.get(0).getParent()).getName();
        String newFolderName = mTagLogInformation.getTaglogTargetFolder() + File.separator
                               + mdlogFolder + MODEM_LOG_NO_NEED_ZIP;
        Utils.logi(TAG, "mdlog no need zip file rename to " + newFolderName);
        File newFolder = new File(newFolderName);
        if (!newFolder.exists()) {
            newFolder.mkdir();
        }
        for (File file : fileList) {
            file.renameTo(new File(newFolder + File.separator + file.getName()));
        }
    }

    private String getC2kLogPath(String beforTime) {
        String c2kLogPath = "";
        String mtkLogPath = Utils.geMtkLogPath();
        String c2kLogParentPath = mtkLogPath + Utils.C2K_MODEM_LOG_PATH;
        File logFile = new File(c2kLogParentPath);
        if (null != logFile && logFile.exists()) {
            File fileTree = new File(c2kLogParentPath + File.separator
                    + Utils.LOG_TREE_FILE);
            String logFolderPath = Utils.getLogFolderFromFileTree(fileTree, beforTime);
            if (logFolderPath != null && !logFolderPath.isEmpty()) {
                c2kLogPath += logFolderPath + ";";
            }
        }
        if (mIsModemEE) {
            String md3DumpPath = "";
            String md3ParentLogPath = mtkLogPath + Utils.MODEM_INDEX_FOLDER_MAP.get(
                                      Utils.MODEM_LOG_K2_INDEX + Utils.C2KLOGGER_INDEX);
            Utils.logv(TAG, "Modem [" + 3 + "] root folder=" + md3ParentLogPath);
            File parentFile = new File(md3ParentLogPath);
            File[] listFiles = parentFile.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                md3DumpPath = listFiles[listFiles.length - 1].getAbsolutePath();
                Utils.logi(TAG, "c2k dump path :" + md3DumpPath);
            }
            if (!md3DumpPath.isEmpty()) {
                c2kLogPath += md3DumpPath;
            }
        }
        if (c2kLogPath.endsWith(";")) {
            c2kLogPath = c2kLogPath.substring(0, c2kLogPath.length() - 1);
        }
        Utils.logi(TAG, "c2k Log Path :" + c2kLogPath);
        return c2kLogPath;
    }
}
