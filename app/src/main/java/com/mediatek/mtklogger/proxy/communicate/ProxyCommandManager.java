package com.mediatek.mtklogger.proxy.communicate;

import android.content.Intent;

import com.mediatek.mtklogger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author MTK81255
 *
 */
public class ProxyCommandManager {
    private static final String TAG = Utils.TAG + "/ProxyCommandManager";

    private List<ProxyCommandInfo> mWaitingResponseList = new ArrayList<ProxyCommandInfo>();
    private List<IProxyCommandInfoListener> mListenerList =
            new ArrayList<IProxyCommandInfoListener>();

    private static ProxyCommandManager sInstance = new ProxyCommandManager();

    private ProxyCommandManager() {
    }

    public static ProxyCommandManager getInstance() {
        return sInstance;
    }

    /**
     * @param commandName String
     * @param commandTarget String
     * @param commandValue String
     * @return ProxyCommandInfo
     */
    public ProxyCommandInfo createProxyCommand(String commandName,
            String commandTarget, String commandValue) {
        return new ProxyCommandInfo(commandName, commandTarget, commandValue);
    }

    /**
     * @param proxyCommandInfo ProxyCommandInfo
     */
    public synchronized void addToWaitingResponseListener(ProxyCommandInfo proxyCommandInfo) {
        mWaitingResponseList.add(proxyCommandInfo);
    }

    /**
     * @param proxyCommandInfo ProxyCommandInfo
     */
    public synchronized void removeFromWaitingResponseListener(ProxyCommandInfo proxyCommandInfo) {
        mWaitingResponseList.remove(proxyCommandInfo);
    }

    /**
     * @param proxyCommandInfoListener IProxyCommandInfoListener
     */
    public synchronized void addToListener(IProxyCommandInfoListener proxyCommandInfoListener) {
        mListenerList.add(proxyCommandInfoListener);
    }

    /**
     * @param proxyCommandInfoListener void
     */
    public synchronized void removeFromListener(
            IProxyCommandInfoListener proxyCommandInfoListener) {
        mListenerList.remove(proxyCommandInfoListener);
    }

    /**
     * @param proxyCommandInfo ProxyCommandInfo
     */
    public synchronized void sendOutCommand(ProxyCommandInfo proxyCommandInfo) {
        String commandName = proxyCommandInfo.getCommandName();
        String commandTarget = proxyCommandInfo.getCommandTarget();
        String commandValue = proxyCommandInfo.getCommandValue();
        Utils.logi(TAG, "sendoutCommand, commandName = " + commandName
                + ", commandTarget = " + commandTarget
                + ", commandValue = " + commandValue);
        Intent intent = new Intent(Utils.PROXY_ACTION_RECEIVER);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setClassName("com.mediatek.mtklogger.proxy",
                "com.mediatek.mtklogger.proxy.ProxyReceiver");
        intent.putExtra(Utils.PROXY_EXTRA_CMD_OPERATE, commandName);
        intent.putExtra(Utils.PROXY_EXTRA_CMD_TARGET, commandTarget);
        intent.putExtra(Utils.PROXY_EXTRA_CMD_VALUE, commandValue);
        Utils.sendBroadCast(intent);
    }

    /**
     * @param commandName String
     * @param commandTarget String
     * @param commandValue String
     * @param commandResult String
     */
    public synchronized void notifyProxyCommandDone(String commandName,
            String commandTarget, String commandValue, String commandResult) {
        Utils.logd(TAG, "notifyProxyCommandDone, commandName = " + commandName
                + ", commandTarget = " + commandTarget
                + ", commandValue = " + commandValue
                + ", commandResult = " + commandResult);
        ProxyCommandInfo proxyCommandInfo = createProxyCommand(
                commandName, commandTarget, commandValue);
        proxyCommandInfo.setCommandResult(commandResult);
        proxyCommandInfo.setCommandDone(true);
        for (IProxyCommandInfoListener proxyCommandInfoListener : mListenerList) {
            proxyCommandInfoListener.notifyListener(proxyCommandInfo);
        }
        for (ProxyCommandInfo waitingResponse : mWaitingResponseList) {
            if (waitingResponse.getCommandName().equals(commandName)
                    && waitingResponse.getCommandTarget().equals(commandTarget)
                    && waitingResponse.getCommandValue().equals(commandValue)) {
                waitingResponse.setCommandResult(commandResult);
                waitingResponse.setCommandDone(true);
            }
        }
    }

}
