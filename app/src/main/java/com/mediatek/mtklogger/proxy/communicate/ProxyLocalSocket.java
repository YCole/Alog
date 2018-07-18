package com.mediatek.mtklogger.proxy.communicate;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.mediatek.mtklogger.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author MTK81255
 *
 */
public class ProxyLocalSocket extends LocalSocket {
    private static final String TAG = Utils.TAG + "/ProxyLocalSocket";

    private ProxyCommunication mProxyCommunication;

    @Override
    public void connect(LocalSocketAddress endpoint) throws IOException {
        try {
            super.connect(endpoint);
            mProxyCommunication = null;
        } catch (IOException ex) {
            ex.printStackTrace();
            Utils.logw(TAG, "Communications error,"
                    + " Exception happens when connect to socket server");
            connectToProxy(endpoint.getName());
        }
    }

    private void connectToProxy(String socketName) throws IOException {
        if (mProxyCommunication == null) {
            mProxyCommunication = new ProxyCommunication();
        }
        mProxyCommunication.connect(socketName);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (mProxyCommunication != null) {
            return mProxyCommunication.getInputStream();
        }
        return super.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (mProxyCommunication != null) {
            return mProxyCommunication.getOutputStream();
        }
        return super.getOutputStream();
    }

    @Override
    public void shutdownInput() throws IOException {
        if (mProxyCommunication != null) {
            mProxyCommunication.getInputStream().close();
            return;
        }
        super.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        if (mProxyCommunication != null) {
            mProxyCommunication.getOutputStream().close();
            return;
        }
        super.shutdownOutput();
    }

    @Override
    public synchronized boolean isConnected() {
        if (mProxyCommunication != null) {
            return true;
        }
        return super.isConnected();
    }

    @Override
    public void close() throws IOException {
        if (mProxyCommunication != null) {
            mProxyCommunication.close();
            return;
        }
        super.close();
    }

}
