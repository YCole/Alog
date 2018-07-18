package com.mediatek.mtklogger.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.ActivityInstrumentationTestCase2;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.mtklogger.MainActivity;
import com.mediatek.mtklogger.framework.LogReceiver;
import com.mediatek.mtklogger.utils.Utils;
import com.mtk.at.BasicFuncTest;

/**
 * @author MTK81255
 *
 */
public class StartStopTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private static final String TAG = TestUtils.TAG + "/StartStopTest";
    private Solo mSolo;

    private Instrumentation mIns;

    private Activity mActivity;

    private Context mContext;

    /**
     * return void.
     */
    public StartStopTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Utils.logd(TAG, "-->setUp()");
        mIns = getInstrumentation();
        mActivity = getActivity();
        mContext = mIns.getTargetContext();
        mSolo = new Solo(mIns, mActivity);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    /**
     * return void.
     */
    @BasicFuncTest
    public void test01StartStopMobileLog() {
        Utils.logd(TAG, "-->test01StartStopMobileLog()");
        if (TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MOBILE)) {
            boolean stopMobileLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MOBILE, false);
            assertTrue(" Fail to stop mobile log", stopMobileLog);
            if (stopMobileLog) {
                boolean startMobileLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MOBILE, true);
                assertTrue(" Fail to start mobile log", startMobileLog);
            }
        } else {
            boolean startMobileLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MOBILE, true);
            assertTrue(" Fail to start mobile log", startMobileLog);
            if (startMobileLog) {
                boolean stopMobileLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MOBILE, false);
                assertTrue(" Fail to stop mobile log", stopMobileLog);
            }
        }
        Utils.logd(TAG, "<--test01StartStopMobileLog() pass!");
    }

    /**
     * @throws InterruptedException InterruptedException
     */
    @BasicFuncTest
    public void test02StartStopModemLog() throws InterruptedException {
        Utils.logd(TAG, "-->test02StartStopModemLog()");
        if (TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM)) {
            boolean stopModemLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
            assertTrue(" Fail to stop modem log", stopModemLog);
            if (stopModemLog) {
                TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_SD);
                boolean startModemLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, true);
                assertTrue(" Fail to start modem1 log in SD mode", startModemLog);

                if (!Utils.isDenaliMd3Solution()) {
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
                    TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_PLS);
                    startModemLog =
                            TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, true);
                    assertTrue(" Fail to start modem1 log in PLS mode", startModemLog);
                }

//                TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
//                TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_USB);
//                startModemLog =
//                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, true);
//                assertTrue(" Fail to start modem1 log in USB mode", startModemLog);
            }
        } else {
            TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_SD);
            boolean startModemLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, true);
            assertTrue(" Fail to start modem1 log in SD mode", startModemLog);
            if (startModemLog) {
                boolean stopModemLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
                assertTrue(" Fail to stop modem log", stopModemLog);
            }

            if (Utils.isDenaliMd3Solution()) {
                TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
                TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_PLS);
                startModemLog = TestUtils.changeLogRunningStatus(
                        mContext, Utils.LOG_TYPE_MODEM, true);
                assertTrue(" Fail to start modem1 log in PLS mode", startModemLog);
            }

//            TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_USB);
//            startModemLog = TestUtils.changeLogRunningStatus(mContext,
//                  Utils.LOG_TYPE_MODEM, true);
//            assertTrue(" Fail to start modem1 log in USB mode", startModemLog);
        }
        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, false);
        TestUtils.switchAllModemMode(mContext, Utils.MODEM_MODE_SD);
        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_MODEM, true);
        Utils.logd(TAG, "<--test02StartStopModemLog() pass");
    }

    /**
     * return void.
     */
    @BasicFuncTest
    public void test03StartStopNetworkLog() {
        Utils.logd(TAG, "-->test03StartStopNetworkLog()");
        if (TestUtils.isTypeLogRunning(Utils.LOG_TYPE_NETWORK)) {
            boolean stopNetworkLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK, false);
            assertTrue(" Fail to stop network log", stopNetworkLog);
            if (stopNetworkLog) {
                boolean startNetworkLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK, true);
                int i = 0;
                while (!startNetworkLog && i < 5) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    startNetworkLog =
                            TestUtils
                                    .changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK, true);
                    i++;
                }
                assertTrue(" Fail to start network log", startNetworkLog);
            }
        } else {
            boolean startNetworkLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK, true);
            assertTrue(" Fail to start network log", startNetworkLog);
            if (startNetworkLog) {
                boolean stopNetworkLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK, false);
                int i = 0;
                while (!stopNetworkLog && i < 5) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    stopNetworkLog =
                            TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_NETWORK,
                                    false);
                    i++;
                }
                assertTrue(" Fail to stop network log", stopNetworkLog);
            }
        }
        Utils.logd(TAG, "<--test03StartStopNetworkLog() pass!");
    }

    /**
     * return void.
     */
    @BasicFuncTest
    public void test04StartStopGPSLog() {
        Utils.logd(TAG, "-->test04StartStopGPSLog()");
        if (!TestUtils.isNeedCheckGPSLogStatus()) {
            Utils.logd(TAG, "<--test04StartStopGPSLog() pass!");
            return;
        }
        if (TestUtils.isTypeLogRunning(Utils.LOG_TYPE_GPS)) {
            boolean stopGPSLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_GPS, false);
            assertTrue(" Fail to stop GPS log", stopGPSLog);
            if (stopGPSLog) {
                boolean startGPSLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_GPS, true);
                assertTrue(" Fail to start GPS log", startGPSLog);
            }
        } else {
            boolean startGPSLog =
                    TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_GPS, true);
            assertTrue(" Fail to start GPS log", startGPSLog);
            if (startGPSLog) {
                boolean stopGPSLog =
                        TestUtils.changeLogRunningStatus(mContext, Utils.LOG_TYPE_GPS, false);
                assertTrue(" Fail to stop GPS log", stopGPSLog);
            }
        }
        Utils.logd(TAG, "<--test04StartStopGPSLog() pass!");
    }

    /**
     * return void.
     */
    public void test05LogAutoStart() {
        Utils.logd(TAG, "-->test05LogAutoStart()");
        // 7 = mobileLog + modemLog + networkLog
        TestUtils.setLogAutoStart(mContext, 23, true);
        TestUtils.changeLogRunningStatus(mContext, 23, true);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(" Mobile log auto start failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MOBILE));
        assertTrue(" Modem log auto start failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM));
        assertTrue(" Network log auto start failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_NETWORK));
        if (TestUtils.isNeedCheckGPSLogStatus()) {
            assertTrue(" GPS log auto start failed",
                    TestUtils.isTypeLogRunning(Utils.LOG_TYPE_GPS));
        }
        Utils.logd(TAG, "<--test05LogAutoStart() pass!");
    }

    /**
     * return void.
     */
    public void test06LogAutoStop() {
        Utils.logd(TAG, "-->test06LogAutoStop()");
        // 7 = mobileLog + modemLog + networkLog
        TestUtils.setLogAutoStart(mContext, 23, false);
        TestUtils.changeLogRunningStatus(mContext, 23, false);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertFalse(" Mobile log auto stop failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MOBILE));
        assertFalse(" Modem log auto stop failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_MODEM));
        assertFalse(" Network log auto stop failed",
                TestUtils.isTypeLogRunning(Utils.LOG_TYPE_NETWORK));
        if (TestUtils.isNeedCheckGPSLogStatus()) {
            assertFalse(" GPS log auto stop failed",
                    TestUtils.isTypeLogRunning(Utils.LOG_TYPE_GPS));
        }
        Utils.logd(TAG, "<--test06LogAutoStop() pass!");
    }
}
