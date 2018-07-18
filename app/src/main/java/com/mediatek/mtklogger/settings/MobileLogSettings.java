package com.mediatek.mtklogger.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
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
import com.mediatek.mtklogger.framework.MobileLog;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class MobileLogSettings extends PreferenceActivity
                               implements OnPreferenceChangeListener, ISettingsActivity {
    private static final String TAG = Utils.TAG + "/MobileLogSettings";

    public static final String KEY_MB_ANDROID_LOG = "mobilelog_androidlog";
    public static final String KEY_MB_KERNEL_LOG = "mobilelog_kernellog";
    public static final String KEY_MB_SCP_LOG = "mobilelog_scplog";
    public static final String KEY_MB_ATF_LOG = "mobilelog_atflog";
    public static final String KEY_MB_BSP_LOG = "mobilelog_bsplog";
    public static final String KEY_MB_MMEDIA_LOG = "mobilelog_mmedialog";
    public static final String KEY_MB_SSPM_LOG = "mobilelog_sspmlog";
    public static final String KEY_MB_LOGSIZE = "mobilelog_logsize";
    public static final String KEY_MB_TOTAL_LOGSIZE = "mobilelog_total_logsize";

    private static final int LIMIT_LOG_SIZE = 100;

    private CheckBoxPreference mMbAndroidLog;
    private CheckBoxPreference mMbKernelLog;
    private CheckBoxPreference mMbBluetoothLog;
    private CheckBoxPreference mMbATFLog;
    private CheckBoxPreference mMbBSPLog;
    private CheckBoxPreference mMbMmediaLog;
    private CheckBoxPreference mMbSSPMLog;
    private EditTextPreference mMbLogSizeLimitPre;
    private CheckBoxPreference mMbAutoStartPre;
    private EditTextPreference mMbTotalLogSizeLimitPre;
    private int mTotalLogSizeBottom;

    private OptionalActionBarSwitch mBarSwitch;
    private SharedPreferences mDefaultSharedPreferences;

    private Toast mToastShowing;
    private long mSdcardSize;

    private SettingsPreferenceFragement mPrefsFragement;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.layout.mobilelog_settings);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, mPrefsFragement).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void findViews() {
        mBarSwitch = new OptionalActionBarSwitch(this);
        mMbAndroidLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_ANDROID_LOG);
        mMbKernelLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_KERNEL_LOG);
        mMbBluetoothLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_SCP_LOG);
        mMbATFLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_ATF_LOG);
        mMbBSPLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_BSP_LOG);
        mMbMmediaLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_MMEDIA_LOG);
        mMbSSPMLog = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MB_SSPM_LOG);
        mMbAutoStartPre = (CheckBoxPreference) mPrefsFragement.
                findPreference(Utils.KEY_START_AUTOMATIC_MOBILE);

        mMbLogSizeLimitPre = (EditTextPreference) mPrefsFragement.
                findPreference(KEY_MB_LOGSIZE);
        mMbTotalLogSizeLimitPre = (EditTextPreference) mPrefsFragement.
                findPreference(KEY_MB_TOTAL_LOGSIZE);
    }

    @Override
    public void initViews() {
        Utils.logd(TAG, "initViews()");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isSwitchChecked =
                mDefaultSharedPreferences.getBoolean(SettingsActivity.KEY_MB_SWITCH, false);
        boolean isRecording;
        try {
            isRecording = MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning();
        } catch (ServiceNullException e) {
            isRecording = false;
        }
        mBarSwitch.setChecked(isSwitchChecked);
        mBarSwitch.setEnabled(!isRecording);
        setAllPreferencesEnable(isSwitchChecked && !isRecording);

        doInitForSmartLogging();

        mMbLogSizeLimitPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mSdcardSize = getIntent().getLongExtra(Utils.SDCARD_SIZE, LIMIT_LOG_SIZE);
        // The max mobile log size
        mSdcardSize = 128 * 1024;
        Object[] objs =
                {
                        getString(R.string.mobile_log_name),
                        LIMIT_LOG_SIZE,
                        mSdcardSize,
                        getString(R.string.limit_log_size_store_type,
                                new Object[] { getString(R.string.mobile_log_name) }) };
        mMbLogSizeLimitPre.setDialogMessage(getString(
                R.string.limit_single_log_size_dialog_message, objs));

        mTotalLogSizeBottom = getIntByObj(getTotalLogSizeBottom());

        mMbTotalLogSizeLimitPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        try {
            formatTotalLogSize(mTotalLogSizeBottom);
        } catch (ServiceNullException e) {
            return;
        }
    }

    /**
     * Total log size should not less than current log size limitation, get the
     * minimum allowed value.
     *
     * @return String
     */
    private String getTotalLogSizeBottom() {
        return mDefaultSharedPreferences.getString(KEY_MB_LOGSIZE,
                String.valueOf(Utils.DEFAULT_CONFIG_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MOBILE)));
    }

    @Override
    public void setListeners() {
        Utils.logd(TAG, "setListeners()");
        mMbAndroidLog.setOnPreferenceChangeListener(this);
        mMbKernelLog.setOnPreferenceChangeListener(this);
        mMbBluetoothLog.setOnPreferenceChangeListener(this);
        mMbATFLog.setOnPreferenceChangeListener(this);
        mMbBSPLog.setOnPreferenceChangeListener(this);
        mMbMmediaLog.setOnPreferenceChangeListener(this);
        mMbSSPMLog.setOnPreferenceChangeListener(this);
        mMbLogSizeLimitPre.setOnPreferenceChangeListener(this);
        mMbAutoStartPre.setOnPreferenceChangeListener(this);
        mMbTotalLogSizeLimitPre.setOnPreferenceChangeListener(this);

        mBarSwitch.setOnCheckedChangeListener(new LogSwitchListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(SettingsActivity.KEY_MB_SWITCH, isChecked).apply();
                setAllPreferencesEnable(isChecked);
            }

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isChecked = Boolean.parseBoolean(newValue.toString());
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(SettingsActivity.KEY_MB_SWITCH,
                                Boolean.parseBoolean(newValue.toString())).apply();
                setAllPreferencesEnable(isChecked);
                return true;
            }

        });

        mMbLogSizeLimitPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Dialog dialog = mMbLogSizeLimitPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= LIMIT_LOG_SIZE
                         && inputSize <= mSdcardSize
                        );
                        String msg = "Please input a valid integer value ("
                              + LIMIT_LOG_SIZE + "~" + mSdcardSize + ").";
                        showToastMsg(isEnable, msg);

                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                isEnable);
                    } catch (NumberFormatException e) {
                        Utils.loge(TAG, "Integer.parseInt(" + String.valueOf(s) + ") is error!");
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                    }
                }
            }

        });

        mMbTotalLogSizeLimitPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Dialog dialog = mMbTotalLogSizeLimitPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= mTotalLogSizeBottom)
                                && (inputSize <= mSdcardSize)
                                && (inputSize % mTotalLogSizeBottom == 0);

                        String msg = "Please input a valid integer value ( N*"
                                + mTotalLogSizeBottom + " and < " + mSdcardSize + " ).";
                        showToastMsg(isEnable, msg);

                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                isEnable);
                    } catch (NumberFormatException e) {
                        Utils.loge(TAG, "Integer.parseInt(" + String.valueOf(s) + ") is error!");
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                    }
                }
            }

        });
    }
    private void showToastMsg(boolean isEnable, String msg) {
        if (!isEnable) {
            if (mToastShowing == null) {
                mToastShowing = Toast.makeText(MobileLogSettings.this, msg,
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
            if (preference.getKey().equals(KEY_MB_ANDROID_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_ANDROID,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_KERNEL_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_KERNEL,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_SCP_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_SCP,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_ATF_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_ATF,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_BSP_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_BSP,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_MMEDIA_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_MMEDIA,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_SSPM_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_SSPM,
                        Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_LOGSIZE)) {
                int newLogSize = getIntByObj(newValue);
                formatTotalLogSize(newLogSize);
                mTotalLogSizeBottom = newLogSize;
                MTKLoggerServiceManager.getInstance().getService()
                    .setEachLogSize(Utils.LOG_TYPE_MOBILE, getIntByObj(newValue));
            } else if (preference.getKey().equals(Utils.KEY_START_AUTOMATIC_MOBILE)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setLogAutoStart(
                            Utils.LOG_TYPE_MOBILE, Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(KEY_MB_TOTAL_LOGSIZE)) {
                Utils.logd(TAG, "New total log size value: " + newValue);
                MTKLoggerServiceManager.getInstance().getService()
                    .setEachTotalLogSize(Utils.LOG_TYPE_MOBILE, getIntByObj(newValue));
            }
        } catch (ServiceNullException e) {
            return false;
        }
        return true;
    }

    private void formatTotalLogSize(int currentLogSize) throws ServiceNullException {
        int oldTotalLogSize =
                getIntByObj(mDefaultSharedPreferences.getString(KEY_MB_TOTAL_LOGSIZE,
                        String.valueOf(Utils.DEFAULT_CONFIG_LOG_SIZE_MAP
                                .get(Utils.LOG_TYPE_MOBILE) * 2)));
        int newTotalLogSize = 0;
        if (mSdcardSize > 0 && currentLogSize > mSdcardSize) {
            String msgStr =
                    "Current size exceed storage capability[" + currentLogSize
                            + "]MB, so log recycle maybe disabled";
            Toast.makeText(this, msgStr, Toast.LENGTH_LONG).show();
            Utils.logi(TAG, msgStr);
        }
        if (currentLogSize > oldTotalLogSize) {
            String msgStr =
                    "Current size exceed total size value, reset total log size value to "
                            + currentLogSize + "MB";
            Toast.makeText(this, msgStr, Toast.LENGTH_LONG).show();
            Utils.logi(TAG, msgStr);
            newTotalLogSize = currentLogSize;
        } else {
            newTotalLogSize = oldTotalLogSize / currentLogSize * currentLogSize;
        }
        mMbTotalLogSizeLimitPre.setText(String.valueOf(newTotalLogSize));
        MTKLoggerServiceManager.getInstance().getService()
            .setEachTotalLogSize(Utils.LOG_TYPE_MOBILE, newTotalLogSize);
        mMbTotalLogSizeLimitPre.setDialogMessage(getString(
                R.string.limit_total_log_size_dialog_message, new Object[] {
                        getString(R.string.mobile_log_name), String.valueOf(currentLogSize),
                        String.valueOf(mSdcardSize), String.valueOf(currentLogSize)}));
    }

    private void setAllPreferencesEnable(boolean isEnable) {
        mMbAndroidLog.setEnabled(isEnable);
        mMbKernelLog.setEnabled(isEnable);
        mMbBluetoothLog.setEnabled(isEnable);
        mMbATFLog.setEnabled(isEnable);
        mMbBSPLog.setEnabled(isEnable);
        mMbMmediaLog.setEnabled(isEnable);
        mMbSSPMLog.setEnabled(isEnable);
        mMbLogSizeLimitPre.setEnabled(isEnable);
        mMbTotalLogSizeLimitPre.setEnabled(isEnable);
    }

    private void doInitForSmartLogging() {
        if (Utils.MODEM_MODE_PLS.equals(mDefaultSharedPreferences.getString(Utils.KEY_MD_MODE_1,
                Utils.MODEM_MODE_SD))) {
            mMbBluetoothLog.setEnabled(false);
            mMbATFLog.setEnabled(false);
            mMbBSPLog.setEnabled(false);
            mMbMmediaLog.setEnabled(false);
            mMbSSPMLog.setEnabled(false);
            mMbLogSizeLimitPre.setEnabled(false);
        }
    }

    private int getIntByObj(Object obj) {
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
