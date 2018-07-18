package com.mediatek.mtklogger.taglog;

import android.util.SparseArray;

import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author MTK81255
 *
 */
public class TagLogUtils {
    public static final String TAGLOG_TAG = Utils.TAG + "/TagLog";

    private static final String TAG = TAGLOG_TAG + "/TagLogUtils";

    public static final int MSG_UI_LOCK = 1;
    public static final int MSG_UI_RELEASE = 2;
    public static final int MSG_ZIPPED_FILE_COUNT = 3;
    public static final int MSG_ZIP_FILE_TOTAL_COUNT = 4;
    public static final int MSG_TAGLOG_DONE = 5;
    public static final int MSG_ALL_LOG_STOPPED = 6;
    public static final int MSG_DO_FILEMANAGER = 7;
    public static final int MSG_TAGLOG_MANAGER_INIT = 8;

    public static final String TAGLOG_FOLDER_NAME = "taglog";
    public static final String TAGLOG_TEMP_FOLDER_PREFIX = "TMP_";
    public static final String ZIP_LOG_TEMP_FOLDER_PREFIX = "TMP_";
    public static final String ZIP_LOG_SUFFIX = ".zip";
    public static final String ZIP_MODEMLOG_NAME = "ModemLog" + ZIP_LOG_SUFFIX;
    public static final long CONFIRM_LOG_STOP_DONE_SLEEP = 2000;

    public static final int LOG_STATUS_CHECK_TIME_OUT = 30000;
    public static final int LOG_STATUS_CHECK_TIME_PERIOD = 1000;

    public static final String CALIBRATION_DATA_KEY = "calibrationData";
//    public static final String KEY_NEED_CHECK_REBOOT_ISSUE = "needCheckRebootIssue";

    public static final int LOG_TYPE_BT = 0x40000000;

    public final static Set<Integer> TAG_LOG_TYPE_SET = new HashSet<Integer>();
    static {
        for (int logType : Utils.LOG_TYPE_SET) {
            TAG_LOG_TYPE_SET.add(logType);
        }
        TAG_LOG_TYPE_SET.add(LOG_TYPE_BT);
    }

    public static final SparseArray<String> LOGPATH_RESULT_KEY = new SparseArray<String>();
    static {
        LOGPATH_RESULT_KEY.put(Utils.LOG_TYPE_MODEM, Utils.BROADCAST_KEY_MDLOG_PATH);
        LOGPATH_RESULT_KEY.put(Utils.LOG_TYPE_MOBILE, Utils.BROADCAST_KEY_MOBILELOG_PATH);
        LOGPATH_RESULT_KEY.put(Utils.LOG_TYPE_NETWORK, Utils.BROADCAST_KEY_NETLOG_PATH);
    }

    /**
     * @author MTK81255
     *
     */
    // For LogInformation
    public enum LogInfoTreatmentEnum {
        ZIP, CUT, COPY, DELETE, ZIP_DELETE, COPY_DELETE, DO_NOTHING;
        private static final Map<String, LogInfoTreatmentEnum> stringToEnum
                                    = new HashMap<String, LogInfoTreatmentEnum>();
        static {
            // Initialize map from constant name to enum constant
            for (LogInfoTreatmentEnum treatment : values()) {
                stringToEnum.put(treatment.toString(), treatment);
            }
        }

        /**
         * @param symbol String
         * @return LogInfoTreatmentEnum
         */
        public static LogInfoTreatmentEnum fromString(String symbol) {
            return stringToEnum.get(symbol);
        }
    }

    public static final int LOG_TYPE_OTHERS = 0x0;
    public static final int LOG_TYPE_AEE = -0x1;
    public static final int LOG_TYPE_SOP = -0x2;
    public static final int LOG_TYPE_LAST_TAGLOG = -0x3;
    public static final SparseArray<Long> LOG_COMPRESS_RATIO_CHANGE = new SparseArray<Long>();
    static {
        LOG_COMPRESS_RATIO_CHANGE.put(Utils.LOG_TYPE_MODEM, 10 * 1024 * 1024L);
        LOG_COMPRESS_RATIO_CHANGE.put(Utils.LOG_TYPE_MOBILE, 10 * 1024 * 1024L);
        LOG_COMPRESS_RATIO_CHANGE.put(Utils.LOG_TYPE_NETWORK, 50 * 1024 * 1024L);
    }
    public static final SparseArray<Double> LOG_COMPRESS_RATIO_MAX = new SparseArray<Double>();
    static {
        LOG_COMPRESS_RATIO_MAX.put(Utils.LOG_TYPE_MODEM, 0.8);
        LOG_COMPRESS_RATIO_MAX.put(Utils.LOG_TYPE_MOBILE, 0.8);
        LOG_COMPRESS_RATIO_MAX.put(Utils.LOG_TYPE_NETWORK, 0.8);
    }
    public static final SparseArray<Double> LOG_COMPRESS_RATIO_MIN = new SparseArray<Double>();
    static {
        LOG_COMPRESS_RATIO_MIN.put(Utils.LOG_TYPE_MODEM, 0.3);
        LOG_COMPRESS_RATIO_MIN.put(Utils.LOG_TYPE_MOBILE, 0.2);
        LOG_COMPRESS_RATIO_MIN.put(Utils.LOG_TYPE_NETWORK, 0.3);
    }

    /**
     * @param content String
     * @param file File
     * @return boolean
     */
    public static boolean writeStringToFile(String content, File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canWrite()) {
            return false;
        }
        Utils.logi(TAG,
                "writeStringToFile() content = " + content + ", File = " + file.getAbsolutePath());
        try {
            FileOutputStream outStream = new FileOutputStream(file);
            OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream);
            outStreamWriter.write(content);
            outStreamWriter.flush();
            outStreamWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * @return String
     */
    public static synchronized String getCurrentTimeString() {
        String currentStr = TagLogUtils.translateTime2(System.currentTimeMillis());
        try {
            // Sleep 1 second for avoid to return the same time string
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return currentStr;
    }

    /**
     * @param gmsTimeStr String
     * @param timeFormat String
     * @return String
     */
    public static synchronized String getCurrentTimeString(String gmsTimeStr, String timeFormat) {
        if (gmsTimeStr == null || gmsTimeStr.isEmpty()) {
            return getCurrentTimeString();
        }
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.US);
        java.util.Date myDate;
        String currentStr = "";
        try {
            myDate = sdf.parse(gmsTimeStr);
            currentStr = TagLogUtils.translateTime2(myDate.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return currentStr;
    }
    /**
     * @param gmsTimeStr String
     * @param timeFormat String
     * @return long
     */
    public static synchronized long getCurrentTime(String gmsTimeStr, String timeFormat) {
        if (gmsTimeStr == null || gmsTimeStr.isEmpty()) {
            return 0;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.US);
        java.util.Date myDate;
        long currentLong = 0;
        try {
            myDate = sdf.parse(gmsTimeStr);
            currentLong = myDate.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return currentLong;
    }
    /**
     * transfer long time to time string.
     *
     * @param time
     *            long type
     * @return ex: 2012_1221_2359
     */
    public static String translateTime2(long time) {
        GregorianCalendar calendar = new GregorianCalendar();
        DecimalFormat df = new DecimalFormat();
        String pattern = "00";
        df.applyPattern(pattern);
        calendar.setTime(new Date(time));

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minu = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        return "" + year + "_" + df.format(month) + df.format(day) + "_" + df.format(hour)
                + df.format(minu) + df.format(second);
    }

}
