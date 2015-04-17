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
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.support.annotation.NonNull;

/**
 * Helper methods for saving and loading application settings.
 */
public class Settings {

    /**
     * The name of the SharedPreferences where these settings are stored by default.
     *
     * @see Context#getSharedPreferences
     */
    public static final String SHARED_PREFS_NAME = "settings";

    /**
     * The key of the SharedPreferences whose value is the host name or IP address of the server.
     * The value of this key is a string.
     *
     * @see SharedPreferences#getString
     */
    public static final String KEY_SERVER_HOST = "server_host";

    /**
     * The key of the SharedPreferences whose value is the TCP port of the server.
     * The value of this key is an integer.
     *
     * @see SharedPreferences#getInt
     */
    public static final String KEY_SERVER_PORT = "server_port";

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

}
