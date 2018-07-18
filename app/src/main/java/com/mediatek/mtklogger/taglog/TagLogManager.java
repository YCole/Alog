package com.mediatek.mtklogger.taglog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.taglog.db.DBManager;
import com.mediatek.mtklogger.utils.ExceptionInfo;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class TagLogManager {
    private static final String TAG = Utils.TAG + "/TagLogManager";

    private List<TagLog> mTaglogList = Collections.synchronizedList(new ArrayList<TagLog>());
    private static TagLogManager sInstance = new TagLogManager();

    private Handler mFileManagerHandler;
    private Handler mTaglogManagerHandler;

    private boolean mIsInitDone = false;

    private TagLogManager() {
        DBManager.getInstance().init();
        HandlerThread  myHandler = new HandlerThread("taglogManagerThread");
        myHandler.setPriority(Thread.MIN_PRIORITY);
        myHandler.start();
        mTaglogManagerHandler = new TaglogManagerHandler(myHandler.getLooper());

        mFileManagerHandler = LogFilesManager.getInstance();
        mFileManagerHandler.sendEmptyMessage(TagLogUtils.MSG_DO_FILEMANAGER);
    }

    public static final TagLogManager getInstance() {
        return sInstance;
    }
    /**.
     *
     */
    public void startTagLogManager() {
        Utils.logd(TAG, "startTagLogManager--> isInitDone = " + mIsInitDone);
        if (!mIsInitDone) {
            mIsInitDone = true;
            mTaglogManagerHandler
            .obtainMessage(TagLogUtils.MSG_TAGLOG_MANAGER_INIT, this)
            .sendToTarget();
        }
    }

    private void doInit() {
        checkNewException();
        doResumeTaglog();
    }

    private void doResumeTaglog() {
        List<TagLogData> taglogDataList = DBManager.getInstance().getResumeTaglog();
        if (taglogDataList == null) {
            Utils.logi(TAG, "-->resumeTag(), no taglog need resume, just return!");
            return;
        }
        Utils.logi(TAG, "-->resumeTag(), taglogDataList.size() = " + taglogDataList.size());
        for (TagLogData data : taglogDataList) {
            String taglogState = data.getTaglogTable().getState();
            String fileList = data.getTaglogTable().getFileList();
            TagLog taglog = createNewTaglog();
            Utils.logi(TAG, "taglogId = " + data.getTaglogTable().getTagLogId()
                            + ", taglogFolder =" + data.getTaglogTable().getTargetFolder()
                            + ", taglogState = " + taglogState + ", fileList = " + fileList);
            mTaglogList.add(taglog);
            taglog.resumeTag(data);
        }
    }

    /**
     * @param path
     *            String
     */
    public synchronized void doRequestNewTaglog(String path) {
        if (!Utils.isTaglogEnable()) {
            Utils.logw(TAG, "<--doRequestNewTaglog return, because taglog is disable");
            return;
        }
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning()
                    && !isUILocked()) {
                Utils.logw(TAG, "<--doRequestNewTaglog return, because all log stop!");
                return;
            }
        } catch (ServiceNullException e) {
            return;
        }
        Utils.logd(TAG, "doRequestNewTaglog--->");
        List<Intent> intentList = DBManager.getInstance().getRequestNewTaglog(path);
        if (intentList == null || intentList.size() == 0) {
            return;
        }
        for (Intent intent : intentList) {
            startNewTaglog(intent);
        }
    }
    /**
     * @param intent Intent
     */
    public synchronized void beginTagLog(Intent intent) {
        String expPath = intent.getStringExtra(Utils.EXTRA_KEY_EXP_PATH);
        Utils.logi(TAG,
                "-->beginTagLog() " + "mTaglogList.size() = " + mTaglogList.size()
                + "isUILocked() = " + isUILocked() + ", dbpath = " + expPath);
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning()
                    && !isUILocked()) {
                Utils.logw(TAG, "no need do taglog because all log stop!");
                return;
            }
        } catch (ServiceNullException e) {
            return;
        }
        if (checkTaglogValid(intent)) {
            startNewTaglog(intent);
        } else {
            Utils.logw(TAG, "-->beginTagLog() intent is invalid!");
        }
    }

    private void startNewTaglog(Intent intent) {
        synchronized (TagLogManager.class) {
            String dbPath = intent.getStringExtra(Utils.EXTRA_KEY_EXP_PATH);
            if (!Utils.MANUAL_SAVE_LOG.equalsIgnoreCase(dbPath)) {
                for (TagLog taglog : mTaglogList) {
                    Intent inputIntent = taglog.getInputIntent();
                    if (inputIntent == null) {
                        continue;
                    }
                    String taglogDbPath = inputIntent.getStringExtra(
                            Utils.EXTRA_KEY_EXP_PATH);
                    if (taglogDbPath != null && taglogDbPath.equalsIgnoreCase(dbPath)) {
                        Utils.logw(TAG, "startNewTaglog the intent:" + dbPath
                                + " has been exist!");
                        return;
                    }
                }
            }
            TagLog newTagLog = createNewTaglog();
            newTagLog.beginTag(intent);
            mTaglogList.add(newTagLog);
        }
    }

    private boolean checkTaglogValid(Intent intent) {
        String dbPath = intent.getStringExtra(Utils.EXTRA_KEY_EXP_PATH);
        if (dbPath == null) {
            return false;
        }
        if (Utils.MANUAL_SAVE_LOG.equalsIgnoreCase(dbPath)) {
            return true;
        }
        String zzTime = getZzTime(intent);
        if (zzTime == null || zzTime.isEmpty()) {
            return false;
        }
        boolean taglogExist = DBManager.getInstance()
                .isTaglogExist(dbPath, zzTime);
        Utils.logi(TAG, "taglogExist ? " + taglogExist + ", for " + dbPath + "," + zzTime);
        return !taglogExist;
    }

    private String getZzTime(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Utils.loge(TAG, "extras == null, just return!");
            return "";
        }

        String expPath = extras.getString(Utils.EXTRA_KEY_EXP_PATH);
        String zzFileName = extras.getString(Utils.EXTRA_KEY_EXP_ZZ, "");
        if (zzFileName.isEmpty()) {
            zzFileName = Utils.EXTRA_VALUE_EXP_ZZ;
        }
        ExceptionInfo expInfo = new ExceptionInfo();
        try {
            expInfo.initFieldsFromZZ(expPath + File.separator + zzFileName);
        } catch (IOException e) {
            Utils.loge(TAG, "fail to init exception info:" + e.getMessage());
            Utils.logd(TAG, "isModemException ? false");
        }
        return expInfo.getmTime();
    }

    private TagLog createNewTaglog() {
        Boolean isReleaseToCustomer1 = Utils.isReleaseToCustomer1();
        Utils.logd(TAG, "isReleaseToCustomer1 ? " + isReleaseToCustomer1);
        if (isReleaseToCustomer1) {
            return new Customer1TagLog(mTaglogManagerHandler);
        } else {
            return new CommonTagLog(mTaglogManagerHandler);
        }
    }
    private int mUILockNumber = 0;

    /**
     * @return boolean
     */
    public boolean isUILocked() {
        Utils.logd(TAG, "isUILocked() mUILockNumber = " + mUILockNumber);
        return mUILockNumber > 0;
    }

    /**
     * return void.
     */
    public void checkNewException() {
        Utils.getBootTimeString();
        String dbHistorySystemPath = Utils.AEE_SYSTEM_PATH +  Utils.AEE_DB_HISTORY_FILE;
        doRequestNewTaglog(dbHistorySystemPath);

        String dbHistoryVendorPath = Utils.AEE_VENDOR_PATH + Utils.AEE_DB_HISTORY_FILE;
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
        doRequestNewTaglog(vendorMonitorPath);
    }

    /**
     * @author MTK11515
     *
     */
    class TaglogManagerHandler extends Handler {
        public TaglogManagerHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            Utils.logi(TAG, "-->mTaglogManagerHandler msg.what = " + msg.what);
            switch (msg.what) {
            case TagLogUtils.MSG_UI_LOCK:
                mUILockNumber++;
                break;
            case TagLogUtils.MSG_UI_RELEASE:
                mUILockNumber--;
                break;
            case TagLogUtils.MSG_TAGLOG_DONE:
                Object obj = msg.obj;
                if (obj instanceof TagLog) {
                    mTaglogList.remove(obj);
                }
                break;
            case TagLogUtils.MSG_DO_FILEMANAGER:
                mFileManagerHandler.sendEmptyMessage(TagLogUtils.MSG_DO_FILEMANAGER);
                break;
            case TagLogUtils.MSG_TAGLOG_MANAGER_INIT:
                doInit();
                break;
            default:
                Utils.logw(TAG, "-->mTaglogManagerHandler msg.what = " + msg.what
                        + " is not supported!");
            }
        }
    }
}
