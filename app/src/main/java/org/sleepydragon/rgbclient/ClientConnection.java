/*
 * Copyright 2015 Denver Coneybeare <denver@sleepydragon.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sleepydragon.rgbclient;

import android.net.Network;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.SocketFactory;

/**
 * Manages a connection with the RGB Server.
 */
public class ClientConnection implements Runnable {

    @NonNull
    private final Network mNetwork;
    @NonNull
    private final String mHostName;
    private final int mPort;

    @Nullable
    private Callback mCallback;
    private final Object mCallbackMutex = new Object();

    private final AtomicBoolean mStopRequested = new AtomicBoolean(false);

    private final Logger mLogger;

    /**
     * Creates a new instance of this class.
     *
     * @param network the network to use to connect to the server; must not be null.
     * @param hostName the host name or IP address of the server; must not be null.
     * @param port the TCP port number of the server.
     */
    public ClientConnection(@NonNull Network network, @NonNull String hostName, int port) {
        mNetwork = network;
        mHostName = hostName;
        mPort = port;
        mLogger = new Logger("ClientConnection network=" + network + " " + hostName + ":" + port);
    }

    /**
     * Returns the TCP port number of the server that was specified to the constructor.
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Returns the the network that is used to connect to the server that was specified to the
     * constructor.
     */
    @NonNull
    public Network geNetwork() {
        return mNetwork;
    }

    /**
     * Returns the host name or IP address of the server that was specified to the constructor.
     */
    @NonNull
    public String getHostName() {
        return mHostName;
    }

    /**
     * Set the Callback to be notified of interesting event relating to the server connection.
     * <p/>
     * The callbacks will occur on the same thread that invokes {@link #run} and therefore should
     * return quickly and schedule any long-running work asynchronously.
     * <p/>
     * This method may be invoked by any thread.
     *
     * @param callback the callback to register; may be null to clear the previously-registered
     * callback.
     */
    public void setCallback(@Nullable Callback callback) {
        synchronized (mCallbackMutex) {
            mCallback = callback;
        }
    }

    /**
     * Connect to the server and start sending callbacks to the registered callback.
     */
    @Override
    public void run() {
        final Logger log = mLogger.createSubLogger("run()");
        log.d("run()");

        if (isStopRequested()) {
            log.d("run() cancelled at checkpoint A");
            return;
        }

        log.d("connecting to server");
        final SocketFactory socketFactory = mNetwork.getSocketFactory();
        final Socket socket;
        try {
            socket = socketFactory.createSocket(mHostName, mPort);
        } catch (IOException e) {
            log.w("server connection failed: " + e);
            notifyConnectionError(Callback.ConnectionError.CONNECTION_ESTABLISHMENT, e.getMessage());
            return;
        }

        try {
            log.d("connecting to server");
        } finally {
            log.d("closing connection to server");
            try {
                socket.close();
            } catch (IOException e) {
                // oh well
            }
        }
    }

    private void notifyConnectionError(@NonNull Callback.ConnectionError error,
            @NonNull String message) {
        synchronized (mCallbackMutex) {
            if (mCallback != null) {
                mCallback.connectionError(error, message);
            }
        }
    }

    /**
     * Signals to run() that the server connection should be closed.
     * <p/>
     * This method may be invoked by any thread.
     *
     * @see #isStopRequested
     */
    public void requestStop() {
        mLogger.d("requestStop()");
        mStopRequested.set(true);
    }

    /**
     * Returns whether or not {@link #requestStop} has been invoked.
     * <p/>
     * This method may be invoked by any thread.
     *
     * @return true if {@link #requestStop} has been invoked or false if it has not.
     */
    public boolean isStopRequested() {
        return mStopRequested.get();
    }

    /**
     * Implement this class to receive information about the server connection.
     */
    public static interface Callback {

        public enum ConnectionError {
            /**
             * Connection error reported when establishing the initial connection fails.
             */
            CONNECTION_ESTABLISHMENT,
        }

        /**
         * Called when the state of the connection with the server changes.
         *
         * @param connected true if a connection was established
         * or false if the connection was closed.
         */
        public void connectionStateChanged(boolean connected);

        /**
         * Called when an error occurred in the connection with the server.
         *
         * @param error the type of error that occurred.
         * @param message a message describing the error; will never be null.
         */
        public void connectionError(@NonNull ConnectionError error, @NonNull String message);

        /**
         * Called when a command is received from the server.
         *
         * @param command the command that was received; will never be null.
         */
        public void commandReceived(@NonNull Command command);

    }

    /**
     * Stores information about a command received from the server.
     */
    public static class Command {

        public enum Type {
            ABSOLUTE,
            RELATIVE,
        }

        @NonNull
        public final Type type;
        public final int r;
        public final int g;
        public final int b;

        public Command(@NonNull Type type, int r, int g, int b) {
            this.type = type;
            this.r = r;
            this.g = g;
            this.b = b;
        }

    }

}
