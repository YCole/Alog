package com.mediatek.mtklogger.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.util.SparseArray;
import android.util.TypedValue;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.mtklogger.LinearColorBar;
import com.mediatek.mtklogger.MainActivity;
import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.settings.SettingsActivity;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;

/**
 * @author MTK81255
 *
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private static final String TAG = TestUtils.TAG + "/MainActivityTest";
    private Solo mSolo;

    private Instrumentation mIns;

    private Activity mActivity;

    private Context mContext;

    private SparseArray<ImageView> mLogImageViews = new SparseArray<ImageView>();
    private SparseArray<TextView> mLogTextViews = new SparseArray<TextView>();
    private ImageView mModemLogImage;
    private TextView mModemLogText;
    private ImageView mMobileLogImage;
    private TextView mMobileLogText;
    private ImageView mNetworkLogImage;
    private TextView mNetworkLogText;
    private ImageView mGPSLogImage;
    private TextView mGPSLogText;
    private TextView mTimeText;
    private TextView mSavePathText;
    private ToggleButton mStartStopToggleButton;
    private ImageButton mClearLogImageButton;

    /**
     * return void.
     */
    public MainActivityTest() {
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

    private void findViews() {
        mModemLogImage = (ImageView) mSolo.getView(R.id.modemLogImageView);
        mModemLogText = (TextView) mSolo.getView(R.id.modemLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_MODEM, mModemLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_MODEM, mModemLogText);
        mMobileLogImage = (ImageView) mSolo.getView(R.id.mobileLogImageView);
        mMobileLogText = (TextView) mSolo.getView(R.id.mobileLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_MOBILE, mMobileLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_MOBILE, mMobileLogText);
        mNetworkLogImage = (ImageView) mSolo.getView(R.id.networkLogImageView);
        mNetworkLogText = (TextView) mSolo.getView(R.id.networkLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_NETWORK, mNetworkLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_NETWORK, mNetworkLogText);

        // GPS log.
        mGPSLogImage = (ImageView) mSolo.getView(R.id.GPSLogImageView);
        mGPSLogText = (TextView) mSolo.getView(R.id.GPSLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_GPS, mGPSLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_GPS, mGPSLogText);

        mTimeText = (TextView) mSolo.getView(R.id.timeTextView);
        mSavePathText = (TextView) mSolo.getView(R.id.savePathTextView);
        mStartStopToggleButton = (ToggleButton) mSolo.getView(R.id.startStopToggleButton);
        mClearLogImageButton = (ImageButton) mSolo.getView(R.id.clearLogImageButton);
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test01AllLogStopped() throws InterruptedException {
        Utils.logi(TAG, "-->test01AllLogStopped()");
        TestUtils.changeLogRunningStatus(mContext, 23, false);
        mIns.waitForIdleSync();
        findViews();

        assertTrue("Log path is error.", mSavePathText.getText().toString().endsWith("/mtklog/"));

        String logSaveTimeStr1 = mTimeText.getText().toString();
        Thread.sleep(2000);
        String logSaveTimeStr2 = mTimeText.getText().toString();
        boolean isStopped = logSaveTimeStr1.equals(logSaveTimeStr2);
        if (!isStopped) {
            logSaveTimeStr1 = mTimeText.getText().toString();
            Thread.sleep(2000);
            logSaveTimeStr2 = mTimeText.getText().toString();
            isStopped = logSaveTimeStr1.equals(logSaveTimeStr2);
        }
        Utils.logd(TAG, "logSaveTimeStr1 = " + logSaveTimeStr1 + ", logSaveTimeStr2 = "
                + logSaveTimeStr2);
        assertTrue("Log time is not stopped.", isStopped);

        assertFalse("Start/stop button shows error.", mStartStopToggleButton.isChecked());

        boolean isAllLogShowsOK = false;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            isAllLogShowsOK =
                    mLogTextViews
                            .get(logType)
                            .getText()
                            .toString()
                            .equals(mActivity.getString(R.string.log_stop,
                                    mActivity.getString(Utils.LOG_NAME_MAP.get(logType))));
            if (!isAllLogShowsOK) {
                break;
            }
        }
        assertTrue("Some log status shows error.", isAllLogShowsOK);
        Utils.logi(TAG, "<--test01AllLogStopped() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test02AllLogRecording() throws InterruptedException {
        Utils.logi(TAG, "-->test02AllLogRecording()");
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        mIns.waitForIdleSync();
        findViews();

        String logSaveTimeStr1 = mTimeText.getText().toString();
        Thread.sleep(2000);
        String logSaveTimeStr2 = mTimeText.getText().toString();
        boolean isStopped = logSaveTimeStr1.equals(logSaveTimeStr2);
        if (isStopped) {
            logSaveTimeStr1 = mTimeText.getText().toString();
            Thread.sleep(2000);
            logSaveTimeStr2 = mTimeText.getText().toString();
            isStopped = logSaveTimeStr1.equals(logSaveTimeStr2);
        }
        Utils.logd(TAG, "logSaveTimeStr1 = " + logSaveTimeStr1 + ", logSaveTimeStr2 = "
                + logSaveTimeStr2);
        assertFalse("Log time is stopped.", isStopped);

        assertTrue("Start/stop button shows error.", mStartStopToggleButton.isChecked());

        boolean isAllLogShowsOK = false;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            isAllLogShowsOK =
                    mLogTextViews
                            .get(logType)
                            .getText()
                            .toString()
                            .equals(mActivity.getString(R.string.log_start,
                                    mActivity.getString(Utils.LOG_NAME_MAP.get(logType))));
            if (!isAllLogShowsOK) {
                break;
            }
        }
        assertTrue("Some log status shows error.", isAllLogShowsOK);
        Utils.logi(TAG, "<--test02AllLogRecording() pass!");
    }

    /**
     * @throws InterruptedException
     *             InterruptedException
     */
    public void test03ClearLogs() throws InterruptedException {
        Utils.logi(TAG, "-->test03ClearLogs()");
        TestUtils.changeLogRunningStatus(mContext, 23, false);
        File taglogFile = new File(Utils.getCurrentLogPath(mContext) + "/mtklog");
        Utils.deleteFile(taglogFile);
        Thread.sleep(5000);
        mIns.waitForIdleSync();
        findViews();
        Utils.logi(TAG, "mClearLogImageButton.isEnabled() = " + mClearLogImageButton.isEnabled());
        // assertFalse("Clear log button status is not disabled when mtklog folder is empty.",
        // mClearLogImageButton.isEnabled());

        TestUtils.changeLogRunningStatus(mContext, 1, true);
        TestUtils.changeLogRunningStatus(mContext, 1, false);
        Thread.sleep(5000);
        mIns.waitForIdleSync();
        findViews();
        Utils.logi(TAG, "mClearLogImageButton.isEnabled() = " + mClearLogImageButton.isEnabled());
        // assertTrue("Clear log button status is not enabled when mtklog folder is not empty.",
        // mClearLogImageButton.isEnabled());

        mSolo.clickOnView(mClearLogImageButton);
        assertTrue("Mobile log folder is not shown.",
                mSolo.searchText(mActivity.getString(R.string.mobile_log_name)));

        mSolo.clickOnMenuItem(mActivity.getString(R.string.cancel_menu));
        assertTrue("Clear log not return to main activity.",
                mSolo.searchText(mActivity.getString(R.string.app_name)));

        mSolo.clickOnView(mClearLogImageButton);
        mSolo.clickOnMenuItem(mActivity.getString(R.string.clear_all_menu));
        mSolo.clickOnButton(mActivity.getString(android.R.string.cancel));
        Thread.sleep(1000);
        assertFalse("Cancel clear all logs failed.",
                mSolo.searchText(mActivity.getString(R.string.message_delete_all_log)));

        mSolo.clickOnMenuItem(mActivity.getString(R.string.clear_all_menu));
        mSolo.clickOnButton(mActivity.getString(android.R.string.ok));
        int i = 0;
        boolean isDialogShow = mSolo.searchText(mActivity.getString(R.string.clear_dialog_content));
        Thread.sleep(2000);
        while (i < 60) { // wait at most 60s
            if (!isDialogShow) {
                break;
            }
            Thread.sleep(1000);
            isDialogShow = mSolo.searchText(mActivity.getString(R.string.clear_dialog_content));
            i++;
        }
        assertTrue("After clear all logs, the activity is not return to Main activity.",
                mSolo.searchText(mActivity.getString(R.string.app_name)));
        Utils.logi(TAG, "<--test03ClearLogs() pass!");
    }
}
