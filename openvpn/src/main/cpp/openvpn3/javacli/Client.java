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

// TESTING_ONLY

public class Client implements OpenVPNClientThread.EventReceiver {
    private OpenVPNClientThread client_thread;

    public static class ConfigError extends Exception {
	public ConfigError(String msg) { super(msg); }
    }

    public static class CredsUnspecifiedError extends Exception {
	public CredsUnspecifiedError(String msg) { super(msg); }
    }

    // Load OpenVPN core (implements ClientAPI_OpenVPNClient) from shared library 
    static {
	System.loadLibrary("ovpncli");
	String test = ClientAPI_OpenVPNClient.crypto_self_test();
	System.out.format("CRYPTO SELF TEST: %s", test);
    }

    public Client(String config_text, String username, String password) throws ConfigError, CredsUnspecifiedError {
	// init client implementation object
	client_thread = new OpenVPNClientThread();

	// load/eval config
	ClientAPI_Config config = new ClientAPI_Config();
	config.setContent(config_text);
	config.setCompressionMode("yes");
	ClientAPI_EvalConfig ec = client_thread.eval_config(config);
	if (ec.getError())
	    throw new ConfigError("OpenVPN config file parse error: " + ec.getMessage());

	// handle creds
	ClientAPI_ProvideCreds creds = new ClientAPI_ProvideCreds();
	if (!ec.getAutologin())
	    {
		if (username.length() > 0)
		    {
			creds.setUsername(username);
			creds.setPassword(password);
			creds.setReplacePasswordWithSessionID(true);
		    }
		else
		    throw new CredsUnspecifiedError("OpenVPN config file requires username/password but none provided");
	    }
	client_thread.provide_creds(creds);
    }

    public void connect() {
	// connect
	client_thread.connect(this);

	// wait for worker thread to exit
	client_thread.wait_thread_long();
    }

    public void stop() {
	client_thread.stop();
    }

    public void show_stats() {
	int n = client_thread.stats_n();
	for (int i = 0; i < n; ++i)
	    {
		String name = client_thread.stats_name(i);
		long value = client_thread.stats_value(i);
		if (value > 0)
		    System.out.format("STAT %s=%s%n", name, value);
	    }
    }

    @Override
    public void event(ClientAPI_Event event) {
	boolean error = event.getError();
	String name = event.getName();
	String info = event.getInfo();
	System.out.format("EVENT: err=%b name=%s info='%s'%n", error, name, info);
    }

    // Callback to get a certificate
    @Override
    public void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req) {
	req.setError(true);
	req.setErrorText("cert request failed: external PKI not implemented");
    }

    // Callback to sign data
    @Override
    public void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req) {
	req.setError(true);
	req.setErrorText("sign request failed: external PKI not implemented");
    }

    @Override
    public void log(ClientAPI_LogInfo loginfo) {
	String text = loginfo.getText();
	System.out.format("LOG: %s", text);
    }

    @Override
    public void done(ClientAPI_Status status) {
	System.out.format("DONE ClientAPI_Status: err=%b msg='%s'%n", status.getError(), status.getMessage());
    }

    @Override
    public boolean socket_protect(int socket)
    {
	return false;
    }

    @Override
    public boolean pause_on_connection_timeout()
    {
	return false;
    }

    @Override
    public OpenVPNClientThread.TunBuilder tun_builder_new()
    {
	return null;
    }
 }
