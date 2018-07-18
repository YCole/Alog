package com.mediatek.mtklogger.proxy.communicate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class ProxyReceiver extends BroadcastReceiver {
    private static final String TAG = Utils.TAG + "/ProxyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String commandName = intent.getStringExtra(Utils.PROXY_EXTRA_CMD_OPERATE);
        String commandTarget = intent.getStringExtra(Utils.PROXY_EXTRA_CMD_TARGET);
        String commandValue = intent.getStringExtra(Utils.PROXY_EXTRA_CMD_VALUE);
        String commandResult = intent.getStringExtra(Utils.PROXY_EXTRA_CMD_RESULT);
        Utils.logi(TAG, "ProxyReceiver, action = " + action
                + ", commandName = " + commandName
                + ", commandTarget = " + commandTarget
                + ", commandValue = " + commandValue
                + ", commandResult = " + commandResult);
        ProxyCommandManager.getInstance().notifyProxyCommandDone(
                commandName, commandTarget, commandValue, commandResult);
    }

}
