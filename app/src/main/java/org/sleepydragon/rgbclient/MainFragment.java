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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * The main fragment for the main activity.
 */
public class MainFragment extends Fragment {

    private static final Logger LOG = new Logger("MainFragment");

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
        Fragment networkClientFragment = fm.findFragmentByTag(NetworkClientFragment.TAG);
        if (networkClientFragment == null) {
            networkClientFragment = new NetworkClientFragment();
            networkClientFragment.setTargetFragment(this, 0);
            fm.beginTransaction().add(networkClientFragment, NetworkClientFragment.TAG).commit();
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

}
