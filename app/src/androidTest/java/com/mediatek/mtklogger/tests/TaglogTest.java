package com.mediatek.mtklogger.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.ImageButton;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.mtklogger.MainActivity;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.utils.Utils;
import com.mtk.at.BasicFuncTest;

import java.io.File;

/**
 * @author MTK81255
 *
 */
public class TaglogTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private static final String TAG = TestUtils.TAG + "/TaglogTest";
    private Solo mSolo;

    private Instrumentation mIns;

    private Activity mActivity;

    private Context mContext;

    /**
     * return void.
     */
    public TaglogTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Utils.logd(TAG, "-->setUp()");
        mIns = getInstrumentation();
        mActivity = getActivity();
        mContext = mIns.getTargetContext();
        mSolo = new Solo(mIns, mActivity);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test01DialogShowForAllLogStopped() throws InterruptedException {
        Utils.logi(TAG, "-->test01DialogShowForAllLogStopped()");
        TestUtils.changeLogRunningStatus(mContext, 23, false);
//        Thread.sleep(5000);
//        mIns.waitForIdleSync();
//        ImageButton buttonTagLog = (ImageButton) mSolo.getView(R.id.tagImageButton);
//        // assertFalse("When all log stopped, taglog button is still enabled.",
//        // buttonTagLog.isEnabled());
//
//        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
//        // switch_taglog --ei cmd_target 1
//        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
//        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 1);
//        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
//        mContext.sendBroadcast(intent);
//        // adb shell am broadcast -a com.mediatek.log2server.EXCEPTION_HAPPEND
//        // -e path SaveLogManually -e db_filename yourInputTagName
//        intent = new Intent(Utils.ACTION_EXP_HAPPENED);
//        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
//        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, "test");
//        mContext.sendBroadcast(intent);
//
//        int i = 0;
//        boolean isDialogShow =
//                mSolo.searchText(mActivity.getString(R.string.taglog_msg_logtool_stopped));
//        while (i < 60) { // wait at most 60s
//            if (isDialogShow) {
//                break;
//            }
//            Utils.logi(TAG, "isDialogShow = " + isDialogShow + ", i = " + i);
//            Thread.sleep(1000);
//            isDialogShow =
//                    mSolo.searchText(mActivity.getString(R.string.taglog_msg_logtool_stopped));
//            i++;
//        }
//        assertTrue("After do taglog(60s) when all log stopped, the dialog is not shown.",
//                isDialogShow);
        Utils.logi(TAG, "<--test01DialogShowForAllLogStopped() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test02StartAllLogsInTaglog() throws InterruptedException {
        Utils.logi(TAG, "-->test02StartAllLogsInTaglog()");
        mIns.waitForIdleSync();
        boolean isDialogShow =
                mSolo.searchText(mActivity.getString(R.string.taglog_msg_logtool_stopped));
        if (!isDialogShow) {
            Utils.logw(TAG,
                    "The Taglog for all log stopped Dialog is not show, can not do this test!");
            Utils.logi(TAG, "<--test02StartAllLogsInTaglog() pass!");
            return;
        }
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));

        int i = 0;
        while (i < 20) { // wait at most 60s
            if (TestUtils.isAnyLogRunning()) {
                break;
            }
            Thread.sleep(1000);
            i++;
        }
        assertTrue("Start logs in taglog failed.", TestUtils.isAnyLogRunning());

        Thread.sleep(15000);
        mIns.waitForIdleSync();
        ImageButton buttonTagLog = (ImageButton) mSolo.getView(R.id.tagImageButton);
        // assertTrue("When any log started & taglog is enabled, taglog button is not enabled.",
        // buttonTagLog.isEnabled());

        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
        // switch_taglog --ei cmd_target 0
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 0);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);
        Thread.sleep(5000);
        mIns.waitForIdleSync();

        buttonTagLog = (ImageButton) mSolo.getView(R.id.tagImageButton);
        // assertFalse("When any log started & taglog is disabled, taglog button is still enabled.",
        // buttonTagLog.isEnabled());
        Utils.logi(TAG, "<--test02StartAllLogsInTaglog() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    @BasicFuncTest
    public void test03TaglogForSDMode() throws InterruptedException {
        Utils.logi(TAG, "-->test03TaglogForSDMode()");
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        Utils.deleteFile(taglogFile);
        TestUtils.changeLogRunningStatus(mContext, 2, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_SD);
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
        // switch_taglog --ei cmd_target 1
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 1);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);

        // adb shell am broadcast -a com.mediatek.log2server.EXCEPTION_HAPPEND
        // -e path SaveLogManually -e db_filename yourInputTagName
        intent = new Intent(Utils.ACTION_EXP_HAPPENED);
        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, "");
        mContext.sendBroadcast(intent);

        int i = 0;
        Thread.sleep(5000);
        boolean isDialogShow =
                mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            if (!isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
            i += 1000;
        }

        i = 0;
        Thread.sleep(5000);
        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            isAllLogZipOK =
                    TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                    && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                    && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
            if (isAllLogZipOK) {
                break;
            }
            Thread.sleep(1000);
            i += 1000;
        }

        assertTrue("test03TaglogForSDMode failed.", isAllLogZipOK);
        Utils.logi(TAG, "<--test03TaglogForSDMode() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test04TaglogWithTestInput() throws InterruptedException {
        Utils.logi(TAG, "-->test04TaglogWithTestInput()");
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        Utils.deleteFile(taglogFile);
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
        // switch_taglog --ei cmd_target 1
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 1);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);

        // adb shell am broadcast -a com.mediatek.log2server.EXCEPTION_HAPPEND
        // -e path SaveLogManually -e db_filename yourInputTagName
        intent = new Intent(Utils.ACTION_EXP_HAPPENED);
        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, "test");
        mContext.sendBroadcast(intent);

        int i = 0;
        boolean isDialogShow =
                mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
        Thread.sleep(5000);
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            if (!isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
            i += 1000;
        }
        boolean isTaglogContainsInput = TestUtils.isFileExistInPath("_test", taglogFile);
        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        assertTrue("TaglogWithTestInput failed.", isTaglogContainsInput && isAllLogZipOK);
        Utils.logi(TAG, "<--test04TaglogWithTestInput() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test05TaglogForPLSMode() throws InterruptedException {
        Utils.logi(TAG, "-->test05TaglogForPLSMode()");
        if (Utils.isDenaliMd3Solution()) {
            Utils.logi(TAG, "It is DenaliMd3Solution, no need run this case!");
            Utils.logi(TAG, "<--test05TaglogForPLSMode() pass!");
            return;
        }
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        Utils.deleteFile(taglogFile);
        TestUtils.changeLogRunningStatus(mContext, 2, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_PLS);

        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
        // switch_taglog --ei cmd_target 1
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 1);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);

        // adb shell am broadcast -a com.mediatek.log2server.EXCEPTION_HAPPEND
        // -e path SaveLogManually -e db_filename yourInputTagName
        intent = new Intent(Utils.ACTION_EXP_HAPPENED);
        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, "");
        mContext.sendBroadcast(intent);

        int i = 0;
        Thread.sleep(5000);
        boolean isLogFlush =
                mSolo.searchText(mActivity.getString(R.string.waiting_dialog_title_flush_log));
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            Utils.logi(TAG, "isLogFlush = " + isLogFlush + ", i = " + i);
            if (!isLogFlush) {
                break;
            }
            Thread.sleep(1000);
            isLogFlush =
                    mSolo.searchText(mActivity.getString(R.string.waiting_dialog_title_flush_log));
            ;
            i += 1000;
        }

        mIns.waitForIdleSync();
        i = 0;
        Thread.sleep(5000);
        boolean isDialogShow =
                mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            Utils.logi(TAG, "isDialogShow = " + isDialogShow + ", i = " + i);
            if (!isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
            i += 1000;
        }

        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                        && !TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        assertTrue("test05TaglogForPLSMode failed.", isAllLogZipOK);
        Utils.logi(TAG, "<--test05TaglogForPLSMode() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    /*public void test06TaglogForUSBMode() throws InterruptedException {
        Utils.logi(TAG, "-->test06TaglogForUSBMode()");
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        Utils.deleteFolder(taglogFile);
        TestUtils.changeLogRunningStatus(mContext, 2, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_USB);
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        // adb shell am broadcast -a
        // com.mediatek.mtklogger.ADB_CMD -e cmd_name switch_taglog --ei
        // cmd_target 1.
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 1);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);

        // adb shell am broadcast -a com.mediatek.log2server.EXCEPTION_HAPPEND
        // -e
        // path SaveLogManually -e db_filename yourInputTagName.
        intent = new Intent(Utils.ACTION_EXP_HAPPENED);
        intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, Utils.MANUAL_SAVE_LOG);
        intent.putExtra(Utils.EXTRA_KEY_EXP_NAME, "");
        mContext.sendBroadcast(intent);

        int i = 0;
        Thread.sleep(5000);
        boolean isDialogShow =
                mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
        while (i < TestUtils.TAGLOG_TIMEOUT) {
            if (!isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.taglog_msg_compress_log));
            i += 1000;
        }

        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                        && !TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        assertTrue("test06TaglogForUSBMode failed.", isAllLogZipOK);
        Utils.logi(TAG, "<--test06TaglogForUSBMode() pass!");
    }*/
}
