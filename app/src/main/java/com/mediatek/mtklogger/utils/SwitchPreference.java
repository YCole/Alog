package com.mediatek.mtklogger.utils;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.mediatek.mtklogger.R;

/**
 * Self define SwitchPreferenc, use check box to replace Switch, since Switch
 * was not supported in GB.
 */
public class SwitchPreference extends Preference {
    private CheckBox mCheckBox = null;
    private boolean mIsChecked = false;
    private Context mContext;

    /**
     * @param context Context
     * @param attr AttributeSet
     */
    public SwitchPreference(Context context, AttributeSet attr) {
        super(context, attr, 0);

        mContext = context;
        setLayoutResource(R.layout.pref_switch);
        if (isPersistent()) {
            mIsChecked =
                    PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(getKey(),
                            true);
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mCheckBox = (CheckBox) view.findViewById(R.id.log_selected);
        mCheckBox.setChecked(mIsChecked);
        if (isPersistent()) {
            persistBoolean(mIsChecked);
        }
        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                callOnStateChangeListener(isChecked);
            }
        });
    }

    @Override
    protected void onClick() {
        super.onClick();
        boolean newValue = !isChecked();
        if (!callChangeListener(newValue)) {
            return;
        }
        setChecked(newValue);
    }

    public boolean isChecked() {
        return mIsChecked;
    }

    /**
     * @param checked boolean
     */
    public void setChecked(boolean checked) {
        mIsChecked = checked;
        notifyChanged();
    }

    private void callOnStateChangeListener(boolean newValue) {
        setChecked(newValue);
        this.callChangeListener(newValue);
    }

}
