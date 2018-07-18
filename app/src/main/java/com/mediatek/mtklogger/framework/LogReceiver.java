package com.mediatek.mtklogger.framework;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 */
public class LogReceiver extends BroadcastReceiver {
    private static final String TAG = Utils.TAG + "/LogReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Utils.logi(TAG, " -->onReceive(), action=" + action);
        ReceiverHandler receiverHandler = ReceiverHandler.getDefaultInstance();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Utils.setBootCompleteReceived();
            receiverHandler.obtainMessage(ReceiverHandler.MSG_RECEIVER_BOOT_COMPLETED,
                    intent).sendToTarget();
        } else if (Utils.ACTION_ADB_CMD.equals(action)) {
            receiverHandler.obtainMessage(ReceiverHandler.MSG_RECEIVER_ADB_CMD,
                    intent).sendToTarget();
        } else if (Utils.ACTION_MDLOGGER_RESTART_DONE.equals(action)) {
            receiverHandler.obtainMessage(
                    ReceiverHandler.MSG_RECEIVER_MDLOGGER_RESTART_DONE,
                    intent).sendToTarget();
        } else if (Utils.ACTION_EXP_HAPPENED.equals(action)) {
            receiverHandler.obtainMessage(ReceiverHandler.MSG_RECEIVER_EXP_HAPPENED,
                    intent).sendToTarget();
        } else if (Utils.ACTION_FROM_BYPASS.equals(action)) {
            receiverHandler.obtainMessage(ReceiverHandler.MSG_RECEIVER_FROM_BYPASS,
                    intent).sendToTarget();
        }
        Utils.logv(TAG, " OnReceive function exit.");
    }

}
