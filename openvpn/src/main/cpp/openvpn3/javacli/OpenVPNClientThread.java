//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// package OPENVPN_PACKAGE

import java.util.HashSet;

public class OpenVPNClientThread extends ClientAPI_OpenVPNClient implements Runnable {
    private EventReceiver parent;
    private TunBuilder tun_builder;
    private Thread thread;
    private ClientAPI_Status m_connect_status;
    private boolean connect_called = false;

    private int bytes_in_index = -1;
    private int bytes_out_index = -1;

    // thrown if instantiator attempts to call connect more than once
    public static class ConnectCalledTwice extends RuntimeException {
    }

    public interface EventReceiver {
	// Called with events from core
	void event(ClientAPI_Event event);

	// Called with log text from core
	void log(ClientAPI_LogInfo loginfo);

	// Called when connect() thread exits
	void done(ClientAPI_Status status);

	// Called to "protect" a socket from being routed through the tunnel
	boolean socket_protect(int socket);

	// When a connection is close to timeout, the core will call this
	// method.  If it returns false, the core will disconnect with a
	// CONNECTION_TIMEOUT event.  If true, the core will enter a PAUSE
	// state.
	boolean pause_on_connection_timeout();

	// Callback to construct a new tun builder
	TunBuilder tun_builder_new();

	// Callback to get a certificate
	void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req);

	// Callback to sign data
	void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req);
    }

    public interface TunBuilder {
	// Tun builder methods.
	// Methods documented in openvpn/tun/builder/base.hpp

	boolean tun_builder_set_remote_address(String address, boolean ipv6);
	boolean tun_builder_add_address(String address, int prefix_length, String gateway, boolean ipv6, boolean net30);
	boolean tun_builder_reroute_gw(boolean ipv4, boolean ipv6, long flags);
	boolean tun_builder_add_route(String address, int prefix_length, boolean ipv6);
	boolean tun_builder_exclude_route(String address, int prefix_length, boolean ipv6);
	boolean tun_builder_add_dns_server(String address, boolean ipv6);
	boolean tun_builder_add_search_domain(String domain);
	boolean tun_builder_set_mtu(int mtu);
	boolean tun_builder_set_session_name(String name);
	int tun_builder_establish();
	void tun_builder_teardown(boolean disconnect);
    }

    public OpenVPNClientThread() {
	final int n = stats_n();
	for (int i = 0; i < n; ++i)
	    {
		String name = stats_name(i);
		if (name.equals("BYTES_IN"))
		    bytes_in_index = i;
		if (name.equals("BYTES_OUT"))
		    bytes_out_index = i;
	    }
    }

    // start connect session in worker thread
    public void connect(EventReceiver parent_arg) {
	if (connect_called)
	    throw new ConnectCalledTwice();
	connect_called = true;

	// direct client callbacks to parent
	parent = parent_arg;

	// clear status
	m_connect_status = null;

	// execute client in a worker thread
	thread = new Thread(this, "OpenVPNClientThread");
	thread.start();
    }

    // Wait for worker thread to complete; to stop thread,
    // first call super stop() method then wait_thread().
    // This method will give the thread one second to
    // exit and will abandon it after this time.
    public void wait_thread_short() {
	final int wait_millisecs = 5000; // max time that we will wait for thread to exit
	Thread th = thread;
	if (th != null) {
	    try {
		th.join(wait_millisecs);
	    }
	    catch (InterruptedException e) {
	    }

	    // thread failed to stop?
	    if (th.isAlive()) {
		// abandon thread and deliver our own status object to instantiator.
		ClientAPI_Status status = new ClientAPI_Status();
		status.setError(true);
		status.setMessage("CORE_THREAD_ABANDONED");
		call_done(status);
	    }
	}
    }

    // Wait for worker thread to complete; to stop thread,
    // first call super stop() method then wait_thread().
    // This method will wait forever for the thread to exit.
    public void wait_thread_long() {
        if (thread != null) {
            boolean interrupted;
            do {
                interrupted = false;
                try {
                    thread.join();
                }
                catch (InterruptedException e) {
                    interrupted = true;
                    super.stop(); // send thread a stop message
                }
            } while (interrupted);
        }
    }

    public long bytes_in()
    {
	return super.stats_value(bytes_in_index);
    }

    public long bytes_out()
    {
	return super.stats_value(bytes_out_index);
    }

    private void call_done(ClientAPI_Status status)
    {
	EventReceiver p = finalize_thread(status);
	if (p != null)
	    p.done(m_connect_status);
    }

    private synchronized EventReceiver finalize_thread(ClientAPI_Status connect_status)
    {
	EventReceiver p = parent;
	if (p != null) {
	    // save thread connection status
	    m_connect_status = connect_status;

	    // disassociate client callbacks from parent
	    parent = null;
	    tun_builder = null;
	    thread = null;
	}
	return p;
    }

    // Runnable overrides

    @Override
    public void run() {
	// Call out to core to start connection.
	// Doesn't return until connection has terminated.
	ClientAPI_Status status = super.connect();
	call_done(status);
    }

    // ClientAPI_OpenVPNClient (C++ class) overrides

    @Override
    public boolean socket_protect(int socket) {
	EventReceiver p = parent;
	if (p != null)
	    return p.socket_protect(socket);
	else
	    return false;
    }

    @Override
    public boolean pause_on_connection_timeout() {
	EventReceiver p = parent;
	if (p != null)
	    return p.pause_on_connection_timeout();
	else
	    return false;
    }

    @Override
    public void event(ClientAPI_Event event) {
	EventReceiver p = parent;
	if (p != null)
	    p.event(event);
    }

    @Override
    public void log(ClientAPI_LogInfo loginfo) {
	EventReceiver p = parent;
	if (p != null)
	    p.log(loginfo);
    }

    @Override
    public void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req) {
	EventReceiver p = parent;
	if (p != null)
	    p.external_pki_cert_request(req);
    }

    @Override
    public void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req) {
	EventReceiver p = parent;
	if (p != null)
	    p.external_pki_sign_request(req);
    }

    // TunBuilderBase (C++ class) overrides

    @Override
    public boolean tun_builder_new() {
	EventReceiver p = parent;
	if (p != null) {
	    tun_builder = p.tun_builder_new();
	    return tun_builder != null;
	} else
	    return false;
    }

    @Override
    public boolean tun_builder_set_remote_address(String address, boolean ipv6) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_set_remote_address(address, ipv6);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_add_address(String address, int prefix_length, String gateway, boolean ipv6, boolean net30) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_add_address(address, prefix_length, gateway, ipv6, net30);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_reroute_gw(boolean ipv4, boolean ipv6, long flags) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_reroute_gw(ipv4, ipv6, flags);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_add_route(String address, int prefix_length, int metric, boolean ipv6) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_add_route(address, prefix_length, ipv6);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_exclude_route(String address, int prefix_length, int metric, boolean ipv6) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_exclude_route(address, prefix_length, ipv6);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_add_dns_server(String address, boolean ipv6) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_add_dns_server(address, ipv6);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_add_search_domain(String domain)
    {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_add_search_domain(domain);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_set_mtu(int mtu) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_set_mtu(mtu);
	else
	    return false;
    }

    @Override
    public boolean tun_builder_set_session_name(String name)
    {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_set_session_name(name);
	else
	    return false;
    }

    @Override
    public int tun_builder_establish() {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    return tb.tun_builder_establish();
	else
	    return -1;
    }

    @Override
    public void tun_builder_teardown(boolean disconnect) {
	TunBuilder tb = tun_builder;
	if (tb != null)
	    tb.tun_builder_teardown(disconnect);
    }
}
