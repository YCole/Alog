package com.mediatek.mtklogger.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class SettingsPreferenceFragement extends PreferenceFragment {
    private static final String TAG = Utils.TAG + "/SettingsPreferenceFragement";

    private ISettingsActivity mSettingsActivity = null;
    private int mResourceId = -1;

    /**
     * default.
     */
    public SettingsPreferenceFragement() {
    }

    /**
     * @param settingsActivity ISettingsActivity
     * @param resourceId int
     */
    public SettingsPreferenceFragement(ISettingsActivity settingsActivity,
            int resourceId) {
        this.mSettingsActivity = settingsActivity;
        this.mResourceId = resourceId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Utils.logd(TAG, "onCreate() Start");
        super.onCreate(savedInstanceState);
        if (mResourceId == -1 || mSettingsActivity == null) {
            Utils.logw(TAG, "onCreate() null end!");
            return;
        }
        addPreferencesFromResource(mResourceId);
        mSettingsActivity.findViews();
        mSettingsActivity.initViews();
        mSettingsActivity.setListeners();
        Utils.logd(TAG, "onCreate() end");
    }

}
