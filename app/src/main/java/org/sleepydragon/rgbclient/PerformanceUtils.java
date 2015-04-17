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

import android.os.StrictMode;

/**
 * Useful functions to assist in ensuring good performance of the application.
 */
public class PerformanceUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private PerformanceUtils() {
    }

    /**
     * Sets the thread policy on the calling thread so that the app reports any detected
     * blocking I/O or long-running operations being performed by the thread.  In debug builds
     * of the application the app will crash if this happens; in release builds it simply logs
     * violations to logcat.
     * <p/>
     * This method should be called in onCreate() method of all activities, services, content
     * providers, and broadcast receivers.
     */
    public static void setMainThreadPolicy() {
        setThreadPolicy();
    }

    private static void setThreadPolicy() {
        final StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder();
        builder.detectAll();
        if (BuildConfig.DEBUG) {
            builder.penaltyDeath();
        } else {
            builder.penaltyLog();
        }

        final StrictMode.ThreadPolicy policy = builder.build();
        StrictMode.setThreadPolicy(policy);
    }

}
