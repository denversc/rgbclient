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

import android.app.Activity;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity implements MainFragment.ActivityCallbacks {

    private static final Logger LOG = new Logger("MainActivity");

    private MainFragment mMainFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LOG.v("onCreate()");
        PerformanceUtils.setMainThreadPolicy();
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final FragmentManager fm = getFragmentManager();
        if (savedInstanceState == null) {
            mMainFragment = new MainFragment();
            fm.beginTransaction().add(R.id.container, mMainFragment, "main").commit();
        } else {
            mMainFragment = (MainFragment) fm.findFragmentByTag("main");
        }
    }

    @Override
    protected void onDestroy() {
        LOG.v("onDestroy()");
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void showSetServerDialog() {
        final FragmentManager fm = getFragmentManager();
        final SetServerDialogFragment fragment = new SetServerDialogFragment();
        if (fm.findFragmentByTag("SetServer") == null) {
            fragment.show(fm, "SetServer");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_set_server:
                showSetServerDialog();
                return true;
            case R.id.action_restart_network_client:
                mMainFragment.restartNetworkClient();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
