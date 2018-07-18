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
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class NetworkLogSettings extends PreferenceActivity
                                implements OnPreferenceChangeListener, ISettingsActivity {
    private static final String TAG = Utils.TAG + "/NetworkLogSettings";

    public static final String KEY_NT_LOGSIZE = "networklog_logsize";

    private static final int LIMIT_LOG_SIZE = 100;

    private EditTextPreference mNtLogSizeLimitPre;
    private CheckBoxPreference mNtAutoStartPre;
    private EditTextPreference mNtPackageSizeLimitationPre;

    private OptionalActionBarSwitch mBarSwitch;
    private SharedPreferences mDefaultSharedPreferences;

    private long mSdcardSize;
    private Toast mToastShowing;

    private SettingsPreferenceFragement mPrefsFragement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.layout.networklog_settings);
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
        mNtLogSizeLimitPre = (EditTextPreference) mPrefsFragement.
                findPreference(KEY_NT_LOGSIZE);
        mNtPackageSizeLimitationPre =
                (EditTextPreference) mPrefsFragement.
                findPreference(Utils.KEY_NT_LIMIT_PACKAGE_SIZE);
        mNtAutoStartPre = (CheckBoxPreference) mPrefsFragement.
                findPreference(Utils.KEY_START_AUTOMATIC_NETWORK);
    }

    @Override
    public void initViews() {
        Utils.logd(TAG, "initViews()");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isSwitchChecked =
                mDefaultSharedPreferences.getBoolean(SettingsActivity.KEY_NT_SWITCH, false);
        boolean isRecording;
        try {
            isRecording = MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning();
        } catch (ServiceNullException e) {
            isRecording = false;
        }
        mBarSwitch.setChecked(isSwitchChecked);
        mBarSwitch.setEnabled(!isRecording);
        setAllPreferencesEnable(isSwitchChecked && !isRecording);

        mNtLogSizeLimitPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mSdcardSize = getIntent().getLongExtra(Utils.SDCARD_SIZE, LIMIT_LOG_SIZE);

        Object[] objs =
                {
                        getString(R.string.network_log_name),
                        LIMIT_LOG_SIZE,
                        mSdcardSize,
                        getString(Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getLogPathType())
                                ? Utils.LOG_SD_CARD : Utils.LOG_PHONE_STORAGE) };
        mNtLogSizeLimitPre
                .setDialogMessage(getString(R.string.limit_log_size_dialog_message, objs));

        mNtPackageSizeLimitationPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mNtPackageSizeLimitationPre
                .setDialogMessage(getString(R.string.networklog_limit_package_size_note));
    }

    @Override
    public void setListeners() {
        Utils.logd(TAG, "setListeners()");
        mNtLogSizeLimitPre.setOnPreferenceChangeListener(this);
        mNtAutoStartPre.setOnPreferenceChangeListener(this);

        mBarSwitch.setOnCheckedChangeListener(new LogSwitchListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(SettingsActivity.KEY_NT_SWITCH, isChecked).apply();
                setAllPreferencesEnable(isChecked);
            }

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isChecked = Boolean.parseBoolean(newValue.toString());
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(SettingsActivity.KEY_NT_SWITCH,
                                Boolean.parseBoolean(newValue.toString())).apply();
                setAllPreferencesEnable(isChecked);
                return true;
            }

        });

        mNtLogSizeLimitPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Dialog dialog = mNtLogSizeLimitPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= LIMIT_LOG_SIZE
                          && inputSize <= mSdcardSize );
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

        mNtPackageSizeLimitationPre.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Dialog dialog = mNtPackageSizeLimitationPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= 0 && inputSize <= 65535);
                        String msg = "Please input a valid integer value ("
                                + 0 + "~" + 65535 + ").";
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
                mToastShowing = Toast.makeText(NetworkLogSettings.this, msg,
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
            if (preference.getKey().equals(KEY_NT_LOGSIZE)) {
                MTKLoggerServiceManager.getInstance().getService()
                .setEachLogSize(Utils.LOG_TYPE_NETWORK, getIntByObj(newValue));
            } else if (preference.getKey().equals(Utils.KEY_START_AUTOMATIC_NETWORK)) {
                MTKLoggerServiceManager.getInstance().getService()
                .setLogAutoStart(Utils.LOG_TYPE_NETWORK,
                        Boolean.parseBoolean(newValue.toString()));
            }
        } catch (ServiceNullException e) {
            return false;
        }
        return true;
    }

    private void setAllPreferencesEnable(boolean isEnable) {
        mNtLogSizeLimitPre.setEnabled(isEnable);
    }

    private int getIntByObj(Object obj) {
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
