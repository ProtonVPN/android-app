/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import de.blinkt.openpvpn.LaunchVPN;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.activities.DisconnectVPN;
import de.blinkt.openpvpn.activities.MainActivity;
import de.blinkt.openpvpn.activities.VPNPreferences;
import de.blinkt.openpvpn.core.ConnectionStatus;
import de.blinkt.openpvpn.core.OpenVPNManagement;
import de.blinkt.openpvpn.core.OpenVPNService;
import de.blinkt.openpvpn.core.Preferences;
import de.blinkt.openpvpn.core.ProfileManager;
import de.blinkt.openpvpn.core.VpnStatus;
import de.blinkt.openpvpn.core.LogItem;
import de.blinkt.openpvpn.core.VpnStatus.LogListener;
import de.blinkt.openpvpn.core.VpnStatus.StateListener;

import static de.blinkt.openpvpn.core.OpenVPNService.humanReadableByteCount;

public class LogFragment extends ListFragment implements StateListener, SeekBar.OnSeekBarChangeListener, RadioGroup.OnCheckedChangeListener, VpnStatus.ByteCountListener {
    private static final String LOGTIMEFORMAT = "logtimeformat";
    private static final int START_VPN_CONFIG = 0;
    private static final String VERBOSITYLEVEL = "verbositylevel";



    private SeekBar mLogLevelSlider;
    private LinearLayout mOptionsLayout;
    private RadioGroup mTimeRadioGroup;
    private TextView mUpStatus;
    private TextView mDownStatus;
    private TextView mConnectStatus;
    private boolean mShowOptionsLayout;
    private CheckBox mClearLogCheckBox;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        ladapter.setLogLevel(progress + 1);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radioISO) {
            ladapter.setTimeFormat(LogWindowListAdapter.TIME_FORMAT_ISO);
        } else if (checkedId == R.id.radioNone) {
            ladapter.setTimeFormat(LogWindowListAdapter.TIME_FORMAT_NONE);
        } else if (checkedId == R.id.radioShort) {
            ladapter.setTimeFormat(LogWindowListAdapter.TIME_FORMAT_SHORT);
        }
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        //%2$s/s %1$s - ↑%4$s/s %3$s
        Resources res = getActivity().getResources();
        final String down = String.format("%2$s %1$s", humanReadableByteCount(in, false, res), humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, res));
        final String up = String.format("%2$s %1$s", humanReadableByteCount(out, false, res), humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, res));

        if (mUpStatus != null && mDownStatus != null) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUpStatus.setText(up);
                        mDownStatus.setText(down);
                    }
                });
            }
        }

    }


    class LogWindowListAdapter implements ListAdapter, LogListener, Callback {

        private static final int MESSAGE_NEWLOG = 0;

        private static final int MESSAGE_CLEARLOG = 1;

        private static final int MESSAGE_NEWTS = 2;
        private static final int MESSAGE_NEWLOGLEVEL = 3;

        public static final int TIME_FORMAT_NONE = 0;
        public static final int TIME_FORMAT_SHORT = 1;
        public static final int TIME_FORMAT_ISO = 2;
        private static final int MAX_STORED_LOG_ENTRIES = 1000;

        private Vector<LogItem> allEntries = new Vector<>();

        private Vector<LogItem> currentLevelEntries = new Vector<LogItem>();

        private Handler mHandler;

        private Vector<DataSetObserver> observers = new Vector<DataSetObserver>();

        private int mTimeFormat = 0;
        private int mLogLevel = 3;


        public LogWindowListAdapter() {
            initLogBuffer();
            if (mHandler == null) {
                mHandler = new Handler(this);
            }

            VpnStatus.addLogListener(this);
        }


        private void initLogBuffer() {
            allEntries.clear();
            Collections.addAll(allEntries, VpnStatus.getlogbuffer());
            initCurrentMessages();
        }

        String getLogStr() {
            String str = "";
            for (LogItem entry : allEntries) {
                str += getTime(entry, TIME_FORMAT_ISO) + entry.getString(getActivity()) + '\n';
            }
            return str;
        }


        private void shareLog() {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, getLogStr());
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ics_openvpn_log_file));
            shareIntent.setType("text/plain");
            startActivity(Intent.createChooser(shareIntent, "Send Logfile"));
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            observers.add(observer);

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            observers.remove(observer);
        }

        @Override
        public int getCount() {
            return currentLevelEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return currentLevelEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return ((Object) currentLevelEntries.get(position)).hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v;
            if (convertView == null)
                v = new TextView(getActivity());
            else
                v = (TextView) convertView;

            LogItem le = currentLevelEntries.get(position);
            String msg = le.getString(getActivity());
            String time = getTime(le, mTimeFormat);
            msg = time + msg;

            int spanStart = time.length();

            SpannableString t = new SpannableString(msg);

            //t.setSpan(getSpanImage(le,(int)v.getTextSize()),spanStart,spanStart+1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            v.setText(t);
            return v;
        }

        private String getTime(LogItem le, int time) {
            if (time != TIME_FORMAT_NONE) {
                Date d = new Date(le.getLogtime());
                java.text.DateFormat timeformat;
                if (time == TIME_FORMAT_ISO)
                    timeformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                else
                    timeformat = DateFormat.getTimeFormat(getActivity());

                return timeformat.format(d) + " ";

            } else {
                return "";
            }

        }

        private ImageSpan getSpanImage(LogItem li, int imageSize) {
            int imageRes = android.R.drawable.ic_menu_call;

            switch (li.getLogLevel()) {
                case ERROR:
                    imageRes = android.R.drawable.ic_notification_clear_all;
                    break;
                case INFO:
                    imageRes = android.R.drawable.ic_menu_compass;
                    break;
                case VERBOSE:
                    imageRes = android.R.drawable.ic_menu_info_details;
                    break;
                case WARNING:
                    imageRes = android.R.drawable.ic_menu_camera;
                    break;
            }

            Drawable d = getResources().getDrawable(imageRes);


            //d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            d.setBounds(0, 0, imageSize, imageSize);
            ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);

            return span;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return currentLevelEntries.isEmpty();

        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void newLog(LogItem logMessage) {
            Message msg = Message.obtain();
            assert (msg != null);
            msg.what = MESSAGE_NEWLOG;
            Bundle bundle = new Bundle();
            bundle.putParcelable("logmessage", logMessage);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

        @Override
        public boolean handleMessage(Message msg) {
            // We have been called
            if (msg.what == MESSAGE_NEWLOG) {

                LogItem logMessage = msg.getData().getParcelable("logmessage");
                if (addLogMessage(logMessage))
                    for (DataSetObserver observer : observers) {
                        observer.onChanged();
                    }
            } else if (msg.what == MESSAGE_CLEARLOG) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }
                initLogBuffer();
            } else if (msg.what == MESSAGE_NEWTS) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }
            } else if (msg.what == MESSAGE_NEWLOGLEVEL) {
                initCurrentMessages();

                for (DataSetObserver observer : observers) {
                    observer.onChanged();
                }

            }

            return true;
        }

        private void initCurrentMessages() {
            currentLevelEntries.clear();
            for (LogItem li : allEntries) {
                if (li.getVerbosityLevel() <= mLogLevel ||
                        mLogLevel == VpnProfile.MAXLOGLEVEL)
                    currentLevelEntries.add(li);
            }
        }

        /**
         * @param logmessage
         * @return True if the current entries have changed
         */
        private boolean addLogMessage(LogItem logmessage) {
            allEntries.add(logmessage);

            if (allEntries.size() > MAX_STORED_LOG_ENTRIES) {
                Vector<LogItem> oldAllEntries = allEntries;
                allEntries = new Vector<LogItem>(allEntries.size());
                for (int i = 50; i < oldAllEntries.size(); i++) {
                    allEntries.add(oldAllEntries.elementAt(i));
                }
                initCurrentMessages();
                return true;
            } else {
                if (logmessage.getVerbosityLevel() <= mLogLevel) {
                    currentLevelEntries.add(logmessage);
                    return true;
                } else {
                    return false;
                }
            }
        }

        void clearLog() {
            // Actually is probably called from GUI Thread as result of the user
            // pressing a button. But better safe than sorry
            VpnStatus.clearLog();
            VpnStatus.logInfo(R.string.logCleared);
            mHandler.sendEmptyMessage(MESSAGE_CLEARLOG);
        }


        public void setTimeFormat(int newTimeFormat) {
            mTimeFormat = newTimeFormat;
            mHandler.sendEmptyMessage(MESSAGE_NEWTS);
        }

        public void setLogLevel(int logLevel) {
            mLogLevel = logLevel;
            mHandler.sendEmptyMessage(MESSAGE_NEWLOGLEVEL);
        }

    }


    private LogWindowListAdapter ladapter;
    private TextView mSpeedView;


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clearlog) {
            ladapter.clearLog();
            return true;
        } else if (item.getItemId() == R.id.cancel) {
            Intent intent = new Intent(getActivity(), DisconnectVPN.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.send) {
            ladapter.shareLog();
        } else if (item.getItemId() == R.id.edit_vpn) {
            VpnProfile lastConnectedprofile = ProfileManager.get(getActivity(), VpnStatus.getLastConnectedVPNProfile());

            if (lastConnectedprofile != null) {
                Intent vprefintent = new Intent(getActivity(), VPNPreferences.class)
                        .putExtra(VpnProfile.EXTRA_PROFILEUUID, lastConnectedprofile.getUUIDString());
                startActivityForResult(vprefintent, START_VPN_CONFIG);
            } else {
                Toast.makeText(getActivity(), R.string.log_no_last_vpn, Toast.LENGTH_LONG).show();
            }
        } else if (item.getItemId() == R.id.toggle_time) {
            showHideOptionsPanel();
        } else if (item.getItemId() == android.R.id.home) {
            // This is called when the Home (Up) button is pressed
            // in the Action Bar.
            Intent parentActivityIntent = new Intent(getActivity(), MainActivity.class);
            parentActivityIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(parentActivityIntent);
            getActivity().finish();
            return true;

        }
        return super.onOptionsItemSelected(item);

    }

    private void showHideOptionsPanel() {
        boolean optionsVisible = (mOptionsLayout.getVisibility() != View.GONE);

        ObjectAnimator anim;
        if (optionsVisible) {
            anim = ObjectAnimator.ofFloat(mOptionsLayout, "alpha", 1.0f, 0f);
            anim.addListener(collapseListener);

        } else {
            mOptionsLayout.setVisibility(View.VISIBLE);
            anim = ObjectAnimator.ofFloat(mOptionsLayout, "alpha", 0f, 1.0f);
            //anim = new TranslateAnimation(0.0f, 0.0f, mOptionsLayout.getHeight(), 0.0f);

        }

        //anim.setInterpolator(new AccelerateInterpolator(1.0f));
        //anim.setDuration(300);
        //mOptionsLayout.startAnimation(anim);
        anim.start();

    }

    AnimatorListenerAdapter collapseListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animator) {
            mOptionsLayout.setVisibility(View.GONE);
        }

    };


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.logmenu, menu);
        if (getResources().getBoolean(R.bool.logSildersAlwaysVisible))
            menu.removeItem(R.id.toggle_time);
    }


    @Override
    public void onResume() {
        super.onResume();
        Intent intent = new Intent(getActivity(), OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
    }

    @Override
    public void onStart() {
        super.onStart();
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == START_VPN_CONFIG && resultCode == Activity.RESULT_OK) {
            String configuredVPN = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);

            final VpnProfile profile = ProfileManager.get(getActivity(), configuredVPN);
            ProfileManager.getInstance(getActivity()).saveProfile(getActivity(), profile);
            // Name could be modified, reset List adapter

            AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
            dialog.setTitle(R.string.configuration_changed);
            dialog.setMessage(R.string.restart_vpn_after_change);


            dialog.setPositiveButton(R.string.restart,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(getActivity(), LaunchVPN.class);
                            intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUIDString());
                            intent.setAction(Intent.ACTION_MAIN);
                            startActivity(intent);
                        }


                    });
            dialog.setNegativeButton(R.string.ignore, null);
            dialog.create().show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public void onStop() {
        super.onStop();
        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);

        getActivity().getPreferences(0).edit().putInt(LOGTIMEFORMAT, ladapter.mTimeFormat)
                .putInt(VERBOSITYLEVEL, ladapter.mLogLevel).apply();

    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ListView lv = getListView();

        lv.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                ClipboardManager clipboard = (ClipboardManager)
                        getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Log Entry", ((TextView) view).getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getActivity(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.log_fragment, container, false);

        setHasOptionsMenu(true);

        ladapter = new LogWindowListAdapter();
        ladapter.mTimeFormat = getActivity().getPreferences(0).getInt(LOGTIMEFORMAT, 1);
        int logLevel = getActivity().getPreferences(0).getInt(VERBOSITYLEVEL, 1);
        ladapter.setLogLevel(logLevel);

        setListAdapter(ladapter);

        mTimeRadioGroup = (RadioGroup) v.findViewById(R.id.timeFormatRadioGroup);
        mTimeRadioGroup.setOnCheckedChangeListener(this);

        if (ladapter.mTimeFormat == LogWindowListAdapter.TIME_FORMAT_ISO) {
            mTimeRadioGroup.check(R.id.radioISO);
        } else if (ladapter.mTimeFormat == LogWindowListAdapter.TIME_FORMAT_NONE) {
            mTimeRadioGroup.check(R.id.radioNone);
        } else if (ladapter.mTimeFormat == LogWindowListAdapter.TIME_FORMAT_SHORT) {
            mTimeRadioGroup.check(R.id.radioShort);
        }

        mClearLogCheckBox = (CheckBox) v.findViewById(R.id.clearlogconnect);
        mClearLogCheckBox.setChecked(PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(LaunchVPN.CLEARLOG, true));
        mClearLogCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getDefaultSharedPreferences(getActivity()).edit().putBoolean(LaunchVPN.CLEARLOG, isChecked).apply();
            }
        });

        mSpeedView = (TextView) v.findViewById(R.id.speed);

        mOptionsLayout = (LinearLayout) v.findViewById(R.id.logOptionsLayout);
        mLogLevelSlider = (SeekBar) v.findViewById(R.id.LogLevelSlider);
        mLogLevelSlider.setMax(VpnProfile.MAXLOGLEVEL - 1);
        mLogLevelSlider.setProgress(logLevel - 1);

        mLogLevelSlider.setOnSeekBarChangeListener(this);

        if (getResources().getBoolean(R.bool.logSildersAlwaysVisible))
            mOptionsLayout.setVisibility(View.VISIBLE);

        mUpStatus = (TextView) v.findViewById(R.id.speedUp);
        mDownStatus = (TextView) v.findViewById(R.id.speedDown);
        mConnectStatus = (TextView) v.findViewById(R.id.speedStatus);
        if (mShowOptionsLayout)
            mOptionsLayout.setVisibility(View.VISIBLE);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Scroll to the end of the list end
        //getListView().setSelection(getListView().getAdapter().getCount()-1);
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        if (getResources().getBoolean(R.bool.logSildersAlwaysVisible)) {
            mShowOptionsLayout = true;
            if (mOptionsLayout != null)
                mOptionsLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //getActionBar().setDisplayHomeAsUpEnabled(true);

    }


    @Override
    public void updateState(final String status, final String logMessage, final int resId, final ConnectionStatus level) {
        if (isAdded()) {
            final String cleanLogMessage = VpnStatus.getLastCleanLogMessage(getActivity());

            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (isAdded()) {
                        if (mSpeedView != null) {
                            mSpeedView.setText(cleanLogMessage);
                        }
                        if (mConnectStatus != null)
                            mConnectStatus.setText(cleanLogMessage);
                    }
                }
            });
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {
    }


    @Override
    public void onDestroy() {
        VpnStatus.removeLogListener(ladapter);
        super.onDestroy();
    }

}
