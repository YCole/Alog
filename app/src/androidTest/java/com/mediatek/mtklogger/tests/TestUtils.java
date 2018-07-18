package com.mediatek.mtklogger.tests;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import com.mediatek.mtklogger.taglog.TagLogUtils;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author MTK81255
 *
 */
public class TestUtils {
    public static final String TAG = "MTKLogger/Test";

    private static final String TAG_MY = TAG + "/TestUtils";

    public static final String ACTION_CMD_LINE = "com.mediatek.mtklogger.ADB_CMD";

    public static final String EXTRA_CMD_TARGET = "cmd_target";

    public static final String EXTRA_CMD_NAME = "cmd_name";
    public static String sMockDbFolderPrefix = "/storage/sdcard0/mtklog/aee_exp/db.00/";
    public static final String MOCK_DB_FILE_NAME = "db.00.dbg";
    public static final String TAGLOG_FOLDER_PREFIX = "/mtklog/taglog";
    public static final String MOCK_ZZ_FILE_CONTENT =
            "Java (JE),2038,-1361051648,99,/data/core/,1,system_app_crash,"
                    + "com.android.development,Wed Jan  9 20:16:50 GMT 2013,1";
    public static final int COMMON_TIMEOUT = 15000;
    public static final int TAGLOG_TIMEOUT = 10 * 60 * 1000;

    /**
     * @param millsSec
     *            int
     */
    public static void sleep(int millsSec) {
        try {
            Thread.sleep(millsSec);
        } catch (InterruptedException e) {
            Utils.loge(TAG, "Sleep process been interrupted", e);
        }
    }

    /**
     * @param file
     *            File
     */
    public static final void deleteFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            file.delete();
            return;
        } else if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            if (subFiles == null) {
                return;
            }
            for (File f : subFiles) {
                deleteFile(f);
            }
            file.delete();
        }
    }

    /**
     * @param context Context
     * @param logType int
     * @param enable boolean
     * @return boolean
     */
    public static boolean changeLogRunningStatus(Context context, int logType, boolean enable) {
        Utils.logd(TAG_MY, "-->changeLogRunningStatus(), type=" + logType + ", enable=" + enable);
        boolean isSuccess = true;
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, logType);
        if (enable) {
            intent.putExtra(TestUtils.EXTRA_CMD_NAME, "start");
        } else {
            intent.putExtra(TestUtils.EXTRA_CMD_NAME, "stop");
        }
        context.sendBroadcast(intent);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int waitingTime = 0;
        boolean isDone = false;
        while (!isDone) {
            isDone = true;
            for (int type : Utils.LOG_TYPE_SET) {
                if ((type & logType) == 0) {
                    continue;
                }
                if (!TestUtils.isNeedCheckGPSLogStatus() && type == Utils.LOG_TYPE_GPS) {
                    continue;
                }
                isDone = (isTypeLogRunning(type) == enable);
                if (!isDone) {
                    break;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            waitingTime += 10;
            if (waitingTime >= COMMON_TIMEOUT) {
                Log.e(TAG, "changeLogRunningStatus is timeout in 15s!");
                isSuccess = false;
                break;
            }
        }
        return isSuccess;
    }

    /**
     * @return boolean
     */
    public static boolean isAnyLogRunning() {
        Log.d(TAG, "-->isAnyLogRunning()");
        boolean isLogRunning = false;
        for (int logType : Utils.LOG_TYPE_SET) {
            if (isTypeLogRunning(logType)) {
                isLogRunning = true;
                break;
            }
        }
        Log.d(TAG, "<--isAnyLogRunning(), isLogRunning = " + isLogRunning);
        return isLogRunning;
    }

    /**
     * @param logType
     *            int
     * @return boolean
     */
    public static boolean isTypeLogRunning(int logType) {
        // Log.d(TAG, "-->isTypeLogRunning(), logType = " + logType);
        boolean isLogRunning = false;
        String key = Utils.KEY_LOG_RUNNING_STATUS_IN_SYSPROP_MAP.get(logType);
        if (key != null) {
            String nativeStatus =
                    SystemProperties.get(key, Utils.VALUE_LOG_RUNNING_STATUS_IN_SYSPROP_OFF);
            isLogRunning = Utils.VALUE_LOG_RUNNING_STATUS_IN_SYSPROP_ON.equals(nativeStatus);
        }
        // Log.d(TAG, "<--isTypeLogRunning(), logType = " + logType +
        // ", isLogRunning = "
        // + isLogRunning);
        return isLogRunning;
    }

    /**
     * @param context Context
     * @param logType int
     * @param enable boolean
     */
    public static void setLogAutoStart(Context context, int logType, boolean enable) {
        Log.d(TAG, "-->setLogAutoStart(), logType = " + logType + ", enable = " + enable);
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, logType);
        if (enable) {
            intent.putExtra(TestUtils.EXTRA_CMD_NAME, "set_auto_start_1");
        } else {
            intent.putExtra(TestUtils.EXTRA_CMD_NAME, "set_auto_start_0");
        }
        context.sendBroadcast(intent);
    }

    // switch_modem_log_mode
    // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
    // switch_modem_log_mode_Mode --ei cmd_target Mdtype
    // Mode vaule is 1/2/3, 1:Usb 2: Sd 3:Pst
    // Mdtype vaule is 1/3, 1: md1 3:md3
    // Demo Set modem1 to Pst mode:
    // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
    // switch_modem_log_mode_3 --ei cmd_target 1
    /**
     * @param context Context
     * @param modemMode String
     * @param modemType int
     */
    public static void switchModemMode(Context context, String modemMode, int modemType) {
        Log.d(TAG, "-->switchModemMode(), modemMode = " + modemMode + ", modemType = " + modemType);
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, modemType);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_modem_log_mode_" + modemMode);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
    // switch_modem_log_mode --ei cmd_target 2
    /**
     * @param context Context
     * @param modemMode String
     */
    public static void switchAllModemMode(Context context, String modemMode) {
        Log.d(TAG, "-->switchAllModemMode(), modemMode = " + modemMode);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int modemModeType = 2;
        try {
            modemModeType = Integer.parseInt(modemMode);
        } catch (NumberFormatException e) {
            modemModeType = 2;
        }
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, modemModeType);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_modem_log_mode");
        context.sendBroadcast(intent);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return boolean
     */
    public static boolean isAllLogRunning() {
        boolean isRunning = true;
        for (int type : Utils.LOG_TYPE_SET) {
            if (getLogRunningStatusFromNative(type) != 1) {
                isRunning = false;
                Utils.logd(TAG_MY, " isAllLogRunning(), log " + type + " is stopped");
                break;
            }
        }
        return isRunning;
    }

    /**
     * Judge whether log instance is running now, one instance at one time.
     *
     * @param type
     *            int
     * @return int
     */
    public static int getLogRunningStatusFromNative(int type) {
        // Utils.logd(TAG, "-->getLogRunningStatusFromNative(), type=" + type);
        if (Utils.LOG_TYPE_SET.contains(type)) {
            String key = Utils.KEY_LOG_RUNNING_STATUS_IN_SYSPROP_MAP.get(type);
            int status = SystemProperties.getInt(key, 0);
            Utils.logi(TAG_MY, "Log[" + type + "] running status value = " + status);
            return status;
        } else {
            Utils.loge(TAG_MY, "Unknown log instance type: " + type);
            return 0;
        }
    }

    /**
     * @param sharedPreferences
     *            SharedPreferences
     * @param type
     *            int
     * @return int
     */
    public static int getLogRunningStatusFromJava(SharedPreferences sharedPreferences, int type) {
        // Utils.logd(TAG, "-->getLogRunningStatusFromNative(), type=" + type);
        if (Utils.LOG_TYPE_SET.contains(type)) {
            int status =
                    sharedPreferences.getInt(Utils.KEY_STATUS_MAP.get(type),
                            Utils.VALUE_STATUS_DEFAULT);

            Utils.logi(TAG_MY, "Log[" + type + "] running status value = " + status);
            return status;
        } else {
            Utils.loge(TAG_MY, "Unknown log instance type: " + type);
            return 0;
        }
    }

    /**
     * @param cmdStr
     *            String
     * @return boolean
     */
    public static boolean runAdbCmd(String cmdStr) {
        try {
            Runtime.getRuntime().exec(cmdStr);
            return true;
        } catch (IOException e) {
            Utils.loge(TAG, "Run adb command [" + cmdStr + "] fail", e);
            return false;
        }
    }

    /**
     * @param context
     *            Context
     */
    public static void checkTagLogPreCondition(Context context) {
        Utils.logd(TAG, "-->tagLogPreConditionCheck()");
        String currLogPath = Utils.getCurrentLogPath(context);
        // Delete older taglog folder in mtklog, for judging taglog running
        // result
        File oldTagLogFolder = new File(currLogPath + TestUtils.TAGLOG_FOLDER_PREFIX);
        if (oldTagLogFolder.exists()) {
            Utils.logi(TAG, "Old taglog folder exist, delete it first.");
            TestUtils.deleteFile(oldTagLogFolder);
        }
        boolean tagLogFolderExist = (oldTagLogFolder != null && oldTagLogFolder.exists());
        AndroidTestCase.assertFalse(
                "Before tag log, we will delete old tag log folder for judging test result,"
                        + "but fail to do this.", tagLogFolderExist);

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        boolean isTaglogEnabled = sharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false);
        if (!isTaglogEnabled) {
            Utils.logi(TAG, "To test TagLog, it should first be enabled, Enable it now");
            sharedPreferences.edit().putBoolean(Utils.TAG_LOG_ENABLE, true).commit();
        }
        AndroidTestCase.assertTrue("Taglog is not enabled.",
                sharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false));
    }

    /**
     * Wait taglog progress finished.
     *
     * @param context
     *            Context
     * @throws InterruptedException
     *             InterruptedException
     */
    public static void waitTagLogFinish(Context context) throws InterruptedException {
        Utils.logd(TAG, "-->waitTagLogFinish()");
        String currLogPath = Utils.getCurrentLogPath(context);
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        AndroidTestCase.assertTrue("Taglog is not enabled.",
                sharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false));

        boolean isTaglogStarted =
                sharedPreferences.getBoolean(Utils.KEY_TAG_LOG_COMPRESSING, false);
        int count = 0;
        while (count < 20 && !isTaglogStarted) { // Wait 10s for Taglog begin,
                                                 // since for boot time
                                                 // exception, need to wait
                                                 // another 5s
            Thread.sleep(500);
            isTaglogStarted = sharedPreferences.getBoolean(Utils.KEY_TAG_LOG_COMPRESSING, false);
            count++;
        }
        AndroidTestCase.assertTrue("Taglog fail to start", isTaglogStarted);

        // Wait Taglog folder created
        count = 0;
        boolean tagLogFolderCreated = false;
        File tagLogFolder = new File(currLogPath + TestUtils.TAGLOG_FOLDER_PREFIX);
        tagLogFolderCreated = tagLogFolder.exists();
        while (count < 30 && !tagLogFolderCreated) { // Wait 15s for Taglog
                                                     // finish
            Thread.sleep(500);
            tagLogFolderCreated = tagLogFolder.exists();
            count++;
        }
        AndroidTestCase.assertTrue("Taglog folder create fail.", tagLogFolderCreated);
        File[] taglogFiles = tagLogFolder.listFiles();
        if (taglogFiles == null || taglogFiles.length == 0) {
            AndroidTestCase.assertTrue("New created taglog folder is invalid", false);
        }
        String targetTaglogFolderStr = taglogFiles[0].getAbsolutePath();
        Utils.logv(TAG, "targetTaglogFolderStr=" + targetTaglogFolderStr);

        // Wait Taglog finish
        count = 0;
        while (count < 1200 && isTaglogStarted) { // Wait 120s for Taglog finish
            Thread.sleep(100);
            isTaglogStarted = sharedPreferences.getBoolean(Utils.KEY_TAG_LOG_COMPRESSING, false);
            count++;
        }
        Utils.logv(TAG, "Wait taglog finish time count=" + count);
        AndroidTestCase.assertFalse("Taglog fail to finish in 120s", isTaglogStarted);

        // check SOP file is the last one to be copied to taglog folder
        if (targetTaglogFolderStr != null
                && targetTaglogFolderStr.contains(File.separator
                        + TagLogUtils.ZIP_LOG_TEMP_FOLDER_PREFIX)) {
            targetTaglogFolderStr =
                    targetTaglogFolderStr.replace(File.separator
                            + TagLogUtils.ZIP_LOG_TEMP_FOLDER_PREFIX, File.separator);
            Utils.logi(TAG, "Remove last taglog folder flag of TMP_, targetTaglogFolderStr="
                    + targetTaglogFolderStr);
        }
        String dbFilePath = targetTaglogFolderStr + "/checksop.txt";
        File dbFile = new File(dbFilePath);
        AndroidTestCase.assertTrue("Fail to create check SOP file to taglog folder, taglog fail.",
                dbFile.exists());
    }

    /**
     * @param context
     *            Context
     * @return boolean
     */
    public static final boolean mockDB(Context context) {
        String logRootPath = Utils.getCurrentLogPath(context) + "/mtklog/mock_db/db.00";
        File dbFolder = new File(logRootPath);
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();

            FileOutputStream out = null;
            try {
                // Create zz file
                File zzFile = new File(logRootPath, "ZZ_INTERNAL");
                if (!zzFile.createNewFile()) {
                    Utils.loge(TAG, "Mock zz_internal file fail.");
                    return false;
                }

                out = new FileOutputStream(zzFile);
                byte[] contentBytes = MOCK_ZZ_FILE_CONTENT.getBytes();
                out.write(contentBytes, 0, contentBytes.length);
                out.flush();
                out.close();

                // create db file
                File dbFile = new File(logRootPath, "db.00.dbg");
                if (!dbFile.createNewFile()) {
                    Utils.loge(TAG, "Mock db.00.dbg file fail.");
                    return false;
                }
                out = new FileOutputStream(dbFile);
                contentBytes = "Hello AEE".getBytes();
                out.write(contentBytes, 0, contentBytes.length);
                out.flush();
                out.close();
            } catch (IOException e) {
                Utils.loge(TAG, "Fail to mock db file", e);
                return false;
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        sMockDbFolderPrefix = logRootPath + "/";
        return true;
    }

    /**
     * @return boolean
     */
    public static boolean isNeedCheckGPSLogStatus() {
        String gpsLogKey = Utils.KEY_LOG_RUNNING_STATUS_IN_SYSPROP_MAP.get(Utils.LOG_TYPE_GPS);
        return "debug.gpsdbglog.enable".equals(gpsLogKey);
    }

    /**
     * @param logType int
     * @param taglogFile File
     * @return boolean
     */
    public static boolean isTypeLogInTaglogFolder(int logType, File taglogFile) {
        boolean isExist = false;
        switch (logType) {
        case Utils.LOG_TYPE_MOBILE:
            isExist = isFileExistInPath("APLog_", taglogFile);
            break;
        case Utils.LOG_TYPE_MODEM:
            isExist = isFileExistInPath("MDLog_", taglogFile)
                || isFileExistInPath("ModemLog", taglogFile);
            break;
        case Utils.LOG_TYPE_NETWORK:
            isExist = isFileExistInPath("NTLog_", taglogFile);
            break;
        default:
            return isExist = true;
        }
        Utils.logd(TAG, "<--isTypeLogInTaglogFolder logType = " + logType + ", taglogFile = "
                + taglogFile.getAbsolutePath() + ", isExist = " + isExist);
        return isExist;
    }

    /**
     * @param fileName String
     * @param file File
     * @return boolean
     */
    public static boolean isFileExistInPath(String fileName, File file) {
        if (file == null) {
            Utils.logd(TAG, "-->isFileExistInPath() parent = null!");
            return false;
        }
        if (file.exists()) {
            if (file.isDirectory()) {
                if (file.getName().contains(fileName)) {
                    return true;
                }
                File[] files = file.listFiles();
                if (files != null) {
                    for (File subFile : files) {
                        boolean isExist = isFileExistInPath(fileName, subFile);
                        if (isExist) {
                            return true;
                        }
                    }
                }
            } else {
                if (file.getName().contains(fileName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
