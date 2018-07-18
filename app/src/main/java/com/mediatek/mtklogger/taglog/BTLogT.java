package com.mediatek.mtklogger.taglog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class BTLogT extends LogInstanceForTaglog {

    /**
     * @param logType int
     * @param tagLogInformation TagLogInformation
     */
    public BTLogT(int logType, TagLogInformation tagLogInformation) {
        super(logType, tagLogInformation);
    }

    @Override
    public boolean isNeedRestart() {
        return false;
    }

    @Override
    public boolean isNeedDoTag() {
        int needLogType = mTagLogInformation.getNeedLogType();
        if (needLogType != 0 && (needLogType & mLogType) != 0) {
            Utils.logi(TAG, "isNeedDoTag ? true. mLogType = " + mLogType + ", needLogType = "
                    + needLogType);
            return true;
        }
        Utils.logi(TAG, "isNeedDoTag ? false. mLogType = " + mLogType);
        return false;
    }

    @Override
    public String getNeedTagPath() {
        mBTLogIntentFilter = new IntentFilter();
        mBTLogIntentFilter.addAction("com.mediatek.mtklogger.BTLOG_MOVE");
        try {
            MTKLoggerServiceManager.getInstance().getService().registerReceiver(
                    mBTLogReceiver, mBTLogIntentFilter,
                    "android.permission.DUMP", null);
        } catch (ServiceNullException e1) {
            return mBTLogPath;
        }
        Intent intent = new Intent("com.mediatek.bluetooth.dtt.BTLOG_MOVE");
        intent.setPackage(Utils.BTLOG_PACKAGE);
        intent.putExtra("cmd_name", "start_move");
        intent.putExtra("cmd_target", 1);
        Utils.sendBroadCast(intent);

        int i = 0;
        while (mBTLogPath == null || mBTLogPath.isEmpty()) {
            try {
                i += 1000;
                Thread.sleep(1000);
                if (i >= 60 * 1000) {
                    Utils.loge(TAG, "Wating BTLog path time out!");
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            MTKLoggerServiceManager.getInstance().getService().unregisterReceiver(mBTLogReceiver);
        } catch (ServiceNullException e) {
            return mBTLogPath;
        }
        Utils.logi(TAG, "mBTLogPath = " + mBTLogPath);
        return mBTLogPath;
    }

    private String mBTLogPath = "";
    private IntentFilter mBTLogIntentFilter = null;
    private BroadcastReceiver mBTLogReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.mediatek.mtklogger.BTLOG_MOVE".equals(action)) {
                mBTLogPath = intent.getStringExtra("cmd_target");
            }
        }
    };

    @Override
    public String getSavingPath() {
        return "";
    }

    @Override
    public String getSavingParentPath() {
        return "";
    }

}