package com.mediatek.mtklogger.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.mtklogger.LogFolderListActivity;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.utils.Utils;
import com.mtk.at.BasicFuncTest;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author MTK81255
 *
 */
public class ClearLogTest extends ActivityInstrumentationTestCase2<LogFolderListActivity> {
    private static final String TAG = TestUtils.TAG + "/ClearLogTest";

    private Solo mSolo;

    private Instrumentation mIns;

    private Activity mActivity;

    private Context mContext;

    /**
     * return void.
     */
    public ClearLogTest() {
        super(LogFolderListActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
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
    @BasicFuncTest
    public void test01ClearAllLogStop() throws InterruptedException {
        Utils.logi(TAG, "-->test01ClearAllLogStop()");
        TestUtils.changeLogRunningStatus(mContext, 23, false);
        mIns.waitForIdleSync();
        mSolo.clickOnMenuItem(mActivity.getString(R.string.clear_all_menu));
        mIns.waitForIdleSync();
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));

        int i = 0;
        while (i < 60 && isExistSomeLog()) { // wait at most 60s
            Thread.sleep(1000);
            i++;
        }
        assertFalse("After clear all log folder(60s), there is still some log folder exist.",
                isExistSomeLog());
        Utils.logi(TAG, "<--test01ClearAllLogStop() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test02ClearAllLogRunning() throws InterruptedException {
        Utils.logi(TAG, "-->test02ClearAllLogRunning()");
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        mSolo.clickOnMenuItem(mActivity.getString(R.string.clear_all_menu));
        mIns.waitForIdleSync();
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));

        int i = 0;
        while (i < 10 && isExistSomeLog()) { // wait at most 10s
            Thread.sleep(1000);
            i++;
        }
        assertTrue("When log running, after clear all logs, there is need some log folder exist.",
                isExistSomeLog());
        Utils.logi(TAG, "<--test02ClearAllLogRunning() pass!");
    }

    private boolean isExistSomeLog() {
        String rootPath = Utils.getCurrentLogPath(mActivity) + "/mtklog";
        boolean isMobileLogExist = !isFolderEmpty(rootPath + "/mobilelog");
        boolean isModemLogExist =
                !isFolderEmpty(rootPath + "/mdlog") || !isFolderEmpty(rootPath + "/mdlog1")
                        || !isFolderEmpty(rootPath + "/mdlog3");
        boolean isNetworkLogExist = !isFolderEmpty(rootPath + "/netlog");
        boolean isTagLogLogExist = !isFolderEmpty(rootPath + "/taglog");
        Utils.logd(TAG, "-->isExistSomeLog(), isMobileLogExist=" + isMobileLogExist
                + ", isModemLogExist=" + isModemLogExist + ", isNetworkLogExist="
                + isNetworkLogExist + ", isTagLogLogExist=" + isTagLogLogExist);
        return isMobileLogExist || isModemLogExist || isNetworkLogExist || isTagLogLogExist;
    }

    private boolean isFolderEmpty(String folderPath) {
        File folder = new File(folderPath);
        FilenameFilter fnFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                if (filename.equals(Utils.LOG_TREE_FILE)) {
                    return false;
                }
                return true;
            }
        };
        return !(folder.exists() && folder.list(fnFilter) != null
                && folder.list(fnFilter).length > 0);
    }

}
