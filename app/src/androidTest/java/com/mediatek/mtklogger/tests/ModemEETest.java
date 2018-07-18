package com.mediatek.mtklogger.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.mtklogger.MainActivity;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.MultiModemLog;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;

/**
 * @author MTK81255
 *
 */
public class ModemEETest extends ActivityInstrumentationTestCase2<MainActivity> {

    private static final String TAG = TestUtils.TAG + "/ModemEETest";

    private Solo mSolo;

    private Instrumentation mIns;

    private Activity mActivity;

    private Context mContext;

    /**
     * return void.
     */
    public ModemEETest() {
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
    public void test01MemoryDumpDialogSDTest() throws InterruptedException {
        Utils.logi(TAG, "-->test01MemoryDumpDialogSDTest()");
        // File taglogFile = new File(Utils.getCurrentLogPath(mContext) +
        // "/mtklog/taglog");
        // Utils.deleteFolder(taglogFile);
        TestUtils.changeLogRunningStatus(mContext, 2, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_SD);

        TestUtils.changeLogRunningStatus(mContext, 23, true);
        Thread.sleep(10000);
        mIns.waitForIdleSync();
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD -e cmd_name
        // switch_taglog --ei cmd_target 1
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 0);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "switch_taglog");
        mContext.sendBroadcast(intent);

        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
        // -e cmd_name force_modem_assert --ei cmd_target 2
        intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 2);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "force_modem_assert");
        mContext.sendBroadcast(intent);
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
        // -e cmd_name modem_auto_reset_0 --ei cmd_target 2
        intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 2);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, MultiModemLog.ADB_COMMAND_MODEM_AUTO_RESET + "0");
        mContext.sendBroadcast(intent);
        int i = 0;
        boolean isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
        while (i < 660) { // wait at most 660s
            if (isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
            i++;
        }
        assertTrue("After modem ee(660s), the memory dump dialog is not show.", isDialogShow);
        Utils.logi(TAG, "<--test01MemoryDumpDialogSDTest() pass!");
    }

    /**
     * @throws InterruptedException InterruptedException
     */
    public void test02ModemResetSDTest() throws InterruptedException {
        Utils.logi(TAG, "-->test02ModemResetSDTest()");
        mIns.waitForIdleSync();
        boolean isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
        if (!isDialogShow) {
            Utils.logw(TAG, "The memoryDumpDialog is not show, can not do reset!");
            return;
        }
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));
        int i = 0;
        boolean isResetProgressShow =
                mSolo.searchText(mActivity.getString(R.string.reset_modem_msg));
        boolean isModemLogRunning = TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM);
        while (i < 60) { // wait at most 60s
            if (!isResetProgressShow && isModemLogRunning) {
                break;
            }
            Thread.sleep(1000);
            Utils.logi(TAG, "start test02ModemResetSDTest i = " + i);
            isResetProgressShow = mSolo.searchText(mActivity.getString(R.string.reset_modem_msg));
            isModemLogRunning = TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM);
            i++;
            Utils.logi(TAG, "end test02ModemResetSDTest i = " + i);
        }
        Utils.logi(TAG, "<--test02ModemResetSDTest() isResetProgressShow = " + isResetProgressShow
                + ", isModemLogRunning = " + isModemLogRunning);
        assertTrue("After reset modem(60s), the modem log is not running.", !isResetProgressShow
                && isModemLogRunning);
        Utils.logi(TAG, "<--test02ModemResetSDTest() pass!");
    }

    /**
     * @throws InterruptedException InterruptedException
     */
    /*public void test03TaglogForModemEESDTest() throws InterruptedException {
        Utils.logi(TAG, "-->test03TaglogForModemEESDTest()");
        Thread.sleep(15000);
        mIns.waitForIdleSync();
        int i = 0;
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
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        assertTrue("Taglog for Modem EE sd mode failed.", isAllLogZipOK);
        Utils.logi(TAG, "<--test03TaglogForModemEESDTest() pass!");
    }*/

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test04MemoryDumpDialogPLSTest() throws InterruptedException {
        Utils.logi(TAG, "-->test04MemoryDumpDialogPLSTest()");
        // File taglogFile = new File(Utils.getCurrentLogPath(mContext) +
        // "/mtklog/taglog");
        // Utils.deleteFolder(taglogFile);
        if (Utils.isDenaliMd3Solution()) {
            Utils.logi(TAG, "It is DenaliMd3Solution, no need run this case!");
            Utils.logi(TAG, "<--test04MemoryDumpDialogPLSTest() pass!");
            return;
        }
        TestUtils.changeLogRunningStatus(mContext, 2, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_PLS);
        TestUtils.changeLogRunningStatus(mContext, 2, true);
        Thread.sleep(10000);
        // adb shell am broadcast -a com.mediatek.mtklogger.ADB_CMD
        // -e cmd_name force_modem_assert --ei cmd_target 2
        Intent intent = new Intent(TestUtils.ACTION_CMD_LINE);
        intent.putExtra(TestUtils.EXTRA_CMD_TARGET, 2);
        intent.putExtra(TestUtils.EXTRA_CMD_NAME, "force_modem_assert");
        mContext.sendBroadcast(intent);

        int i = 0;
        boolean isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
        while (i < 660) { // wait at most 660s
            if (isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
            i++;
        }
        assertTrue("After modem ee(660s), the memory dump dialog is not show.", isDialogShow);
        Utils.logi(TAG, "<--test04MemoryDumpDialogPLSTest() pass!");
    }

    /**
     * @throws InterruptedException InterruptedException
     */
    public void test05ModemResetPLSTest() throws InterruptedException {
        Utils.logi(TAG, "-->test05ModemResetPLSTest()");
        if (Utils.isDenaliMd3Solution()) {
            Utils.logi(TAG, "It is DenaliMd3Solution, no need run this case!");
            Utils.logi(TAG, "<--test05ModemResetPLSTest() pass!");
            return;
        }
        mIns.waitForIdleSync();
        boolean isDialogShow = mSolo.searchText(mActivity.getString(R.string.memorydump_done));
        if (!isDialogShow) {
            Utils.logw(TAG, "The memoryDumpDialog is not show, can not do reset!");
            return;
        }
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));
        int i = 0;
        boolean isResetProgressShow =
                mSolo.searchText(mActivity.getString(R.string.reset_modem_msg));
        boolean isModemLogRunning = TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM);
        while (i < 60) { // wait at most 60s
            if (!isResetProgressShow && isModemLogRunning) {
                break;
            }
            Thread.sleep(1000);
            isResetProgressShow = mSolo.searchText(mActivity.getString(R.string.reset_modem_msg));
            isModemLogRunning = TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM);
            i++;
        }
        Utils.logi(TAG, "<--test05ModemResetPLSTest() isResetProgressShow = " + isResetProgressShow
                + ", isModemLogRunning = " + isModemLogRunning);
        assertTrue("After reset modem(60s), the modem log is not running.", !isResetProgressShow
                && isModemLogRunning);
        Utils.logi(TAG, "<--test05ModemResetPLSTest() pass!");
    }

    /**
     * @throws InterruptedException InterruptedException
     */
    /*public void test06TaglogForModemEEPLSTest() throws InterruptedException {
       if (Utils.isDenaliMd3Solution()) {
            Utils.logi(TAG, "It is DenaliMd3Solution, no need run this case!");
            Utils.logi(TAG, "<--test06TaglogForModemEEPLSTest() pass!");
            return;
        }
        Utils.logi(TAG, "-->test06TaglogForModemEEPLSTest()");
        Thread.sleep(15000);
        mIns.waitForIdleSync();
        int i = 0;
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
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog/taglog");
        boolean isAllLogZipOK =
                TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MOBILE, taglogFile)
                        && TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_MODEM, taglogFile)
                        && !TestUtils.isTypeLogInTaglogFolder(Utils.LOG_TYPE_NETWORK, taglogFile);
        assertTrue("Taglog for Modem EE PLS mode failed.", isAllLogZipOK);
        Utils.logi(TAG, "<--test06TaglogForModemEEPLSTest() pass!");
    }*/

}
