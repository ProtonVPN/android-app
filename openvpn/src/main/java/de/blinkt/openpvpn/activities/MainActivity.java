/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.activities;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.support.v4n.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.fragments.AboutFragment;
import de.blinkt.openpvpn.fragments.FaqFragment;
import de.blinkt.openpvpn.fragments.GeneralSettings;
import de.blinkt.openpvpn.fragments.GraphFragment;
import de.blinkt.openpvpn.fragments.LogFragment;
import de.blinkt.openpvpn.fragments.SendDumpFragment;
import de.blinkt.openpvpn.fragments.VPNProfileList;
import de.blinkt.openpvpn.views.ScreenSlidePagerAdapter;
import de.blinkt.openpvpn.views.SlidingTabLayout;
import de.blinkt.openpvpn.views.TabBarView;


public class MainActivity extends BaseActivity {

    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;
    private SlidingTabLayout mSlidingTabLayout;
    private TabBarView mTabs;

    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);


        // Instantiate a ViewPager and a PagerAdapter.
        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ScreenSlidePagerAdapter(getFragmentManager(), this);

        /* Toolbar and slider should have the same elevation */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            disableToolbarElevation();
        }


        mPagerAdapter.addTab(R.string.vpn_list_title, VPNProfileList.class);
        mPagerAdapter.addTab(R.string.graph, GraphFragment.class);

        mPagerAdapter.addTab(R.string.generalsettings, GeneralSettings.class);
        mPagerAdapter.addTab(R.string.faq, FaqFragment.class);

        if (SendDumpFragment.getLastestDump(this) != null) {
            mPagerAdapter.addTab(R.string.crashdump, SendDumpFragment.class);
        }


        if (isDirectToTV())
            mPagerAdapter.addTab(R.string.openvpn_log, LogFragment.class);

        mPagerAdapter.addTab(R.string.about, AboutFragment.class);
        mPager.setAdapter(mPagerAdapter);

        mTabs = (TabBarView) findViewById(R.id.sliding_tabs);
        mTabs.setViewPager(mPager);
    }

    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    private boolean isDirectToTV() {
        return(getPackageManager().hasSystemFeature(FEATURE_TELEVISION)
                || getPackageManager().hasSystemFeature(FEATURE_LEANBACK));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void disableToolbarElevation() {
        ActionBar toolbar = getActionBar();
        toolbar.setElevation(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent()!=null) {
            String page = getIntent().getStringExtra("PAGE");
            if ("graph".equals(page)) {
                mPager.setCurrentItem(1);
            }
            setIntent(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==R.id.show_log){
            Intent showLog = new Intent(this, LogWindow.class);
            startActivity(showLog);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		System.out.println(data);


	}



}
