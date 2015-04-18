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

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * A non-UI fragment that manages the network connection with the server.
 */
public class NetworkClientFragment extends Fragment {

    private static final Logger LOG = new Logger("NetworkClientFragment");

    /**
     * A suggested tag to use for this fragment with the FragmentManager.
     */
    public static final String TAG = "NetworkClient";

    private final BroadcastReceiver mRestartBroadcastReceiver = new RestartBroadcastReceiver();
    private final ConnectivityManager.NetworkCallback mNetworkConnectionListener =
            new NetworkConnectionListener();
    private final ClientConnection.Callback mClientConnectionCallback =
            new ClientConnectionCallback();

    private static final int MAX_COMMAND_HISTORY_SIZE = 1000;
    private final Queue<ColorCommand> mCommands = new ArrayDeque<>();

    private LoadSettingsAsyncTask mLoadSettingsAsyncTask;
    private SharedPreferences mSharedPreferences;
    private LocalBroadcastManager mLocalBroadcastManager;
    private Handler mHandler;
    private ConnectivityManager mConnectivityManager;
    private TargetFragmentCallbacks mTargetFragmentCallbacks;

    private final Object mClientConnectionThreadMutex = new Object();
    private volatile ClientConnectionThread mClientConnectionThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        super.onCreate(savedInstanceState);

        // ensure that this fragment doesn't get destroyed due to configuration changes, as that
        // would cause the network connection to have to be re-established every time
        setRetainInstance(true);

        mHandler = new Handler(new MainHandlerCallback());

        mConnectivityManager =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        final NetworkRequest networkRequest = networkRequestBuilder.build();
        mConnectivityManager.registerNetworkCallback(networkRequest, mNetworkConnectionListener);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        final IntentFilter restartReceiverFilter = new IntentFilter();
        restartReceiverFilter.addAction(Settings.ACTION_SERVER_INFO_CHANGED);
        mLocalBroadcastManager.registerReceiver(mRestartBroadcastReceiver, restartReceiverFilter);

        mLoadSettingsAsyncTask = new LoadSettingsAsyncTask(getActivity());
        mLoadSettingsAsyncTask.execute();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LOG.v("onActivityCreated()");
        super.onActivityCreated(savedInstanceState);
        mTargetFragmentCallbacks = (TargetFragmentCallbacks) getTargetFragment();
        scheduleStartClient(); // in case the LoadSettingsAsyncTask already finished
    }

    @Override
    public void onDestroy() {
        LOG.v("onDestroy()");
        super.onDestroy();

        stopClient();
        mLoadSettingsAsyncTask.cancel(false);
        mLocalBroadcastManager.unregisterReceiver(mRestartBroadcastReceiver);
        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.removeMessages(R.id.MSG_STOP_CLIENT);
        mConnectivityManager.unregisterNetworkCallback(mNetworkConnectionListener);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mTargetFragmentCallbacks = null;
    }

    private void startClient() {
        LOG.d("startClient()");

        final Context context = getActivity();
        if (context == null) {
            LOG.w("startClient(): getActivity() returned null; aborting");
            return;
        }

        final SharedPreferences prefs = mSharedPreferences;
        if (prefs == null) {
            LOG.w("startClient(): mSharedPreferences==null; aborting");
            return;
        }

        final String hostKey = Settings.getServerHostKey(context);
        final String host = prefs.getString(hostKey, null);
        if (host == null) {
            LOG.w("startClient(): server host name not set in SharedPreferences; aborting");
            if (mTargetFragmentCallbacks != null) {
                mTargetFragmentCallbacks.showSetServerDialog();
            }
            return;
        }

        final String portKey = Settings.getServerPortKey(context);
        final int port = prefs.getInt(portKey, -1);
        if (port == -1) {
            if (mTargetFragmentCallbacks != null) {
                mTargetFragmentCallbacks.showSetServerDialog();
            }
            LOG.w("startClient(): server port not set in SharedPreferences; aborting");
            return;
        }

        final NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            LOG.w("startClient(): no network connection available; aborting");
            return;
        } else if (!networkInfo.isConnected()) {
            LOG.w("startClient(): the default network is not currently connected; aborting");
            return;
        }

        synchronized (mClientConnectionThreadMutex) {
            if (mClientConnectionThread != null) {
                final ClientConnection connection = mClientConnectionThread.getConnection();
                if (!connection.isStopRequested() && host.equals(connection.getHost())
                        && port == connection.getPort()) {
                    if (connection.isConnected()) {
                        LOG.w("startClient(): already connected to the server; aborting");
                    } else {
                        LOG.w("startClient(): establishing connection to the server; "
                                + "will check back again shortly to make sure it succeeded");
                        mHandler.removeMessages(R.id.MSG_START_CLIENT);
                        mHandler.sendEmptyMessageDelayed(R.id.MSG_START_CLIENT, 1000);
                    }
                    return;
                }
                connection.requestStop();
                mClientConnectionThread.interrupt();
                mClientConnectionThread = null;
            }

            final ClientConnection connection = new ClientConnection(host, port,
                    mClientConnectionCallback);
            final ClientConnectionThread thread = new ClientConnectionThread(connection);
            thread.start();

            mClientConnectionThread = thread;
        }

        LOG.d("startClient(): started connection with server " + host + ":" + port);
    }

    private void stopClient() {
        LOG.d("stopClient()");

        final ClientConnectionThread thread;
        synchronized (mClientConnectionThreadMutex) {
            thread = mClientConnectionThread;
            mClientConnectionThread = null;
        }
        if (thread != null) {
            final ClientConnection connection = thread.getConnection();
            connection.requestStop();
            thread.interrupt();
        }
    }

    private void scheduleStartClient() {
        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.removeMessages(R.id.MSG_STOP_CLIENT);
        mHandler.sendEmptyMessage(R.id.MSG_START_CLIENT);
    }

    public void restart() {
        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.removeMessages(R.id.MSG_STOP_CLIENT);
        stopClient();
        startClient();
    }

    @NonNull
    public List<ColorCommand> getCommandsSince(@Nullable UUID id) {
        final List<ColorCommand> result = new ArrayList<>();

        boolean idFound = false;
        synchronized (mCommands) {
            if (id != null) {
                for (final ColorCommand command : mCommands) {
                    if (idFound) {
                        result.add(command);
                    } else if (command.id.equals(id)) {
                        idFound = true;
                    }
                }
            }

            // if the given ID was not found, then it must have fallen off the end; so treat all
            // command as being new
            if (! idFound) {
                // sanity check
                if (result.size() > 0) {
                    throw new AssertionError("result.size()==" + result.size());
                }
                result.addAll(mCommands);
            }
        }

        return result;
    }

    private class LoadSettingsAsyncTask extends Settings.GetSharedPreferencesAsyncTask {

        public LoadSettingsAsyncTask(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onPostExecute(SharedPreferences sharedPreferences) {
            LOG.d("LoadSettingsAsyncTask.onPostExecute() SharedPreferences loaded");
            if (isCancelled()) {
                return;
            }
            mSharedPreferences = sharedPreferences;
            scheduleStartClient();
        }

    }

    /**
     * The broadcast receiver that is registered with LocalBroadcastManager to restart the server
     * when the settings change.
     */
    private class RestartBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            LOG.d("RestartBroadcastReceiver.onReceive() intent=" + intent);
            if (intent == null) {
                return;
            }
            final String action = intent.getAction();
            if (Settings.ACTION_SERVER_INFO_CHANGED.equals(action)) {
                scheduleStartClient();
            }
        }

    }

    private class MainHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case R.id.MSG_START_CLIENT:
                    startClient();
                    return true;
                case R.id.MSG_STOP_CLIENT:
                    stopClient();
                    return true;
                default:
                    return false;
            }
        }

    }

    private class NetworkConnectionListener extends ConnectivityManager.NetworkCallback {

        @Override
        public void onAvailable(Network network) {
            LOG.d("NetworkConnectionListener.onAvailable() network=" + network);
            scheduleStartClient();
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            LOG.d("NetworkConnectionListener.onLosing() network=" + network
                    + " maxMsToLive=" + maxMsToLive);
        }

        @Override
        public void onLost(Network network) {
            LOG.d("NetworkConnectionListener.onLost() network=" + network);
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            LOG.d("NetworkConnectionListener.onCapabilitiesChanged() network=" + network
                    + " networkCapabilities=" + networkCapabilities);
            scheduleStartClient();
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            LOG.d("NetworkConnectionListener.onLinkPropertiesChanged() network=" + network
                    + " linkProperties=" + linkProperties);
            scheduleStartClient();
        }

    }

    private static class ClientConnectionThread extends Thread {

        @NonNull
        private final ClientConnection mConnection;

        public ClientConnectionThread(@NonNull ClientConnection connection) {
            super(connection);
            mConnection = connection;
        }

        @NonNull
        public ClientConnection getConnection() {
            return mConnection;
        }

    }

    /**
     * An interface to be implemented by the target fragment of this fragment to allow this fragment
     * to make demands on it.
     */
    public interface TargetFragmentCallbacks {

        /**
         * Show the dialog that allows the user to enter the server information.
         */
        void showSetServerDialog();

        /**
         * Process a command received from the server.
         * @param command the command that was received; will never be null.
         */
        void onCommandReceived(@NonNull ColorCommand command);

    }

    private class ClientConnectionCallback implements ClientConnection.Callback {

        @Override
        public void connectionStateChanged(@NonNull ClientConnection connection,
                boolean connected) {
            LOG.d("ClientConnectionCallback: connectionStateChanged() connected=" + connected);
            if (!connected) {
                clearConnection(connection);
            }
        }

        @Override
        public void connectionError(@NonNull ClientConnection connection,
                @NonNull ConnectionError error, @NonNull String message) {
            LOG.w("ClientConnectionCallback: connectionError() error=" + error
                    + " message=" + message);
            clearConnection(connection);
        }

        @Override
        public void commandReceived(@NonNull ClientConnection connection,
                @NonNull ColorCommand command) {
            if (isActiveConnection(connection)) {
                LOG.d("ClientConnectionCallback: commandReceived() command=" + command);
                final TargetFragmentCallbacks cb = mTargetFragmentCallbacks;
                if (cb != null) {
                    cb.onCommandReceived(command);
                }
                synchronized (mCommands) {
                    mCommands.offer(command);
                    if (mCommands.size() > MAX_COMMAND_HISTORY_SIZE) {
                        mCommands.poll();
                    }
                }
            }
        }

        private boolean isActiveConnection(@NonNull ClientConnection connection) {
            synchronized (mClientConnectionThreadMutex) {
                return (mClientConnectionThread != null
                        && mClientConnectionThread.getConnection() == connection);
            }
        }

        private void clearConnection(@NonNull ClientConnection connection) {
            connection.requestStop();
            synchronized (mClientConnectionThreadMutex) {
                if (mClientConnectionThread != null
                        && mClientConnectionThread.getConnection() == connection) {
                    mClientConnectionThread = null;
                }
            }
        }

    }

}
