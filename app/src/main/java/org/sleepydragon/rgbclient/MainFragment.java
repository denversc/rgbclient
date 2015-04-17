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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * The main fragment for the main activity.
 */
public class MainFragment extends Fragment
        implements NetworkClientFragment.TargetFragmentCallbacks {

    private static final Logger LOG = new Logger("MainFragment");

    private NetworkClientFragment mNetworkClientFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        super.onCreate(savedInstanceState);
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
    }

    @Override
    public void onDestroy() {
        LOG.v("onDestroy()");
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
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

    /**
     * An interface to be implemented by the hosting activity to allow this fragment to make
     * demands on it.
     */
    public static interface ActivityCallbacks {

        /**
         * Show the dialog that allows the user to enter the server information.
         */
        public void showSetServerDialog();

    }

}
