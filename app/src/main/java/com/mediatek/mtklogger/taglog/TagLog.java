package com.mediatek.mtklogger.taglog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.taglog.TagLogUtils.LogInfoTreatmentEnum;
import com.mediatek.mtklogger.taglog.db.DBManager;
import com.mediatek.mtklogger.taglog.db.FileInfoTable;
import com.mediatek.mtklogger.taglog.db.MySQLiteHelper;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public abstract class TagLog {
    protected static final String TAG = TagLogUtils.TAGLOG_TAG + "/TagLog";

    protected Context mContext;
    protected SharedPreferences mSharedPreferences;
    protected SharedPreferences mDefaultSharedPreferences;
    protected TagLogInformation mTaglogInformation;
    protected Intent mInputIntent = null;

    protected LogTManager mLogManager;
    protected List<LogInformation> mNeededTaglogFileList = new ArrayList<LogInformation>();
    protected String mTaglogFolder = "";
    protected TagLogData mTaglogData = null;

    protected Handler mTaglogManagerHandler;

    protected StatusBarNotify mStatusBarNotify = null;

    protected long mTaglogId = -1;
    protected String mFileListStr = "";

    /**
     * @param taglogManagerHandler
     *            Handler
     */
    public TagLog(Handler taglogManagerHandler) {
        this.mTaglogManagerHandler = taglogManagerHandler;
    }

    /**
     * @return Intent
     */
    public Intent getInputIntent() {
        return mInputIntent;
    }

    /**
     * @param intent
     *            Intent
     */
    public void beginTag(Intent intent) {
        Utils.logi(TAG, "-->beginTag");
        mInputIntent = intent;
        startTaglogThread();
    }
    /**
     * @param data
     *            TagLogData
     */
    public void resumeTag(final TagLogData data) {
        Utils.logi(TAG, "-->resumeTag");
        mTaglogData = data;
        startTaglogThread();
    }

    private void startTaglogThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!doInit()) {
                    deInit();
                    Utils.loge(TAG, "doInit() failed!");
                    return;
                }
                createTaglogFolder();
                prepareNeededLogFiles();
                doTag();
                notifyTaglogSize();
                doFilesManager();
                reportTaglogResult(true);
                deInit();
            }
        }).start();
    }

    private boolean doInit() {
        Utils.logi(TAG, "-->doInit() for Intent");
        mContext = MyApplication.getInstance();
        mSharedPreferences = MyApplication.getInstance().getSharedPreferences();
        mDefaultSharedPreferences = MyApplication.getInstance().getDefaultSharedPreferences();

        if (mTaglogData != null) {
            mTaglogInformation = new TagLogInformation(mTaglogData);
        } else {
            mTaglogInformation = new TagLogInformation(mInputIntent);
        }
        if (!mTaglogInformation.isAvailable()) {
            Utils.loge(TAG, "The intent for taglog is error, just return!");
            return false;
        }
        mLogManager = new LogTManager(this);
        if (mTaglogData != null) {
            mTaglogId = mTaglogData.getTaglogTable().getTagLogId();
        } else {
            mTaglogId = DBManager.getInstance().insertTaglogToDb(mTaglogInformation);
        }
        return true;
    }

    protected void checkFileState() {
        // 1. get file state 2.update state bar
        int startTotalFileCount = startNotificationBar();
        if (startTotalFileCount == 0) {
            return;
        }
        String[] fileIds = mFileListStr.split(",");
        Utils.logi(TAG, "CheckFileState, mFileListStr = " + mFileListStr);

        long timeOut = 20 * 60 * 1000;
        while (true) {
            // For update progress bar
            int totalFileCount = 0;
            // For log file treatment status
            boolean isAllDone = true;
            for (String strFileId : fileIds) {
                if (strFileId == null || strFileId.isEmpty()) {
                    continue;
                }
                long fileId = Long.valueOf(strFileId);
                FileInfoTable fileInfo = DBManager.getInstance().getFileInfoById(fileId);
                if (fileInfo == null) {
                    continue;
                }
                // update progress
                totalFileCount += fileInfo.getFileProgress();

                if (!MySQLiteHelper.FILEINFO_STATE_DONE.equals(fileInfo.getState())) {
                    isAllDone = false;
                }
            }
            Utils.logd(TAG, "CheckFileState, totalFileCount = " + totalFileCount
                    + "isAllDone = " + isAllDone);
            mStatusBarNotify.updateState(TagLogUtils.MSG_ZIPPED_FILE_COUNT, totalFileCount);
            if (isAllDone) {
                Utils.logi(TAG, "CheckFileState, break for isAllDone = " + isAllDone);
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeOut -= 1000;
            if (timeOut <= 0) {
                Utils.logi(TAG, "CheckFileState, break for timeOut = " + timeOut);
                break;
            }
        }
        stopNotificationBar(startTotalFileCount);
    }

    protected void createTaglogFolder() {
        if (mTaglogData != null) {
            String taglogFolder = mTaglogData.getTaglogTable().getTargetFolder();
            File tagLogFolder = new File(taglogFolder);
            if (!tagLogFolder.exists()) {
                tagLogFolder.mkdirs();
            }
            Utils.logi(TAG, "createTagLogFolder : " + taglogFolder);
            mTaglogFolder = taglogFolder;
            return;
        }
        String logPath = mTaglogInformation.getTaglogTargetFolder();
        File tagLogFolder = new File(logPath);
        if (!tagLogFolder.exists()) {
            tagLogFolder.mkdirs();
        } else {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String type = mTaglogInformation.getTaglogType();
            logPath = getTaglogParentPath() + TagLogUtils.TAGLOG_TEMP_FOLDER_PREFIX + "TagLog_"
                      + TagLogUtils.getCurrentTimeString()
                      + ((type == null || "".equals(type)) ? "" : ("_" + type));
            tagLogFolder = new File(logPath);
            tagLogFolder.mkdirs();
            mTaglogInformation.setTaglogTargetFolder(logPath);
            DBManager.getInstance().updateTaglogFolder(mTaglogId, logPath);
        }
        Utils.logi(TAG, "createTagLogFolder : " + logPath);
        mTaglogFolder = tagLogFolder.getAbsolutePath();
    }

    protected void prepareNeededLogFiles() {
        Utils.logi(TAG, "-->getNeededTagLogFiles()");
        if (mTaglogData != null) {
            String fileListStr = mTaglogData.getTaglogTable().getFileList();
            if (!fileListStr.isEmpty()) {
                List<LogInformation> logInfoList =
                        DBManager.getInstance().getFileInfoByIds(fileListStr);
                if (logInfoList != null && logInfoList.size() > 0) {
                    Utils.logi(TAG, "-->getNeededTagLogFiles() from db");
                    mFileListStr = fileListStr;
                    mNeededTaglogFileList = logInfoList;
                    return;
                }
            }
        }
        synchronized (TagLog.class) {
            List<LogInformation> logInfoList = new ArrayList<LogInformation>();
            if (mTaglogInformation.isNeedAllLogs()) {
                logInfoList = mLogManager.getSavingLogParentInformation();
            } else {
                logInfoList = mLogManager.getSavingLogInformation();
            }

            // Add aee & sop files to taglog folder
            if (mTaglogInformation.isFromException()) {
                logInfoList.add(new LogInformation(TagLogUtils.LOG_TYPE_AEE, new File(
                        mTaglogInformation.getExpPath()), LogInfoTreatmentEnum.COPY));
            }
            logInfoList
                    .add(new LogInformation(TagLogUtils.LOG_TYPE_SOP, createSopFile()));
            setTargetForLogFiles(logInfoList);

            mFileListStr = DBManager.getInstance().insertFileInfoToDb(logInfoList);
            DBManager.getInstance().updateTaglogFileList(mTaglogId, mFileListStr);
            Utils.logi(TAG, "<--getNeededTagLogFiles() done");

            mNeededTaglogFileList = logInfoList;
        }
    }

    protected String getTaglogParentPath() {
        return Utils.geMtkLogPath() + "/" + TagLogUtils.TAGLOG_FOLDER_NAME + "/";
    }

    protected void doTag() {
        synchronized (TagLog.class) {
            if (mTaglogInformation.isNeedAllLogs()) {
                mLogManager.stopLogs();
            } else {
                mLogManager.restartLogs();
            }
        }

        for (LogInformation logInformation : mNeededTaglogFileList) {
            File neededLogFolder = new File(logInformation.getFileInfo().getOriginalPath());
            String targetLogFileName = mTaglogFolder + File.separator + neededLogFolder.getName();
            if (!logInformation.isNeedTag()) {
                Utils.logi(TAG, "no need do tag for " + neededLogFolder);
                continue;
            }
            Utils.logi(TAG, "Tag Logs from " + neededLogFolder.getAbsolutePath() + " to "
                    + targetLogFileName);
            File newLogFolder = new File(targetLogFileName);
            if (!neededLogFolder.renameTo(newLogFolder)) {
                Utils.logw(TAG, "Tag Logs from " + neededLogFolder.getAbsolutePath() + " to "
                        + targetLogFileName + " is failed!");
            }
        }
        DBManager.getInstance().updateTaglogState(mTaglogId,
                                      MySQLiteHelper.TAGLOG_STATE_DOTAG);
        synchronized (TagLog.class) {
            if (mTaglogInformation.isNeedAllLogs()) {
                mLogManager.startLogs();
            }
        }
    }

    protected void notifyTaglogSize() {
        long totalTaglogSize = 0;
        for (LogInformation logInfo : mNeededTaglogFileList) {
            logInfo.calculateLogFiles();
            totalTaglogSize += logInfo.getLogSize();
        }
        Intent taglogSizeIntent = new Intent();
        taglogSizeIntent.setAction(Utils.ACTION_TAGLOG_SIZE);
        taglogSizeIntent.putExtra(Utils.BROADCAST_KEY_LOG_SIZE, totalTaglogSize);
        if (mInputIntent != null) {
            taglogSizeIntent.putExtras(mInputIntent);
        }
        Utils.logi(TAG, "sent out broadcast : com.mediatek.syslogger.taglog.size with "
                + "compress_size = " + totalTaglogSize);
        Utils.sendBroadCast(taglogSizeIntent);
    }

    protected void doFilesManager() {
        Utils.logi(TAG, "-->doFilesManager");
        DBManager.getInstance().changeFileStateToWaiting(mFileListStr);

        Utils.logd(TAG, "insert taglogid = " + mTaglogId + ", filelist = " + mFileListStr);
        mTaglogManagerHandler.obtainMessage(TagLogUtils.MSG_DO_FILEMANAGER, null).sendToTarget();

        checkFileState();
        removeTempFromTalogName();
    }

    protected void removeTempFromTalogName() {
        // Remove "Temp_" from taglog folder name
        while (!DBManager.getInstance().isFileInDependence(mTaglogFolder)) {
            try {
                Utils.logw(TAG, "cannot remove tmg, wait 1s");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        String newTaglogFolder = mTaglogFolder.replace(TagLogUtils.TAGLOG_TEMP_FOLDER_PREFIX, "");
        int renameTimeout = 0;
        while (!new File(mTaglogFolder).renameTo(new File(newTaglogFolder))) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            renameTimeout += 100;
            if (renameTimeout >= 5000) {
                Utils.logw(TAG, mTaglogFolder + " rename to " + newTaglogFolder + " is failed!");
                break;
            }
        }
        if (renameTimeout < 5000) {
            mTaglogFolder = newTaglogFolder;
        }
    }

    private File createSopFile() {
        Utils.logi(TAG, "-->createSopFile");
        File checkSopFile =
                new File(new File(mTaglogFolder).getAbsolutePath() + File.separator
                        + "checksop.txt");
        if (checkSopFile != null) {
            try {
                if (!checkSopFile.exists()) {
                    checkSopFile.createNewFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            TagLogUtils.writeStringToFile(getSOPContent().toString(), checkSopFile);
        }
        return checkSopFile;
    }

    protected StringBuffer getSOPContent() {
        StringBuffer content = new StringBuffer("Check SOP result:\n");
        SharedPreferences preferences =
                MyApplication.getInstance().getSharedPreferences("calibration_data",
                        Context.MODE_PRIVATE);
        boolean isCalibrated = preferences.getBoolean("calibrationData", false);
        String calibration = new String("Calibration data is downloaded: " + isCalibrated + "\n");

        String keyIVSR = "ivsr_setting";
        long checked =
                Settings.System.getLong(MyApplication.getInstance().getContentResolver(), keyIVSR,
                        0);
        String ivsr = "The IVSR is " + String.valueOf(checked) + "\n";
        Utils.logd(TAG, "IVSR enable status: " + ivsr);

        String buildNumberStr = SystemProperties.get("ro.build.display.id");
        String buildProduct = SystemProperties.get("ro.build.product");
        String basebandVersion = SystemProperties.get("gsm.version.baseband");
        StringBuilder buildInfo = new StringBuilder("===Build Version Information===");
        buildInfo.append("\nBuild Number: " + buildNumberStr)
                .append("\nThe production is " + buildProduct)
                .append(" with " + Utils.BUILD_TYPE + " build")
                .append("\nAnd the baseband version is " + basebandVersion + "\n");
        Utils.logd(TAG, "Build number is " + buildInfo.toString());

        content.append(calibration).append(ivsr).append(buildInfo.toString());
        //for Customer1
        String buildDisplayId = "version: " + Build.DISPLAY + "\n";
        String buildId = "buildid: " + SystemProperties.get("ro.build.vendor_sw_ver")
                          + "\n";
        String buildType = "buildtype: " + Utils.BUILD_TYPE + "\n";
        content.append(buildDisplayId).append(buildId).append(buildType);
        return content;
    }

    protected void setTargetForLogFiles(List<LogInformation> logInfoList) {
        Utils.logi(TAG, "-->setTargetForLogFiles");
        for (LogInformation logInfo : logInfoList) {
            logInfo.setTargetTagFolder(mTaglogFolder);
            if (logInfo.getLogType() == Utils.LOG_TYPE_MODEM && mTaglogInformation.isNeedZip()) {
                logInfo.setTargetFileName(TagLogUtils.ZIP_MODEMLOG_NAME);
            }
        }
    }

    protected void reportTaglogResult(boolean isSuccessful) {
        Utils.logi(TAG, "-->reportTaglogResult(), isSuccessful = " + isSuccessful);
        Intent intent = new Intent();
        intent.setAction(Utils.ACTION_TAGLOG_TO_LOG2SERVER);
        intent.putExtra(Utils.KEY_NOTIFY_LOG2SERVER_REASON, Utils.NOTIFY_LOG2SERVER_REASON_DONE);
        intent.putExtra(Utils.BROADCAST_KEY_TAGLOG_RESULT, isSuccessful
                ? Utils.BROADCAST_VAL_TAGLOG_SUCCESS : Utils.BROADCAST_VAL_TAGLOG_FAILED);

        String taglogFolder = mTaglogFolder;
        intent.putExtra(Utils.BROADCAST_KEY_TAGLOG_PATH, taglogFolder);
        Utils.logi(TAG, "Broadcast out : taglogFolder = " + taglogFolder);
        String dbPathInTaglog = "";
        for (LogInformation logInformation : mNeededTaglogFileList) {
            int logType = logInformation.getLogType();
            if (logType == TagLogUtils.LOG_TYPE_AEE) {
                dbPathInTaglog = taglogFolder + File.separator
                        + logInformation.getLogFile().getName() + File.separator;
                continue;
            }
            String logPathResultKey = TagLogUtils.LOGPATH_RESULT_KEY.get(logType);
            if (logPathResultKey == null) {
                continue;
            }
            String logPath = taglogFolder + File.separator + logInformation.getTargetFileName();
            intent.putExtra(logPathResultKey, logPath);
            Utils.logi(TAG, logPathResultKey + " = " + logPath);
        }

        // Fill the input intent to result
        if (mInputIntent != null) {
            intent.putExtras(mInputIntent);
        } else {
            Utils.logw(TAG, "Data From AEE is null, maybe this is a resume progress");
            for (LogInformation logInformation : mNeededTaglogFileList) {
                int logType = logInformation.getLogType();
                if (logType == TagLogUtils.LOG_TYPE_AEE) {
                    intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, taglogFolder + File.separator
                            + logInformation.getLogFile().getName() + File.separator);
                    intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, logInformation.getLogFile().getName()
                            + ".dbg");
                    intent.putExtra(Utils.EXTRA_KEY_EXP_ZZ, Utils.EXTRA_VALUE_EXP_ZZ);
                }
            }
        }
        if (!dbPathInTaglog.isEmpty()) {
            intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, dbPathInTaglog);
        }
        Utils.logd(TAG, "expPath = " + intent.getStringExtra(Utils.EXTRA_KEY_EXP_PATH));
        Utils.sendBroadCast(intent);

        Utils.logd(TAG, "ReportTaglogResult done!");
    }

    protected void deInit() {
        Utils.logi(TAG, "deInit()");
        mTaglogManagerHandler.obtainMessage(TagLogUtils.MSG_TAGLOG_DONE, this).sendToTarget();
        DBManager.getInstance().updateTaglogState(mTaglogId, MySQLiteHelper.TAGLOG_STATE_DONE);
    }

    protected int startNotificationBar() {
        Utils.logd(TAG, "-->treatLogFilesStart()");
        if (null == mNeededTaglogFileList ||
            mNeededTaglogFileList.size() < 1) {
            mNeededTaglogFileList = DBManager.getInstance().getFileInfoByIds(mFileListStr);
            Utils.logw(TAG, "-->treatLogFilesStart() get fielList from DB");
        }

        int totalFileCount = 0;
        boolean showNotificationBar = false;
        for (LogInformation logInfo : mNeededTaglogFileList) {
            totalFileCount += logInfo.getFileInfo().getFileCount();
            if (logInfo.getTreatMent() != LogInfoTreatmentEnum.DO_NOTHING) {
                showNotificationBar = true;
            }
        }
        if (!showNotificationBar) {
            Utils.logd(TAG, "no need startNotificationBar!");
            return 0;
        }
        if (mStatusBarNotify == null) {
            mStatusBarNotify = new StatusBarNotify(
                     getTargetFile(), (int) mTaglogId);
        }
        Utils.logd(TAG, "Total file count = " + totalFileCount);
        mStatusBarNotify.updateState(TagLogUtils.MSG_ZIP_FILE_TOTAL_COUNT,
                         totalFileCount);
        return totalFileCount;
    }

    protected void stopNotificationBar(int startTotalFileCount) {
        Utils.logd(TAG, "stopNotificationBar, startTotalFileCount = " + startTotalFileCount);
        mStatusBarNotify.updateState(TagLogUtils.MSG_ZIPPED_FILE_COUNT, startTotalFileCount);
    }

    public String getTaglogFolder() {
        return mTaglogFolder;
    }

    public void setTaglogFolder(String taglogFolder) {
        this.mTaglogFolder = taglogFolder;
    }

    protected String getTargetFile() {
        return new File(mTaglogFolder).getName();
    }
    public TagLogInformation getTaglogInformation() {
        return mTaglogInformation;
    }

    public Handler getTaglogManagerHandler() {
        return mTaglogManagerHandler;
    }
}
