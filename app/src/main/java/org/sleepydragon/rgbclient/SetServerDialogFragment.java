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

import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/**
 * A fragment to set the server information.
 */
public class SetServerDialogFragment extends DialogFragment {

    private EditText mHostView;
    private EditText mPortView;
    private Button mOkButtonView;

    private SharedPreferences mSharedPreferences;
    private String mKeyHost;
    private String mKeyPort;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        getDialog().setTitle(R.string.dialog_title_server_settings);

        final View view = inflater.inflate(R.layout.fragment_set_server_settings, container, false);
        mOkButtonView = (Button) view.findViewById(R.id.btn_ok);
        mOkButtonView.setOnClickListener(new OkButtonClickListener());
        mOkButtonView.setEnabled(false);
        mHostView = (EditText) view.findViewById(R.id.server_host);
        mPortView = (EditText) view.findViewById(R.id.server_port);
        final TextWatcher updateOkButtonTextWatcher = new UpdateOkButtonTextWatcher();
        mHostView.addTextChangedListener(updateOkButtonTextWatcher);
        mPortView.addTextChangedListener(updateOkButtonTextWatcher);

        new LoadSettingsAsyncTask(getActivity()).execute();

        return view;
    }

    private void handleOkButtonClick() {
        final SharedPreferences prefs = mSharedPreferences;
        if (prefs == null) {
            return;
        }

        final HostPortPair serverInfo = getHostAndPortFromViews();
        if (serverInfo == null) {
            return;
        }

        prefs.edit()
                .putString(mKeyHost, serverInfo.host)
                .putInt(mKeyPort, serverInfo.port)
                .apply();

        final Context context = getActivity();
        if (context != null) {
            Settings.notifyServerInfoChanged(context);
        }

        dismiss();
    }

    @Nullable
    private HostPortPair getHostAndPortFromViews() {
        final CharSequence hostCS = mHostView.getText();
        final CharSequence portCS = mPortView.getText();

        if (hostCS == null || portCS == null) {
            return null;
        }

        final String portStr = portCS.toString().trim();
        if (portStr.length() == 0) {
            return null;
        }
        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return null;
        }

        final String host = hostCS.toString().trim();
        if (host.length() == 0) {
            return null;
        }

        return new HostPortPair(host, port);
    }

    private void updateOkButtonEnabledState() {
        final HostPortPair serverInfo = getHostAndPortFromViews();
        mOkButtonView.setEnabled(serverInfo != null);
    }

    private class OkButtonClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            handleOkButtonClick();
        }

    }

    private class UpdateOkButtonTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updateOkButtonEnabledState();
        }

    }

    private class LoadSettingsAsyncTask extends Settings.GetSharedPreferencesAsyncTask {

        public LoadSettingsAsyncTask(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onPostExecute(SharedPreferences sharedPreferences) {
            final String hostKey = Settings.getServerHostKey(mContext);
            final String portKey = Settings.getServerPortKey(mContext);

            final String host = sharedPreferences.getString(hostKey, null);
            if (host != null) {
                mHostView.setText(host);
            }

            final int port = sharedPreferences.getInt(portKey, -1);
            if (port != -1) {
                mPortView.setText(Integer.toString(port));
            }

            mSharedPreferences = sharedPreferences;
            mKeyHost = hostKey;
            mKeyPort = portKey;
        }

    }

    private static class HostPortPair {
        public final String host;
        public final int port;

        public HostPortPair(@NonNull String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
