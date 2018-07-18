package com.mediatek.mtklogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.settings.MobileLogSettings;
import com.mediatek.mtklogger.settings.SettingsActivity;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.io.FileFilter;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author MTK81255
 *
 */
public class MainActivity extends Activity {
    private static final String TAG = Utils.TAG + "/MainActivity";

    private static final int ALPHA_FULL = 255;
    private static final int ALPHA_GRAY = 75;

    private static final int TIMER_PERIOD = 1000;
    private static final int START_STOP_TIMER = 20 * TIMER_PERIOD;
    private static final int SDCARD_RATIO_BAR_TIMER = 30 * TIMER_PERIOD;
    private static final int TAGLOG_PROGRESS_DIALOG_SHOW_TIMEOUT = 60 * TIMER_PERIOD;

    private static final int MSG_TIMER = 1;
    private static final int MSG_WAITING_DIALOG_TIMER = 2;
    private static final int MSG_MONITOR_SDCARD_BAR = 3;
//    private static final int DLG_WAITING_SERVICE = 10;
    private static final int TAGLOG_TIMER_CHECK = 11;

    /**
     * At boot time, since MTKLogger service will not start up before receiving
     * boot_completed broadcast, to avoid status confusion, blocking UI until we
     * receive such broadcast. Use this message to monitor boot_completed
     * broadcast coming status
     */
    private static final int MSG_WAITING_SERVICE_READY = 5;
    private static final int MESSAGE_DELAY_WAITING_SERVICE = 500; // ms
    private static final int WAITING_SERVICE_READY_TIMER = 60 * TIMER_PERIOD;

    /**
     * If log exist, clear log button will be enabled, else, disable it. Since
     * IO operation may need a long time, put such check operation into thread
     */
    private static final int MSG_CHECK_LOG_EXIST = 6;

    /**
     * Before enter clear log activity. We need to check current log folder
     * status. Do such things in main thread may hang UI, so need to put them
     * into thread
     */
//    private static final int DLG_CHECKING_LOG_FILES = 11;
    private static final int MSG_SHOW_CHECKING_LOG_DIALOG = 7;
    private static final int MSG_REMOVE_CHECKING_LOG_DIALOG = 8;
    private static final int WAITING_CHECKING_LOG_TIMER = 600 * TIMER_PERIOD;

    private SharedPreferences mDefaultSharedPreferences;
    private SharedPreferences mSharedPreferences;

    private MenuItem mSettingsMenuItem;

    private ImageView mModemLogImage;
    private TextView mModemLogText;
    private ImageView mMobileLogImage;
    private TextView mMobileLogText;
    private ImageView mNetworkLogImage;
    private TextView mNetworkLogText;

    private ImageView mMetLogImage;
    private TextView mMetLogText;

    private ImageView mGPSLogImage;
    private TextView mGPSLogText;

    private SparseArray<ImageView> mLogImageViews = new SparseArray<ImageView>();
    private SparseArray<TextView> mLogTextViews = new SparseArray<TextView>();
    private TextView mTimeText;
    private TextView mSavePathText;
    private LinearColorBar mSdcardRatioBar;
    private TextView mStorageChartLabelText;
    private TextView mUsedStorageText;
    private TextView mFreeStorageText;
    private ImageButton mTagImageButton;
    private ToggleButton mStartStopToggleButton;
    private ImageButton mClearLogImageButton;

    private String mSDCardPathStr;
    private String mSavePathStr;
    private int mAvailableStorageSize = -1;

    private Timer mTimer;
    private long mTimeMillisecond = 0;
    float mNormalTimeTextSize = 1.0f;
    private boolean mIsUpdateTimerFirst = false;

    private ProgressDialog mWaitingDialog;
    private Timer mMonitorTimer;

    private boolean mAlreadyNotifyUserSDNotReady = false;
    RefreshStorageAsyncTask mRefreshStorageTask = null;
    private ProgressDialog mTagProgressDialog;

    private UpdateUITask mUpdateUITask = null;

    private Context mContext;

    /**
     * Use this flag to make sure taglog dialog not been clicked twice.
     */
    private boolean mIsTaglogClicked = false;

    private boolean mIsAutoTest = false;

    /**
     * Flag for whether we are now calculating storage used ratio, if already
     * being doing so, blocking duplicated refresh request.
     */
    private boolean mWaitingRefreshStatusBar = false;

    private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.ACTION_LOG_STATE_CHANGED)) {
                Utils.logi(TAG, "ACTION_LOG_STATE_CHANGED");
                stopWaitingDialog();
                String failReason = intent.getStringExtra(Utils.EXTRA_FAIL_REASON);
                if (failReason != null && !"".equals(failReason)) {
                    Utils.logd(TAG, "ACTION_LOG_STATE_CHANGED : failReason = " + failReason);
                    Toast.makeText(MainActivity.this, analyzeReason(failReason), Toast.LENGTH_SHORT)
                            .show();
                }
                updateUI();
            } else if (action.equals(Utils.EXTRA_RUNNING_STAGE_CHANGE_EVENT)) {
                Utils.logi(TAG, "EXTRA_RUNNING_STAGE_CHANGE_EVENT");
                try {
                    changeWaitingDialog(MTKLoggerServiceManager.getInstance().getService()
                            .getCurrentRunningStage());
                } catch (ServiceNullException e) {
                    return;
                }
            }
        }
    };

    /**
     * When storage status changed, need to update U to check whether need to
     * refresh current log path.
     */
    private BroadcastReceiver mStorageStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Utils.logi(TAG, "Storage status changed, update UI now");
            mMessageHandler.sendEmptyMessage(MSG_MONITOR_SDCARD_BAR);
            updateUI();
        }
    };

    private String analyzeReason(String reason) {
        String rsReason = "";
        for (int i = 0; i < reason.length(); ) {
            int index = reason.indexOf(":", i);
            if (index == -1) {
                break;
            }
            int logType = -1;
            try {
                logType = Integer.parseInt(reason.substring(i, index));
            } catch (NumberFormatException e) {
                logType = -1;
            }
            if (!Utils.LOG_TYPE_SET.contains(logType)) {
                break;
            }
            i = index + 1;
            index = reason.indexOf(";", i);
            if (index == -1) {
                break;
            }
            String reasonType = reason.substring(i, index);

            // For error reason return start
            int reasonKey = 0;
            try {
                reasonKey = Utils.FAIL_REASON_DETAIL_MAP.get(reasonType);
            } catch (NullPointerException e) {
                Utils.logd(TAG, "NullPointerException;");
                reasonKey = -1;
                rsReason += getString(Utils.LOG_NAME_MAP.get(logType)) + " : " + reasonType + "\n";
                Utils.logd(TAG, "analyzeReason:after " + rsReason);
                return rsReason;
            }
            if (reasonKey == -1) {
                i = index + 1;
                break;
            }
            // For error reason return end
            rsReason +=
                    getString(Utils.LOG_NAME_MAP.get(logType)) + " : " + getString(reasonKey)
                            + "\n";
            i = index + 1;
        }
        return rsReason;
    }

    private void changeWaitingDialog(int currentRunningStage) {
        dismissTagProgressDialog();
        Utils.logi(TAG, "changeWaitingDialog() -> currentRunningStage is " + currentRunningStage);
        if (currentRunningStage == Utils.RUNNING_STAGE_IDLE) {
            stopWaitingDialog();
            try {
                if (mAvailableStorageSize <= Utils.DEFAULT_STORAGE_WATER_LEVEL
                        && mAvailableStorageSize > Utils.RESERVED_STORAGE_SIZE
                        && MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning()) {
                    alertLowStorageDialog();
                }
            } catch (ServiceNullException e) {
                return;
            }
        } else {
            String title = "";
            String message = "";
            int timeout = 0;
            if (currentRunningStage == Utils.RUNNING_STAGE_STARTING_LOG) {
                title = getString(R.string.waiting_dialog_title_start_log);
                message = getString(R.string.waiting_dialog_message_start_log);
                timeout = START_STOP_TIMER;
            } else if (currentRunningStage == Utils.RUNNING_STAGE_STOPPING_LOG) {
                title = getString(R.string.waiting_dialog_title_stop_log);
                message = getString(R.string.waiting_dialog_message_stop_log);
                timeout = START_STOP_TIMER;
            } else if (currentRunningStage == Utils.RUNNING_STAGE_RESTARTING_LOG) {
                title = getString(R.string.waiting_dialog_title_restart_log);
                message = getString(R.string.waiting_dialog_message_restart_log);
                timeout = START_STOP_TIMER * 2;
            } else if (currentRunningStage == Utils.RUNNING_STAGE_POLLING_LOG) {
                title = getString(R.string.waiting_dialog_title_poll_log);
                message = getString(R.string.waiting_dialog_message_poll_log);
            } else if (currentRunningStage == Utils.RUNNING_STAGE_FLUSHING_LOG) {
                title = getString(R.string.waiting_dialog_title_flush_log);
                message = getString(R.string.waiting_dialog_message_flush_log);
            }
            showWaitingDialog(title, message, timeout);
        }
    }

    private void showWaitingDialog(String title, String message, int timeout) {
        // Before show dialog, our process may be killed by system, then the
        // dialog.show() method
        // will pop up JE. Do such check before dialog show
        boolean isFinishingFlag = isFinishing();
        Utils.logv(TAG, "Before show dialog, isFinishingFlag=" + isFinishingFlag);
        if (mWaitingDialog == null && !isFinishingFlag) {
            Utils.logi(TAG, "showWaitingDialog() -> title = " + title
                    + ", message = " + message + ", timeout = " + timeout);
            mWaitingDialog = ProgressDialog.show(MainActivity.this, title, message, true, false);
            mMessageHandler.removeMessages(MSG_WAITING_DIALOG_TIMER);
            if (timeout > 0) {
                mMessageHandler.sendMessageDelayed(
                        mMessageHandler.obtainMessage(MSG_WAITING_DIALOG_TIMER, message), timeout);
            }
        }
    }

    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_TIMER:
                mTimeMillisecond += 1;
                String timeString = calculateTimer();
                mTimeText.setText(timeString);
                int timeLength = timeString.length(); // 0:00:00-9999:59:59
                float rate = (17 - timeLength) / 10f;
                mTimeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mNormalTimeTextSize * rate);
                break;
            case TAGLOG_TIMER_CHECK:
                Utils.logw(TAG,
                        "mMessageHandler->handleMessage() Tag Progress Dialog shows timeout,"
                        + " maybe some error happended for manual TagLog!");
                dismissTagProgressDialog();
                break;
            case MSG_WAITING_DIALOG_TIMER:
                String message = "";
                if (msg.obj != null && msg.obj instanceof String) {
                    message = (String) msg.obj;
                }
                Utils.logd(TAG,
                        "mMessageHandler->handleMessage() "
                        + "msg.what == MSG_WAITING_DIALOG_TIMER, msg.obj = " + message);
                if (message.equals(getString(R.string.waiting_service_dialog_message))) {
                    Utils.setBootCompleteDone();
                    mSharedPreferences.edit().putLong(
                            Utils.KEY_BEGIN_RECORDING_TIME, SystemClock.elapsedRealtime()).apply();
                    updateUI();
                } else {
                    stopWaitingDialog();
                }
                break;
            case MSG_MONITOR_SDCARD_BAR:
                Utils.logd(TAG,
                        "mMessageHandler->handleMessage() msg.what == MSG_MONITOR_SDCARD_BAR");
                refreshSdcardBar();
                break;
            case MSG_WAITING_SERVICE_READY:
                boolean isServiceReady = true;
                try {
                    isServiceReady = (MTKLoggerServiceManager.getInstance().getService() != null);
                } catch (ServiceNullException e) {
                    isServiceReady = false;
                }
                if (!Utils.isBootCompleteDone() || !isServiceReady) {
                    showWaitingDialog("", getString(R.string.waiting_service_dialog_message),
                            WAITING_SERVICE_READY_TIMER);
                    mMessageHandler.removeMessages(MSG_WAITING_SERVICE_READY);
                    mMessageHandler.sendMessageDelayed(
                            mMessageHandler.obtainMessage(MSG_WAITING_SERVICE_READY),
                            MESSAGE_DELAY_WAITING_SERVICE);
                    break;
                } else {
                    Utils.logi(TAG, "Log service is ready, release UI blocking.");
                    stopWaitingDialog();
                    updateUI();
                }
                break;
            case MSG_CHECK_LOG_EXIST:
                Utils.logi(TAG, "Receive check existing log folder done message," + " result="
                        + msg.obj);
                boolean isLogExists = (Boolean) msg.obj;
                Utils.logv(TAG, "isLogExists=" + isLogExists);
                if (isLogExists) {
                    mClearLogImageButton.setEnabled(true);
                    mClearLogImageButton.setImageAlpha(ALPHA_FULL);
                } else {
                    mClearLogImageButton.setEnabled(false);
                    mClearLogImageButton.setImageAlpha(ALPHA_GRAY);
                }
                break;
            case MSG_SHOW_CHECKING_LOG_DIALOG:
                Utils.logv(TAG, "Show waiting checking log files dialog now.");
                showWaitingDialog("", getString(R.string.waiting_checking_log_dialog_message),
                        WAITING_CHECKING_LOG_TIMER);
                break;
            case MSG_REMOVE_CHECKING_LOG_DIALOG:
                Utils.logv(TAG, "Remove waiting checking log files dialog now.");
                stopWaitingDialog();
                break;
            default:
                Utils.logw(TAG, "Unknown message");
                break;
            }
        }
    };

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Utils.logi(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mContext = this;
        findViews();
        initViews();
        setListeners();
    }

    @Override
    protected void onResume() {
        Utils.logi(TAG, "-->onResume");

        IntentFilter serviceIntent = new IntentFilter();
        serviceIntent.addAction(Utils.ACTION_LOG_STATE_CHANGED);
        serviceIntent.addAction(Utils.EXTRA_RUNNING_STAGE_CHANGE_EVENT);
        registerReceiver(mServiceReceiver, serviceIntent,
                "android.permission.DUMP", null);

        IntentFilter sdStatusIntentFilter = new IntentFilter();
        sdStatusIntentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        sdStatusIntentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        sdStatusIntentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        sdStatusIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        sdStatusIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        sdStatusIntentFilter.addDataScheme("file");
        registerReceiver(mStorageStatusReceiver, sdStatusIntentFilter,
                "android.permission.DUMP", null);
        setButtonStatus(false);
        updateUI();

        monitorSdcardRatioBar();
        super.onResume();
    }

    @Override
    protected void onPause() {
        Utils.logi(TAG, "onPause");
        super.onPause();
        if (mMonitorTimer != null) {
            mMonitorTimer.cancel();
            mMonitorTimer = null;
        }
        stopTimer();
        try {
            unregisterReceiver(mServiceReceiver);
            unregisterReceiver(mStorageStatusReceiver);
        } catch (IllegalArgumentException e) {
            Utils.logd(TAG, "unregisterReceiver failed!");
        }
        mMessageHandler.removeMessages(MSG_WAITING_SERVICE_READY);
        stopWaitingDialog();
        dismissTagProgressDialog();
        if (mRefreshStorageTask != null) {
            mRefreshStorageTask.cancel(true);
            mWaitingRefreshStatusBar = false;
        }
        if (mUpdateUITask != null) {
            mUpdateUITask.cancel(true);
        }
    }

    @Override
    protected void onStop() {
        Utils.logi(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Utils.logi(TAG, "onDestroy");
        dismissTagProgressDialog();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mSettingsMenuItem = menu.add(getString(R.string.menu_settings));
        mSettingsMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mSettingsMenuItem.setIcon(R.drawable.ic_settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!Utils.isDeviceOwner()) {
            Utils.logi(TAG, "In multi user case, only device owner can change log configuration");
            Toast.makeText(this, R.string.warning_no_permission_for_setting, Toast.LENGTH_SHORT)
                    .show();
            return true;
        }
        Utils.logi(TAG, "SettingsActivity open!");
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setClass(this, SettingsActivity.class);
        startActivity(intent);
        return true;
    }

    private void findViews() {
        mModemLogImage = (ImageView) findViewById(R.id.modemLogImageView);
        mModemLogText = (TextView) findViewById(R.id.modemLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_MODEM, mModemLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_MODEM, mModemLogText);
        mMobileLogImage = (ImageView) findViewById(R.id.mobileLogImageView);
        mMobileLogText = (TextView) findViewById(R.id.mobileLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_MOBILE, mMobileLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_MOBILE, mMobileLogText);
        mNetworkLogImage = (ImageView) findViewById(R.id.networkLogImageView);
        mNetworkLogText = (TextView) findViewById(R.id.networkLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_NETWORK, mNetworkLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_NETWORK, mNetworkLogText);
        // met log
        mMetLogImage = (ImageView) findViewById(R.id.metLogImageView);
        mMetLogText = (TextView) findViewById(R.id.metLogTextView);

        // GPS log.
        mGPSLogImage = (ImageView) findViewById(R.id.GPSLogImageView);
        mGPSLogText = (TextView) findViewById(R.id.GPSLogTextView);
        mLogImageViews.put(Utils.LOG_TYPE_GPS, mGPSLogImage);
        mLogTextViews.put(Utils.LOG_TYPE_GPS, mGPSLogText);

        mTimeText = (TextView) findViewById(R.id.timeTextView);
        mSavePathText = (TextView) findViewById(R.id.savePathTextView);

        mSdcardRatioBar = (LinearColorBar) findViewById(R.id.storage_color_bar);
        mStorageChartLabelText = (TextView) findViewById(R.id.storageChartLabel);
        mUsedStorageText = (TextView) findViewById(R.id.usedStorageText);
        mFreeStorageText = (TextView) findViewById(R.id.freeStorageText);
        mTagImageButton = (ImageButton) findViewById(R.id.tagImageButton);
        mStartStopToggleButton = (ToggleButton) findViewById(R.id.startStopToggleButton);
        mClearLogImageButton = (ImageButton) findViewById(R.id.clearLogImageButton);
    }

    private void initViews() {
        Utils.logv(TAG, "-->initViews()");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences = getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        // mSharedPreferences.edit().putBoolean(Utils.MET_LOG_ENABLE,
        // false).commit();
        if (mSharedPreferences.getBoolean(Utils.MET_LOG_ENABLE, false) != true) {
            mMetLogImage.setVisibility(View.GONE);
            mMetLogText.setVisibility(View.GONE);

        } else {
            mLogImageViews.put(Utils.LOG_TYPE_MET, mMetLogImage);
            mLogTextViews.put(Utils.LOG_TYPE_MET, mMetLogText);
            mMetLogImage.setVisibility(View.VISIBLE);
            mMetLogText.setVisibility(View.VISIBLE);
        }

        float fontScale = getResources().getConfiguration().fontScale;
        Utils.logd(TAG, "fontScale = " + fontScale);
        mIsUpdateTimerFirst = true;
        mTimeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTimeText.getTextSize() / fontScale);
        for (Integer logType : Utils.LOG_TYPE_SET) {
            TextView textView = mLogTextViews.get(logType);
            if (textView == null) {
                continue;
            }
            textView.setText(
                    getString(R.string.log_stop, getString(Utils.LOG_NAME_MAP.get(logType))));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mLogTextViews.get(logType).getTextSize() / fontScale);
        }
        mSavePathText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mSavePathText.getTextSize()
                / fontScale);
        mUsedStorageText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                mUsedStorageText.getTextSize() / fontScale);
        mFreeStorageText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                mFreeStorageText.getTextSize() / fontScale);
    }

    private void alertLowStorageDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.low_storage_warning_dialog_title)
                .setMessage(
                        getString(R.string.low_storage_warning_dialog_msg,
                                Utils.DEFAULT_STORAGE_WATER_LEVEL))
                .setPositiveButton(android.R.string.ok,
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
    }

    private void setListeners() {
        mStartStopToggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.logi(TAG, "StartStopToggleButton button is clicked!");
                if (v instanceof ToggleButton) {
                    final ToggleButton button = (ToggleButton) v;
                    final boolean isChecked = button.isChecked();
                    Utils.logd(TAG, "StartStopToggleButton button is checked ? " + isChecked);
                    if (!Utils.isBootCompleteDone()) {
                        Utils.logw(TAG, "Service Manager is not init!");
                        button.setChecked(!isChecked);
                        mMessageHandler.sendEmptyMessage(MSG_WAITING_SERVICE_READY);
                        return;
                    }
                    if (!Utils.isDeviceOwner()) {
                        Toast.makeText(mContext, R.string.info_not_device_owner,
                                Toast.LENGTH_LONG).show();
                        button.setChecked(!isChecked);
                        return;
                    }
                    String waitSDStatusStr =
                            mSharedPreferences.getString(Utils.KEY_WAITING_SD_READY_REASON, "");
                    if (!TextUtils.isEmpty(waitSDStatusStr)) {
                        Utils.logi(TAG, "At click start button time, waitSDStatusStr="
                                + waitSDStatusStr + ", ignore start command now");
                        button.setChecked(!isChecked);
                        Toast.makeText(MainActivity.this, getString(R.string.info_wait_sd_ready),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isChecked) {
                        if (Utils.getAllEnabledLog() == 0) {
                            button.setChecked(!isChecked);
                            Utils.logw(TAG, "No log type was enabled in settings page.");
                            Toast.makeText(MainActivity.this, getString(R.string.no_log_on),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!SystemProperties.get("ro.aee.build.info", "").equalsIgnoreCase("mtk")
                                && Utils.BUILD_TYPE.equals("user")) {
                            Utils.logi(TAG, "For customer user load,"
                                    + " show start log confirm dialog");
                            AlertDialog alertDialog = new AlertDialog.Builder(
                                    mContext)
                                    .setTitle(R.string.start_log_confirm_dlg_title)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setMessage(R.string.message_start_log_confirm)
                                    .setPositiveButton(android.R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                                    startRecording(Utils.LOG_TYPE_ALL);
                                                }
                                            })
                                    .setNegativeButton(android.R.string.cancel,
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                                    Utils.logw(TAG,
                                                            "Do not start logs after confime.");
                                                    button.setChecked(!isChecked);
                                                    dialog.dismiss();
                                                }
                                            }).create();
                            alertDialog.setCancelable(false);
                            alertDialog.show();
                            return;
                        } else {
                            startRecording(Utils.LOG_TYPE_ALL);
                        }
                    } else {
                        if (!SystemProperties.get("ro.aee.build.info", "").equalsIgnoreCase("mtk")
                                && Utils.BUILD_TYPE.equals("user")) {
                            Utils.logi(TAG, "For customer user load,"
                                    + " show stop log confirm dialog");
                            AlertDialog alertDialog = new AlertDialog.Builder(
                                    mContext)
                                    .setTitle(R.string.stop_log_confirm_dlg_title)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setMessage(R.string.message_stop_log_confirm)
                                    .setPositiveButton(android.R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                                    stopRecording(Utils.LOG_TYPE_ALL);
                                                }
                                            }).create();
                            alertDialog.setCancelable(false);
                            alertDialog.show();
                            return;
                        } else {
                            stopRecording(Utils.LOG_TYPE_ALL);
                        }
                    }
                }
            }
        });

        mClearLogImageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Utils.logi(TAG, "ClearLogs button is clicked!");
                clearLogs();
            }
        });

        mTagImageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Utils.logi(TAG, "Taglog button is clicked!");
                tagLogs();
            }
        });
    }

    private void startRecording(final int logType) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MTKLoggerServiceManager.getInstance().getService()
                            .startRecording(logType, Utils.LOG_START_STOP_REASON_FROM_UI);
                } catch (ServiceNullException e) {
                    return;
                }
            }
        }).start();
        changeWaitingDialog(
                Utils.RUNNING_STAGE_STARTING_LOG);
    }

    private void stopRecording(final int logType) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MTKLoggerServiceManager.getInstance().getService()
                            .stopRecording(logType, Utils.LOG_START_STOP_REASON_FROM_UI);
                } catch (ServiceNullException e) {
                    return;
                }
            }
        }).start();
        changeWaitingDialog(
                Utils.RUNNING_STAGE_STOPPING_LOG);
    }

    /**
     * @author MTK81255
     *
     */
    private class UpdateUITask extends AsyncTask<Void, Void, Void> {
        // Do the long-running work in here
        @Override
        protected Void doInBackground(Void... params) {
            // Update for Save path
            mSDCardPathStr = Utils.getCurrentLogPath(mContext);
            mAvailableStorageSize = Utils.getAvailableStorageSize(mSDCardPathStr);
            Utils.logd(TAG, " mSDCardPathStr=" + mSDCardPathStr + ", mAvailableStorageSize = "
                    + mAvailableStorageSize);
            mSavePathStr = mSDCardPathStr + Utils.MTKLOG_PATH;
            mIsAutoTest = Utils.isAutoTest();
            return null;
        }

        // This is called when doInBackground() is finished
        @Override
        protected void onPostExecute(Void result) {
            updateUI();
        }

        private void updateUI() {
            Utils.logi(TAG, "-->updateUI(), Update UI Start");
            try {
                changeWaitingDialog(
                        MTKLoggerServiceManager.getInstance().getService()
                        .getCurrentRunningStage());
            } catch (ServiceNullException e) {
                return;
            }
            mSavePathText.setText(getString(R.string.log_path_str) + ": " + mSavePathStr);
            mStorageChartLabelText.setText(Utils.getLogPathTypeLabelRes());

            if (mSharedPreferences.getBoolean(Utils.MET_LOG_ENABLE, false) != true) {
                if (mLogImageViews.get(Utils.LOG_TYPE_MET) != null) {
                    mLogImageViews.removeAt(Utils.LOG_TYPE_MET);
                    mLogTextViews.removeAt(Utils.LOG_TYPE_MET);
                }
                mMetLogImage.setVisibility(View.GONE);
                mMetLogText.setVisibility(View.GONE);

            } else {
                mLogImageViews.put(Utils.LOG_TYPE_MET, mMetLogImage);
                mLogTextViews.put(Utils.LOG_TYPE_MET, mMetLogText);
                mMetLogImage.setVisibility(View.VISIBLE);
                mMetLogText.setVisibility(View.VISIBLE);
            }
            boolean isStart = false;
            // Update for logs status of UI
            for (Integer logType : Utils.LOG_TYPE_SET) {
                ImageView logImage = mLogImageViews.get(logType);
                TextView logText = mLogTextViews.get(logType);
                if (logImage == null || logText == null) {
                    continue;
                }
                boolean isLogStart = true;
                try {
                    isLogStart = MTKLoggerServiceManager.getInstance().getService()
                            .isTypeLogRunning(logType);
                } catch (ServiceNullException e) {
                    isLogStart = false;
                }
                if (isLogStart) {
                    isStart = true;
                    logImage.setImageResource(R.drawable.log_status_red);
                    logText.setText(
                            getString(R.string.log_start,
                                    getString(Utils.LOG_NAME_MAP.get(logType))));
                    logImage.setImageAlpha(ALPHA_FULL);
                    logText.setAlpha(ALPHA_FULL);
                } else {
                    boolean isLogOn =
                            mDefaultSharedPreferences.getBoolean(
                                    SettingsActivity.KEY_LOG_SWITCH_MAP.get(logType), true);
                    logImage.setImageResource(R.drawable.log_status_grey);
                    logText.setText(
                            getString(isLogOn ? R.string.log_stop : R.string.log_unselected,
                                    getString(Utils.LOG_NAME_MAP.get(logType))));
                    logImage.setImageAlpha(isLogOn ? ALPHA_FULL : ALPHA_GRAY);
                    logText.setAlpha(isLogOn ? ALPHA_FULL : ALPHA_GRAY);
                }
            }
            mStartStopToggleButton.setChecked(isStart);
            // Update for record time refresh
            if (isStart) {
                startTimer();
            } else {
                stopTimer();
            }
            // Update status bar
            try {
                MTKLoggerServiceManager.getInstance().getService().udpateStatusBarForAllLogs();
            } catch (ServiceNullException e) {
                Utils.logw(TAG, "udpateStatusBarForAllLogs error for service is null!");
            }
            // Update for tag log button
            boolean isTagLogEnabled = false;
            if (!Utils.BUILD_TYPE.equals("eng")) {
                Utils.logd(TAG, "Build type is not eng");
                isTagLogEnabled = mSharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false);
            } else {
                Utils.logd(TAG, "Build type is eng");
                isTagLogEnabled = mSharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, true);
            }
            if (isStart && isTagLogEnabled) {
                mTagImageButton.setEnabled(true);
                mTagImageButton.setImageAlpha(ALPHA_FULL);
            } else {
                mTagImageButton.setEnabled(false);
                mTagImageButton.setImageAlpha(ALPHA_GRAY);
            }

            // Disable clear log button until check log file done
            mClearLogImageButton.setEnabled(false);
            mClearLogImageButton.setImageAlpha(ALPHA_GRAY);

            // Add for notify user we are waiting SD ready at boot time,
            // and we need to avoid frequently call and show duplicated message
            // to user
            String waitSDStatusStr =
                    mSharedPreferences.getString(Utils.KEY_WAITING_SD_READY_REASON, "");
            if (!TextUtils.isEmpty(waitSDStatusStr) && !mAlreadyNotifyUserSDNotReady) {
                Utils.logi(TAG, "Still waiting SD ready");
                mAlreadyNotifyUserSDNotReady = true;
                Toast.makeText(mContext, R.string.info_wait_sd_ready, Toast.LENGTH_LONG).show();
            }
            // Update for auto test & not device Owner
            if (mIsAutoTest) {
                setButtonStatus(false);
            } else if (!Utils.isDeviceOwner()) {
                setButtonStatus(false);
                // If is not device owner,
                // need show toast to notify user why start/stop can not used.
                mStartStopToggleButton.setEnabled(true);
            } else {
                if (mSettingsMenuItem != null) {
                    mSettingsMenuItem.setEnabled(true);
                }
                mStartStopToggleButton.setEnabled(true);
                updateClearLogImageButtonStatus();
            }
        }
    }

    private void updateClearLogImageButtonStatus() {
        new Thread(new Runnable() {
            public void run() {
                Utils.logd(TAG, "Start a new thread to check whether existing any log now.");
                boolean isLogExists = false;
                Intent intent = new Intent();
                setRunningFiles(intent);
                for (Integer logType : Utils.LOG_TYPE_SET) {
                    if (logType == Utils.LOG_TYPE_MODEM) {
                        continue; // Check modem log folder in the following
                        // loop
                    }
                    File logRootPath = new File(mSavePathStr + Utils.LOG_PATH_MAP.get(logType));
                    if (logRootPath != null && logRootPath.exists()) {
                        final String runningFilePath = intent.getStringExtra(
                                LogFolderListActivity.EXTRA_FILTER_FILES_KEY.get(logType));
                        Utils.logv(TAG, "runningFilePath = " + runningFilePath);
                        File[] fileList = logRootPath.listFiles(new FileFilter() {
                            @Override
                            public boolean accept(File pathname) {
                                if (pathname.getName().equals(Utils.LOG_TREE_FILE)) {
                                    return false;
                                } else if (runningFilePath != null) {
                                    return runningFilePath.indexOf(pathname.getName()) < 0;
                                }
                                return true;
                            }
                        });
                        if (fileList != null && fileList.length > 0) {
                            isLogExists = true;
                            break;
                        } else {
                            Utils.logi(TAG, "List files in [" + logRootPath.getAbsolutePath()
                                    + "] get a null or empty");
                        }
                    }
                }

                if (!isLogExists) {
                    for (int modemIndex : Utils.MODEM_INDEX_SET) {
                        File modemLogRootFolder =
                                new File(mSavePathStr
                                        + Utils.MODEM_INDEX_FOLDER_MAP.get(modemIndex));
                        if (modemLogRootFolder != null && modemLogRootFolder.exists()) {
                            final String runningModemLogFilePath = intent.getStringExtra(
                                    LogFolderListActivity.EXTRA_MODEM_FILTER_FILES_KEY
                                    .get(modemIndex));
                            Utils.logv(TAG, "runningModemLogFilePath = " + runningModemLogFilePath);
                            File[] mdLogList = modemLogRootFolder.listFiles(new FileFilter() {
                                @Override
                                public boolean accept(File pathname) {
                                    Utils.logv(TAG, "pathname = " + pathname.getName());
                                    if (pathname.getName().equals(Utils.LOG_TREE_FILE)) {
                                        return false;
                                    } else if (runningModemLogFilePath != null) {
                                        return runningModemLogFilePath.indexOf(
                                                pathname.getName()) < 0;
                                    }
                                    return true;
                                }
                            });
                            if (mdLogList != null && mdLogList.length > 0) {
                                isLogExists = true;
                                break;
                            } else {
                                Utils.logv(
                                        TAG,
                                        "List files in ["
                                                + modemLogRootFolder.getAbsolutePath()
                                                + "] get a null or empty");
                            }
                        }
                    }
                }

                File taglogRootPath = new File(mSavePathStr + Utils.TAG_LOG_PATH);
                if (!isLogExists && taglogRootPath != null && taglogRootPath.exists()) {
                    File[] taglogList = taglogRootPath.listFiles();
                    if (taglogList != null && taglogList.length > 0) {
                        isLogExists = true;
                    } else {
                        Utils.logi(TAG, "List files in [" + taglogRootPath.getAbsolutePath()
                                + "] get a null or empty");
                    }
                }
                mMessageHandler.obtainMessage(MSG_CHECK_LOG_EXIST, isLogExists).sendToTarget();

                Utils.logv(TAG, "Check whether existing any log finish, result=" + isLogExists);
            }
        }).start();
    }

    private boolean setRunningFiles(Intent intent) {
        boolean isAnyFilesSet = false;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            boolean isLogStart =
                    (Utils.VALUE_STATUS_RUNNING == mSharedPreferences.getInt(
                            Utils.KEY_STATUS_MAP.get(logType),
                            Utils.VALUE_STATUS_DEFAULT));

            if (isLogStart) {
                if (logType == Utils.LOG_TYPE_MODEM) {
                    String currentMode =
                            mDefaultSharedPreferences
                                    .getString(Utils.KEY_MD_MODE_1, Utils.MODEM_MODE_SD);
                    if (Utils.MODEM_MODE_PLS.equals(currentMode)) {
                        continue;
                    }
                    // Deal with modem log
                    int currentRunningStage;
                    try {
                        currentRunningStage = MTKLoggerServiceManager.getInstance().getService()
                                .getLogRunningStatus(Utils.LOG_TYPE_MODEM);
                    } catch (ServiceNullException e) {
                        currentRunningStage = Utils.LOG_RUNNING_STATUS_UNKNOWN;
                    }
                    Utils.logd(TAG, "modemLogRunningDetailStatus : "
                            + currentRunningStage);

                    for (int modemIndex : Utils.MODEM_INDEX_SET) {
                        boolean isModemRunning =
                                (currentRunningStage & modemIndex) != 0;
                        Utils.logd(TAG, "Modem[" + modemIndex + "] is running?"
                                + isModemRunning);
                        if (isModemRunning) {
                            String parentPath =
                                    mSavePathStr
                                            + Utils.MODEM_INDEX_FOLDER_MAP
                                                    .get(modemIndex);
                            File fileTree =
                                    new File(parentPath + File.separator
                                            + Utils.LOG_TREE_FILE);
                            File logFile =
                                    Utils.getLogFolderFromFileTree(fileTree, false);
                            if (null != logFile && logFile.exists()) {
                                intent.putExtra(
                                        LogFolderListActivity
                                            .EXTRA_MODEM_FILTER_FILES_KEY
                                                .get(modemIndex), logFile
                                                .getAbsolutePath());
                                isAnyFilesSet = true;
                            }
                        }
                    }
                    continue;
                }
                String parentPath = mSavePathStr + Utils.LOG_PATH_MAP.get(logType);
                File fileTree =
                        new File(parentPath + File.separator + Utils.LOG_TREE_FILE);
                File logFile = Utils.getLogFolderFromFileTree(fileTree, false);
                if (null != logFile && logFile.exists()) {
                    intent.putExtra(
                            LogFolderListActivity.EXTRA_FILTER_FILES_KEY.get(logType),
                            logFile.getAbsolutePath());
                    isAnyFilesSet = true;
                }
            }
        }
        return isAnyFilesSet;
    }

    private void setButtonStatus(boolean enabled) {
        if (mSettingsMenuItem != null) {
            mSettingsMenuItem.setEnabled(enabled);
        }
        mStartStopToggleButton.setEnabled(enabled);

        mTagImageButton.setEnabled(enabled);
        mTagImageButton.setImageAlpha(enabled ? ALPHA_FULL : ALPHA_GRAY);
        mClearLogImageButton.setEnabled(enabled);
        mClearLogImageButton.setImageAlpha(enabled ? ALPHA_FULL : ALPHA_GRAY);
    }

    private void startTimer() {
        long startTime =
                mSharedPreferences.getLong(Utils.KEY_BEGIN_RECORDING_TIME,
                        Utils.VALUE_BEGIN_RECORDING_TIME_DEFAULT);
        if (startTime == 0) {
            mTimeMillisecond = 0;
        } else {
            mTimeMillisecond = (SystemClock.elapsedRealtime() - startTime) / 1000;
        }

        String timeString = calculateTimer();
        Utils.loge(TAG, "timeString : " + timeString);
        mTimeText.setText(timeString);
        if (mIsUpdateTimerFirst) {
            mNormalTimeTextSize = mTimeText.getTextSize();
            mIsUpdateTimerFirst = false;
        }
        int timeLength = timeString.length(); // 0:00:00-9999:59:59
        float rate = (17 - timeLength) / 10f;
        mTimeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mNormalTimeTextSize * rate);

        stopTimer();
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mMessageHandler.sendEmptyMessage(MSG_TIMER);
            }
        }, TIMER_PERIOD, TIMER_PERIOD);
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    private String calculateTimer() {
        if (mTimeMillisecond < 0) {
            Utils.logi(TAG, "System time adjust to past, just reset log time to 0,"
                    + " to avoid UI show incorrect");
            mTimeMillisecond = 0;
        }

        int hour = (int) mTimeMillisecond / 3600;
        if (hour > 9999) {
            Utils.loge(TAG, "There is something wrong with time record! The hour is " + hour);
            mTimeMillisecond = 0;
            Utils.loge(TAG, "There is something wrong with time record!");
        }
        int minute = (int) mTimeMillisecond / 60 % 60;
        long second = mTimeMillisecond % 60;
        String timerStr =
                "" + hour + ":" + (minute < 10 ? "0" : "") + minute + ":"
                        + (second < 10 ? "0" : "") + second;

        return timerStr;
    }

    private void monitorSdcardRatioBar() {
        if (mMonitorTimer != null) {
            return;
        }
        mMonitorTimer = new Timer(true);
        mMonitorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mMessageHandler.sendEmptyMessage(MSG_MONITOR_SDCARD_BAR);
            }
        }, 0, SDCARD_RATIO_BAR_TIMER);
    }

    private void refreshSdcardBar() {
        Utils.logd(TAG, "-->refreshSdcardBar()");
        if (mWaitingRefreshStatusBar) {
            Utils.logi(TAG, " Last refresh request not finished yet, just wait a moment.");
        } else {
            mRefreshStorageTask = new RefreshStorageAsyncTask();
            mRefreshStorageTask.execute();
        }
    }

    /**
     * Get storage information need to access SD card, to void blocking UI
     * thread, put it into background task.
     */
    class RefreshStorageAsyncTask extends AsyncTask<Void, Void, Void> {
        private boolean mIsPathOK = false;
        private long mUsedStorageSize;
        private long mFreeStorageSize;
        private long mMtkLogSize;

        @Override
        protected Void doInBackground(Void... params) {
            mWaitingRefreshStatusBar = true;
            mIsPathOK = checkPath();
            if (!mIsPathOK) {
                return null;
            }
            int retryNum = 1;
            while (retryNum <= 3) {
                try {
                    StatFs statFs = new StatFs(mSDCardPathStr);
                    long blockSize = statFs.getBlockSizeLong() / 1024;
                    mFreeStorageSize = statFs.getAvailableBlocksLong() * blockSize;
                    mUsedStorageSize = statFs.getBlockCountLong() * blockSize - mFreeStorageSize;
                    Utils.logd(TAG, " mSDCardPathStr=" + mSDCardPathStr + ", free size="
                            + mFreeStorageSize + "KB, used size=" + mUsedStorageSize + "KB");
                    mMtkLogSize = Utils.getFileSize(mSavePathStr);
                    // For show GradientPaint, set the minimum size of
                    // mtkLogSize == 1024bit
                    if (mMtkLogSize <= 1024) {
                        mMtkLogSize = 1024;
                    }
                    return null;
                } catch (IllegalArgumentException e) {
                    Utils.loge(TAG, "Fail to get storage info from [" + mSDCardPathStr
                            + "] by StatFs, try again(index=" + retryNum + ").", e);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                retryNum++;
            }
            Utils.loge(TAG, "Fail to get [" + mSDCardPathStr + "]storage info through StatFs");
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Utils.logv(TAG, " -->RefreshStorageAsyncTask.refreshStatusBar()");
            if (!mIsPathOK) {
                Utils.logd(TAG, "Selected log path is unavailable, reset storage info");
                Toast.makeText(MainActivity.this,
                        getString(R.string.log_path_not_exist, mSDCardPathStr), Toast.LENGTH_SHORT)
                        .show();
                mSdcardRatioBar.setGradientPaint(0, 0);
                mSdcardRatioBar.setRatios(0, 0, 0);
                mUsedStorageText.setText("0M " + getString(R.string.log_used_storage));
                mFreeStorageText.setText("0M " + getString(R.string.log_free_storage));
                mWaitingRefreshStatusBar = false;
                return;
            }
            mSdcardRatioBar.setShowingGreen(false);
            // Add a tiny extra in storage size to avoid divide 0 exception
            float ratio = 1.0f * mUsedStorageSize / (mFreeStorageSize + mUsedStorageSize + 1);
            mSdcardRatioBar.setGradientPaint(ratio - 1.0f * (mMtkLogSize / 1024)
                    / (mFreeStorageSize + mUsedStorageSize + 1), ratio);
            mSdcardRatioBar.setRatios(0, ratio, 1 - ratio);
            mUsedStorageText.setText((int) (mUsedStorageSize / 1024) + "M "
                    + getString(R.string.log_used_storage));
            mFreeStorageText.setText((int) (mFreeStorageSize / 1024) + "M "
                    + getString(R.string.log_free_storage));
            mWaitingRefreshStatusBar = false;
        }

        @Override
        protected void onCancelled(Void result) {
            Utils.logi(TAG, "RefreshStorageAsyncTask is cancelled.");
            mWaitingRefreshStatusBar = false;
            super.onCancelled(result);
        }
    }

    private synchronized void stopWaitingDialog() {
        mMessageHandler.removeMessages(MSG_WAITING_SERVICE_READY);
        mMessageHandler.removeMessages(MSG_WAITING_DIALOG_TIMER);
        if (mWaitingDialog != null) {
            try {
                mWaitingDialog.cancel();
                mWaitingDialog = null;
            } catch (IllegalArgumentException e) {
                mWaitingDialog = null;
                Utils.logd(TAG, "exception happened when cancel waitingdialog.");
            }
        }
    }

    private void clearLogs() {
        if (!checkPath()) {
            return;
        }
        final Intent intent = new Intent(this, LogFolderListActivity.class);
        intent.putExtra(LogFolderListActivity.EXTRA_ROOTPATH_KEY, mSavePathStr);
        if (mStartStopToggleButton.isChecked()) {
            new Thread(new Runnable() {
                public void run() {
                    mMessageHandler.sendEmptyMessage(MSG_SHOW_CHECKING_LOG_DIALOG);
                    setRunningFiles(intent);
                    mMessageHandler.sendEmptyMessage(MSG_REMOVE_CHECKING_LOG_DIALOG);
                    Utils.logv(TAG, "Check log file done, start clear log activity now.");
                    intent.setClass(mContext, LogFolderListActivity.class);
                    MainActivity.this.startActivity(intent);
                }
            }).start();
        } else {
            Utils.logv(TAG, "No need to check log file, start clear log activity directly.");
            intent.setClass(mContext, LogFolderListActivity.class);
            startActivity(intent);
        }
        mMessageHandler.sendEmptyMessage(MSG_MONITOR_SDCARD_BAR);
    }

    private void tagLogs() {
        if (!mStartStopToggleButton.isChecked()) {
            return;
        }

        Builder builder = new AlertDialog.Builder(this);
        final EditText inputText = new EditText(this);
        inputText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                return false;
            }

            public void onDestroyActionMode(ActionMode actionMode) {
            }
        });
        inputText.setLongClickable(false);
        inputText.setTextIsSelectable(false);

        inputText.setKeyListener(new NumberKeyListener() {
            public int getInputType() {
                return InputType.TYPE_TEXT_FLAG_CAP_WORDS;
            }

            @Override
            protected char[] getAcceptedChars() {
                char[] numberChars =
                        { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
                                'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0',
                                '1', '2', '3', '4', '5', '6', '7', '8', '9', '_', ' ', 'A', 'B',
                                'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
                                'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };
                return numberChars;
            }
        });

        mIsTaglogClicked = false;
        builder.setTitle(R.string.taglog_title)
                .setMessage(R.string.taglog_msg_input)
                .setView(inputText)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mIsTaglogClicked) {
                            Utils.logd(TAG,
                                    "Dialog button is already clicked, do not click OK again");
                            return;
                        }
                        mIsTaglogClicked = true;
                        // inputText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                        InputMethodManager inputManager =
                                (InputMethodManager) MainActivity.this
                                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputManager.hideSoftInputFromWindow(inputText.getWindowToken(), 0);

                        // User input tag log name
                        String mTag = inputText.getText().toString().trim();
                        Utils.logi(TAG, "Input tag: " + mTag);
                        try {
                            MTKLoggerServiceManager.getInstance().getService().beginTagLog(mTag);
                        } catch (ServiceNullException e) {
                            return;
                        }
                        createTagProgressDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mIsTaglogClicked) {
                            Utils.logd(TAG,
                                    "Dialog button is already clicked, do not click Cancel again");
                            return;
                        }
                        mIsTaglogClicked = true;
                        Utils.logv(TAG, " Cancel Taglog operation manually.");
                    }
                });
        Dialog mDialog = builder.create();
        mDialog.setCancelable(false);
        mDialog.show();
    }

    private boolean checkPath() {
        boolean isExist = true;
        if (mSDCardPathStr == null) {
            // Update for Save path
            mSDCardPathStr = Utils.getCurrentLogPath(mContext);
            mAvailableStorageSize = Utils.getAvailableStorageSize(mSDCardPathStr);
            Utils.logd(TAG, " mSDCardPathStr=" + mSDCardPathStr + ", mAvailableStorageSize = "
                    + mAvailableStorageSize);
            mSavePathStr = mSDCardPathStr + Utils.MTKLOG_PATH;
        }
        if (mSDCardPathStr == null) {
            Utils.loge(TAG, "mSDCardPathStr is null!");
            return false;
        }
        if (!new File(mSDCardPathStr).exists()) {
            isExist = false;
        }
        String mountStatus = Utils.getVolumeState(this, mSDCardPathStr);
        Utils.logd(TAG, "-->checkPath(), path=" + mSDCardPathStr + ", exist?" + isExist
                + ", volumeState=" + mountStatus);
        // For /data, should not judge its volume state
        return isExist
                && (Utils.LOG_PATH_TYPE_PHONE.equals(mSDCardPathStr) || Environment.MEDIA_MOUNTED
                        .equals(mountStatus));
    }

    private void createTagProgressDialog() {
        Utils.logd(TAG, "-->createTagProgressDialog()");
        boolean isFinishingFlag = isFinishing();
        Utils.logv(TAG, "Before show dialog, isFinishingFlag=" + isFinishingFlag);
        if (mTagProgressDialog == null && !isFinishingFlag) {
            mTagProgressDialog =
                    ProgressDialog.show(MainActivity.this, getString(R.string.taglog_title),
                            getString(R.string.taglog_msg_tag_log), true, false);
            mMessageHandler.sendMessageDelayed(mMessageHandler.obtainMessage(TAGLOG_TIMER_CHECK),
                    TAGLOG_PROGRESS_DIALOG_SHOW_TIMEOUT);
        }
        return;
    }

    private void dismissTagProgressDialog() {
        Utils.logi(TAG, "-->dismissTagProgressDialog()");
        if (null != mTagProgressDialog) {
            mMessageHandler.removeMessages(TAGLOG_TIMER_CHECK);
            mTagProgressDialog.dismiss();
            mTagProgressDialog = null;
        }
    }

    /**
     * Update UI.
     */
    public void updateUI() {
        boolean isServiceReady;
        try {
            isServiceReady = (MTKLoggerServiceManager.getInstance().getService() != null);
        } catch (ServiceNullException e) {
            isServiceReady = false;
        }
        if (!Utils.isBootCompleteDone() || !isServiceReady) {
            mMessageHandler.sendEmptyMessage(MSG_WAITING_SERVICE_READY);
            return;
        }
        mUpdateUITask = new UpdateUITask();
        mUpdateUITask.execute();
    }

}
