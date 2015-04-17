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
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Provides methods to conveniently log messages to logcat with an application-specific log tag.
 * <p/>
 * This class is intended to have one static instance per class that uses it.  Each class sets its
 * own "sub-tag" that will be automatically prepended to all messages that it logs.
 */
public class Logger {

    /**
     * The log tag that will be used in all logcat messages emitted by this class.
     */
    public static final String LOG_TAG = "RgbClient";

    private final String mSubTag;

    /**
     * Creates a new instance of this class.
     *
     * @param subTag the log "sub-tag" to prepend to each log messages emitted by this object;
     * must not be null.
     */
    public Logger(@NonNull String subTag) {
        this(null, subTag);
    }

    /**
     * Creates a new instance of this class.
     *
     * @param parentLogger the Logger whose child to create; may be null if this logger has no
     * parent.
     * @param subTag the log "sub-tag" to prepend to each log messages emitted by this object;
     * must not be null.
     */
    public Logger(@Nullable Logger parentLogger, @NonNull String subTag) {
        mSubTag = (parentLogger == null) ? subTag : (parentLogger.mSubTag + ": " + subTag);
    }

    /**
     * Logs an "information" level message.
     *
     * @param message the message to log; should not be null.
     */
    public void i(@NonNull String message) {
        Log.i(LOG_TAG, createLogMessage(message));
    }

    /**
     * Logs a "debug" level message.
     * Note that "debug" level messages are suppressed on release builds of this application.
     *
     * @param message the message to log; should not be null.
     */
    public void d(@NonNull String message) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, createLogMessage(message));
        }
    }

    /**
     * Logs a "verbose" level message.
     * Note that "verbose" level messages are suppressed on release builds of this application.
     *
     * @param message the message to log; should not be null.
     */
    public void v(@NonNull String message) {
        if (BuildConfig.DEBUG) {
            Log.v(LOG_TAG, createLogMessage(message));
        }
    }

    /**
     * Logs a "warning" level message.
     *
     * @param message the message to log; should not be null.
     */
    public void w(@NonNull String message) {
        Log.w(LOG_TAG, createLogMessage(message));
    }

    /**
     * Creates a new Logger with this logger as the parent.
     * Invoking this method is exactly the same as using the {@link #Logger(Logger, String)}
     * constructor except that using this method may be more readable in code.
     *
     * @param subTag the log "sub-tag" to prepend to each log messages emitted by this object;
     * must not be null.
     * @return the newly-created Logger; never returns null.
     */
    @NonNull
    public Logger createSubLogger(@NonNull String subTag) {
        return new Logger(this, subTag);
    }

    private String createLogMessage(@NonNull String message) {
        return mSubTag + ": " + message;
    }

}
