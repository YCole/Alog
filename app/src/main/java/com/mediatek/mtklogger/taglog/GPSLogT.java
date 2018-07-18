package com.mediatek.mtklogger.taglog;

import com.mediatek.mtklogger.framework.MTKLoggerServiceManager;
import com.mediatek.mtklogger.framework.MTKLoggerServiceManager.ServiceNullException;
import com.mediatek.mtklogger.utils.Utils;

/**
 * @author MTK81255
 *
 */
public class GPSLogT extends LogInstanceForTaglog {

    /**
     * @param logType int
     * @param tagLogInformation TagLogInformation
     */
    public GPSLogT(int logType, TagLogInformation tagLogInformation) {
        super(logType, tagLogInformation);
    }

    @Override
    public boolean isNeedDoTag() {
        try {
            if (!MTKLoggerServiceManager.getInstance().getService().isTypeLogRunning(mLogType)) {
                Utils.logi(TAG, "GPS Log is not running, no need do tag.");
                Utils.logd(TAG, "isNeedDoTag ? false. mLogType = " + mLogType);
                return false;
            }
        } catch (ServiceNullException e) {
            return false;
        }
        return super.isNeedDoTag();
    }

}