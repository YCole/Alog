package com.mediatek.mtklogger.proxy.communicate;

import com.mediatek.mtklogger.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author MTK81255
 *
 */
public class ProxyCommunication implements IProxyCommandInfoListener {
    private static final String TAG = Utils.TAG + "/ProxyCommunication";

    private ProxyCommandInfo mProxyCommandInfo;
    private Lock mLock = new ReentrantLock();

    // mTypeLog which need MTKLoggerPorxy communicate with native daemon.
    private String mSocketName = "";
    /**
     * default construct.
     */
    public ProxyCommunication() {
    }

    /**
     * @param socketName String
     * @throws IOException ioException
     */
    public void connect(String socketName) throws IOException {
        Utils.logi(TAG, "ProxyCommunication : connect to socket = " + socketName);
        this.mSocketName = socketName;
        String commandName = Utils.PROXY_CMD_OPERATE_CONNECT_SOCKET;
        ProxyCommandInfo proxyCommandInfo = ProxyCommandManager.getInstance()
                .createProxyCommand(commandName, socketName, socketName);
        ProxyCommandManager.getInstance().addToWaitingResponseListener(proxyCommandInfo);
        ProxyCommandManager.getInstance().sendOutCommand(proxyCommandInfo);
        long timeout = 60 * 1000;
        long sleepPeriod = 500;
        while (!proxyCommandInfo.isCommandDone()) {
            try {
                Thread.sleep(sleepPeriod);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            timeout -= sleepPeriod;
            if (timeout <= 0) {
                Utils.logw(TAG, "connect to socket " + socketName + " is timout!");
                ProxyCommandManager.getInstance().removeFromWaitingResponseListener(
                        proxyCommandInfo);
                throw new IOException("Connect socket " + socketName + " failed!");
            }
        }
        boolean isSuccessful = Utils.PROXY_RESULT_OK.equals(proxyCommandInfo.getCommandResult());
        if (!isSuccessful) {
            Utils.logw(TAG, "connect to socket " + socketName + " is failed!");
            ProxyCommandManager.getInstance().removeFromWaitingResponseListener(proxyCommandInfo);
            throw new IOException("Connect socket " + socketName + " failed!");
        }
        Utils.logi(TAG, "connect to socket " + socketName + " is OK!");
        ProxyCommandManager.getInstance().removeFromWaitingResponseListener(proxyCommandInfo);
        ProxyCommandManager.getInstance().addToListener(this);
    }

    private InputStream mInputStream;
    /**
     * @return InputStream
     */
    public InputStream getInputStream() {
        synchronized (this) {
            if (mInputStream == null) {
                mInputStream = new ProxyInputStream();
            }
            return mInputStream;
        }
    }

    private OutputStream mOutputStream;
    /**
     * @return OutputStream
     */
    public OutputStream getOutputStream() {
        synchronized (this) {
            if (mOutputStream == null) {
                mOutputStream = new ProxyOutputStream();
            }
            return mOutputStream;
        }
    }

    /**
     * @author MTK81255
     *
     */
    class ProxyInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return 0;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            synchronized (mLock) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                byte[] resultBytes = mProxyCommandInfo.getCommandResult().getBytes();
                System.arraycopy(resultBytes, 0, b, 0,
                        resultBytes.length <= len ? resultBytes.length : len);
                return resultBytes.length <= len ? resultBytes.length : len;
            }
        }
    }

    /**
     * @author MTK81255
     *
     */
    class ProxyOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            write(new byte[] {
                    (byte) (b >> 24),
                    (byte) (b >> 16),
                    (byte) (b >> 8),
                    (byte) b});
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ProxyCommandInfo proxyCommandInfo = ProxyCommandManager.getInstance()
                    .createProxyCommand(Utils.PROXY_CMD_OPERATE_SEND_COMMAND,
                    mSocketName, new String(b).trim());
            ProxyCommandManager.getInstance().sendOutCommand(proxyCommandInfo);
        }

    }

    /**
     * void.
     */
    public void close() {
        ProxyCommandManager.getInstance().removeFromListener(this);
    }

    @Override
    public void notifyListener(ProxyCommandInfo proxyCommandInfo) {
        synchronized (mLock) {
            if (proxyCommandInfo.getCommandTarget().equals(mSocketName)) {
                mProxyCommandInfo = proxyCommandInfo;
                mLock.notifyAll();
            }
        }
    }

}
