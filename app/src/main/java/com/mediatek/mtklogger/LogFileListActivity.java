package com.mediatek.mtklogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mediatek.mtklogger.settings.OptionalActionBarSwitch;
import com.mediatek.mtklogger.utils.Utils;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class LogFileListActivity extends Activity implements OnItemClickListener {

    private static final String TAG = Utils.TAG + "/LogFileListActivity";

    private static final int FINISH_CLEAR_LOG = 1;

    /**
     * Before showing log file list. We need to check current log list status
     * and file size Do such things in main thread may hang UI, so need to put
     * them into thread
     */
    private static final int DLG_CHECKING_LOG_FILES = 1001;
    private static final int MSG_SHOW_CHECKING_LOG_DIALOG = 11;
    private static final int MSG_REMOVE_CHECKING_LOG_DIALOG = 12;

    private ListView mListView;

    private Dialog mClearLogConfirmDialog;

    private List<LogFileItem> mLogItemList = new ArrayList<LogFileItem>();
    private LogFileAdapter mAdapter;
    private OptionalActionBarSwitch mActionBar;
    private int mNumSelected;
    private String mRootPath;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == FINISH_CLEAR_LOG) {
                mListView.invalidateViews();
                mNumSelected = 0;
                updateTitle(mNumSelected);
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_files);

        findViews();
        setListeners();

        new Thread(new Runnable() {
            public void run() {
                mHandler.sendEmptyMessage(MSG_SHOW_CHECKING_LOG_DIALOG);
                initLogItemList(getIntent()
                        .getStringExtra(LogFolderListActivity.EXTRA_ROOTPATH_KEY), getIntent()
                        .getStringExtra(LogFolderListActivity.EXTRA_FILTER_FILE_PATH_KEY));
                mHandler.sendEmptyMessage(MSG_REMOVE_CHECKING_LOG_DIALOG);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (mClearLogConfirmDialog != null) {
            mClearLogConfirmDialog.dismiss();
        }
        dismissProgressDialog();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mtklogger_contact_menu, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_select_all:
            setAllFileSelected(true);
            break;
        case R.id.action_unselect_all:
            setAllFileSelected(false);
            break;
        case R.id.action_delete_selected:
            clearFileSelected();
            break;
        default:
            break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * select all file list in the adapter.
     *
     * @param checked
     *            true for check
     */
    private void setAllFileSelected(boolean checked) {
        if (mListView != null) {
            for (LogFileItem logFileItem : mLogItemList) {
                logFileItem.setChecked(checked);
            }
            mNumSelected = checked ? mLogItemList.size() : 0;
            mListView.invalidateViews();
        }
        updateTitle(mNumSelected);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CheckBox checkBox = (CheckBox) view.findViewById(R.id.log_files_check_box);
        if (checkBox != null) {
            boolean isChecked = checkBox.isChecked();
            checkBox.setChecked(!isChecked);
            if (checkBox.isChecked()) {
                mNumSelected++;
                mLogItemList.get(position).setChecked(true);
            } else {
                mNumSelected--;
                mLogItemList.get(position).setChecked(false);
            }
            updateTitle(mNumSelected);
        }
    }

    private void findViews() {
        mListView = (ListView) findViewById(R.id.log_files_list_view);
    }

    private void initViews() {
        mAdapter = new LogFileAdapter(this);
        mListView.setAdapter(mAdapter);

        mActionBar = new OptionalActionBarSwitch(this, mNumSelected);
    }

    private void updateTitle(int num) {
        mActionBar.updateTitle(num);
    }

    private void setListeners() {
        if (mListView != null) {
            mListView.setOnItemClickListener((OnItemClickListener) LogFileListActivity.this);
        }
    }

    private void initLogItemList(String rootPath, String filterPath) {
        if (rootPath == null || "".equals(rootPath)) {
            rootPath = Utils.getCurrentLogPath(this) + Utils.MTKLOG_PATH;
        }
        Utils.logd(TAG, "initLogItemList() rootPath = "
                + rootPath + "; filterPath = " + filterPath);
        if (!new File(rootPath).exists()) {
            return;
        }
        mRootPath = rootPath;
        String[] files = new File(rootPath).list();
        if (files == null || files.length == 0) {
            return;
        }
        File filterFile = new File(filterPath);
        boolean doFilter = (null != filterFile && filterFile.exists());
        for (String fileName : files) {
            if (doFilter && fileName.equals(filterFile.getName())) {
                continue;
            }
            if (fileName.equals(Utils.LOG_TREE_FILE)) {
                continue;
            }
            mLogItemList.add(new LogFileItem(fileName, 0));
        }

        Collections.sort(mLogItemList, new Comparator<LogFileItem>() {
            @Override
            public int compare(LogFileItem logFileItem1, LogFileItem logFileItem2) {
                return logFileItem1.getFileName().compareTo(logFileItem2.getFileName());
            }
        });

        // Calculate log file's size
        Utils.logd(TAG, "Calculate log file's size");
        for (LogFileItem logFileItem : mLogItemList) {
            logFileItem.setFileSize(Utils.getFileSize(mRootPath + File.separator
                    + logFileItem.getFileName()));
        }
    }

    private ProgressDialog mProgressDialog;

    /**
     * @param id
     *            int
     */
    private void createProgressDialog(int id) {
        if (id == DLG_CHECKING_LOG_FILES) {
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

    private void clearFileSelected() {
        if (mNumSelected == 0) {
            Toast.makeText(LogFileListActivity.this, getString(R.string.clear_non_selected_item),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mClearLogConfirmDialog = new AlertDialog.Builder(LogFileListActivity.this)
                .setTitle(R.string.clear_dlg_title)
                .setMessage(R.string.message_deletelog)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final ProgressDialog clearLogWaitingStopDialog = ProgressDialog.show(
                                LogFileListActivity.this, getString(R.string.clear_dialog_title),
                                getString(R.string.clear_dialog_content), true, false);
                        new Thread() {
                            @Override
                            public void run() {
                                int i = mLogItemList.size() - 1;
                                for (; i >= 0; i--) {
                                    LogFileItem logFileItem = mLogItemList.get(i);
                                    Utils.logi(TAG,
                                            "Log File Item name : " + logFileItem.getFileName());
                                    if (logFileItem.isChecked()) {
                                        clearLogs(new File(mRootPath + File.separator
                                                + logFileItem.getFileName()));
                                        mLogItemList.remove(i);
                                    }
                                }
                                if (clearLogWaitingStopDialog != null) {
                                    clearLogWaitingStopDialog.cancel();
                                }
                                mHandler.sendEmptyMessage(FINISH_CLEAR_LOG);
                            }
                        }.start();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                }).create();
        mClearLogConfirmDialog.show();
    }

    private void clearLogs(File dir) {
        Utils.deleteFile(dir);
    }

    /**
     * @author MTK81255
     *
     */
    class LogFileItem {

        private String mFileName;
        private long mFileSize;
        private boolean mIsChecked;

        public LogFileItem(String fileName, long fileSize) {
            mFileName = fileName;
            mFileSize = fileSize;
        }

        public String getFileName() {
            return mFileName;
        }

        public long getFileSize() {
            return mFileSize;
        }

        public void setFileSize(long fileSize) {
            mFileSize = fileSize;
        }

        public boolean isChecked() {
            return mIsChecked;
        }

        public void setChecked(boolean isChecked) {
            mIsChecked = isChecked;
        }

    }

    /**
     * @author MTK81255
     *
     */
    class LogFileAdapter extends BaseAdapter {

        private LayoutInflater mInflater;

        public LogFileAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mLogItemList.size();
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
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.xml.log_files_item, parent, false);
            }

            TextView fileNameTextView = (TextView) view.findViewById(R.id.log_files_name);
            TextView fileSizeTextView = (TextView) view.findViewById(R.id.log_files_size);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.log_files_check_box);

            LogFileItem logFileItem = mLogItemList.get(position);
            fileNameTextView.setText(logFileItem.getFileName());
            double fileSize = logFileItem.getFileSize();
            if (fileSize < 1024) {
                fileSizeTextView.setText("Size " + logFileItem.getFileSize() + " B");
            } else if (fileSize / 1024 < 1024) {
                fileSizeTextView.setText("Size " + new DecimalFormat(".00").format(fileSize / 1024)
                        + " KB");
            } else {
                fileSizeTextView.setText("Size "
                        + new DecimalFormat(".00").format(fileSize / 1024 / 1024) + " MB");
            }
            checkBox.setChecked(logFileItem.isChecked());

            return view;
        }

    }

}
