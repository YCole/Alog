package com.mediatek.mtklogger.proxy.communicate;

/**
 * @author MTK81255
 *
 */
public class ProxyCommandInfo {

    private String mCommandName = "";
    private String mCommandTarget = "";
    private String mCommandValue = "";
    private String mCommandResult = "";
    private boolean mIsCommandDone = false;

    /**
     * @param commandName String
     * @param commandTarget String
     * @param commandValue String
     */
    public ProxyCommandInfo(String commandName, String commandTarget, String commandValue) {
        mCommandName = commandName;
        mCommandTarget = commandTarget;
        mCommandValue = commandValue;
    }

    public String getCommandResult() {
        return mCommandResult;
    }

    public void setCommandResult(String commandResult) {
        this.mCommandResult = commandResult;
    }

    public boolean isCommandDone() {
        return mIsCommandDone;
    }

    public void setCommandDone(boolean isCommandDone) {
        this.mIsCommandDone = isCommandDone;
    }

    public String getCommandName() {
        return mCommandName;
    }

    public String getCommandTarget() {
        return mCommandTarget;
    }

    public String getCommandValue() {
        return mCommandValue;
    }

}
