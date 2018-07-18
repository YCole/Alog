package com.mediatek.mtklogger.settings;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.LogInstance;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.framework.MobileLog;
import com.mediatek.mtklogger.framework.MultiModemLog;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class ModemLogSettings extends PreferenceActivity
                              implements OnPreferenceChangeListener, ISettingsActivity {
    private static final String TAG = Utils.TAG + "/ModemLogSettings";

    public static final String KEY_MD_PREFERENCE_SCREEN = "modemlog_preference_screen";
    public static final String KEY_MD_MODE = "log_mode";
    public static final String KEY_MD_LOGSIZE = "modemlog_logsize";
    public static final String KEY_MD_AUTORESET = "modem_autoreset";
    public static final String KEY_MD_MONITOR_MODEM_ABNORMAL_EVENT = "monitor_modme_abnormal_event";
    public static final String KEY_CCB_GEAR = "ccb_gear";

    private static final int LIMIT_LOG_SIZE = 600;

    private ListPreference mMdLogModeList1;
    private ListPreference mMdLogModeList2;
    private EditTextPreference mMdLogSizeLimitPre;
    private CheckBoxPreference mMdAutoStartPre;
    private CheckBoxPreference mMdMonitorAbnormalEventPre;
    private CheckBoxPreference mMdSavelocationInLogPre;
    private ListPreference mCCBGearList;

    private OptionalActionBarSwitch mBarSwitch;
    private SharedPreferences mDefaultSharedPreferences;

    private long mSdcardSize;

    private Toast mToastShowing;

    private SettingsPreferenceFragement mPrefsFragement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.layout.modemlog_settings);
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

        mMdLogModeList1 = (ListPreference) mPrefsFragement.findPreference(Utils.KEY_MD_MODE_1);
        mMdLogModeList2 = (ListPreference) mPrefsFragement.findPreference(Utils.KEY_MD_MODE_2);
        // distinction for PLS mode in 3G 4G modem
        // use [init.svc.mdlogger]: [stopped] to decide mdlogger running or not
        String hardware = SystemProperties.get("ro.hardware");
        Utils.logd(TAG, "current hardware is " + hardware.substring(0, 4));
        // chip is mt65xx not mt67xx
        if (((hardware.substring(0, 4)).compareToIgnoreCase("mt65") <= 0) &&
               // mdlogger is running
                ((SystemProperties.get("init.svc.mdlogger", "stopped"))
                        .compareToIgnoreCase("running")) == 0 ||
                Utils.isDenaliMd3Solution()) {
            CharSequence[] mode = mMdLogModeList1.getEntries();
            if ( mode.length >= 3) { // if just SD and USB,not reset
                CharSequence[] modifyMode = new CharSequence[mode.length - 1];
                for (int i = 0; i < mode.length - 1; i++) {
                    Utils.logd(TAG, "findViews()================>" + mode[i]);
                    modifyMode[i] = mode[i];
                }
                if (mMdLogModeList1.findIndexOfValue(
                        mMdLogModeList1.getValue()) >= modifyMode.length) {
                    mMdLogModeList1.setValueIndex(modifyMode.length - 1);
                }
                if (mMdLogModeList2.findIndexOfValue(
                        mMdLogModeList2.getValue()) >= modifyMode.length) {
                    mMdLogModeList2.setValueIndex(modifyMode.length - 1);
                }
                mMdLogModeList1.setEntries(modifyMode);
                mMdLogModeList2.setEntries(modifyMode);
            }
        }
        // end
        mCCBGearList = (ListPreference) mPrefsFragement.findPreference(KEY_CCB_GEAR);
        mMdLogSizeLimitPre = (EditTextPreference) mPrefsFragement.
                findPreference(KEY_MD_LOGSIZE);

        mMdAutoStartPre = (CheckBoxPreference) mPrefsFragement.
                findPreference(Utils.KEY_START_AUTOMATIC_MODEM);
        mMdMonitorAbnormalEventPre =
                (CheckBoxPreference) mPrefsFragement.
                findPreference(KEY_MD_MONITOR_MODEM_ABNORMAL_EVENT);
        mMdSavelocationInLogPre =
                (CheckBoxPreference) mPrefsFragement.
                findPreference(Utils.KEY_MD_SAVE_LOCATIN_IN_LOG);
    }

    @Override
    public void initViews() {
        Utils.logd(TAG, "initViews()");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isSwitchChecked =
                mDefaultSharedPreferences.getBoolean(SettingsActivity.KEY_MD_SWITCH, false);
        boolean isRecording;
        try {
            isRecording = MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning();
        } catch (ServiceNullException e) {
            isRecording = false;
        }
        mBarSwitch.setChecked(isSwitchChecked);
        mBarSwitch.setEnabled(!isRecording);
        setAllPreferencesEnable(isSwitchChecked && !isRecording);

        initLogMode(mMdLogModeList1, Utils.KEY_MD_MODE_1, "Md"
                + (Utils.sAvailableModemList.size() >= 1 ? Utils.sAvailableModemList.get(0) : ""));
        Utils.logd(TAG, "Utils.getModemSize(), " + Utils.sAvailableModemList.size());
        if (Utils.sAvailableModemList.size() == 2) {
            initLogMode(mMdLogModeList2, Utils.KEY_MD_MODE_2,
                    "Md" + Utils.sAvailableModemList.get(1));
        } else {
            PreferenceScreen preferenceScreen =
                    (PreferenceScreen) mPrefsFragement.findPreference(KEY_MD_PREFERENCE_SCREEN);
            preferenceScreen.removePreference(mMdLogModeList2);
        }

        int selectedMode = mMdLogModeList1.findIndexOfValue(mMdLogModeList1.getValue());

        mMdLogSizeLimitPre.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        mSdcardSize = getIntent().getLongExtra(Utils.SDCARD_SIZE, LIMIT_LOG_SIZE);

        Object[] objs =
                {
                        getString(R.string.modem_log_name),
                        LIMIT_LOG_SIZE,
                        mSdcardSize,
                        getString(Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getLogPathType())
                                ? Utils.LOG_SD_CARD : Utils.LOG_PHONE_STORAGE) };
        mMdLogSizeLimitPre
                .setDialogMessage(getString(R.string.limit_log_size_dialog_message, objs));

        mMdMonitorAbnormalEventPre.setEnabled(selectedMode == 2);
        PreferenceScreen advancePreCategory =
                (PreferenceScreen) mPrefsFragement.
                findPreference("modemlog_preference_screen");
        int modemLogSpecialFeatureSupport = 0;
        try {
            modemLogSpecialFeatureSupport = MTKLoggerServiceManager.getInstance().getService()
                    .getSpecialFeatureSupport(Utils.LOG_TYPE_MODEM);
        } catch (ServiceNullException e) {
            modemLogSpecialFeatureSupport = 0;
        }
        boolean isGpsLocationSupport = (modemLogSpecialFeatureSupport & 1) != 0;
        if (!isGpsLocationSupport) {
            advancePreCategory.removePreference(mMdSavelocationInLogPre);
        }

        boolean isCCBFeatureSupport = (modemLogSpecialFeatureSupport & 2) != 0;
        if (!isCCBFeatureSupport) {
            advancePreCategory.removePreference(mCCBGearList);
        } else {
            mCCBGearList.setSummary(mCCBGearList.getEntry());
            mCCBGearList.setOnPreferenceChangeListener(this);
        }

    }

    /**
     * @param mdLogModeList
     *            ListPreference
     * @param keyMdMode
     *            String
     */
    private void initLogMode(ListPreference mdLogModeList, String keyMdMode, String mdName) {
        String logModeValue = mDefaultSharedPreferences.getString(keyMdMode, "");
        Utils.logd(TAG, "mDefaultSharedPreferences.getString(KEY_MD_MODE, " + logModeValue);
        if (TextUtils.isEmpty(logModeValue)) {
            logModeValue = getString(R.string.default_mode_value);
            Utils.logw(TAG, "No default log mode value stored in default shared preference, "
                    + "try to get it from string res, logModeValue=" + logModeValue);
        }
        int selectedMode = mdLogModeList.findIndexOfValue(logModeValue);
        if (selectedMode < 0) {
            Utils.loge(TAG, "Fail to select the given mode, just take the first one.");
        }
        mdLogModeList.setTitle(mdName + " " + mdLogModeList.getTitle());
        mdLogModeList.setDialogTitle(mdName + " " + mdLogModeList.getDialogTitle());
        mdLogModeList.setValueIndex(selectedMode);
        mdLogModeList.setSummary(mdLogModeList.getEntries()[selectedMode]);
        mdLogModeList.setOnPreferenceChangeListener(this);
    }

    @Override
    public void setListeners() {
        Utils.logd(TAG, "setListeners()");
        mMdLogSizeLimitPre.setOnPreferenceChangeListener(this);
        mMdAutoStartPre.setOnPreferenceChangeListener(this);
        mMdSavelocationInLogPre.setOnPreferenceChangeListener(this);

        mBarSwitch.setOnCheckedChangeListener(new LogSwitchListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(SettingsActivity.KEY_MD_SWITCH, isChecked).apply();
                setAllPreferencesEnable(isChecked);
            }

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isChecked = Boolean.parseBoolean(newValue.toString());
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(SettingsActivity.KEY_MD_SWITCH,
                                Boolean.parseBoolean(newValue.toString())).apply();
                setAllPreferencesEnable(isChecked);
                return true;
            }

        });

        mMdLogSizeLimitPre.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Dialog dialog = mMdLogSizeLimitPre.getDialog();
                if (dialog != null && dialog instanceof AlertDialog) {
                    if ("".equals(String.valueOf(s))) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                                false);
                        return;
                    }

                    try {
                        int inputSize = Integer.parseInt(String.valueOf(s));
                        boolean isEnable = (inputSize >= LIMIT_LOG_SIZE
                        && inputSize <= mSdcardSize);

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
    }

    private void showToastMsg(boolean isEnable, String msg) {
        if (!isEnable) {
            if (mToastShowing == null) {
                mToastShowing = Toast.makeText(ModemLogSettings.this, msg,
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
            if (preference.getKey().equals(Utils.KEY_MD_MODE_1)) {
                return logModePreferenceChange(
                        mMdLogModeList1, (String) newValue, Utils.KEY_MD_MODE_1);
            } else if (preference.getKey().equals(Utils.KEY_MD_MODE_2)) {
                return logModePreferenceChange(
                        mMdLogModeList2, (String) newValue, Utils.KEY_MD_MODE_2);
            } else if (preference.getKey().equals(KEY_CCB_GEAR)) {
                MTKLoggerServiceManager.getInstance().getService()
                        .sentMessageToLog(Utils.LOG_TYPE_MODEM, LogInstance.MSG_CONFIG,
                                MultiModemLog.PREFIX_SET_CCB_GEAR_ID + "," + newValue);
                int valueIndex = mCCBGearList.findIndexOfValue((String) newValue);
                mCCBGearList.setValueIndex(valueIndex);
                mCCBGearList.setSummary(mCCBGearList.getEntries()[valueIndex]);
                showCCBGearWarningDialog(valueIndex == 3);
            } else if (preference.getKey().equals(KEY_MD_LOGSIZE)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setEachLogSize(Utils.LOG_TYPE_MODEM, getIntByObj(newValue));
            } else if (preference.getKey().equals(Utils.KEY_START_AUTOMATIC_MODEM)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setLogAutoStart(
                            Utils.LOG_TYPE_MODEM, Boolean.parseBoolean(newValue.toString()));
            } else if (preference.getKey().equals(Utils.KEY_MD_SAVE_LOCATIN_IN_LOG)) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setSaveLocationInModemLog(Boolean.parseBoolean(newValue.toString()));
                boolean isCheck = Boolean.parseBoolean(newValue.toString());
                if (isCheck) {
                    showSaveLocationWarningDialog();
                }
            }
        } catch (ServiceNullException e) {
            return false;
        }
        return true;
    }

    private boolean logModePreferenceChange(ListPreference mdLogModeList, String newValue,
            String keyMdMode) throws ServiceNullException {
        String oldModeValue = mdLogModeList.getValue();
        int oldSelectedMode = mdLogModeList.findIndexOfValue(oldModeValue);
        String logModeValue = newValue;
        int selectedMode = mdLogModeList.findIndexOfValue(logModeValue);
        if (selectedMode < 0) {
            Utils.loge(TAG, "Fail to select the given mode, ignore.");
            return false;
        }
        if (selectedMode == oldSelectedMode) {
            Utils.loge(TAG, "The new selected mode is the same as old, ignore");
            return false;
        }

        mdLogModeList.setSummary(mdLogModeList.getEntries()[selectedMode]);
        Utils.logd(TAG, "preference.getKey().equals(KEY_MD_MODE) mode=" + selectedMode);
        if (selectedMode == 2 || oldSelectedMode == 2) {
            if (keyMdMode.equalsIgnoreCase(Utils.KEY_MD_MODE_1)) {
                mMdLogModeList2.setValueIndex(selectedMode);
                mMdLogModeList2.setSummary(mdLogModeList.getEntries()[selectedMode]);
            } else {
                mMdLogModeList1.setValueIndex(selectedMode);
                mMdLogModeList1.setSummary(mdLogModeList.getEntries()[selectedMode]);
            }
        }
        mMdMonitorAbnormalEventPre.setEnabled(selectedMode == 2);
        setMobileLogSmartLoggingStatus(selectedMode, oldSelectedMode);


        if (mMdAutoStartPre != null && mMdAutoStartPre.isChecked()) {
            // Make mode log mode take effect right now
            MTKLoggerServiceManager.getInstance().getService()
                .setLogAutoStart(Utils.LOG_TYPE_MODEM, true);
        }
        return true;
    }

    /*
     * If modem is in Passive Log mode, mobile log only need tag android_log,
     * kernal_log, and echo type logs only 10M
     */
    private void setMobileLogSmartLoggingStatus(int selectedMode, int oldSelectedMode)
            throws ServiceNullException {
        Utils.logd(TAG, "setMobileLogSmartLoggingStatus()");
        if (selectedMode != 2) {
            if (oldSelectedMode == 2) {
                MTKLoggerServiceManager.getInstance().getService()
                    .setEachLogSize(Utils.LOG_TYPE_MOBILE,
                        Utils.DEFAULT_CONFIG_LOG_SIZE_MAP.get(Utils.LOG_TYPE_MOBILE));
                mDefaultSharedPreferences
                        .edit()
                        .putString(
                                MobileLogSettings.KEY_MB_LOGSIZE,
                                String.valueOf(Utils.DEFAULT_CONFIG_LOG_SIZE_MAP
                                        .get(Utils.LOG_TYPE_MOBILE))).apply();
            }
            return;
        } else {
            MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_SCP, false);
            mDefaultSharedPreferences.edit().putBoolean(MobileLogSettings.KEY_MB_SCP_LOG, false)
                    .apply();
            MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_ATF, false);
            mDefaultSharedPreferences.edit().putBoolean(MobileLogSettings.KEY_MB_ATF_LOG, false)
                    .apply();
            MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_BSP, false);
            mDefaultSharedPreferences.edit().putBoolean(MobileLogSettings.KEY_MB_BSP_LOG, false)
                    .apply();
            MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_MMEDIA, false);
            mDefaultSharedPreferences.edit().putBoolean(MobileLogSettings.KEY_MB_MMEDIA_LOG, false)
                    .apply();
            MTKLoggerServiceManager.getInstance().getService()
                    .setSubLogEnable(Utils.LOG_TYPE_MOBILE, MobileLog.SUB_LOG_TYPE_SSPM, false);
            mDefaultSharedPreferences.edit().putBoolean(MobileLogSettings.KEY_MB_SSPM_LOG, false)
                    .apply();
            // 40M = radio + event + main + kernal log
            MTKLoggerServiceManager.getInstance().getService()
                    .setEachLogSize(Utils.LOG_TYPE_MOBILE, 40);
            mDefaultSharedPreferences.edit()
                    .putString(MobileLogSettings.KEY_MB_LOGSIZE, String.valueOf(40)).apply();
        }
    }

    private void setAllPreferencesEnable(boolean isEnable) {
        mMdLogModeList1.setEnabled(isEnable);
        mMdLogModeList2.setEnabled(isEnable);
        mMdLogSizeLimitPre.setEnabled(isEnable);
        mMdLogModeList1.setSummary(mMdLogModeList1.getEntry());
        mMdLogModeList2.setSummary(mMdLogModeList2.getEntry());
    }

    private int getIntByObj(Object obj) {
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showCCBGearWarningDialog(boolean isNeedShowMoreWarningInfo) {
        Utils.logi(TAG, "showCCBGearWarningDialog()."
                + " isNeedShowMoreWarningInfo = " + isNeedShowMoreWarningInfo);
        String message = getString(R.string.ccb_gear_warning_dialog_context)
                + (isNeedShowMoreWarningInfo ?
                        "\n" + getString(R.string.ccb_gear_warning_dialog_context_more) : "");
        Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.ccb_gear_warning_dialog_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void showSaveLocationWarningDialog() {
        Utils.logi(TAG, "Show save location warning dialog.");
        String message = getString(R.string.save_location_in_log_dialog_context);
        Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.save_location_in_log_dialog_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }
}
