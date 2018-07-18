package com.mediatek.mtklogger.taglog;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.settings.ModemLogSettings;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author MTK81255
 *
 */
public class TagLogInformation {
    protected static final String TAG = TagLogUtils.TAGLOG_TAG + "/TagLogIntentParser";
    private SharedPreferences mDefaultSharedPreferences;

    private boolean mIsAvailable = false;
    // The taglog is start from exception if true
    private boolean mIsFromException = false;
    // The path of the dbg folder
    private String mExpPath = "";
    // Do zip logs if true
    private boolean mIsNeedZip = true;
    // Tag all logs if true : mtklog/mobilelog
    // Only tag current running logs if false : mtklog/mobilelog/APLog_****
    private boolean mIsNeedAllLogs = false;
    // Is the taglog from reboot exception
    private boolean mIsFromReboot = false;
    // The value is exception type, but if is not exception, it will be the
    // input value.
    private String mTaglogType = "";
    // Which type log needed to do tag, like : 23 = 1 + 2 + 4 + 16
    // The default value 0 means all
    private int mNeedLogType = 0;
    private String mTaglogTargetFolder = "";

    private String mDbFileName = "";
    private String mZzFileName = "";
    private String mReason = "";
    private String mFromWhere = "";
    private String mExpTime = "";
    private String mZzInternalTime = "";

    public int getNeedLogType() {
        return mNeedLogType;
    }

    public void setNeedLogType(int needLogType) {
        this.mNeedLogType = needLogType;
    }

    /**
     * @param intent Intent
     */
    public TagLogInformation(Intent intent) {
        mDefaultSharedPreferences = MyApplication.getInstance().getDefaultSharedPreferences();
        doParser(intent);
    }
    /**
     * @param data TagLogData
     */
    public TagLogInformation(TagLogData data) {
        mExpPath = data.getTaglogTable().getDBPath();
        mReason = data.getTaglogTable().getReason();
        mFromWhere = data.getTaglogTable().getFromWhere();

        if (mExpPath == null || mExpPath.isEmpty()) {
            Utils.logi(TAG, "mExpPath = null or empty, just return!");
            return;
        }
        mZzFileName = data.getTaglogTable().getmDBZZFileName();
        if (mZzFileName.isEmpty()) {
            mZzFileName = Utils.EXTRA_VALUE_EXP_ZZ;
        }
        mTaglogTargetFolder = data.getTaglogTable().getTargetFolder();

        if (Utils.MANUAL_SAVE_LOG.equalsIgnoreCase(mExpPath)) {
            mIsFromException = false;
            mTaglogType = data.getTaglogTable().getDBFileName();
            Pattern mLogFolderNamePattern = Pattern
                    .compile("\\w*_(\\d{4}_\\d{4}_\\d{6})(\\w)*");
            Matcher matcher = mLogFolderNamePattern.matcher(
                    new File(mTaglogTargetFolder).getName());
            if (matcher.matches()) {
                mExpTime = matcher.group(1);
            }
        } else {
            mIsFromException = true;
            File expFile = new File(mExpPath);
            if (expFile.exists()) {
                String expFileName = expFile.getName();
                int index = expFileName.lastIndexOf(".");
                if (index != -1 && index < expFileName.length()) {
                    mTaglogType = expFileName.substring(index + 1);
                }
            }
            Utils.logi(TAG, "mTaglogType = " + mTaglogType);

            mZzInternalTime = data.getTaglogTable().getZzInternalTime();
            mExpTime = getTriggerTime(mZzInternalTime);
            Utils.logi(TAG, "mExpTime = " + mExpTime);
        }

        mNeedLogType = data.getTaglogTable().getNeedLogType();
        mIsNeedZip = data.getTaglogTable().isNeedZip().equals("1") ? true : false;
        mIsNeedAllLogs = data.getTaglogTable().isNeedAllLogs().equals("1") ? true : false;
        mDbFileName = data.getTaglogTable().getDBFileName();

        mIsAvailable = true;
        Utils.logd(TAG, "doParser() for taglogdata done!" + " mIsAvailable = " + mIsAvailable
                + ", mExpPath = " + mExpPath + ", mIsFromException = " + mIsFromException
                + ", mTaglogType = " + mTaglogType + ", mIsNeedZip = " + mIsNeedZip
                + ", mIsNeedAllLogs = " + mIsNeedAllLogs
                + ", mTaglogTargetFolder = " + mTaglogTargetFolder + ", mExpTime = " + mExpTime);
    }

    private void doParser(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Utils.loge(TAG, "extras == null, just return!");
            return;
        }

        mExpPath = extras.getString(Utils.EXTRA_KEY_EXP_PATH);
        if (mExpPath == null) {
            // Check is from abnormal monitor
            String reason = extras.getString(Utils.EXTRA_KEY_EXP_REASON);
            if (reason != null && Utils.EXTRA_VALUE_EXP_REASON.equalsIgnoreCase(reason)) {
                Utils.logd(TAG, "EXTRA_VALUE_EXP_REASON = " + reason);
                // Just do log output for fromWhere
                String fromWhere = extras.getString(Utils.EXTRA_KEY_EXP_FROM_WHERE);
                Utils.logd(TAG, "fromWhere = " + fromWhere);
                mReason = reason;
                mFromWhere = fromWhere;
                if (Utils.MODEM_MODE_PLS.equals(mDefaultSharedPreferences.getString(
                        Utils.KEY_MD_MODE_1, Utils.MODEM_MODE_SD))
                        && mDefaultSharedPreferences.getBoolean(
                                ModemLogSettings.KEY_MD_MONITOR_MODEM_ABNORMAL_EVENT, false)) {
                    mExpPath = Utils.MANUAL_SAVE_LOG;
                }
            }
        }
        if (mExpPath == null) {
            Utils.loge(TAG, "params are not valid! exp_path is null");
            return;
        }

        mZzFileName = extras.getString(Utils.EXTRA_KEY_EXP_ZZ, "");
        if (mZzFileName.isEmpty()) {
            mZzFileName = Utils.EXTRA_VALUE_EXP_ZZ;
        }
        if (Utils.MANUAL_SAVE_LOG.equalsIgnoreCase(mExpPath)) {
            mIsFromException = false;
            mTaglogType = extras.getString(Utils.EXTRA_KEY_EXP_NAME, "");
            mExpTime = TagLogUtils.getCurrentTimeString();
        } else {
            mIsFromException = true;
            File expFile = new File(mExpPath);
            if (expFile.exists()) {
                String expFileName = expFile.getName();
                int index = expFileName.lastIndexOf(".");
                if (index != -1 && index < expFileName.length()) {
                    mTaglogType = expFileName.substring(index + 1);
                }
            }
            Utils.logi(TAG, "mTaglogType = " + mTaglogType);

            mZzInternalTime = extras.getString(Utils.EXTRA_KEY_EXP_TIME, "");
            mExpTime = getTriggerTime(mZzInternalTime);
            Utils.logi(TAG, "mExpTime = " + mExpTime);
            String isFromRebootStr = extras.getString(Utils.EXTRA_KEY_EXP_FROM_REBOOT);
            if (isFromRebootStr != null && Utils.EXTRA_VALUE_FROM_REBOOT.equals(isFromRebootStr)) {
                mIsFromReboot = true;
            } else if (mTaglogType.equalsIgnoreCase("KE") || mTaglogType.equalsIgnoreCase("HWT")
                    || mTaglogType.equalsIgnoreCase("HW_Reboot")) {
                Utils.logi(TAG, "mTaglogType == " + mTaglogType);
                mIsFromReboot = true;
            }
            Utils.logd(TAG, "mIsFromReboot ? " + mIsFromReboot);
        }

        mNeedLogType = extras.getInt(Utils.EXTRAL_KEY_IS_NEED_LOG_TYPE, 0);
        mIsNeedZip = extras.getBoolean(Utils.EXTRAL_KEY_IS_NEED_ZIP, true);
        mIsNeedAllLogs = extras.getBoolean(Utils.EXTRAL_KEY_IS_NEED_ALL_LOGS, false);
        mDbFileName = extras.getString(Utils.EXTRA_KEY_EXP_NAME, "");

        mIsAvailable = true;

        mTaglogTargetFolder = Utils.geMtkLogPath() + "/" + TagLogUtils.TAGLOG_FOLDER_NAME
                    + "/" + TagLogUtils.TAGLOG_TEMP_FOLDER_PREFIX + "TagLog_"
                    + TagLogUtils.getCurrentTimeString()
                    + ((mTaglogType == null || "".equals(mTaglogType)) ? "" : ("_" + mTaglogType));
        Utils.logd(TAG, "doParser() done!" + " mIsAvailable = " + mIsAvailable + ", mExpPath = "
                + mExpPath + ", mIsFromException = " + mIsFromException + ", mTaglogType = "
                + mTaglogType + ", mIsNeedZip = "
                + mIsNeedZip + ", mIsNeedAllLogs = " + mIsNeedAllLogs
                + ", mTaglogTargetFolder = " + mTaglogTargetFolder
                + ", mExpTime = " + mExpTime);
    }

    private String getTriggerTime(String zzTimeStr) {
        if (zzTimeStr == null || zzTimeStr.isEmpty()) {
            return "";
        }
        String triggerTime = "";
        String[] times = zzTimeStr.split("@");

        String timeFormat = "";
        triggerTime = times[0].trim();
        String[] timeStrs = triggerTime.split(" ");
        String timeZone = timeStrs[timeStrs.length - 2];
        triggerTime = triggerTime.replace(timeZone, "");
        timeFormat = "EEE MMM dd HH:mm:ss yyyy";

        if (times.length >= 2) {
            String triggerTime1 = times[1].trim();
            String timeFormat1 = "yyyy-MM-dd HH:mm:ss";
            long dblong = TagLogUtils.getCurrentTime(triggerTime, timeFormat);
            long triggerlong = TagLogUtils.getCurrentTime(triggerTime1, timeFormat1);
            if (Math.abs(dblong - triggerlong) < 60 * 60 * 1000) {
                triggerTime = triggerTime1;
                timeFormat = timeFormat1;
            }
        }
        return TagLogUtils.getCurrentTimeString(triggerTime, timeFormat);
    }
    public boolean isAvailable() {
        return mIsAvailable;
    }

    public void setAvailable(boolean isAvailable) {
        this.mIsAvailable = isAvailable;
    }

    public boolean isFromException() {
        return mIsFromException;
    }

    public void setFromException(boolean isFromException) {
        this.mIsFromException = isFromException;
    }

    public String getExpPath() {
        return mExpPath;
    }

    public void setExpPath(String expPath) {
        this.mExpPath = expPath;
    }

    public boolean isNeedZip() {
        return mIsNeedZip;
    }

    public void setNeedZip(boolean isNeedZip) {
        this.mIsNeedZip = isNeedZip;
    }

    public boolean isNeedAllLogs() {
        return mIsNeedAllLogs;
    }

    public void setNeedAllLogs(boolean isNeedAllLogs) {
        this.mIsNeedAllLogs = isNeedAllLogs;
    }

    public boolean isFromReboot() {
        return mIsFromReboot;
    }

    public void setFromReboot(boolean isFromReboot) {
        this.mIsFromReboot = isFromReboot;
    }

    public String getTaglogType() {
        return mTaglogType;
    }

    public void setTaglogType(String taglogType) {
        this.mTaglogType = taglogType;
    }

    public String getTaglogTargetFolder() {
        return mTaglogTargetFolder;
    }

    public void setTaglogTargetFolder(String taglogfolder) {
        this.mTaglogTargetFolder = taglogfolder;
    }

    public String getDbFileName() {
        return this.mDbFileName;
    }

    public String getZzFilename() {
        return this.mZzFileName;
    }

    public String getReason() {
        return this.mReason;
    }

    public String getFromWhere() {
        return this.mFromWhere;
    }

    public String getExpTime() {
        return this.mExpTime;
    }

    public String getZzInternalTime() {
        return this.mZzInternalTime;
    }
}
