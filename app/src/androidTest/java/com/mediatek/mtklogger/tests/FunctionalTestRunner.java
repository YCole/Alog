package com.mediatek.mtklogger.tests;

import android.test.InstrumentationTestRunner;

import junit.framework.TestSuite;

/**
 * @author MTK81255
 *
 */
public class FunctionalTestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        TestSuite tests = new TestSuite();
        tests.addTestSuite(StartStopTest.class);
        tests.addTestSuite(MainActivityTest.class);
        tests.addTestSuite(ClearLogTest.class);
        tests.addTestSuite(TaglogTest.class);
        tests.addTestSuite(ModemEETest.class);

//        tests.addTestSuite(BasicOnOffFunctionTest.class);
//        tests.addTestSuite(DebugBoxTest.class);
//        tests.addTestSuite(SettingsPageTest.class);
//        tests.addTestSuite(MobileLogSettingsTest.class);
//        tests.addTestSuite(ModemLogSettingsTest.class);
//        tests.addTestSuite(NetworkLogSettingsTest.class);
//        tests.addTestSuite(ClearLogFolderTest.class);
//        tests.addTestSuite(LogFileListTest.class);
//        tests.addTestSuite(TagLogOldTest.class);
//        tests.addTestSuite(FrameworkTest.class);
//        tests.addTestSuite(MiscTest.class);
        return tests;
    }
}
