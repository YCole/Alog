package com.mediatek.mtklogger.taglog;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.mediatek.mtklogger.proxy.communicate.ProxyCommandInfo;
import com.mediatek.mtklogger.proxy.communicate.ProxyCommandManager;
import com.mediatek.mtklogger.taglog.TagLogUtils.LogInfoTreatmentEnum;
import com.mediatek.mtklogger.taglog.db.DBManager;
import com.mediatek.mtklogger.utils.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author MTK81255
 *
 */
public class LogFilesManager extends Handler {
    private static final String TAG = TagLogUtils.TAGLOG_TAG + "/LogFilesManager";

    private static final int ZIP_BUFFER_SIZE = 1024 * 1024;
    private static final long CHECK_ZIP_TIMER = 60000;

    private static LogFilesManager sInstance = null;
    private Boolean mIsDoZiping = false;

    private LogFilesManager(Looper looper) {
        super(looper);
    }
    /**
     * @return LogFilesManager
     */
    public static LogFilesManager getInstance() {
        if (sInstance == null) {
            synchronized (LogFilesManager.class) {
                if (sInstance == null) {
                    HandlerThread  myHandler = new HandlerThread("filemanagerThread");
                    myHandler.setPriority(Thread.MIN_PRIORITY);
                    myHandler.start();
                    sInstance = new LogFilesManager(myHandler.getLooper());
                }
            }
        }
        return sInstance;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case TagLogUtils.MSG_DO_FILEMANAGER:
            checkDoFileManager();
            break;

        default:
            Utils.logw(TAG, "-->mTaglogManagerHandler msg.what = " + msg.what
                    + " is not supported!");
        }
    }

    private void checkDoFileManager() {
        Utils.logi(TAG, "-->checkDoFileManager");
        if (mIsDoZiping) {
            Utils.logw(TAG, "is doing zip, just return");
            return;
        }
        List<LogInformation> logInfomationList = DBManager.getInstance()
                .getWaitingDoingLogInformationList();
        if (logInfomationList != null && logInfomationList.size() > 0) {
            mIsDoZiping = true;
            treatLogFiles(logInfomationList);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mIsDoZiping = false;
        }
        if (!this.hasMessages(TagLogUtils.MSG_DO_FILEMANAGER)) {
            this.sendMessageDelayed(this.obtainMessage(TagLogUtils.MSG_DO_FILEMANAGER),
                    CHECK_ZIP_TIMER);
        }
    }

    /**
     * @param logInfoList
     *            List<LogInformation>
     */
    public void treatLogFiles(List<LogInformation> logInfoList) {
        if (logInfoList == null || logInfoList.size() == 0) {
            Utils.logw(TAG, "No log infor need to do, just return!");
            return;
        }
        Utils.logi(TAG, "-->treatLogFiles() logInfoList.size = " + logInfoList.size());
        dealWithZip(logInfoList);
        for (LogInformation logInfo : logInfoList) {
            String sourceFilePath = logInfo.getLogFile().getAbsolutePath();
            String targetFilePath = logInfo.getFileInfo().getTargetFolder()
                                    + File.separator + logInfo.getTargetFileName();
            Utils.logi(TAG, "for fileId = " + logInfo.getFileInfo().getFileId()
                       + ", treatMent = " + logInfo.getTreatMent());
            switch (logInfo.getTreatMent()) {
            case ZIP:
                doZip(logInfo);
                if (!checkZip(new File(targetFilePath))) {
                    doDelete(targetFilePath);
                    Utils.logd(TAG, "do zip again for " + targetFilePath);
                    doZip(logInfo);
                    boolean result = checkZip(new File(targetFilePath));
                    if (!result) {
                        doDelete(targetFilePath);
                    }
                }
                break;
            case CUT:
                doCut(sourceFilePath, targetFilePath);
                break;
            case COPY:
                doCopy(sourceFilePath, targetFilePath);
                break;
            case DELETE:
                doDelete(sourceFilePath);
                break;
            case ZIP_DELETE:
                if (doZip(logInfo)) {
                    boolean checkResult = checkZip(new File(targetFilePath));
                    if (!checkResult) {
                        doDelete(targetFilePath);
                        Utils.logd(TAG, "do zip again for " + targetFilePath);
                        doZip(logInfo);
                        checkResult = checkZip(new File(targetFilePath));
                    }
                    if (checkResult) {
                        doDelete(sourceFilePath);
                    } else {
                        doDelete(targetFilePath);
                    }
                } else {
                    Utils.logi(TAG, "do zip fail for " + targetFilePath);
                }
                break;
            case COPY_DELETE:
                Utils.doCopy(sourceFilePath, targetFilePath);
                doDelete(sourceFilePath);
                break;
            case DO_NOTHING:
                break;
            default :
                Utils.logw(TAG, logInfo.getTreatMent() + " is not support!");
            }
            DBManager.getInstance().typelogDone(logInfo);

        }
    }

    private void doCopy(String sourcePath, String targetFilePath) {
        String[] subPaths = sourcePath.split(";");
        for (String subPath : subPaths) {
            if (new File(subPath).exists()) {
                Utils.doCopy(subPath, targetFilePath);
            } else if (subPath.startsWith(Utils.AEE_VENDOR_PATH)) {
                ProxyCommandInfo proxyCopyFileInfo = ProxyCommandManager.getInstance()
                        .createProxyCommand(Utils.PROXY_CMD_OPERATE_COPY_FILE,
                                subPath, targetFilePath);
                ProxyCommandManager.getInstance().addToWaitingResponseListener(proxyCopyFileInfo);
                ProxyCommandManager.getInstance().sendOutCommand(proxyCopyFileInfo);
                long timeout = 10 * 60 * 1000;
                long sleepPeriod = 1000;
                while (!proxyCopyFileInfo.isCommandDone()) {
                    try {
                        Thread.sleep(sleepPeriod);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    timeout -= sleepPeriod;
                    if (timeout <= 0) {
                        Utils.logw(TAG, "MTKLoggerProxy copy file from "
                    + sourcePath + " to " + targetFilePath + " is timout!");
                        break;
                    }
                }
                ProxyCommandManager.getInstance().removeFromWaitingResponseListener(
                        proxyCopyFileInfo);
            }
        }
    }
    /**
     * When multi log need zip to the same target, do them at one time.
     */
    private void dealWithZip(List<LogInformation> logInfoList) {
        Map<String, List<LogInformation>> multiZipMap = new HashMap<String, List<LogInformation>>();
        List<String> needZipPath = new ArrayList<String>();
        for (LogInformation logInfo : logInfoList) {
            if (logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP
                    || logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP_DELETE) {
                String targetZip = logInfo.getFileInfo().getTargetFolder()
                                   + File.separator + logInfo.getTargetFileName();

                Utils.logd(TAG, "fileId :" + logInfo.getFileInfo().getFileId());
                if (!multiZipMap.containsKey(targetZip)) {
                    List<LogInformation> curlogInfoList = new ArrayList<LogInformation>();
                    curlogInfoList.add(logInfo);
                    needZipPath.add(logInfo.getLogFile().getName());
                    multiZipMap.put(targetZip, curlogInfoList);
                } else {
                    if (!needZipPath.contains(logInfo.getLogFile().getName())) {
                        multiZipMap.get(targetZip).add(logInfo);
                        needZipPath.add(logInfo.getLogFile().getName());
                    } else {
                        if (logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP) {
                            logInfo.setTreatMent(LogInfoTreatmentEnum.DO_NOTHING);
                        } else if (logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP_DELETE) {
                            logInfo.setTreatMent(LogInfoTreatmentEnum.DELETE);
                        }
                    }
                }
            }
        }
        for (String targetFileName : multiZipMap.keySet()) {
            Utils.logd(TAG, "dealWithZip targetFileName = " + targetFileName);
            List<LogInformation> curlogInfoList = multiZipMap.get(targetFileName);
                doZip(curlogInfoList);
                boolean checkZipResult = checkZip(new File(targetFileName));
                if (!checkZipResult) {
                    doDelete(targetFileName);
                    Utils.logd(TAG, "doZip again for " + targetFileName);
                    doZip(curlogInfoList);
                    checkZipResult = checkZip(new File(targetFileName));
                    if (!checkZipResult) {
                        doDelete(targetFileName);
                    }
                }
                for (LogInformation logInfo : curlogInfoList) {
                    if (!checkZipResult) {
                        logInfo.setTreatMent(LogInfoTreatmentEnum.DO_NOTHING);
                    } else {
                        if (logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP) {
                            logInfo.setTreatMent(LogInfoTreatmentEnum.DO_NOTHING);
                        } else if (logInfo.getTreatMent() == LogInfoTreatmentEnum.ZIP_DELETE) {
                            logInfo.setTreatMent(LogInfoTreatmentEnum.DELETE);
                        }
                    }
                }
        }
    }
    /**
     * @param sourceFileList
     *            List<String>
     * @param targetFilePath
     *            String
     * @param needAddFileCount
     *            boolean
     * @return boolean
     */
    public boolean doZip(List<String> sourceFileList, String targetFilePath,
                         boolean needAddFileCount) {
        Utils.logi(TAG, "-->doZip() from sourceFileList.size() = " + sourceFileList.size() + " to "
                + targetFilePath);
        boolean result = false;
        ZipOutputStream outZip = null;
        try {
            outZip = new ZipOutputStream(new FileOutputStream(targetFilePath));
            for (String sourceFilePath : sourceFileList) {
                File sourceFile = new File(sourceFilePath);
                result = zipFile(sourceFile.getParent(), sourceFile.getName(),
                                 outZip, needAddFileCount);
            }
            outZip.flush();
            outZip.finish();
            outZip.close();
        } catch (FileNotFoundException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } catch (IOException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } finally {
            if (outZip != null) {
                try {
                    outZip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    /**
     * @param sourceFilePath
     *            String
     * @param targetFilePath
     *            String
     * @param needAddFileCount
     *            boolean
     * @return boolean
     */
    public boolean doZip(String sourceFilePath, String targetFilePath, boolean needAddFileCount) {
        Utils.logi(TAG, "-->doZip() from " + sourceFilePath + " to " + targetFilePath);
        boolean result = false;
        ZipOutputStream outZip = null;
        try {
            outZip = new ZipOutputStream(new FileOutputStream(targetFilePath));
            File sourceFile = new File(sourceFilePath);
            result = zipFile(sourceFile.getParent(), sourceFile.getName(),
                             outZip, needAddFileCount);
            outZip.flush();
            outZip.finish();
            outZip.close();
        } catch (FileNotFoundException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } catch (IOException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } finally {
            if (outZip != null) {
                try {
                    outZip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    /**
     * @param logInfo
     *            LogInformation
     * @return boolean
     */
    public boolean doZip(LogInformation logInfo) {
        String targetFilePath = logInfo.getFileInfo().getTargetFolder()
                                + File.separator + logInfo.getTargetFileName();
        Utils.logi(TAG, "-->doZip LogInformation for " + targetFilePath );
        boolean result = false;
        ZipOutputStream outZip = null;
        try {
            outZip = new ZipOutputStream(new FileOutputStream(targetFilePath));
            result = zipFile(logInfo, logInfo.getLogFile().getName(), outZip);
            outZip.flush();
            outZip.finish();
            outZip.close();
        } catch (FileNotFoundException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } catch (IOException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } finally {
            if (outZip != null) {
                try {
                    outZip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
    /**
     * @param listLogInfo List<LogInformation>
     * @return boolean
     */
    public boolean doZip(List<LogInformation> listLogInfo) {
        if (listLogInfo == null || listLogInfo.size() < 1) {
            Utils.logd(TAG, "doZip fail fro listLogInfo == null");
            return false;
        }
        String targetFilePath = listLogInfo.get(0).getFileInfo().getTargetFolder()
                + File.separator + listLogInfo.get(0).getTargetFileName();
        boolean isNeedDoZip = false;
        for (LogInformation logInfo :listLogInfo) {
            if (logInfo.getFileInfo().getFileCount()
                    != logInfo.getFileInfo().getFileProgress()) {
                isNeedDoZip = true;
                break;
            }
        }
        if (!isNeedDoZip) {
            Utils.logi(TAG, "doZip done for " + targetFilePath + ", no need zip again.");
            return true;
        }
        boolean result = false;
        ZipOutputStream outZip = null;
        try {
            outZip = new ZipOutputStream(new FileOutputStream(targetFilePath));
            for (LogInformation logInfo : listLogInfo) {
                result = zipFile(logInfo, logInfo.getLogFile().getName(), outZip);
                Utils.logi(TAG, "-->doZip LogInformation for " + targetFilePath
                        + ", fileid = " + logInfo.getFileInfo().getFileId());
            }
            outZip.flush();
            outZip.finish();
            outZip.close();
        } catch (FileNotFoundException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } catch (IOException e) {
            result = false;
            Utils.loge(TAG, "FileNotFoundException", e);
        } finally {
            if (outZip != null) {
                try {
                    outZip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
    /**
     * @param srcRootPath
     *            String
     * @param fileRelativePath
     *            String
     * @param zout
     *            ZipOutputStream
     * @param needAddFileCount
     *            boolean
     * @return boolean
     */
    public boolean zipFile(String srcRootPath, String fileRelativePath, ZipOutputStream zout,
                           boolean needAddFileCount) {
        Utils.logd(TAG, "zipFile(), srcRootPath=" + srcRootPath + ", fileRelativePath="
                + fileRelativePath);
        if (zout == null) {
            Utils.loge(TAG, "Can not zip file into a null stream");
            return false;
        }
        boolean result = false;
        File file = new File(srcRootPath + File.separator + fileRelativePath);
        if (file.exists()) {
            if (file.isFile()) {
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                    ZipEntry entry = new ZipEntry(fileRelativePath);
                    zout.putNextEntry(entry);

                    int len = 0;
                    byte[] buffer = new byte[ZIP_BUFFER_SIZE];
                    while ((len = in.read(buffer)) > -1) {
                        zout.write(buffer, 0, len);
                    }
                    zout.closeEntry();
                    zout.flush();
                    result = true;
                } catch (FileNotFoundException e) {
                    Utils.loge(TAG, "FileNotFoundException", e);
                } catch (IOException e) {
                    Utils.loge(TAG, "IOException", e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return result;
            } else {
                result = true;
                String[] fileList = file.list();
                if (fileList == null) {
                    return false;
                }
                if (fileList.length <= 0) {
                    ZipEntry entry = new ZipEntry(fileRelativePath + File.separator);
                    try {
                        zout.putNextEntry(entry);
                        zout.closeEntry();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                for (String subFileName : fileList) {
                    if (!zipFile(srcRootPath, fileRelativePath + File.separator
                            + subFileName, zout, needAddFileCount)) {
                        result = false;
                        Utils.loge(TAG, "File [" + subFileName + "] zip failed");
                    }
                }
                return result;
            }
        } else {
            Utils.loge(TAG, "File [" + file.getPath() + "] does not exitst");
            return false;
        }
    }
    /**
     * @param logInfo LogInformation
     * @param fileRelativePath String
     * @param zout ZipOutputStream
     * @return boolean
     */
    public boolean zipFile(LogInformation logInfo, String fileRelativePath, ZipOutputStream zout) {
        String srcRootPath = logInfo.getLogFile().getParent();
        Utils.logd(TAG, "zipFile(), LogInformation, srcRootPath=" + srcRootPath
                + ", fileRelativePath = " + fileRelativePath);
        if (zout == null) {
            Utils.loge(TAG, "Can not zip file into a null stream");
            return false;
        }
        boolean result = false;
        File file = new File(srcRootPath + File.separator + fileRelativePath);
        if (file.exists()) {
            if (file.isFile()) {
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                    ZipEntry entry = new ZipEntry(fileRelativePath);
                    zout.putNextEntry(entry);

                    int len = 0;
                    byte[] buffer = new byte[ZIP_BUFFER_SIZE];
                    while ((len = in.read(buffer)) > -1) {
                        zout.write(buffer, 0, len);
                    }
                    zout.closeEntry();
                    zout.flush();
                    result = true;
                } catch (FileNotFoundException e) {
                    Utils.loge(TAG, "FileNotFoundException", e);
                } catch (IOException e) {
                    Utils.loge(TAG, "IOException", e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    logInfo.addFileProgress(1);
                }
                return result;
            } else {
                result = true;
                String[] fileList = file.list();
                if (fileList == null) {
                    return false;
                }
                if (fileList.length <= 0) {
                    ZipEntry entry = new ZipEntry(fileRelativePath + File.separator);
                    try {
                        zout.putNextEntry(entry);
                        zout.closeEntry();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                for (String subFileName : fileList) {
                    if (!zipFile(logInfo, fileRelativePath + File.separator
                            + subFileName, zout)) {
                        result = false;
                        Utils.loge(TAG, "File [" + subFileName + "] zip failed");
                    }
                }
                return result;
            }
        } else {
            Utils.loge(TAG, "File [" + file.getPath() + "] does not exitst");
            return false;
        }
    }
    /**
     * @param sourceFilePath
     *            String
     * @param targetFilePath
     *            String
     * @return boolean
     */
    public boolean doCut(String sourceFilePath, String targetFilePath) {
        Utils.logi(TAG, "-->doCut() from " + sourceFilePath + " to " + targetFilePath);
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            Utils.logw(TAG, "The sourceFilePath = " + sourceFilePath
                    + " is not existes, do cut failed!");
            return false;
        }
        File targetFile = new File(targetFilePath);
        return sourceFile.renameTo(targetFile);
    }


    /**
     * @param sourceFilePath
     *            String
     * @return boolean
     */
    public boolean doDelete(String sourceFilePath) {
        Utils.logi(TAG, "-->doDelete() for " + sourceFilePath);
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            Utils.logw(TAG, "The sourceFilePath = " + sourceFilePath
                    + " is not existes, no need do delete!");
            return true;
        }
        Utils.deleteFile(sourceFile);
        return true;
    }

    /**
     * @param zipFile File
     * @return boolean
     */
    public boolean checkZip(File zipFile) {
        int buffer = 1024 * 1024 * 10;
        boolean checkResult = true;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis, buffer));
            while (zis.getNextEntry() != null) {
                continue;
            }
            zis.close();
            fis.close();
        } catch (FileNotFoundException e) {
            Utils.logw(TAG, "checkZip FileNotFoundException happen" );
            checkResult = false;
        } catch (ZipException e) {
            Utils.logw(TAG, "checkZip ZipException happen");
            checkResult = false;
        } catch (IOException e) {
            Utils.logw(TAG, "checkZip IOException happen");
            checkResult = false;
        }
        Utils.logd(TAG, "checkZipFile result = " + checkResult +
                 ", for file " + zipFile.getAbsolutePath());
        return checkResult;
    }
}
