package com.mediatek.mtklogger.framework;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Message;

import com.mediatek.mtklogger.MyApplication;
import com.mediatek.mtklogger.proxy.communicate.ProxyLocalSocket;
import com.mediatek.mtklogger.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class which will communicate with native layer. Send command to native by
 * local socket, then monitor response code, and feed that back to user
 */
public class LogConnection {
    private static final String TAG = Utils.TAG + "/LogConnection";

    private Thread mListenThread = null;
    Handler mHandler = null;
    private Message mMsg;

    LocalSocket mSocket;
    LocalSocketAddress mAddress;

    private OutputStream mOutputStream;
    private InputStream mInputStream;
    /**
     * Get at most this number of byte from socket. Since modem log may return a
     * log folder path through socket, this response maybe a long string
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * Flag for whether local monitor thread should keep running.
     */
    private boolean mShouldStop = false;

    /**
     * Add for multiple modem instance. When there are more than one connection
     * instance, we need to identify one of them from another
     */
    private int mInstanceIndex = -1;

    private static final int SEND_COMMAND_TIME_INTERVAL = 50;
    /**
     * Constructor with default socket name space.
     *
     * @param sockname
     *            String
     * @param handler
     *            Handler
     */
    public LogConnection(String sockname, Handler handler) {
        this(sockname, LocalSocketAddress.Namespace.ABSTRACT, handler);
    }

    /**
     * @param sockname
     *            String
     * @param nameSpace
     *            Namespace
     * @param handler
     *            Handler
     */
    public LogConnection(String sockname, LocalSocketAddress.Namespace nameSpace, Handler handler) {
        mHandler = handler;
        if (GPSLog.SOCKET_NAME.equals(sockname) && isRunAsSystemProcess()) {
            mSocket = new ProxyLocalSocket();
        } else {
            mSocket = new LocalSocket();
        }
        mAddress = new LocalSocketAddress(sockname, nameSpace);
    }

    private boolean isRunAsSystemProcess() {
        boolean isRunAsSystemProcess = false;
        try {
            ApplicationInfo applicationInfo = MyApplication.getInstance().getPackageManager()
                    .getApplicationInfo("com.mediatek.mtklogger", 0);
            isRunAsSystemProcess = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        Utils.logi(TAG, "is MTKLogger run as system process ? " + isRunAsSystemProcess);
        return isRunAsSystemProcess;
    }

    /**
     * @param instanceIndex
     *            int
     * @param sockname
     *            String
     * @param nameSpace
     *            Namespace
     * @param handler
     *            Handler
     */
    public LogConnection(int instanceIndex, String sockname,
            LocalSocketAddress.Namespace nameSpace, Handler handler) {
        this(sockname, nameSpace, handler);
        mInstanceIndex = instanceIndex;
    }

    /**
     * @return boolean
     */
    public boolean connect() {
        Utils.logi(TAG, "-->connect(), socketName=" + mAddress.getName());
        if (mSocket == null) {
            Utils.logw(TAG, "-->connect(), mSocket = null, just return.");
            return false;
        }
        try {
            mSocket.connect(mAddress);
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
        } catch (IOException ex) {
            Utils.logw(TAG, "Communications error,"
                    + " Exception happens when connect to socket server");
            return false;
        }

        mListenThread = new Thread() {
            public void run() {
                listen();
            }
        };
        mListenThread.start();

        Utils.logd(TAG, "Connect to native socket OK. And start local monitor thread now");
        return true;
    }

    /**
     * @return boolean
     */
    public boolean isConnected() {
        boolean isConnectedNow = (mSocket != null && mSocket.isConnected());
        // Utils.logd(TAG, "-->isConnected() ? "+isConnectedNow);
        return isConnectedNow;
    }

    /**
     * @param cmd String
     * @return boolean
     */
    public boolean sendCmd(String cmd) {
        Utils.logd(TAG, "-->send cmd: [" + cmd + "] to [" + mAddress.getName() + "]");
        boolean success = false;
        synchronized (this) {
            if (mOutputStream == null) {
                Utils.loge(TAG, "No connection to daemon, outputstream is null.");
                stop();
            } else {
                StringBuilder builder = new StringBuilder(cmd);
                builder.append('\0');
                Utils.logd(TAG, "Command builder success");
                try {
                    Thread.sleep(SEND_COMMAND_TIME_INTERVAL);
                    mOutputStream.write(builder.toString().getBytes());
                    mOutputStream.flush();
                    success = true;
                } catch (IOException ex) {
                    Utils.loge(TAG, "IOException while sending command to native.", ex);
                    mOutputStream = null;
                    stop();
                } catch (InterruptedException e) {
                    Utils.loge(TAG, "InterruptedException while sending command to native.", e);
                }
            }
        }
        Utils.logi(TAG, "<--send cmd done : [" + cmd + "] to [" + mAddress.getName() + "]");
        return success;
    }

    /**
     * void return.
     */
    public void listen() {
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];

        Utils.logd(TAG, "Monitor thread running");
        while (!mShouldStop/* true */) {
            try {
                count = mInputStream.read(buffer, 0, BUFFER_SIZE);
                if (count < 0) {
                    Utils.loge(TAG, "Get a empty response from native layer, stop listen.");
                    break;
                }
                Utils.logv(TAG, "Response from native byte size=" + count);
                byte[] resp = new byte[count];
                System.arraycopy(buffer, 0, resp, 0, count);
                dealWithResponse(resp, mHandler);
            } catch (IOException ex) {
                Utils.loge(TAG, "read failed", ex);
                break;
            }

        }
        if (!mShouldStop) {
            Utils.loge(TAG, "listen break at address: " + mAddress.getName());
            mMsg = mHandler.obtainMessage(LogInstance.MSG_DIE);
            if (mInstanceIndex > 0) {
                mMsg.arg1 = mInstanceIndex;
            }
            mHandler.sendMessage(mMsg);
        }
        return;
    }

    /**
     * return void.
     */
    public void stop() {
        Utils.logi(TAG, "-->stop()");
        mShouldStop = true;

        if (mSocket == null) {
            return;
        }

        try {
            mSocket.shutdownInput();
            mSocket.shutdownOutput();
            mSocket.close();
        } catch (IOException e) {
            Utils.loge(TAG, "Exception happended while closing socket: " + e);
        }
        mListenThread = null;
        mSocket = null;
    }

    /**
     * @param resp byte[]
     * @param handler Handler
     */
    public void dealWithResponse(byte[] resp, Handler handler) {
    }

    public LocalSocket getSocket() {
        return this.mSocket;
    }
}
