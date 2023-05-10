//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

import java.io.*;
import java.nio.charset.Charset;

public class Main {
    // utility method to read a file and return as a String
    public static String readFile(String filename) throws IOException {
	return readStream(new FileInputStream(filename));
    }

    private static String readStream(InputStream stream) throws IOException {
	// No real need to close the BufferedReader/InputStreamReader
	// as they're only wrapping the stream
	try {
	    Reader reader = new BufferedReader(new InputStreamReader(stream));
	    StringBuilder builder = new StringBuilder();
	    char[] buffer = new char[4096];
	    int read;
	    while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
		builder.append(buffer, 0, read);
	    }
	    return builder.toString();
	} finally {
	    // Potential issue here: if this throws an IOException,
	    // it will mask any others. Normally I'd use a utility
	    // method which would log exceptions and swallow them
	    stream.close();
	}
    }

    public static void main(String[] args) throws IOException, Client.ConfigError, Client.CredsUnspecifiedError {
	if (args.length >= 1)
	    {
		// load config file
		String config = readFile(args[0]);

		// get creds
		String username = "";
		String password = "";
		if (args.length >= 3)
		    {
			username = args[1];
			password = args[2];
		    }

		// instantiate client object
		final Client client = new Client(config, username, password);

		// catch signals
		final Thread mainThread = Thread.currentThread();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
			    client.stop();
			    try {
				mainThread.join();
			    }
			    catch (InterruptedException e) {
			    }
			}
		    });

		// execute client session
		client.connect();

		// show stats before exit
		client.show_stats();
	    }
	else
	    {
		System.err.println("OpenVPN Java client");
		System.err.println("Usage: java Client <client.ovpn> [username] [password]");
		System.exit(2);
	    }
    }
}
