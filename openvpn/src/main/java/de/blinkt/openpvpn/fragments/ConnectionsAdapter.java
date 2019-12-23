/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.core.Connection;

public class ConnectionsAdapter extends RecyclerView.Adapter<ConnectionsAdapter.ConnectionsHolder> {
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_FOOTER = TYPE_NORMAL + 1;
    private final Context mContext;
    private final VpnProfile mProfile;
    private final Settings_Connections mConnectionFragment;
    private Connection[] mConnections;

    ConnectionsAdapter(Context c, Settings_Connections connections_fragments, VpnProfile vpnProfile) {
        mContext = c;
        mConnections = vpnProfile.mConnections;
        mProfile = vpnProfile;
        mConnectionFragment = connections_fragments;
    }

    @Override
    public ConnectionsAdapter.ConnectionsHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater li = LayoutInflater.from(mContext);

        View card;
        if (viewType == TYPE_NORMAL) {
            card = li.inflate(R.layout.server_card, viewGroup, false);

        } else { // TYPE_FOOTER
            card = li.inflate(R.layout.server_footer, viewGroup, false);
        }
        return new ConnectionsHolder(card, this, viewType);

    }

    @Override
    public void onBindViewHolder(final ConnectionsAdapter.ConnectionsHolder cH, int position) {
        if (position == mConnections.length) {
            // Footer
            return;
        }
        final Connection connection = mConnections[position];

        cH.mConnection = null;

        cH.mPortNumberView.setText(connection.mServerPort);
        cH.mServerNameView.setText(connection.mServerName);
        cH.mPortNumberView.setText(connection.mServerPort);
        cH.mRemoteSwitch.setChecked(connection.mEnabled);


        cH.mProxyNameView.setText(connection.mProxyName);
        cH.mProxyPortNumberView.setText(connection.mProxyPort);

        cH.mConnectText.setText(String.valueOf(connection.getTimeout()));

        cH.mConnectSlider.setProgress(connection.getTimeout());


        cH.mProtoGroup.check(connection.mUseUdp ? R.id.udp_proto : R.id.tcp_proto);

        switch (connection.mProxyType) {
            case NONE:
                cH.mProxyGroup.check(R.id.proxy_none);
                break;
            case HTTP:
                cH.mProxyGroup.check(R.id.proxy_http);
                break;
            case SOCKS5:
                cH.mProxyGroup.check(R.id.proxy_http);
                break;
            case ORBOT:
                cH.mProxyGroup.check(R.id.proxy_orbot);
                break;
        }

        cH.mProxyAuthCb.setChecked(connection.mUseProxyAuth);
        cH.mProxyAuthUser.setText(connection.mProxyAuthUser);
        cH.mProxyAuthPassword.setText(connection.mProxyAuthPassword);

        cH.mCustomOptionsLayout.setVisibility(connection.mUseCustomConfig ? View.VISIBLE : View.GONE);
        cH.mCustomOptionText.setText(connection.mCustomConfiguration);

        cH.mCustomOptionCB.setChecked(connection.mUseCustomConfig);
        cH.mConnection = connection;

        setVisibilityProxyServer(cH, connection);

    }

    private void setVisibilityProxyServer(ConnectionsHolder cH, Connection connection) {
        int visible = (connection.mProxyType == Connection.ProxyType.HTTP || connection.mProxyType == Connection.ProxyType.SOCKS5) ? View.VISIBLE : View.GONE;
        int authVisible = (connection.mProxyType == Connection.ProxyType.HTTP) ? View.VISIBLE : View.GONE;

        cH.mProxyNameView.setVisibility(visible);
        cH.mProxyPortNumberView.setVisibility(visible);
        cH.mProxyNameLabel.setVisibility(visible);

        cH.mProxyAuthLayout.setVisibility(authVisible);

    }

    private void removeRemote(int idx) {
        Connection[] mConnections2 = Arrays.copyOf(mConnections, mConnections.length - 1);
        for (int i = idx + 1; i < mConnections.length; i++) {
            mConnections2[i - 1] = mConnections[i];
        }
        mConnections = mConnections2;

    }

    @Override
    public int getItemCount() {
        return mConnections.length + 1; //for footer
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mConnections.length)
            return TYPE_FOOTER;
        else
            return TYPE_NORMAL;
    }

    void addRemote() {
        mConnections = Arrays.copyOf(mConnections, mConnections.length + 1);
        mConnections[mConnections.length - 1] = new Connection();
        notifyItemInserted(mConnections.length - 1);
        displayWarningIfNoneEnabled();
    }

    void displayWarningIfNoneEnabled() {
        int showWarning = View.VISIBLE;
        for (Connection conn : mConnections) {
            if (conn.mEnabled)
                showWarning = View.GONE;
        }
        mConnectionFragment.setWarningVisible(showWarning);
    }

    void saveProfile() {
        mProfile.mConnections = mConnections;
    }

    static abstract class OnTextChangedWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }
    }

    class ConnectionsHolder extends RecyclerView.ViewHolder {
        private final EditText mServerNameView;
        private final EditText mPortNumberView;
        private final Switch mRemoteSwitch;
        private final RadioGroup mProtoGroup;
        private final EditText mCustomOptionText;
        private final CheckBox mCustomOptionCB;
        private final View mCustomOptionsLayout;
        private final ImageButton mDeleteButton;
        private final EditText mConnectText;
        private final SeekBar mConnectSlider;
        private final ConnectionsAdapter mConnectionsAdapter;
        private final RadioGroup mProxyGroup;
        private final EditText mProxyNameView;
        private final EditText mProxyPortNumberView;
        private final View mProxyNameLabel;
        private final View mProxyAuthLayout;
        private final EditText mProxyAuthUser;
        private final EditText mProxyAuthPassword;
        private final CheckBox mProxyAuthCb;

        private Connection mConnection; // Set to null on update


        ConnectionsHolder(View card, ConnectionsAdapter connectionsAdapter, int viewType) {
            super(card);
            mServerNameView = card.findViewById(R.id.servername);
            mPortNumberView = card.findViewById(R.id.portnumber);
            mRemoteSwitch = card.findViewById(R.id.remoteSwitch);
            mCustomOptionCB = card.findViewById(R.id.use_customoptions);
            mCustomOptionText = card.findViewById(R.id.customoptions);
            mProtoGroup = card.findViewById(R.id.udptcpradiogroup);
            mCustomOptionsLayout = card.findViewById(R.id.custom_options_layout);
            mDeleteButton = card.findViewById(R.id.remove_connection);
            mConnectSlider = card.findViewById(R.id.connect_silder);
            mConnectText = card.findViewById(R.id.connect_timeout);

            mProxyGroup = card.findViewById(R.id.proxyradiogroup);
            mProxyNameView = card.findViewById(R.id.proxyname);
            mProxyPortNumberView = card.findViewById(R.id.proxyport);
            mProxyNameLabel = card.findViewById(R.id.proxyserver_label);

            mProxyAuthLayout = card.findViewById(R.id.proxyauthlayout);
            mProxyAuthCb = card.findViewById(R.id.enable_proxy_auth);
            mProxyAuthUser = card.findViewById(R.id.proxyuser);
            mProxyAuthPassword = card.findViewById(R.id.proxypassword);

            mConnectionsAdapter = connectionsAdapter;

            if (viewType == TYPE_NORMAL)
                addListeners();
        }


        void addListeners() {
            mRemoteSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
                if (mConnection != null) {
                    mConnection.mEnabled = isChecked;
                    mConnectionsAdapter.displayWarningIfNoneEnabled();
                }
            });

            mProtoGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (mConnection != null) {
                    if (checkedId == R.id.udp_proto)
                        mConnection.mUseUdp = true;
                    else if (checkedId == R.id.tcp_proto)
                        mConnection.mUseUdp = false;
                }
            });

            mProxyGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (mConnection != null) {
                    if (checkedId == R.id.proxy_none) {
                        mConnection.mProxyType = Connection.ProxyType.NONE;
                    } else if (checkedId == R.id.proxy_http) {
                        mConnection.mProxyType = Connection.ProxyType.HTTP;
                    } else if (checkedId == R.id.proxy_socks) {
                        mConnection.mProxyType = Connection.ProxyType.SOCKS5;
                    } else if (checkedId == R.id.proxy_orbot) {
                        mConnection.mProxyType = Connection.ProxyType.ORBOT;
                    }
                    setVisibilityProxyServer(this, mConnection);
                }
            });

            mProxyAuthCb.setOnCheckedChangeListener((group, isChecked) ->
            {
                if (mConnection != null) {
                    mConnection.mUseProxyAuth = isChecked;
                    setVisibilityProxyServer(this, mConnection);
                }
            });

            mCustomOptionText.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null)
                        mConnection.mCustomConfiguration = s.toString();
                }
            });

            mCustomOptionCB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mConnection != null) {
                    mConnection.mUseCustomConfig = isChecked;
                    mCustomOptionsLayout.setVisibility(mConnection.mUseCustomConfig ? View.VISIBLE : View.GONE);
                }
            });


            mServerNameView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mServerName = s.toString();
                    }
                }

            });

            mPortNumberView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mServerPort = s.toString();
                    }
                }
            });

            mProxyNameView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mProxyName = s.toString();
                    }
                }

            });

            mProxyPortNumberView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mProxyPort = s.toString();
                    }
                }
            });

            mCustomOptionCB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mConnection != null) {
                    mConnection.mUseProxyAuth = isChecked;
                }
            });

             mProxyAuthPassword.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mProxyAuthPassword = s.toString();
                    }
                }
            });


            mProxyAuthUser.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mProxyAuthUser = s.toString();
                    }
                }
            });



            mCustomOptionText.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.mCustomConfiguration = s.toString();
                    }
                }
            });

            mConnectSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mConnection != null) {
                        mConnectText.setText(String.valueOf(progress));
                        mConnection.mConnectTimeout = progress;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            mConnectText.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        try {
                            int t = Integer.valueOf(String.valueOf(s));
                            mConnectSlider.setProgress(t);
                            mConnection.mConnectTimeout = t;
                        } catch (Exception ignored) {
                        }
                    }
                }
            });

            mDeleteButton.setOnClickListener(
                    v -> {
                        AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
                        ab.setTitle(R.string.query_delete_remote);
                        ab.setPositiveButton(R.string.keep, null);
                        ab.setNegativeButton(R.string.delete, (dialog, which) -> {
                            removeRemote(getAdapterPosition());
                            notifyItemRemoved(getAdapterPosition());
                        });
                        ab.create().show();
                    }
            );


        }
    }
}
