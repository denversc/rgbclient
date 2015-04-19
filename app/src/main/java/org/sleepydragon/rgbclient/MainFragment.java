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
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.CircularArray;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The main fragment for the main activity.
 */
public class MainFragment extends Fragment
        implements NetworkClientFragment.TargetFragmentCallbacks {

    private static final Logger LOG = new Logger("MainFragment");
    private static final String KEY_COLOR_STATE = "color_state";
    private static final String KEY_COMMAND_QUEUE = "command_queue";

    private final ColorState.RGB mRGB = new ColorState.RGB();
    private final ArrayList<ColorCommand> mCommandQueue = new ArrayList<>();

    private Handler mHandler;
    private NetworkClientFragment mNetworkClientFragment;

    private ColorState mColorState;
    private View mColorFillView;
    private TextView mColorTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        super.onCreate(savedInstanceState);
        mHandler = new Handler(new MainHandlerCallback());

        if (savedInstanceState == null) {
            mColorState = new ColorState();
        } else {
            mColorState = savedInstanceState.getParcelable(KEY_COLOR_STATE);

            final ArrayList<ColorCommand> commandQueue =
                    savedInstanceState.getParcelableArrayList(KEY_COMMAND_QUEUE);
            synchronized (mCommandQueue) {
                mCommandQueue.addAll(commandQueue);
            }
        }
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

        updateDisplayedColor();

        // add any color commands that were received during the configuration change
        synchronized (mCommandQueue) {
            final ColorCommand lastCommand;
            if (mCommandQueue.size() > 0) {
                lastCommand = mCommandQueue.get(mCommandQueue.size() - 1);
            } else {
                lastCommand = mColorState.getLastAddedCommand();
            }
            final UUID lastCommandId = (lastCommand == null) ? null : lastCommand.id;
            mNetworkClientFragment.getCommandsSince(lastCommandId, mCommandQueue);
        }
        mHandler.removeMessages(R.id.MSG_PROCESS_QUEUED_COMMANDS);
        mHandler.sendEmptyMessage(R.id.MSG_PROCESS_QUEUED_COMMANDS);
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

        final Context context = container.getContext();
        final RecyclerView commandHistoryRecyclerView = new RecyclerView(context);
        commandHistoryRecyclerView.setHasFixedSize(true);
        commandHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        commandHistoryRecyclerView.setAdapter(mColorState.getRecyclerViewAdapter());
        final FrameLayout commandHistoryLayout =
                (FrameLayout) root.findViewById(R.id.command_history);
        commandHistoryLayout.addView(commandHistoryRecyclerView);

        return root;
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        LOG.v("onSaveInstanceState()");
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_COLOR_STATE, mColorState);
        synchronized (mCommandQueue) {
            outState.putParcelableArrayList(KEY_COMMAND_QUEUE, mCommandQueue);
        }
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
        synchronized (mCommandQueue) {
            mCommandQueue.add(command);
        }
        mHandler.removeMessages(R.id.MSG_PROCESS_QUEUED_COMMANDS);
        mHandler.sendEmptyMessage(R.id.MSG_PROCESS_QUEUED_COMMANDS);
    }

    private void processQueuedCommands() {
        synchronized (mCommandQueue) {
            for (final ColorCommand command : mCommandQueue) {
                mColorState.addCommand(command);
            }
            mCommandQueue.clear();
        }
        updateDisplayedColor();
    }

    private void updateDisplayedColor() {
        final boolean colorsSuccess = mColorState.getEffectiveColor(mRGB);

        final int color;
        final String text;
        if (colorsSuccess) {
            color = 0xFF000000 | ((mRGB.r & 0xFF) << 16) | ((mRGB.g & 0xFF) << 8) | (mRGB.b & 0xFF);
            text = "(" + mRGB.r + ", " + mRGB.g + ", " + mRGB.b + ")";
        } else {
            color = Color.TRANSPARENT;
            text = ";";
        }

        mColorFillView.setBackgroundColor(color);
        mColorTextView.setText(text);
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
                case R.id.MSG_PROCESS_QUEUED_COMMANDS:
                    processQueuedCommands();
                    return true;
                default:
                    return false;
            }
        }
    }

    private static class ColorState implements Parcelable {

        private static final int MAX_COMMAND_HISTORY = 100;

        private final Set<ColorCommand> mSelectedRelativeCommands;
        private ColorCommand mSelectedAbsoluteCommand;
        private final CircularArray<ColorCommand> mCommandHistory;
        private final RecyclerView.Adapter<ViewHolderImpl> mRecyclerViewAdapter;

        public ColorState() {
            this(null, null, null);
        }

        public ColorState(@Nullable List<ColorCommand> commandHistory,
                @Nullable ColorCommand selectedAbsoluteCommand,
                @Nullable List<ColorCommand> selectedRelativeCommands) {
            mSelectedAbsoluteCommand = selectedAbsoluteCommand;

            mSelectedRelativeCommands = new HashSet<>();
            if (selectedRelativeCommands != null) {
                for (final ColorCommand command : selectedRelativeCommands) {
                    mSelectedRelativeCommands.add(command);
                }
            }

            mCommandHistory = new CircularArray<>();
            if (commandHistory != null) {
                for (final ColorCommand command : commandHistory) {
                    mCommandHistory.addLast(command);
                }
            }

            mRecyclerViewAdapter = new AdapterImpl();
        }

        public boolean getEffectiveColor(@NonNull RGB rgb) {
            if (mSelectedAbsoluteCommand == null) {
                return false;
            }

            int r = mSelectedAbsoluteCommand.r;
            int g = mSelectedAbsoluteCommand.g;
            int b = mSelectedAbsoluteCommand.b;

            for (final ColorCommand command : mSelectedRelativeCommands) {
                r += command.r;
                g += command.g;
                b += command.b;
            }

            rgb.r = r;
            rgb.g = g;
            rgb.b = b;
            return true;
        }

        public void addCommand(@NonNull ColorCommand command) {
            switch (command.instruction) {
                case ABSOLUTE:
                    mSelectedAbsoluteCommand = command;
                    mSelectedRelativeCommands.clear();
                    mRecyclerViewAdapter.notifyItemRangeChanged(0, mCommandHistory.size());
                    break;
                case RELATIVE:
                    mSelectedRelativeCommands.add(command);
                    break;
                default:
                    throw new AssertionError("unknown instruction type: " + command.instruction);
            }

            // only add "real" commands to the historoy
            if (! command.synthetic) {
                final int position = mCommandHistory.size();
                mCommandHistory.addLast(command);
                if (mCommandHistory.size() <= MAX_COMMAND_HISTORY) {
                    mRecyclerViewAdapter.notifyItemInserted(position);
                } else {
                    mCommandHistory.popFirst();
                    mRecyclerViewAdapter.notifyItemRemoved(0);
                    mRecyclerViewAdapter.notifyItemInserted(position-1);
                }
            }
        }

        @Nullable
        public ColorCommand getLastAddedCommand() {
            return (mCommandHistory.isEmpty()) ? null : mCommandHistory.getLast();
        }

        @NonNull
        public RecyclerView.Adapter getRecyclerViewAdapter() {
            return mRecyclerViewAdapter;
        }

        public static class RGB {
            public int r;
            public int g;
            public int b;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeParcelable(mSelectedAbsoluteCommand, 0);

            final List<ColorCommand> selectedRelativeCommands = new ArrayList<>();
            selectedRelativeCommands.addAll(mSelectedRelativeCommands);
            dest.writeList(selectedRelativeCommands);

            final List<ColorCommand> commandHistory = new ArrayList<>();
            for (int i=0; i<mCommandHistory.size(); i++) {
                commandHistory.add(mCommandHistory.get(i));
            }
            dest.writeList(commandHistory);
        }

        public static final Parcelable.Creator<ColorState> CREATOR =
                new Parcelable.Creator<ColorState>() {

                    @Override
                    public ColorState createFromParcel(final Parcel src) {
                        final ColorCommand selectedAbsoluteCommand = src.readParcelable(null);
                        final List<ColorCommand> selectedRelativeCommands = new ArrayList<>();
                        src.readList(selectedRelativeCommands, null);
                        final List<ColorCommand> commandHistory = new ArrayList<>();
                        src.readList(commandHistory, null);
                        return new ColorState(commandHistory,
                                selectedAbsoluteCommand, selectedRelativeCommands);
                    }

                    @Override
                    public ColorState[] newArray(final int size) {
                        return new ColorState[size];
                    }

                };

        private static class ViewHolderImpl extends RecyclerView.ViewHolder {

            private final CheckBox mView;

            @Nullable
            private ColorCommand mCommand;

            public ViewHolderImpl(@NonNull CheckBox view) {
                super(view);
                mView = view;
            }

            public void setCommand(@Nullable ColorCommand command, boolean selected) {
                if (command == null) {
                    clearCommand();
                } else if (command.equals(mCommand)) {
                    mView.setChecked(selected);
                } else {
                    mCommand = command;
                    mView.setText(command.instruction
                            + " (" + command.r + ", " + command.g + ", " + command.b + ")");
                    mView.setChecked(selected);
                }
            }

            public void clearCommand() {
                mCommand = null;
                mView.setText("");
            }
        }

        private class AdapterImpl extends RecyclerView.Adapter<ViewHolderImpl> {

            public AdapterImpl() {
                setHasStableIds(true);
            }

            @Override
            public long getItemId(final int position) {
                final ColorCommand command = mCommandHistory.get(position);
                return command.id.getLeastSignificantBits();
            }

            @Override
            public ViewHolderImpl onCreateViewHolder(final ViewGroup parent, final int viewType) {
                final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                final Object o = inflater.inflate(R.layout.color_command, null);
                final CheckBox view = (CheckBox) o;
                return new ViewHolderImpl(view);
            }

            @Override
            public void onBindViewHolder(final ViewHolderImpl holder, final int position) {
                final ColorCommand command = mCommandHistory.get(position);
                final boolean selected = mSelectedRelativeCommands.contains(command)
                            || mSelectedAbsoluteCommand == command;
                holder.setCommand(command, selected);
            }

            @Override
            public void onViewRecycled(final ViewHolderImpl holder) {
                super.onViewRecycled(holder);
                holder.clearCommand();
            }

            @Override
            public int getItemCount() {
                return mCommandHistory.size();
            }

        }
    }

}
