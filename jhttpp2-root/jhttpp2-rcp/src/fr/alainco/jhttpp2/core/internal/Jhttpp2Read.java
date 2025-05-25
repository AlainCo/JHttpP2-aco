package fr.alainco.jhttpp2.core.internal;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * File: Jhttpp2Read.java reads from a Jhttpp2ClientInputStream and writes to
 * the BufferedOutputStream
 * 
 * @author Benjamin Kohl
 */
public class Jhttpp2Read extends Thread {
	private final int BUFFER_SIZE = 65535;
	private BufferedInputStream in;
	private BufferedOutputStream out;
	private Jhttpp2HTTPSession connection;
	private Jhttpp2Server server;
	
	private long totalWritten=0L;
	
	public long getTotalWritten() {
		return totalWritten;
	}

	public Jhttpp2Read(Jhttpp2Server server, Jhttpp2HTTPSession connection,
			BufferedInputStream l_in, BufferedOutputStream l_out) {
		in = l_in;
		out = l_out;
		this.connection = connection;
		this.server = server;
		setPriority(Thread.MIN_PRIORITY);
		start();
	}

	public void run() {
		read();
		
		server = null;
		connection = null;
		in = null;
		out = null;
		
	}

	private void read() {
		int bytes_read = 0;
		byte[] buf = new byte[BUFFER_SIZE];
		try {
			while (true) {
				bytes_read = in.read(buf);
				if (bytes_read != -1) {
					out.write(buf, 0, bytes_read);
					out.flush();
					totalWritten+=bytes_read;
				} else
					break;
			}
		} catch (IOException e) {
		}

		try {
			if (connection.getStatus() != connection.SC_CONNECTING_TO_HOST)
				connection.getLocalSocket().close();
			// If we are connecting to a new host (and this thread is
			// already running!) , the upstream
			// socket will be closed. So we get here and close our own
			// downstream socket..... and the browser
			// displays an empty page because jhttpp2
			// closes the connection..... so close the downstream socket only
			// when NOT connecting to a new host....
		} catch (IOException e_socket_close) {
		}
		
		buf = null;
	}

	/**
	 * stop the thread by closing the socket
	 */
	public void close() {
		try {
			if(in!=null)
				in.close();
		} catch (Exception e) {
		}
	}
}
