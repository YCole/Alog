package com.mediatek.mtklogger.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author mtk80130
 *
 */
public class MetLogSettings extends PreferenceActivity
                            implements OnPreferenceChangeListener, ISettingsActivity {
    private static final String TAG = Utils.TAG + "/MetLogSettings";
    public static final String KEY_MT_PREFERENCE_SCREEN = "metlog_preference_screen";
    public static final String KEY_MT_CATEGORY = "metlog_category";
    public static final String KEY_MT_PRIOD = "metlog_period";
    public static final String KEY_MT_CPU_BUFFER = "metlog_cpu_buffer";
    public static final String KEY_MT_HEAVY_LOAD_RECORDING = "metlog_heavy_load_recording";

    public static final String KEY_MT_LOGSIZE = "metlog_logsize";

    private SharedPreferences mDefaultSharedPreferences;

    private OptionalActionBarSwitch mBarSwitch;

    private ProgressDialog mWaitingDialog;

    private static final int MSG_SHOW_DIALOG = 1;
    private static final int MSG_UPDATE_DEFAULT_VALUES = 2;
    private static final int MSG_DISMISS_DIALOG = 3;

    private EditTextPreference mMtLogPreiodPre;
    private EditTextPreference mMtLogCPUBufferPre;
    private CheckBoxPreference mMtLogHeavyLoadRecordingPre;
    private EditTextPreference mMtLogSizeLimitPre;

    public static final int KEY_MET_LOG_MAX_PERIOD = 1;
    public static final int KEY_MET_LOG_MIN_PERIOD = 2;
    public static final int KEY_MET_LOG_MAX_CPU_BUFFER = 3;
    public static final int KEY_MET_LOG_MIN_CPU_BUFFER = 4;
    public static final int KEY_MET_LOG_MAX_LOG_SIZE = 5;
    public static final int KEY_MET_LOG_MIN_LOG_SIZE = 6;
    public static final int KEY_MET_LOG_CURRENT_CPU_BUFFER = 7;
    public static final int KEY_MET_LOG_CURRENT_PERIOD = 8;
    public static final int KEY_MET_LOG_CURRENT_LOG_SIZE = 9;
    private static int sMaxPeriod = 0;
    private static int sMinPeriod = 0;
    private static int sMaxCpuBuffer = 0;
    private static int sMinCpuBuffer = 0;
    private static int sMaxLogsize = 0;
    private static int sMinLogsize = 0;
    private static int sCurrentCpuBuffer = 0;
    private static int sCurrentPeriod = 0;
    private static int sCurrentLogsize = 0;

    private SettingsPreferenceFragement mPrefsFragement;

    private Toast mToastShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.layout.metlog_settings);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, mPrefsFragement).commit();
    }

    @Override
    public void findViews() {
        Utils.logd(TAG, "findViews()");
        mBarSwitch = new OptionalActionBarSwitch(this);
        mMtLogPreiodPre = (EditTextPreference) mPrefsFragement.findPreference(KEY_MT_PRIOD);
        mMtLogCPUBufferPre = (EditTextPreference) mPrefsFragement.findPreference(KEY_MT_CPU_BUFFER);
        mMtLogHeavyLoadRecordingPre = (CheckBoxPreference) mPrefsFragement.findPreference(
                KEY_MT_HEAVY_LOAD_RECORDING);
        mMtLogSizeLimitPre = (EditTextPreference) mPrefsFragement.findPreference(KEY_MT_LOGSIZE);
    }

    @Override
    public void initViews() {
        Utils.logd(TAG, "initViews()");

        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // mManager = new MTKLoggerManager(this);
        boolean isSwitchChecked =
                mDefaultSharedPreferences.getBoolean(SettingsActivity.KEY_MT_SWITCH, false);
        boolean isRecording;
        try {
            isRecording = MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning();
        } catch (ServiceNullException e) {
            isRecording = false;
        }
        mBarSwitch.setChecked(isSwitchChecked);
        mBarSwitch.setEnabled(!isRecording);
        setAllPreferencesEnable(isSwitchChecked && !isRecording);
        Utils.logd(TAG, "initViews,get value from native");
        mMtLogPreiodPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mMtLogCPUBufferPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mMtLogSizeLimitPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

        Thread getValue = new Thread() {
            public void run() {
                Utils.logd(TAG, "before getValueFromNative()");
                try {
                    getValueFromNative();
                } catch (ServiceNullException e) {
                    return;
                }
                Utils.logd(TAG, "after getValueFromNative()");
            }
        };
        Utils.logd(TAG, "before thread start()");
        mMessageHandler.obtainMessage(MSG_SHOW_DIALOG).sendToTarget();
        getValue.start();
        Utils.logd(TAG, "after thread start()");
    }

    @Override
    public void setListeners() {
        mMtLogPreiodPre.setOnPreferenceChangeListener(this);
        mMtLogCPUBufferPre.setOnPreferenceChangeListener(this);
        mMtLogSizeLimitPre.setOnPreferenceChangeListener(this);
        mBarSwitch.setOnCheckedChangeListener(new LogSwitchListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(SettingsActivity.KEY_MT_SWITCH, isChecked).apply();
                setAllPreferencesEnable(isChecked);
            }

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isChecked = Boolean.parseBoolean(newValue.toString());
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(SettingsActivity.KEY_MT_SWITCH,
                                Boolean.parseBoolean(newValue.toString())).apply();
                setAllPreferencesEnable(isChecked);
                return true;
            }

        });
        mMtLogSizeLimitPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                Dialog dialog = mMtLogSizeLimitPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= sMinLogsize && inputSize <= sMaxLogsize);
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                isEnable);
                        String msg = "Please input a valid integer value ("
                                + sMinLogsize + "~" + sMaxLogsize + ").";
                        showToastMsg(isEnable, msg);
                    } catch (NumberFormatException e) {
                        Utils.loge(TAG, "Integer.parseInt(" + String.valueOf(s) + ") is error!");
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                    }
                }
            }

        });

        mMtLogPreiodPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // TODO Auto-generated method stub

                Dialog dialog = mMtLogPreiodPre.getDialog();

                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= sMinPeriod && inputSize <= sMaxPeriod);
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                isEnable);
                    } catch (NumberFormatException e) {
                        // Utils.loge(TAG, "Integer.parseInt(" +
                        // String.valueOf(s) + ") is error!");
                        Toast.makeText(MetLogSettings.this,
                                "Please input a valid integer value (less than 2147483648).",
                                Toast.LENGTH_SHORT).show();
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                    }
                }
            }

        });
        // Utils.logd(TAG,"getEditText.gettext:"+mMtLogCPUBufferPre.getEditText().getText());
        // mMtLogCPUBufferPre.getEditText().setText("512");
        mMtLogCPUBufferPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub
                Utils.logd(TAG, "afterTextChanged:" + s);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
                Utils.logd(TAG, "beforeTextChanged:" + s + "," + "start," + start + "count,"
                        + count + "after," + after);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // TODO Auto-generated method stub
                Utils.logd(TAG, "onTextChanged:" + s + "start," + start + "before," + before
                        + "count," + count);
                Dialog dialog = mMtLogCPUBufferPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable =
                                (inputSize >= sMinCpuBuffer && inputSize <= sMaxCpuBuffer);
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                isEnable);
                    } catch (NumberFormatException e) {
                        // Utils.loge(TAG, "Integer.parseInt(" +
                        // String.valueOf(s) + ") is error!");
                        Toast.makeText(MetLogSettings.this,
                                "Please input a valid integer value (less than 2147483648).",
                                Toast.LENGTH_SHORT).show();
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                    }
                }
            }

        });

        if (mMtLogHeavyLoadRecordingPre != null) {
            mMtLogHeavyLoadRecordingPre.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    try {
                        MTKLoggerServiceManager.getInstance().getService().setHeavyLoadRecording(
                                Utils.LOG_TYPE_MET, mMtLogHeavyLoadRecordingPre.isChecked());
                    } catch (ServiceNullException e) {
                        Utils.logw(TAG, "MTKLogger service is not ready when do set, just return");
                        return false;
                    }
                    return true;
                }
            });
        }

    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        if (mWaitingDialog != null) {
            mWaitingDialog.cancel();
            mWaitingDialog = null;
        }

        super.onDestroy();
    }

    private void showToastMsg(boolean isEnable, String msg) {
        if (!isEnable) {
            if (mToastShowing == null) {
                mToastShowing = Toast.makeText(MetLogSettings.this, msg,
                        Toast.LENGTH_LONG);
            } else {
                mToastShowing.setText(msg);
            }
            mToastShowing.show();
        }
        if (isEnable && mToastShowing != null) {
            mToastShowing.cancel();
            mToastShowing = null;
        }
    }
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Utils.logi(TAG, "Preference Change Key : " + preference.getKey() + " newValue : "
                + newValue);
        try {
            if (preference.getKey().equals(KEY_MT_PRIOD)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setLogPeriod(Utils.LOG_TYPE_MET, getIntByObj(newValue));
                sCurrentPeriod = getIntByObj(newValue);
                mMtLogPreiodPre.setText(String.valueOf(sCurrentPeriod));
            } else if (preference.getKey().equals(KEY_MT_CPU_BUFFER)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setLogCPUBuffer(Utils.LOG_TYPE_MET, getIntByObj(newValue));
                sCurrentCpuBuffer = getIntByObj(newValue);
                mMtLogCPUBufferPre.setText(String.valueOf(sCurrentCpuBuffer));
            } else if (preference.getKey().equals(KEY_MT_LOGSIZE)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setEachLogSize(Utils.LOG_TYPE_MET, getIntByObj(newValue));
                sCurrentLogsize = getIntByObj(newValue);
                mMtLogSizeLimitPre.setText(String.valueOf(sCurrentLogsize));
            }
        } catch (ServiceNullException e) {
            return false;
        }
        return true;

    }

    private void setAllPreferencesEnable(boolean isEnable) {
        mMtLogPreiodPre.setEnabled(isEnable);
        mMtLogCPUBufferPre.setEnabled(isEnable);
        mMtLogSizeLimitPre.setEnabled(isEnable);

    }

    private int getIntByObj(Object obj) {
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showWaitingDialog() {
        Utils.logd(TAG, "showWaitingDialog()");
        mWaitingDialog =
                ProgressDialog.show(MetLogSettings.this, getString(R.string.met_log_name),
                        "get value from Met native layer", true, false);
    }

    private void releaseDialog() {
        Utils.logd(TAG, "releaseDialog()");
        if (mWaitingDialog != null) {
            mWaitingDialog.cancel();
            mWaitingDialog = null;
        }

    }

    private Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message message) {
            Utils.logd(TAG, " MyHandler handleMessage --> start " + message.what);
            switch (message.what) {
            case MSG_SHOW_DIALOG:
                showWaitingDialog();
                break;
            case MSG_UPDATE_DEFAULT_VALUES:
                Object[] cpuBufferObjs =
                    { getString(R.string.met_log_name), sMinCpuBuffer, sMaxCpuBuffer };
                mMtLogCPUBufferPre.setDialogMessage(
                        getString(R.string.limit_cpu_buffer_dialog_message,
                        cpuBufferObjs));
                mMtLogCPUBufferPre.setText(String.valueOf(sCurrentCpuBuffer));

                Object[] periodObjs = {
                        getString(R.string.met_log_name), sMinPeriod, sMaxPeriod };
                mMtLogPreiodPre.setDialogMessage(getString(R.string.limit_period_dialog_message,
                        periodObjs));
                mMtLogPreiodPre.setText(String.valueOf(sCurrentPeriod));

                Object[] logSizeObjs = {
                        getString(R.string.met_log_name), sMinLogsize, sMaxLogsize };
                mMtLogSizeLimitPre.setDialogMessage(
                        getString(R.string.limit_met_log_size_dialog_message,
                        logSizeObjs));
                mMtLogSizeLimitPre.setText(String.valueOf(sCurrentLogsize));
                mMessageHandler.obtainMessage(MSG_DISMISS_DIALOG).sendToTarget();
                break;
            case MSG_DISMISS_DIALOG:
                releaseDialog();
                break;
            default:
                Utils.logw(TAG, "Not supported message: " + message.what);
                break;
            }
        }
    };

    private void getValueFromNative() throws ServiceNullException {
        if (sMaxLogsize == 0) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sMaxLogsize = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MAX_LOG_SIZE);
        }
        if (sMinLogsize == 0) {
            sMinLogsize = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MIN_LOG_SIZE);
        }
        if (sMaxPeriod == 0) {
            sMaxPeriod = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MAX_PERIOD);
        }
        if (sMinPeriod == 0) {
            sMinPeriod = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MIN_PERIOD);
        }
        if (sMaxCpuBuffer == 0) {
            sMaxCpuBuffer = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MAX_CPU_BUFFER);
        }
        if (sMinCpuBuffer == 0) {
            sMinCpuBuffer = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_MIN_CPU_BUFFER);
        }
        if (sCurrentCpuBuffer == 0) {
            sCurrentCpuBuffer = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_CURRENT_CPU_BUFFER);
        }
        if (sCurrentLogsize == 0) {
            sCurrentLogsize = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_CURRENT_PERIOD);
        }
        if (sCurrentPeriod == 0) {
            sCurrentPeriod = MTKLoggerServiceManager.getInstance().getService()
                    .getMetLogValues(KEY_MET_LOG_CURRENT_LOG_SIZE);
        }
        mMessageHandler.obtainMessage(MSG_UPDATE_DEFAULT_VALUES).sendToTarget();
    }
}
