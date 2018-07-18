/**
 *
 */
package com.mediatek.mtklogger.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.CompoundButton;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;
/**
 * @author mtk80130
 *
 */
public class GPSLogSettings extends PreferenceActivity
                            implements OnPreferenceChangeListener, ISettingsActivity {

    private final String mTAG = Utils.TAG + "/GPSLogSettings";

    public static final String KEY_SAVE_LOG_PATH = "save_log_path";
    private ListPreference mGPSLogPathList = null;

    private SharedPreferences mDefaultSharedPreferences;
    private OptionalActionBarSwitch mBarSwitch;
    private SettingsPreferenceFragement mPrefsFragement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.layout.gpslog_settings);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, mPrefsFragement).commit();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Utils.logi(mTAG, "Preference Change Key : " + preference.getKey() + " newValue : "
                + newValue);

        if (preference.getKey().equals(KEY_SAVE_LOG_PATH)) {
            String logModeValue = (String) newValue;
            int selectedMode = mGPSLogPathList.findIndexOfValue(logModeValue);
            if (selectedMode < 0) {
                Utils.loge(mTAG, "Fail to select the given mode, ignore.");
                return false;
            }
            mGPSLogPathList.setSummary(mGPSLogPathList.getEntries()[selectedMode]);
            Utils.logd(mTAG, "preference.getKey().equals(KEY_SAVE_LOG_PATH) path=" + selectedMode);
            if (selectedMode == 0) {
            //    SetUSBPortSetting(true);
            } else {
            //    SetUSBPortSetting(false);
            }

        }
        return true;
    }

    @Override
    public void findViews() {
        mBarSwitch = new OptionalActionBarSwitch(this);
        mGPSLogPathList = (ListPreference) mPrefsFragement.findPreference(KEY_SAVE_LOG_PATH);
    }

    @Override
    public void initViews() {
        Utils.logd(mTAG, "initViews()");
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
//        mManager = new MTKLoggerManager(this);
        boolean isSwitchChecked = mDefaultSharedPreferences.getBoolean(
                SettingsActivity.KEY_GPS_SWITCH, false);
        boolean isRecording;
        try {
            isRecording = MTKLoggerServiceManager.getInstance().getService().isAnyLogRunning();
        } catch (ServiceNullException e) {
            isRecording = false;
        }
        mBarSwitch.setChecked(isSwitchChecked);
        mBarSwitch.setEnabled(!isRecording);
        setAllPreferencesEnable(isSwitchChecked && !isRecording);

        String logPathValue = mDefaultSharedPreferences.getString(KEY_SAVE_LOG_PATH, "");
        Utils.logd(mTAG, "mDefaultSharedPreferences.getString(KEY_SAVE_LOG_PATH), " + logPathValue);

        if (TextUtils.isEmpty(logPathValue)) {
            logPathValue = getString(R.string.default_gps_path_value);
            Utils.logw(mTAG, "No default log mode value stored in default shared preference, "
                    + "try to get it from string res, logModeValue=" + logPathValue);
        }
        int selectedMode = mGPSLogPathList.findIndexOfValue(logPathValue);
        if (selectedMode < 0) {
            Utils.loge(mTAG, "Fail to select the given mode, just take the first one.");
            selectedMode = 1;
        }
        mGPSLogPathList.setValueIndex(selectedMode);
        mGPSLogPathList.setSummary(mGPSLogPathList.getEntries()[selectedMode]);

    }

    private void setAllPreferencesEnable(boolean isEnable) {
        // TODO Auto-generated method stub
        mGPSLogPathList.setEnabled(isEnable);

    }

    @Override
    public void setListeners() {
        Utils.logd(mTAG, "setListeners()");
        mGPSLogPathList.setOnPreferenceChangeListener(this);
        mBarSwitch.setOnCheckedChangeListener(new LogSwitchListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDefaultSharedPreferences.edit()
                        .putBoolean(SettingsActivity.KEY_GPS_SWITCH, isChecked).apply();
                setAllPreferencesEnable(isChecked);
            }

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isChecked = Boolean.parseBoolean(newValue.toString());
                mDefaultSharedPreferences
                        .edit()
                        .putBoolean(SettingsActivity.KEY_GPS_SWITCH,
                                Boolean.parseBoolean(newValue.toString())).apply();
                setAllPreferencesEnable(isChecked);
                return true;
            }

        });

    }

}
