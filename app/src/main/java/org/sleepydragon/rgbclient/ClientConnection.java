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

import android.support.annotation.NonNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a connection with the RGB Server.
 */
public class ClientConnection implements Runnable {

    @NonNull
    private final String mHost;
    private final int mPort;

    @NonNull
    private final Callback mCallback;

    private final AtomicBoolean mStopRequested = new AtomicBoolean(false);
    private final AtomicBoolean mConnected = new AtomicBoolean(false);

    private final Logger mLogger;

    /**
     * Creates a new instance of this class.
     *
     * @param host the host name or IP address of the server; must not be null.
     * @param port the TCP port number of the server.
     * @param callback the callback to be notified of interesting events; must not be null;
     * the callbacks will occur on the same thread that invokes {@link #run} and therefore should
     * return quickly and schedule any long-running work asynchronously.
     */
    public ClientConnection(@NonNull String host, int port, @NonNull Callback callback) {
        mHost = host;
        mPort = port;
        mCallback = callback;
        mLogger = new Logger("ClientConnection " + host + ":" + port);
    }

    /**
     * Returns the TCP port number of the server that was specified to the constructor.
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Returns the host name or IP address of the server that was specified to the constructor.
     */
    @NonNull
    public String getHost() {
        return mHost;
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
        final Socket socket;
        try {
            socket = new Socket(mHost, mPort);
        } catch (IOException e) {
            log.w("server connection failed: " + e);
            mCallback.connectionError(this, Callback.ConnectionError.CONNECTION_ESTABLISHMENT,
                    e.getMessage());
            return;
        }

        try {
            log.d("connected to server");
            if (isStopRequested()) {
                log.d("run() cancelled at checkpoint B");
                return;
            }

            final InputStream inputStream = socket.getInputStream();
            final DataInputStream in = new DataInputStream(inputStream);
            mConnected.set(true);
            mCallback.connectionStateChanged(this, true);

            boolean resetCommandSent = false;
            while (true) {
                if (isStopRequested()) {
                    log.d("run() cancelled at checkpoint C");
                    return;
                }

                final byte instructionCode = in.readByte();
                final ColorCommand.Instruction instruction;
                switch (instructionCode) {
                    case 1:
                        instruction = ColorCommand.Instruction.RELATIVE;
                        break;
                    case 2:
                        instruction = ColorCommand.Instruction.ABSOLUTE;
                        break;
                    default:
                        throw new ProtocolException("invalid instruction: " + instructionCode);
                }

                final int r, g, b;
                switch (instruction) {
                    case RELATIVE:
                        r = in.readShort();
                        g = in.readShort();
                        b = in.readShort();
                        break;
                    case ABSOLUTE:
                        r = in.readUnsignedByte();
                        g = in.readUnsignedByte();
                        b = in.readUnsignedByte();
                        break;
                    default:
                        throw new RuntimeException("should never get here");
                }

                log.d("data received from server: instruction=" + instruction
                        + " (" + r + ", " + g + ", " + b + ")");

                if (!resetCommandSent) {
                    log.d("sending synthetic reset command");
                    final ColorCommand resetCommand =
                            new ColorCommand(ColorCommand.Instruction.ABSOLUTE, 127, 127, 127);
                    mCallback.commandReceived(this, resetCommand);
                    resetCommandSent =true;
                }

                final ColorCommand command = new ColorCommand(instruction, r, g, b);
                mCallback.commandReceived(this, command);
            }
        } catch (IOException e) {
            log.w("error reading from server: " + e);
            mCallback.connectionError(this, Callback.ConnectionError.READ, e.getMessage());
        } catch (ProtocolException e) {
            log.w("protocol error reading from server: " + e.getMessage());
            mCallback.connectionError(this, Callback.ConnectionError.PROTOCOL, e.getMessage());
        } finally {
            log.d("closing connection to server");
            mConnected.set(false);
            try {
                socket.close();
            } catch (IOException e) {
                // oh well
            } finally {
                mCallback.connectionStateChanged(this, false);
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
     * Returns whether or not the connection to the server has been successfully established.
     * @return true if the connection to the server has been successfully established or false if
     * it has not.
     */
    public boolean isConnected() {
        return mConnected.get();
    }

    /**
     * Implement this class to receive information about the server connection.
     */
    public interface Callback {

        enum ConnectionError {
            /**
             * Connection error reported when establishing the initial connection fails.
             */
            CONNECTION_ESTABLISHMENT,

            /**
             * An I/O error occurred while reading from the socket.
             */
            READ,

            /**
             * The data that was received from the server does not conform to the protocol.
             */
            PROTOCOL,
        }

        /**
         * Called when the state of the connection with the server changes.
         *
         * @param connection the connection from which this event originated; will never be null.
         * @param connected true if a connection was established
         * or false if the connection was closed.
         */
        void connectionStateChanged(@NonNull ClientConnection connection, boolean connected);

        /**
         * Called when an error occurred in the connection with the server.
         *
         * @param connection the connection from which this event originated; will never be null.
         * @param error the type of error that occurred.
         * @param message a message describing the error; will never be null.
         */
        void connectionError(@NonNull ClientConnection connection,
                @NonNull ConnectionError error, @NonNull String message);

        /**
         * Called when a command is received from the server.
         *
         * @param connection the connection from which this event originated; will never be null.
         * @param command the command that was received; will never be null.
         */
        void commandReceived(@NonNull ClientConnection connection, @NonNull ColorCommand command);

    }

    /**
     * Exception thrown if the protocol received from the server is non-conformant.
     */
    private static class ProtocolException extends Exception {
        public ProtocolException(String message) {
            super(message);
        }
    }

}
