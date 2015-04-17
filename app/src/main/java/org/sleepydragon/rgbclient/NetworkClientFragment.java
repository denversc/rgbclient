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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

/**
 * A non-UI fragment that manages the network connection with the server.
 */
public class NetworkClientFragment extends Fragment {

    private static final Logger LOG = new Logger("NetworkClientFragment");
    private static final String ACTION_RESTART_CLIENT =
            "org.sleepydragon.rgbclient.NetworkClientFragment.RESTART_CLIENT";

    /**
     * A suggested tag to use for this fragment with the FragmentManager.
     */
    public static final String TAG = "NetworkClient";

    private final BroadcastReceiver mRestartBroadcastReceiver = new RestartBroadcastReceiver();

    private LoadSettingsAsyncTask mLoadSettingsAsyncTask;
    private SharedPreferences mSharedPreferences;
    private LocalBroadcastManager mLocalBroadcastManager;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        super.onCreate(savedInstanceState);

        // ensure that this fragment doesn't get destroyed due to configuration changes, as that
        // would cause the network connection to have to be re-established every time
        setRetainInstance(true);

        mHandler = new Handler(new MainHandlerCallback());

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        final IntentFilter restartReceiverFilter = new IntentFilter();
        restartReceiverFilter.addAction(ACTION_RESTART_CLIENT);
        mLocalBroadcastManager.registerReceiver(mRestartBroadcastReceiver, restartReceiverFilter);

        mLoadSettingsAsyncTask = new LoadSettingsAsyncTask(getActivity());
        mLoadSettingsAsyncTask.execute();
    }

    @Override
    public void onDestroy() {
        LOG.v("onDestroy()");
        super.onDestroy();

        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.removeMessages(R.id.MSG_STOP_CLIENT);
        mLocalBroadcastManager.unregisterReceiver(mRestartBroadcastReceiver);
        mLoadSettingsAsyncTask.cancel(false);
    }

    /**
     * Creates and returns an Intent that, when posted to {@link LocalBroadcastManager} will cause
     * this fragment to restart the server.  This is typically done when the host and/or port of
     * the server is changed in SharedPreferences.
     * @return the Intent; never returns null.
     */
    @NonNull
    public static Intent getRestartClientIntent() {
        final Intent intent = new Intent();
        intent.setAction(ACTION_RESTART_CLIENT);
        return intent;
    }

    private void startClient() {
        LOG.d("startClient()");
    }

    private void stopClient() {
        LOG.d("startClient()");
    }

    private void scheduleRestartClient() {
        mHandler.removeMessages(R.id.MSG_STOP_CLIENT);
        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.sendEmptyMessage(R.id.MSG_STOP_CLIENT);
        mHandler.sendEmptyMessage(R.id.MSG_START_CLIENT);
    }

    private void scheduleStartClient() {
        mHandler.removeMessages(R.id.MSG_START_CLIENT);
        mHandler.sendEmptyMessage(R.id.MSG_START_CLIENT);
    }

    private class LoadSettingsAsyncTask extends AsyncTask<Void, Void, SharedPreferences> {

        @NonNull
        private final Context mContext;

        public LoadSettingsAsyncTask(@NonNull Context context) {
            mContext = context;
        }

        @Override
        protected SharedPreferences doInBackground(Void... params) {
            return Settings.getSharedPreferences(mContext);
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
            if (ACTION_RESTART_CLIENT.equals(action)) {
                scheduleRestartClient();
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

}
