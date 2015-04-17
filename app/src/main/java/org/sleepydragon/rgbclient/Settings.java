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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Helper methods for saving and loading application settings.
 */
public class Settings {

    public static final String ACTION_SERVER_INFO_CHANGED =
            "org.sleepydragon.rgbclient.Settings.ACTION_SERVER_INFO_CHANGED";

    /**
     * The name of the SharedPreferences where these settings are stored by default.
     *
     * @see Context#getSharedPreferences
     */
    public static final String SHARED_PREFS_NAME = "settings";

    /**
     * Private constructor to prevent instantiation.
     */
    private Settings() {
    }

    /**
     * Opens the SharedPreferences in which the settings outlined in this class are stored.
     *
     * @param context the Context object to use to open the SharedPreferences.
     * @return the SharedPreferences; never returns null.
     */
    @NonNull
    public static SharedPreferences getSharedPreferences(@NonNull Context context) {
        StrictMode.noteSlowCall("opening SharedPreferences potentially performs blocking I/O");
        final Context appContext = context.getApplicationContext();
        return appContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Retrieves and returns the key in SharedPreferences where the server's host name or IP address
     * is stored as a string.
     *
     * @param context the Context to use to retrieve the key's value; must not be null.
     * @return the SharedPreferences key; never returns null.
     */
    @NonNull
    public static String getServerHostKey(@NonNull Context context) {
        return context.getString(R.string.pref_key_server_host);
    }

    /**
     * Retrieves and returns the key in SharedPreferences where the server's TCP port number
     * is stored as an int.
     *
     * @param context the Context to use to retrieve the key's value; must not be null.
     * @return the SharedPreferences key; never returns null.
     */
    @NonNull
    public static String getServerPortKey(@NonNull Context context) {
        return context.getString(R.string.pref_key_server_port);
    }

    /**
     * Notifies other parties in this application that the server settings have been changed.
     * This method should be invoked whenever {@link #KEY_SERVER_HOST} or {@link #KEY_SERVER_PORT}
     * are changed so that the application can respond accordingly.
     * <p/>
     * This method will post an intent with action {@link #ACTION_SERVER_INFO_CHANGED} to
     * {@link android.support.v4.content.LocalBroadcastManager}.
     *
     * @param context the Context to use to get the LocalBroadcastManager; must not be null.
     */
    @NonNull
    public static void notifyServerInfoChanged(@NonNull Context context) {
        final Intent intent = new Intent();
        intent.setAction(ACTION_SERVER_INFO_CHANGED);
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        broadcastManager.sendBroadcast(intent);
    }

    /**
     * An AsyncTask that can be used to load the Settings SharedPreferences in a worker thread.
     */
    public static class GetSharedPreferencesAsyncTask
            extends AsyncTask<Void, Void, SharedPreferences> {

        @NonNull
        protected final Context mContext;

        /**
         * Creates a new instance of this class.
         * @param context the Context object to specify to {@link #getSharedPreferences};
         * must not be null.
         */
        public GetSharedPreferencesAsyncTask(@NonNull Context context) {
            mContext = context;
        }

        @Override
        protected SharedPreferences doInBackground(Void... params) {
            final SharedPreferences prefs = getSharedPreferences(mContext);
            // load all values to avoid future disk I/O
            prefs.getAll();
            return prefs;
        }

    }


}
