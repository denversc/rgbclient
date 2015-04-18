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
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.CircularArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The main fragment for the main activity.
 */
public class MainFragment extends Fragment
        implements NetworkClientFragment.TargetFragmentCallbacks {

    private static final Logger LOG = new Logger("MainFragment");
    private static final String KEY_DISPLAYED_COLOR = "displayed_color";
    private static final String KEY_UNPROCESSED_COMMANDS = "unprocessed_commands";

    private final CircularArray<ColorCommand> mUnprocessedCommandQueue = new CircularArray<>();

    private Handler mHandler;
    private NetworkClientFragment mNetworkClientFragment;

    private DisplayedColor mDisplayedColor;
    private View mColorFillView;
    private TextView mColorTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        super.onCreate(savedInstanceState);
        mHandler = new Handler(new MainHandlerCallback());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LOG.v("onActivityCreated()");
        super.onActivityCreated(savedInstanceState);

        final FragmentManager fm = getFragmentManager();
        mNetworkClientFragment = (NetworkClientFragment)
                fm.findFragmentByTag(NetworkClientFragment.TAG);
        if (mNetworkClientFragment == null) {
            mNetworkClientFragment = new NetworkClientFragment();
            mNetworkClientFragment.setTargetFragment(this, 0);
            fm.beginTransaction().add(mNetworkClientFragment, NetworkClientFragment.TAG).commit();
        }

        restoreCommandsState(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        LOG.v("onDestroy()");
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        LOG.v("onCreateView() savedInstanceState=" + savedInstanceState);
        final View root = inflater.inflate(R.layout.fragment_main, container, false);
        mColorFillView = root.findViewById(R.id.color_fill);
        mColorTextView = (TextView) root.findViewById(R.id.color_text);
        return root;
    }

    private void restoreCommandsState(@Nullable Bundle state) {
        if (state != null && state.containsKey(KEY_DISPLAYED_COLOR)) {
            mDisplayedColor = state.getParcelable(KEY_DISPLAYED_COLOR);
            updateDisplayedColor();
        } else {
            mDisplayedColor = new DisplayedColor();
        }

        ColorCommand lastCommand = null;
        if (state != null) {
            final ArrayList<ColorCommand> unprocessedCommands =
                    state.getParcelableArrayList(KEY_UNPROCESSED_COMMANDS);
            if (unprocessedCommands != null) {
                synchronized (mUnprocessedCommandQueue) {
                    for (final ColorCommand command : unprocessedCommands) {
                        mUnprocessedCommandQueue.addLast(command);
                        lastCommand = command;
                    }
                }
            }
        }

        final UUID lastCommandId = (lastCommand == null) ? null : lastCommand.id;
        final List<ColorCommand> missedCommands =
                mNetworkClientFragment.getCommandsSince(lastCommandId);
        synchronized (mUnprocessedCommandQueue) {
            for (final ColorCommand command : missedCommands) {
                mUnprocessedCommandQueue.addLast(command);
            }
        }

        mHandler.removeMessages(R.id.MSG_COMMANDS_RECEIVED);
        mHandler.sendEmptyMessage(R.id.MSG_COMMANDS_RECEIVED);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        LOG.v("onSaveInstanceState()");
        super.onSaveInstanceState(outState);

        outState.putParcelable(KEY_DISPLAYED_COLOR, mDisplayedColor);

        final ArrayList<ColorCommand> unprocessedCommands;
        synchronized (mUnprocessedCommandQueue) {
            final int size = mUnprocessedCommandQueue.size();
            unprocessedCommands = new ArrayList<>(size);
            for (int i=0; i<size; i++) {
                unprocessedCommands.add(mUnprocessedCommandQueue.get(i));
            }
        }
        outState.putParcelableArrayList(KEY_UNPROCESSED_COMMANDS, unprocessedCommands);
    }

    /**
     * Ask the hosting activity to display a dialog that allows the user to set the server info.
     */
    @Override
    public void showSetServerDialog() {
        final ActivityCallbacks cb = (ActivityCallbacks) getActivity();
        if (cb != null) {
            cb.showSetServerDialog();
        }
    }

    public void restartNetworkClient() {
        mNetworkClientFragment.restart();
    }

    public void onCommandReceived(@NonNull ColorCommand command) {
        synchronized (mUnprocessedCommandQueue) {
            mUnprocessedCommandQueue.addLast(command);
        }
        mHandler.removeMessages(R.id.MSG_COMMANDS_RECEIVED);
        mHandler.sendEmptyMessage(R.id.MSG_COMMANDS_RECEIVED);
    }

    private void processCommands() {
        synchronized (mUnprocessedCommandQueue) {
            while (mUnprocessedCommandQueue.size() > 0) {
                final ColorCommand command = mUnprocessedCommandQueue.popFirst();

                switch (command.instruction) {
                    case ABSOLUTE:
                        mDisplayedColor.r = command.r;
                        mDisplayedColor.g = command.g;
                        mDisplayedColor.b = command.b;
                        break;
                    case RELATIVE:
                        mDisplayedColor.r += command.r;
                        mDisplayedColor.g += command.g;
                        mDisplayedColor.b += command.b;
                        break;
                }
            }
        }

        updateDisplayedColor();
    }

    private void updateDisplayedColor() {
        final int color = mDisplayedColor.toColor();
        mColorFillView.setBackgroundColor(color);
        mColorTextView.setText(mDisplayedColor.toString());
    }

    /**
     * An interface to be implemented by the hosting activity to allow this fragment to make demands
     * on it.
     */
    public interface ActivityCallbacks {

        /**
         * Show the dialog that allows the user to enter the server information.
         */
        void showSetServerDialog();

    }

    private class MainHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(final Message msg) {
            switch (msg.what) {
                case R.id.MSG_COMMANDS_RECEIVED:
                    processCommands();
                    return true;
                default:
                    return false;
            }
        }
    }

    private static class DisplayedColor implements Parcelable {

        public int r;
        public int g;
        public int b;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeInt(r);
            dest.writeInt(g);
            dest.writeInt(b);
        }

        public int toColor() {
            int color = 0xFF000000;
            color |= (r & 0xFF) << 16;
            color |= (g & 0xFF) << 8;
            color |= (b & 0xFF);
            return color;
        }

        @Override
        public String toString() {
            return "(" + r + ", " + g + ", " + b + ")";
        }

        public static final Parcelable.Creator<DisplayedColor> CREATOR =
                new Parcelable.Creator<DisplayedColor>() {

                    @Override
                    public DisplayedColor createFromParcel(final Parcel src) {
                        final DisplayedColor result = new DisplayedColor();
                        result.r = src.readInt();
                        result.g = src.readInt();
                        result.b = src.readInt();
                        return result;
                    }

                    @Override
                    public DisplayedColor[] newArray(final int size) {
                        return new DisplayedColor[size];
                    }

                };
    }
}
