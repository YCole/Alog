package com.mediatek.mtklogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class LogFolderListActivity extends Activity implements
        OnItemClickListener, OnItemLongClickListener {

    private static final String TAG = Utils.TAG + "/LogFolderListActivity";

    private static final int CANCEL_MENU_ID = 11;
    private static final int CLEAR_ALL_MENU_ID = 12;

    public static final String EXTRA_ROOTPATH_KEY = "rootpath";
    public static final String EXTRA_FROM_WHERE_KEY = "fromWhere";
    public static final String EXTRA_FILTER_FILE_PATH_KEY = "filterFilePath";
    public static final String FROM_TAGLOG = "fromTagLog";
    public static final String EXTRA_TAGLOG_INPUT_NAME_KEY = "taglogInputName";

    public static final SparseArray<String> EXTRA_FILTER_FILES_KEY = new SparseArray<String>();
    static {
        EXTRA_FILTER_FILES_KEY.put(Utils.LOG_TYPE_MODEM, "filterModemFile");
        EXTRA_FILTER_FILES_KEY.put(Utils.LOG_TYPE_MOBILE, "filterMobileFile");
        EXTRA_FILTER_FILES_KEY.put(Utils.LOG_TYPE_NETWORK, "filterNetworkFile");
        EXTRA_FILTER_FILES_KEY.put(Utils.LOG_TYPE_MET, "filterMetFile");
        EXTRA_FILTER_FILES_KEY.put(Utils.LOG_TYPE_GPS, "filterGPSFile");
    }
    public static final SparseArray<String> EXTRA_MODEM_FILTER_FILES_KEY =
            new SparseArray<String>();
    static {
        EXTRA_MODEM_FILTER_FILES_KEY.put(Utils.MODEM_LOG_TYPE_DEFAULT, "filterModemFile");
        EXTRA_MODEM_FILTER_FILES_KEY.put(Utils.MODEM_LOG_TYPE_DUAL, "filterDualModemFile");
        EXTRA_MODEM_FILTER_FILES_KEY.put(Utils.MODEM_LOG_TYPE_EXT, "filterExtModemFile");
        for (int i = 1; i <= Utils.MODEM_MAX_COUNT; i++) {
            if (Utils.isTypeMDEnable(i)) {
                EXTRA_MODEM_FILTER_FILES_KEY.put(Utils.MODEM_LOG_K2_INDEX + i,
                        "filterModemFile" + i);
                Utils.logd(TAG + "/Utils", "EXTRA_MODEM_FILTER_FILES_KEY added index: "
                        + i);
            }
        }
    }

    private String mRootPath;
    private ListView mListView;

    private static final int FINISH_CLEAR_LOG = 1;
    /**
     * Before enter show log folder list. We need to check current log folder status,
     *  whether they are empty.
     * Do such things in main thread may hang UI, so need to put them into thread
     */
    private static final int DLG_CHECKING_LOG_FILES = 1001;
    private static final int MSG_SHOW_CHECKING_LOG_DIALOG = 11;
    private static final int MSG_REMOVE_CHECKING_LOG_DIALOG = 12;

    private List<LogFileItem> mLogFolderList = new ArrayList<LogFileItem>();
    private LogFolderAdapter mAdapter;
    private Dialog mClearLogConfirmDialog;
    private static final int DLG_WAITING_DELETE = 1;

    private boolean mIsLongClick = false;
    // When mIsClearDone <= 0, all clear is done
    private int mIsClearDone;
    /**
     * Since more than one thread may change clear log count value, add synchronized to it.
     */
    private Object mClearLogCountLock = new Object();
    private boolean mIsClearing = false;

    /**
     * @author MTK81255
     *
     */
    static class LogFileItem {

        String mFileName;
        String mShowName;
        String mFilterFilePath;

        public LogFileItem(String fileName, String showName,
                String filterFilePath) {
            this.mFileName = fileName;
            this.mShowName = showName;
            if (null == filterFilePath) {
                filterFilePath = "";
            }
            this.mFilterFilePath = filterFilePath;
        }

    }

    private Handler mClearLogProgressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == FINISH_CLEAR_LOG) {
//                mListView.invalidateViews();
//                dismissProgressDialog();
                finish();
            } else if (msg.what == MSG_SHOW_CHECKING_LOG_DIALOG) {
                Utils.logv(TAG, "Show waiting checking log files dialog now.");
                createProgressDialog(DLG_CHECKING_LOG_FILES);
            } else if (msg.what == MSG_REMOVE_CHECKING_LOG_DIALOG) {
                Utils.logv(TAG, "Remove waiting checking log files dialog now.");
                dismissProgressDialog();
                initViews();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Utils.logd(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_folder);

        findViews();
        setListeners();

        new Thread(new Runnable() {
            public void run() {
                initLogItemList(getIntent().getStringExtra(EXTRA_ROOTPATH_KEY));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        Utils.logd(TAG, "onDestroy()");
        if (mClearLogConfirmDialog != null && mClearLogConfirmDialog.isShowing()) {
            mClearLogConfirmDialog.dismiss();
        }
        dismissProgressDialog();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Utils.logd(TAG, "onResume()");
        super.onResume();
    }

    @Override
    public void finish() {
        Utils.logd(TAG, "finish()");
        String fromWhere = getIntent().getStringExtra(EXTRA_FROM_WHERE_KEY);
        if (fromWhere != null && FROM_TAGLOG.equals(fromWhere)) {
            Intent intent = new Intent();
            intent.setAction(Utils.ACTION_EXP_HAPPENED);
            intent.putExtra(Utils.EXTRA_KEY_EXP_PATH, getIntent()
                    .getStringExtra(Utils.EXTRA_KEY_EXP_PATH));
            intent.putExtra(EXTRA_TAGLOG_INPUT_NAME_KEY, getIntent()
                    .getStringExtra(EXTRA_TAGLOG_INPUT_NAME_KEY));
            Utils.sendBroadCast(intent);
        }
        super.finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, CANCEL_MENU_ID, 1, getString(R.string.cancel_menu))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, CLEAR_ALL_MENU_ID, 2,
                getString(R.string.clear_all_menu)).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case CANCEL_MENU_ID:
            finish();
            break;
        case CLEAR_ALL_MENU_ID:
            clearAllLogs();
            break;
        default:
            break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterview, View view,
            int i, long l) {
        mIsLongClick = true;
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        if (mIsLongClick) {
            mIsLongClick = false;
            return;
        }
        Intent intent = new Intent(LogFolderListActivity.this,
                LogFileListActivity.class);
        intent.putExtra(EXTRA_ROOTPATH_KEY,
                mRootPath + mLogFolderList.get(position).mFileName);
        intent.putExtra(EXTRA_FILTER_FILE_PATH_KEY,
                mLogFolderList.get(position).mFilterFilePath);
        startActivity(intent);
    }

    private void findViews() {
        mListView = (ListView) findViewById(R.id.log_folder_list_view);
    }

    private void initViews() {
        mAdapter = new LogFolderAdapter(this);
        mListView.setAdapter(mAdapter);
    }

    private ProgressDialog mProgressDialog;
    /**
     * @param id int
     */
    private void createProgressDialog(int id) {
        if (id == DLG_WAITING_DELETE) {
            mProgressDialog = ProgressDialog.show(LogFolderListActivity.this,
                    getString(R.string.clear_dialog_title),
                    getString(R.string.clear_dialog_content),
                    true, false);
        } else if (id == DLG_CHECKING_LOG_FILES) {
            mProgressDialog = ProgressDialog.show(this, null,
                    getString(R.string.waiting_checking_log_dialog_message), true, false);
        }
    }

    private void dismissProgressDialog() {
        if (null != mProgressDialog) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void setListeners() {
        if (mListView != null) {
            mListView.setOnItemClickListener(LogFolderListActivity.this);
            mListView.setOnItemLongClickListener(LogFolderListActivity.this);
        }
    }

    private void clearAllLogs() {
        mClearLogConfirmDialog = new AlertDialog.Builder(
                LogFolderListActivity.this)
                .setTitle(R.string.clear_all_dlg_title)
                .setMessage(R.string.message_delete_all_log)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                if (mIsClearing) {
                                    return;
                                }
                                mIsClearing = true;

                                createProgressDialog(DLG_WAITING_DELETE);

                                mIsClearDone = mLogFolderList.size();
                                for (final LogFileItem logFileItem : mLogFolderList) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            Intent intent = new Intent();
                                            intent.setAction(Utils.ACTION_TO_BTLOG);
                                            intent.setPackage(Utils.BTLOG_PACKAGE);
                                            intent.putExtra(Utils.EXTRA_BTLOG_OPERATE,
                                                    Utils.VALUE_BTLOG_OPERATE);
                                            Utils.sendBroadCast(intent);
                                            clearAllLogs(
                                                    new File(mRootPath + File.separator
                                                            + logFileItem.mFileName),
                                                    logFileItem.mFilterFilePath);
                                            synchronized (mClearLogCountLock) {
                                                mIsClearDone--;
                                                Utils.logi(TAG, "[" + logFileItem.mFileName
                                                        + "] clear log done, mIsClearDone="
                                                        + mIsClearDone);
                                            }
                                        }
                                    } .start();
                                }
                                new Thread() {
                                    @Override
                                    public void run() {
                                        while (mIsClearDone > 0) {
                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        mIsClearing = false;
                                        Utils.logd(TAG, "Detelect log folder clear done");
                                        mClearLogProgressHandler.sendEmptyMessage(FINISH_CLEAR_LOG);
                                    }
                                } .start();
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                dialog.dismiss();
                            }
                        }).create();
        mClearLogConfirmDialog.show();
    }


    private void clearAllLogs(File dir, String filterFilePath) {
        Utils.logi(TAG, "clearAllLogs() : dir=" + dir.getAbsolutePath()
                + " filterFilePath=" + filterFilePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File filterFile = new File(filterFilePath);
        boolean doFilter = (null != filterFile && filterFile.exists());
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (doFilter && file.getName().equals(filterFile.getName())) {
                continue;
            }
            if (file.getName().equals(Utils.LOG_TREE_FILE)) {
                continue;
            }
            clearLogs(file);
        }
    }

    private void clearLogs(File dir) {
        Utils.deleteFile(dir);
    }

    private void initLogItemList(String rootPath) {
        mClearLogProgressHandler.sendEmptyMessage(MSG_SHOW_CHECKING_LOG_DIALOG);
        if (rootPath == null || "".equals(rootPath)) {
            rootPath = Utils.getCurrentLogPath(this) + Utils.MTKLOG_PATH;
        }

        Utils.logd(TAG, "initLogItemList() rootPath = " + rootPath);
        if (!new File(rootPath).exists()) {
            mClearLogProgressHandler.sendEmptyMessage(MSG_REMOVE_CHECKING_LOG_DIALOG);
            return;
        }
        mRootPath = rootPath;
        for (Integer logType : Utils.LOG_TYPE_SET) {
            if (logType == Utils.LOG_TYPE_MODEM) {
                continue; //Check modem log folder in the following loop
            }
            File logRootPath = new File(rootPath + Utils.LOG_PATH_MAP.get(logType));
            if (logRootPath != null && logRootPath.exists()) {
                File[] files = logRootPath.listFiles();
                if (files != null && files.length > 0) {
                    mLogFolderList.add(new LogFileItem(Utils.LOG_PATH_MAP.get(logType),
                            getString(Utils.LOG_NAME_MAP.get(logType)),
                            getIntent().getStringExtra(
                                    EXTRA_FILTER_FILES_KEY.get(logType))));
                }
            }
        }

        for (int modemIndex : Utils.MODEM_INDEX_SET) {
            File modemLogRootFile = new File(rootPath
                    + Utils.MODEM_INDEX_FOLDER_MAP.get(modemIndex));
            if (modemLogRootFile != null && modemLogRootFile.exists()) {
                File[] files = modemLogRootFile.listFiles();
                if (files != null && files.length > 0) {
                    mLogFolderList.add(new LogFileItem(Utils.MODEM_INDEX_FOLDER_MAP.get(modemIndex),
                            getString(Utils.MODEM_LOG_NAME_MAP.get(modemIndex)),
                            getIntent().getStringExtra(
                                    EXTRA_MODEM_FILTER_FILES_KEY.get(modemIndex))));
                }
            }
        }

        // Add C2KMDLog clear
        File c2kmdFile = new File(rootPath + Utils.C2K_MODEM_LOG_PATH);
        if (c2kmdFile != null && c2kmdFile.exists()) {
            File[] files = c2kmdFile.listFiles();
            if (files != null && files.length > 0) {
                mLogFolderList.add(new LogFileItem(Utils.C2K_MODEM_LOG_PATH,
                        Utils.C2K_MODEM_LOG_PATH.toUpperCase(), this
                                .getSharedPreferences(Utils.CONFIG_FILE_NAME,
                                        Context.MODE_PRIVATE).getString(
                                        Utils.KEY_C2K_MODEM_LOGGING_PATH, "")));
            }
        }

        File taglogRootPath = new File(rootPath + Utils.TAG_LOG_PATH);
        if (taglogRootPath != null && taglogRootPath.exists()) {
            File[] files = taglogRootPath.listFiles();
            if (files != null && files.length > 0) {
                mLogFolderList.add(new LogFileItem(Utils.TAG_LOG_PATH,
                        getString(R.string.tag_log_name), ""));
            }
        }
        mClearLogProgressHandler.sendEmptyMessage(MSG_REMOVE_CHECKING_LOG_DIALOG);
    }

    /**
     * @author MTK81255
     *
     */
    class LogFolderAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public LogFolderAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mLogFolderList.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.xml.log_folder_item, parent, false);
            }

            TextView textView = (TextView) view
                    .findViewById(R.id.log_folder_name);
            textView.setText(mLogFolderList.get(position).mShowName);

            return view;
        }

    }

}
