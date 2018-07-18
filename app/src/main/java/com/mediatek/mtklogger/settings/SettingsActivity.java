package com.mediatek.mtklogger.settings;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.mediatek.mtklogger.R;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.framework.MultiModemLog;
import com.mediatek.mtklogger.utils.SelfdefinedSwitchPreference;
import com.mediatek.mtklogger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class SettingsActivity extends PreferenceActivity
                              implements OnPreferenceChangeListener, ISettingsActivity {

    private static final String TAG = Utils.TAG + "/SettingsActivity";
    private static final String sAEE_BUILD_INFO = "ro.aee.build.info";
    public static final String KEY_MB_SWITCH = "mobilelog_switch";
    public static final String KEY_MD_SWITCH = "modemlog_switch";
    public static final String KEY_NT_SWITCH = "networklog_switch";
    public static final String KEY_MT_SWITCH = "metlog_switch";
    public static final String KEY_GPS_SWITCH = "gpslog_switch";
    public static final SparseArray<String> KEY_LOG_SWITCH_MAP = new SparseArray<String>();
    static {
        KEY_LOG_SWITCH_MAP.put(Utils.LOG_TYPE_MOBILE, KEY_MB_SWITCH);
        KEY_LOG_SWITCH_MAP.put(Utils.LOG_TYPE_MODEM, KEY_MD_SWITCH);
        KEY_LOG_SWITCH_MAP.put(Utils.LOG_TYPE_NETWORK, KEY_NT_SWITCH);
        KEY_LOG_SWITCH_MAP.put(Utils.LOG_TYPE_MET, KEY_MT_SWITCH);
        KEY_LOG_SWITCH_MAP.put(Utils.LOG_TYPE_GPS, KEY_GPS_SWITCH);
    }

    public static final String KEY_ADVANCED_SETTINGS_CATEGORY = "advanced_settings_category";
    public static final String KEY_GENERAL_SETTINGS_CATEGORY = "general_settings_category";
    public static final String KEY_TAGLOG_ENABLE = "taglog_enable";
    public static final String KEY_ALWAYS_TAG_MODEM_LOG_ENABLE = "always_tag_modem_log_enable";
    public static final String KEY_ADVANCED_LOG_STORAGE_LOCATION = "log_storage_location";
    public static final String KEY_MET_LOG_ENABLE = "metlog_enable";
    public static final String KEY_BT_LOG_ENABLE = "btlog_enable";
    public static final String KEY_WIFI_LOG_TOOL = "wifi_log_tool";

    private static final String WIFI_LOG_TOOL_INTENT_ACTION =
            "mediatek.intent.action.engineermode.wifilogswitch";

    private static final String BT_LOG_PACKAGE_NAME = "com.mediatek.bluetooth.dtt";
    private static final String BT_LOG_CLASS_NAME = "com.mediatek.bluetooth.dtt.MainActivity";

    private SelfdefinedSwitchPreference mMbSwitchPre;
    private SelfdefinedSwitchPreference mMdSwitchPre;
    private SelfdefinedSwitchPreference mNtSwitchPre;
    private SelfdefinedSwitchPreference mMTSwitchPre;
    private SelfdefinedSwitchPreference mGPSSwitchPre;

    private CheckBoxPreference mTaglogEnable;
    private CheckBoxPreference mAlwaysTagModemLogEnable;
    private ListPreference mLogStorageLocationList;
    private CheckBoxPreference mMetLogEnable;
    private Preference mBTLogEnable;
    private Preference mWIFILogTool;

    private SharedPreferences mDefaultSharedPreferences;
    private SharedPreferences mSharedPreferences;
    private String mFilterFileInfoStr = null;

    private long mSdcardSize = 0;

    private boolean mIsRecording = false;

    private Context mContext;
    private SettingsPreferenceFragement mPrefsFragement;

    private ProgressDialog mCheckGPSLocationEnabledDialog = null;

    private UpdateLogStorageEntriesTask mUpdateLogStorageEntriesTask =
            new UpdateLogStorageEntriesTask();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.logd(TAG, "SettingsActivity onCreate() start");
        super.onCreate(savedInstanceState);
        mPrefsFragement = new SettingsPreferenceFragement(this, R.xml.settings);
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, mPrefsFragement).commit();
        mContext = this;
        mFilterFileInfoStr = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mFilterFileInfoStr = MTKLoggerServiceManager.getInstance().getService()
                            .getValueFromNative(Utils.LOG_TYPE_MODEM,
                                    MultiModemLog.COMMAND_GET_FILTER_INFO);
                } catch (ServiceNullException e) {
                    return;
                }
            }
        }).start();
    }

    private ListView mListView = null;
    @Override
    protected void onResume() {
        Utils.logd(TAG, "onResume()");
        if (mListView == null) {
            mListView = mPrefsFragement.getListView();
            mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                        long id) {
                    Object item = mListView.getItemAtPosition(position);
                    if (mMdSwitchPre.equals(item)) {
                          SelfdefinedSwitchPreference sSwitchPre =
                                  (SelfdefinedSwitchPreference) item;
                          Utils.logd(TAG, "onCreateContextMenu sSwitchPre.getTitle() = "
                          + sSwitchPre.getTitle());
                          if (mMdSwitchPre.equals(sSwitchPre)) {
                              Utils.logi(TAG, "mFilterFileInfoStr = " + mFilterFileInfoStr);
                              String[] filterInfos = new String[]{"N/A", "N/A", "N/A"};
                              if (mFilterFileInfoStr == null || mFilterFileInfoStr.isEmpty()) {
                                  Utils.logw(TAG, "The format for mFilterFileInfoStr is error!");
                              } else {
                                  String[] filterInfoStrs = mFilterFileInfoStr.split(";");
                                  int length =
                                          filterInfoStrs.length > 3 ? 3 : filterInfoStrs.length;
                                  for (int i = 0; i < length; i++) {
                                      filterInfos[i] = filterInfoStrs[i];
                                  }
                              }
                              String lineSeparator = System.getProperty("line.separator", "/n");
                              String filePath = getString(R.string.file_info_path)
                                      + lineSeparator + filterInfos[0] + lineSeparator
                                      + lineSeparator;
                              String modifiedTime = getString(R.string.file_info_modified_time)
                                      + lineSeparator + filterInfos[1] + lineSeparator
                                      + lineSeparator;
                              String fileSize = getString(R.string.file_info_size)
                                      + lineSeparator + filterInfos[2];
                              showLogSettingsInfoDialog(
                                      mMdSwitchPre.getTitle().toString(),
                                      filePath + modifiedTime + fileSize);
                          }
                    }
                    return true;
                }
            });
        }
        updateUI();
        super.onResume();
    }

    private void showLogSettingsInfoDialog(String titile, String inforStr) {
        Utils.logi(TAG, "showLogSettingsInfoDialog titile = " + titile
                + ", inforStr = " + inforStr);
        Builder builder =
                new AlertDialog.Builder(this).setTitle(titile).setMessage(inforStr)
                        .setPositiveButton(android.R.string.yes, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void dismissProgressDialog() {
        Utils.logd(TAG, "--> dismissProgressDialog");
        if (mCheckGPSLocationEnabledDialog != null) {
            Utils.logd(TAG, "Dismiss Dialog.");
            mCheckGPSLocationEnabledDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();

        super.onDestroy();
    }

    @Override
    public void findViews() {
        mMbSwitchPre = (SelfdefinedSwitchPreference) mPrefsFragement.findPreference(KEY_MB_SWITCH);
        mMdSwitchPre = (SelfdefinedSwitchPreference) mPrefsFragement.findPreference(KEY_MD_SWITCH);
        mNtSwitchPre = (SelfdefinedSwitchPreference) mPrefsFragement.findPreference(KEY_NT_SWITCH);
        mMTSwitchPre = (SelfdefinedSwitchPreference) mPrefsFragement.findPreference(KEY_MT_SWITCH);
        mGPSSwitchPre = (SelfdefinedSwitchPreference) mPrefsFragement.
                findPreference(KEY_GPS_SWITCH);

        mTaglogEnable = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_TAGLOG_ENABLE);
        mAlwaysTagModemLogEnable = (CheckBoxPreference) mPrefsFragement.findPreference(
                KEY_ALWAYS_TAG_MODEM_LOG_ENABLE);

        mLogStorageLocationList =
                (ListPreference) mPrefsFragement.findPreference(KEY_ADVANCED_LOG_STORAGE_LOCATION);
        mMetLogEnable = (CheckBoxPreference) mPrefsFragement.findPreference(KEY_MET_LOG_ENABLE);
        mBTLogEnable = (Preference) mPrefsFragement.findPreference(KEY_BT_LOG_ENABLE);
        mWIFILogTool = (Preference) mPrefsFragement.findPreference(KEY_WIFI_LOG_TOOL);
    }

    @Override
    public void initViews() {
        mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences = getSharedPreferences(Utils.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        String aeebuildinfo = SystemProperties.get(sAEE_BUILD_INFO);
        Utils.logd(TAG, "ro.aee.build.info:" + aeebuildinfo);
        int isMETLogSupport = 0;
        try {
            isMETLogSupport = MTKLoggerServiceManager.getInstance().getService()
                    .getSpecialFeatureSupport(Utils.LOG_TYPE_MET);
        } catch (ServiceNullException e) {
            isMETLogSupport = 0;
        }
        if (isMETLogSupport != 1) {
            Utils.logi(TAG, "Hide metlog");
            // Hide metlog
            PreferenceCategory advancePreCategory =
                    (PreferenceCategory) mPrefsFragement.
                    findPreference(KEY_ADVANCED_SETTINGS_CATEGORY);
            advancePreCategory.removePreference(mMetLogEnable);
            mMetLogEnable = null;

        }

        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(BT_LOG_PACKAGE_NAME, 0);
        } catch (NameNotFoundException e) {
            Utils.logw(TAG, "BTLog tool does not exist! Remove Start UI.");
            PreferenceCategory advancePreCategory =
                    (PreferenceCategory) mPrefsFragement.
                    findPreference(KEY_ADVANCED_SETTINGS_CATEGORY);
            advancePreCategory.removePreference(mBTLogEnable);
            mBTLogEnable = null;
        }

        Intent wifiLogToolIntent = new Intent(WIFI_LOG_TOOL_INTENT_ACTION);
        ResolveInfo ri = pm.resolveActivity(wifiLogToolIntent, 0);
        if (ri == null) {
            Utils.logw(TAG, "WIFI Log Tool does not exist! Remove Start UI.");
            PreferenceCategory advancePreCategory =
                    (PreferenceCategory) mPrefsFragement.
                    findPreference(KEY_ADVANCED_SETTINGS_CATEGORY);
            advancePreCategory.removePreference(mWIFILogTool);
            mWIFILogTool = null;
        }

        if (!Utils.BUILD_TYPE.equals("eng")) {
            Utils.logd(TAG, "initViews() BuildType is not eng.");
            mTaglogEnable.setChecked(mSharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, false));

        } else {
            mTaglogEnable.setChecked(mSharedPreferences.getBoolean(Utils.TAG_LOG_ENABLE, true));
        }
        mAlwaysTagModemLogEnable.setEnabled(mTaglogEnable.isChecked());

        // Hide AlwaysTagModemLog settings
        PreferenceCategory advancePreCategory =
                (PreferenceCategory) mPrefsFragement.findPreference(
                        KEY_ADVANCED_SETTINGS_CATEGORY);
        advancePreCategory.removePreference(mAlwaysTagModemLogEnable);
        mAlwaysTagModemLogEnable = null;

        setDefaultEntry();
        setSdcardSize();
        updateUI();
        mUpdateLogStorageEntriesTask = new UpdateLogStorageEntriesTask();
        mUpdateLogStorageEntriesTask.execute();
    }

    private void setDefaultEntry() {
        List<CharSequence> entriesList = new ArrayList<CharSequence>();
        List<CharSequence> entryValuesList = new ArrayList<CharSequence>();
        entriesList.add(getString(Utils.LOG_PHONE_STORAGE));
        entryValuesList.add(Utils.LOG_PHONE_STORAGE_KEY);
        mLogStorageLocationList
        .setEntries(entriesList.toArray(new CharSequence[entriesList.size()]));
        mLogStorageLocationList.setEntryValues(entryValuesList
        .toArray(new CharSequence[entryValuesList.size()]));
        mLogStorageLocationList.setValue(Utils.LOG_PHONE_STORAGE_KEY);
        mLogStorageLocationList.setSummary(Utils.LOG_PHONE_STORAGE);
    }

    @Override
    public void setListeners() {
        mMbSwitchPre.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Utils.logd(TAG, "mMbSwitchPre onPreferenceClick");
                Intent intent = new Intent(SettingsActivity.this, MobileLogSettings.class);
                intent.putExtra(Utils.SETTINGS_IS_SWITCH_CHECKED, mMbSwitchPre.isChecked());
                setSdcardSize();
                intent.putExtra(Utils.SDCARD_SIZE, mSdcardSize);
                startActivity(intent);
                return true;
            }
        });

        mMbSwitchPre.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean checked = (Boolean) newValue;
                Utils.logd(TAG, "mMbSwitchPre onPreferenceChange = " + checked);
                // Force new enable status persist manually, to cover plug out
                // battery event
                mDefaultSharedPreferences.edit().putBoolean(KEY_MB_SWITCH + "_bak", checked)
                        .apply();
                return true;
            }
        });

        mMdSwitchPre.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Utils.logd(TAG, "mMdSwitchPre onPreferenceClick");
                final Intent intent = new Intent(SettingsActivity.this, ModemLogSettings.class);
                intent.putExtra(Utils.SETTINGS_IS_SWITCH_CHECKED, mMdSwitchPre.isChecked());
                setSdcardSize();
                intent.putExtra(Utils.SDCARD_SIZE, mSdcardSize);
                startActivity(intent);
                return true;
            }
        });

        mMdSwitchPre.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean checked = (Boolean) newValue;
                Utils.logd(TAG, "mMdSwitchPre onPreferenceChange = " + checked);
                // Force new enable status persist manually, to cover plug out
                // battery event
                mDefaultSharedPreferences.edit().putBoolean(KEY_MD_SWITCH + "_bak", checked)
                        .apply();
                return true;
            }
        });

        mNtSwitchPre.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Utils.logd(TAG, "mNtSwitchPre onPreferenceClick");
                Intent intent = new Intent(SettingsActivity.this, NetworkLogSettings.class);
                intent.putExtra(Utils.SETTINGS_IS_SWITCH_CHECKED, mNtSwitchPre.isChecked());
                setSdcardSize();
                intent.putExtra(Utils.SDCARD_SIZE, mSdcardSize);
                startActivity(intent);
                return true;
            }
        });

        mNtSwitchPre.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean checked = (Boolean) newValue;
                Utils.logd(TAG, "mNtSwitchPre onPreferenceChange = " + checked);
                // Force new enable status persist manually, to cover plug out
                // battery event
                mDefaultSharedPreferences.edit().putBoolean(KEY_NT_SWITCH + "_bak", checked)
                        .apply();
                return true;
            }
        });

        mMTSwitchPre.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            public boolean onPreferenceClick(Preference arg0) {
                Utils.logd(TAG, "mMTSwitchPre onPreferenceClick");
                Intent intent = new Intent(SettingsActivity.this, MetLogSettings.class);
                intent.putExtra(Utils.SETTINGS_IS_SWITCH_CHECKED, mMTSwitchPre.isChecked());
                setSdcardSize();
                intent.putExtra(Utils.SDCARD_SIZE, mSdcardSize);
                startActivity(intent);
                return true;
            }

        });

        mMTSwitchPre.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean checked = (Boolean) newValue;
                Utils.logd(TAG, "mMTSwitchPre onPreferenceChange = " + checked);
                // Force new enable status persist manually, to cover plug out
                // battery event
                mDefaultSharedPreferences.edit().putBoolean(KEY_MT_SWITCH + "_bak", checked)
                        .apply();
                return true;
            }
        });

        mGPSSwitchPre.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            public boolean onPreferenceClick(Preference arg0) {
                Utils.logd(TAG, "mGPSSwitchPre onPreferenceClick");
                Intent intent = new Intent(SettingsActivity.this, GPSLogSettings.class);
                intent.putExtra(Utils.SETTINGS_IS_SWITCH_CHECKED, mGPSSwitchPre.isChecked());
                // setSdcardSize();
                // intent.putExtra(Utils.SDCARD_SIZE, mSdcardSize);
                startActivity(intent);
                return true;
            }

        });

        mGPSSwitchPre.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean checked = (Boolean) newValue;
                Utils.logd(TAG, "mGPSSwitchPre onPreferenceChange = " + checked);
                // Force new enable status persist manually, to cover plug out
                // battery event
                mDefaultSharedPreferences.edit().putBoolean(KEY_GPS_SWITCH + "_bak", checked)
                        .apply();
                return true;
            }
        });

        mTaglogEnable.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                mSharedPreferences.edit()
                        .putBoolean(Utils.TAG_LOG_ENABLE, mTaglogEnable.isChecked())
                        .remove(Utils.KEY_MODEM_EXCEPTION_PATH)// When enable
                                                               // taglog, clear
                                                               // old data
                        .apply();
                if (mAlwaysTagModemLogEnable != null) {
                    mAlwaysTagModemLogEnable.setEnabled(mTaglogEnable.isChecked());
                }
                return true;
            }
        });
        if (mMetLogEnable != null) {
            mMetLogEnable.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    mSharedPreferences.edit()
                            .putBoolean(Utils.MET_LOG_ENABLE, mMetLogEnable.isChecked()).apply();
                    updateUI();
                    return true;
                }
            });
        }
        if (mBTLogEnable != null) {
            mBTLogEnable.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        ComponentName cn = new ComponentName(
                                BT_LOG_PACKAGE_NAME, BT_LOG_CLASS_NAME);
                        intent.setComponent(cn);
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Utils.logw(TAG, "BTLog tool does not exist!");
                    }
                    return true;
                }
            });
        }

        if (mWIFILogTool != null) {
            mWIFILogTool.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    Utils.logi(TAG, "Sent intent to open WIFI Log Tool.");
                    Intent wifiLogToolIntent = new Intent(WIFI_LOG_TOOL_INTENT_ACTION);
                    startActivity(wifiLogToolIntent);
                    return true;
                }
            });
        }

        mLogStorageLocationList.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Utils.logi(TAG, "Preference Change Key : " + preference.getKey() + " newValue : "
                + newValue);

        if (preference.getKey().equals(KEY_ADVANCED_LOG_STORAGE_LOCATION)) {
            String oldValue = Utils.LOG_PHONE_STORAGE_KEY;
            if (Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getModemLogPathType())
                    && Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(Utils.getLogPathType())) {
                oldValue = Utils.LOG_MODEM_TO_SD_CARD_KEY;
            } else {
                oldValue =
                        Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getLogPathType())
                        ? Utils.LOG_SD_CARD_KEY : Utils.LOG_PHONE_STORAGE_KEY;
            }
            if (oldValue.equals(newValue.toString())) {
                return true;
            }
            if (Utils.LOG_MODEM_TO_SD_CARD_KEY.equals(newValue)) {
                preference.setSummary(Utils.LOG_MODEM_TO_SD_CARD);
                Utils.setLogPathType(Utils.LOG_PATH_TYPE_INTERNAL_SD);
                Utils.setModemLogPathType(Utils.LOG_PATH_TYPE_EXTERNAL_SD);
            } else {
                preference.setSummary(Utils.LOG_SD_CARD_KEY.equals(newValue.toString())
                        ? Utils.LOG_SD_CARD : Utils.LOG_PHONE_STORAGE);
                String logPathType = Utils.LOG_SD_CARD_KEY.equals(newValue.toString())
                        ? Utils.LOG_PATH_TYPE_EXTERNAL_SD :
                            Utils.LOG_PATH_TYPE_INTERNAL_SD;
                Utils.setLogPathType(logPathType);
                Utils.setModemLogPathType(logPathType);
            }
            setSdcardSize();
        }
        return true;
    }

    private void setSdcardSize() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StatFs statFs = new StatFs(Utils.getCurrentLogPath(mContext));
                    long blockSize = statFs.getBlockSizeLong() / 1024;
                    mSdcardSize = statFs.getBlockCountLong() * blockSize / 1024;
                } catch (IllegalArgumentException e) {
                    Utils.loge(TAG,
                            "setSdcardSize() : StatFs error, maybe currentLogPath is invalid");
                    mSdcardSize = 0;
                }
            }
        }).run();
    }

    /**
     * @author MTK81255
     *
     */
    private class UpdateLogStorageEntriesTask extends AsyncTask<Void, Void, Void> {

        List<CharSequence> mEntriesList = new ArrayList<CharSequence>();
        List<CharSequence> mEntryValuesList = new ArrayList<CharSequence>();

        @Override
        protected Void doInBackground(Void... params) {
            // First check whether storage is mounted
            String status =
                    Utils.getVolumeState(mContext,
                            Utils.getLogPath(mContext, Utils.LOG_PATH_TYPE_INTERNAL_SD));
            boolean isInternalStorageExist = Environment.MEDIA_MOUNTED.equals(status);
            if (isInternalStorageExist) {
                mEntriesList.add(getString(Utils.LOG_PHONE_STORAGE));
                mEntryValuesList.add(Utils.LOG_PHONE_STORAGE_KEY);
            }

            status =
                    Utils.getVolumeState(mContext,
                            Utils.getLogPath(mContext, Utils.LOG_PATH_TYPE_EXTERNAL_SD));
            boolean isExternalStorageExist = Environment.MEDIA_MOUNTED.equals(status);
            if (isExternalStorageExist) {
                mEntriesList.add(getString(Utils.LOG_SD_CARD));
                mEntryValuesList.add(Utils.LOG_SD_CARD_KEY);
            }
            if (isInternalStorageExist & isExternalStorageExist) {
                mEntriesList.add(getString(Utils.LOG_MODEM_TO_SD_CARD));
                mEntryValuesList.add(Utils.LOG_MODEM_TO_SD_CARD_KEY);
            }
            return null;
        }

        // This is called when doInBackground() is finished
        @Override
        protected void onPostExecute(Void result) {
            setLogStorageEntries(mEntriesList, mEntryValuesList);
        }
    }

    /**
     * @param entriesList
     *            List<CharSequence>
     * @param entryValuesList
     *            List<CharSequence>
     */
    private void setLogStorageEntries(List<CharSequence> entriesList,
            List<CharSequence> entryValuesList) {
        if (entriesList.size() == 0) {
            mLogStorageLocationList.setEnabled(false);
            return;
        }
        mLogStorageLocationList.setEnabled(!mIsRecording);
        mLogStorageLocationList.setEntries(null);
        mLogStorageLocationList.setEntryValues(null);
        mLogStorageLocationList
                .setEntries(entriesList.toArray(new CharSequence[entriesList.size()]));
        mLogStorageLocationList.setEntryValues(entryValuesList
                .toArray(new CharSequence[entryValuesList.size()]));
        if (Utils.LOG_PATH_TYPE_INTERNAL_SD.equals(Utils.getLogPathType())
                && Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getModemLogPathType())) {
            mLogStorageLocationList.setValue(Utils.LOG_MODEM_TO_SD_CARD_KEY);
            mLogStorageLocationList.setSummary(Utils.LOG_MODEM_TO_SD_CARD);
            return;
        }
        String logPathTypeKey =
                Utils.LOG_PATH_TYPE_EXTERNAL_SD.equals(Utils.getLogPathType())
                        ? Utils.LOG_SD_CARD_KEY : Utils.LOG_PHONE_STORAGE_KEY;
        mLogStorageLocationList.setValue(logPathTypeKey);
        mLogStorageLocationList.setSummary(Utils.LOG_SD_CARD_KEY.equals(logPathTypeKey)
                ? Utils.LOG_SD_CARD : Utils.LOG_PHONE_STORAGE);
    }

    private void updateUI() {
        Utils.logi(TAG, "updateUI()");

        for (Integer logType : Utils.LOG_TYPE_SET) {
            boolean isRecording;
            try {
                isRecording = MTKLoggerServiceManager.getInstance().getService()
                        .isTypeLogRunning(logType);
            } catch (ServiceNullException e) {
                isRecording = false;
            }
            if (isRecording) {
                mIsRecording = true;
                break;
            }
        }
        mMbSwitchPre.setEnabled(!mIsRecording);
        mMdSwitchPre.setEnabled(!mIsRecording);
        mNtSwitchPre.setEnabled(!mIsRecording);
        mMTSwitchPre.setEnabled(!mIsRecording);
        mGPSSwitchPre.setEnabled(!mIsRecording);

        CharSequence[] logStorageEntries = mLogStorageLocationList.getEntries();
        if ((logStorageEntries == null) || (logStorageEntries.length == 0)) {
            Utils.logw(TAG, "Log storage entry is null or empty, disable storage set item");
            mLogStorageLocationList.setEnabled(false);
        } else {
            mLogStorageLocationList.setEnabled(!mIsRecording);
        }

        mMbSwitchPre.setChecked(mDefaultSharedPreferences.getBoolean(KEY_MB_SWITCH, true));
        mMdSwitchPre.setChecked(mDefaultSharedPreferences.getBoolean(KEY_MD_SWITCH, true));
        mNtSwitchPre.setChecked(mDefaultSharedPreferences.getBoolean(KEY_NT_SWITCH, true));

        mMTSwitchPre.setChecked(mDefaultSharedPreferences.getBoolean(KEY_MT_SWITCH, true));
        mGPSSwitchPre.setChecked(mDefaultSharedPreferences.getBoolean(KEY_GPS_SWITCH, true));

        PreferenceCategory advancePreCategory =
                (PreferenceCategory) mPrefsFragement.findPreference(KEY_GENERAL_SETTINGS_CATEGORY);

        if (mSharedPreferences.getBoolean(Utils.MET_LOG_ENABLE, false) != true) {
            advancePreCategory.removePreference(mMTSwitchPre);

        } else {
            advancePreCategory.addPreference(mMTSwitchPre);
        }
        if (mMetLogEnable != null) {
            mMetLogEnable.setEnabled(!mIsRecording);
            mMetLogEnable.setChecked(mSharedPreferences.getBoolean(Utils.MET_LOG_ENABLE, false));
        }
    }

}
