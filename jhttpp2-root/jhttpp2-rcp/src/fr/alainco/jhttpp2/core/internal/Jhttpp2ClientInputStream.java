package fr.alainco.jhttpp2.core.internal;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * File: Jhttpp2ClientInputStream.java
 * 
 * @author Benjamin Kohl
 */
public class Jhttpp2ClientInputStream extends BufferedInputStream {

	/**
	 * Enable filtering?
	 */
	// private boolean filter = false;
	/**
	 * Buffer
	 */
	private String buf;
	/**
	 * How many Bytes read?
	 */
	private int nRead = 0;

	/**
	 * one line
	 */
	private String line;
	/**
	 * The length of the header (with body, if one)
	 */
	private int headerLength = 0;
	/**
	 * The length of the (optional) body of the actual request
	 */
	private int contentLength = 0;
	/**
	 * This is set to true with requests with bodies, like "POST"
	 */
	private boolean body = false;

	private static Jhttpp2Server server;

	/**
	 * Connection variables
	 */
	private Jhttpp2HTTPSession connection;
	private InetAddress remoteHostAddress;
	private String remoteHostName;
	private boolean ssl = false;

	private String errordescription;
	private int statuscode;

	public String url;
	public String method;
	public int HTTPversion;
	public boolean ipv6reference; // true only for IPv6 address in URL (RFC
									// 2732)
	public int remotePort = 0;
	public int post_data_len = 0;

	public int getHeaderLength() {
		return headerLength;
	}

	public InetAddress getRemoteHost() {
		return remoteHostAddress;
	}

	public String getRemoteHostName() {
		return remoteHostName;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public Jhttpp2ClientInputStream(Jhttpp2Server server,
			Jhttpp2HTTPSession connection, InputStream a) {
		super(a, server.http_request_buffer_size);
		Jhttpp2ClientInputStream.server = server;
		this.connection = connection;
	}

	/**
	 * Handler for the actual HTTP request
	 * 
	 * @exception IOException
	 */
	public int read(byte[] a) throws IOException {
		statuscode = Jhttpp2HTTPSession.SC_OK;

		if (ssl) {// no parsing required if in SSL mode
			int nChars = super.read(a);
			return nChars;
		}

		if (server == null) {
			throw new IOException("Stream closed");
		}

		boolean cookies_enabled = server.enableCookiesByDefault();
		int nChars;
		String rq = "";
		if (!body) {

			boolean start_line = true;

			headerLength = 0;
			post_data_len = 0;
			contentLength = 0;

			nChars = getLine(); // reads the first line
			buf = line;

			while (nChars != -1 && nChars > 2) {
				// while (lread > 2) {
				if (start_line) {
					start_line = false;
					int methodID = server.getHttpMethod(buf);
					switch (methodID) {
					case -1:
						statuscode = Jhttpp2HTTPSession.SC_NOT_SUPPORTED;
						break;
					case 2:
						ssl = true;
					default:
						InetAddress host = parseRequest(buf, methodID);
						if (statuscode != Jhttpp2HTTPSession.SC_OK)
							break; // error occurred, go on with the next line
						boolean needProxy = server
								.isHostNeedProxy(getRemoteHostName());
						if (!needProxy /* !server.useProxy */ && !ssl) {
							/* creates a new request without the host name */
							buf = method + " " + url + " "
									+ server.getHttpVersion() + "\r\n";
							nRead = buf.length();
						}
						// patch pour g�rer le cas o� host==null, par exemple si
						// le
						// nom de host cible est externe inconnu du dns
						// par 13969182+AlainCo@users.noreply.github.com
						if ((needProxy && connection
								.notConnected())
								|| (host != null && !host
										.equals(remoteHostAddress))) {
							if (server.debug) {
								server.writeLog("connect: " + remoteHostAddress
										+ " -> " + host);
							}
							statuscode = Jhttpp2HTTPSession.SC_CONNECTING_TO_HOST;
							remoteHostAddress = host;
						}
						/*
						 * URL blocking (only GET method)
						 */
						if (server.block_urls
								&& methodID == 0
								&& statuscode != Jhttpp2HTTPSession.SC_FILE_REQUEST) {
							if (server.debug)
								System.out.println("Searching match...");
							Jhttpp2URLMatch match = server
									.findMatch(this.remoteHostName + url);
							if (match != null) {
								if (server.debug)
									System.out.println("Match found!");
								cookies_enabled = match.getCookiesEnabled();
								if (match.getActionIndex() == -1)
									break;
								OnURLAction action = (OnURLAction) server
										.getURLActions().elementAt(
												match.getActionIndex());
								if (action.onAccesssDeny()) {
									statuscode = Jhttpp2HTTPSession.SC_URL_BLOCKED;
									if (action.onAccessDenyWithCustomText())
										errordescription = action
												.getCustomErrorText();
								} else if (action.onAccessRedirect()) {
									statuscode = Jhttpp2HTTPSession.SC_MOVED_PERMANENTLY;
									errordescription = action.newLocation();
								}
							} // end if match!=null)
						} // end if (server.block...
					} // end switch
				} // end if(startline)
				else {
					/*-----------------------------------------------
					 * Content-Length parsing
					 *-----------------------------------------------*/
					if (server.startsWith(buf.toUpperCase(), "CONTENT-LENGTH")) {
						String clen = buf.substring(16);
						if (clen.indexOf("\r") != -1)
							clen = clen.substring(0, clen.indexOf("\r"));
						else if (clen.indexOf("\n") != -1)
							clen = clen.substring(0, clen.indexOf("\n"));
						try {
							contentLength = Integer.parseInt(clen);
						} catch (NumberFormatException e) {
							statuscode = Jhttpp2HTTPSession.SC_CLIENT_ERROR;
						}
						if (server.debug)
							server.writeLog("read_f: content_len: "
									+ contentLength);
						if (!ssl)
							body = true; // Note: in HTTP/1.1 any method can
											// have a
											// body, not only "POST"
					} else if (server.startsWith(buf, "Proxy-Connection:")) {
						boolean needProxy = server
								.isHostNeedProxy(getRemoteHostName());
						if (!needProxy)
							buf = null;
						else {
							buf = "Proxy-Connection: Keep-Alive\r\n";
							nRead = buf.length();
						}
					}
					/*
					 * else if (server.startsWith(buf,"Connection:")) { if
					 * (!server.use_proxy) { buf="Connection: Keep-Alive\r\n";
					 * //use always keep-alive lread=buf.length(); } else
					 * buf=null; }
					 */
					/*
					 * cookie crunch section
					 */
					else if (server.startsWith(buf, "Cookie:")) {
						if (!cookies_enabled)
							buf = null;
					}
					/*
					 * HTTP header filtering section
					 */
					else if (server.filter_http) {
						if (server.startsWith(buf, "Referer:")) {// removes
							// "Referer"
							buf = null;
						} else if (server.startsWith(buf, "User-Agent")) // changes
						// User-Agent
						{
							buf = "User-Agent: " + server.getUserAgent()
									+ "\r\n";
							nRead = buf.length();
						}
					}
				}

				if (buf != null) {
					rq += buf;
					// if (server!= null && server.debug)
					// server.writeLog(buf , false);
					headerLength += nRead;
				}
				nChars = getLine();
				// buf = getLine();
				buf = line;
			}
		} else {
			nChars = 0;// if we are back from a buffer overflow in the content
			headerLength = 0;
		}
		if (nChars != -1) {
			// adds last line (should be an empty line) to the header
			// String
			if (nChars > 0) {
				rq += buf;
				headerLength += nRead;
			}

			if (headerLength == 0 && body == false) {
				// server.writeLog("lread: " + lread);
				// server.writeLog("Buf: ####" + buf + "###");
				// server.writeLog("Line: " + line);
				// server.writeLog("rq: #-->" + rq + "<--#");
				// if (server.debug)
				// server.writeLog("header_length=0, setting status to SC_CONNECTION_CLOSED (buggy request)");
				statuscode = Jhttpp2HTTPSession.SC_CONNECTION_CLOSED;
			}
			int oldMax = server.getMaxRequestHeaderSize();
			server.tryIncreaseMaxRequestHeaderSizeTo(headerLength);
			if (headerLength > oldMax) {
				server.tryIncreaseMaxRequestHeaderSizeTo(server
						.getMaxRequestHeaderSize() + 1024);// rallonge pour
																											// �viter trop de
																											// messages
				server.writeLog("header_length="
						+ server.getMaxRequestHeaderSize() + " new maximum");
			}
			if (headerLength > a.length) {
				server.writeLog("header_length="
						+ headerLength
						+ " bigger than first request buffer: "
						+ a.length
						+ " please extend property: server.buffer or constant: Jhttpp2HTTPSession.MAX_HTTP_HEADER_SIZE to more than "
						+ headerLength);
				server.writeLog("temporary extending server.buffer to "
						+ server.getMaxRequestHeaderSize() + "");
				server.http_request_buffer_size = server
						.getMaxRequestHeaderSize();

				throw new IOException(
						"header_length="
								+ headerLength
								+ " bigger than first request buffer: "
								+ a.length
								+ " please extend property: server.http_request_buffer_size or constant: Jhttpp2HTTPSession.MAX_HTTP_HEADER_SIZE to more than "
								+ headerLength);
			}
			for (int i = 0; i < headerLength; i++)
				a[i] = (byte) rq.charAt(i);
			if (body) {// read the body, if "Content-Length" given
				post_data_len = 0;
				while (post_data_len < contentLength) {
					if (headerLength + post_data_len >= a.length) {
						/*
						 * server.
						 * writeLog("read_f: buffer overflow content_len: " +
						 * contentLength + " headerLenght: " + headerLength +
						 * " bufferLength: " + a.length); throw new
						 * RuntimeException
						 * ("read_f: buffer overflow content_len: " +
						 * contentLength + " headerLenght: " + headerLength +
						 * " bufferLength: " + a.length);
						 */
						break;
					}
					a[headerLength + post_data_len] = (byte) read(); // writes data
					// into the
					// array
					post_data_len++;
				}
				headerLength += post_data_len; // add the body-length to the
				// header-length
				if (post_data_len >= contentLength) {
					body = false;// finished
				} else {

					body = true;// please continue reading the content
					contentLength -= post_data_len; // remaining

				}
			}
		}

		// return -1 with an error
		return (statuscode == Jhttpp2HTTPSession.SC_OK) ? headerLength : -1;

	}

	/**
	 * reads a line
	 * 
	 * @exception IOException
	 * @return number of chars in the line
	 */
	public int getLine() throws IOException {
		int c = 0;
		line = "";
		nRead = 0;
		while (c != '\n') {
			c = read();
			if (c != -1) {
				line += (char) c;
				nRead++;
			} else
				break;
		}
		return nRead;
	}

	/**
	 * Parser for the first line from the HTTP request. Sets up the URL, method
	 * and remote host name.
	 * 
	 * @return an InetAddress for the host name, null on errors with a
	 *         statuscode!=SC_OK
	 */
	public InetAddress parseRequest(String a, int method_index) {

		int pos;
		int ipv6bracket;

		String f = "";
		String r_host_name = "";
		String r_port = "";

		url = "";

		if (ssl) {
			// remove CONNECT
			f = a.substring(8);
		} else {
			method = a.substring(0, a.indexOf(" ")); // first word in the line
			pos = a.indexOf(":"); // locate first ":"
			if (pos == -1) {
				// Occurs with "GET / HTTP/1.1"
				// This is not a proxy request
				url = a.substring(a.indexOf(" ") + 1, a.lastIndexOf(" "));
				if (method_index == 0) { // method_index==0 --> GET/HEAD
					if (url.indexOf(server.WEB_CONFIG_FILE) != -1)
						statuscode = Jhttpp2HTTPSession.SC_CONFIG_RQ;
					else
						statuscode = Jhttpp2HTTPSession.SC_FILE_REQUEST;
				} else {
					if (method_index == 1
							&& url.indexOf(server.WEB_CONFIG_FILE) != -1) {
						// allow "POST" for admin log in
						statuscode = Jhttpp2HTTPSession.SC_CONFIG_RQ;
					} else {
						statuscode = Jhttpp2HTTPSession.SC_INTERNAL_SERVER_ERROR;
						errordescription = "This HTTP proxy supports only the \"GET\" method while acting as webserver.";
					}
				}
				remotePort = server.port;
				remoteHostAddress = connection.serveraddress;
				// patch par 13969182+AlainCo@users.noreply.github.com
				if (remoteHostAddress == null) {
					server.writeLog("remoteHostAddress==null because connection.serveraddress==null");
				}
				return remoteHostAddress;
			}
			// Proxy request
			f = a.substring(pos + 3); // removes "http://"
		}
		// Strip white spaces
		f = f.replace("\r", "").replace("\n", "");

		int versionp = f.indexOf("HTTP/");
		String HTTPversionRaw;

		// length of "HTTP/x.x": 8 chars
		if (versionp == (f.length() - 8)) {
			// Detect the HTTP version
			HTTPversionRaw = f.substring(versionp + 5);
			if (HTTPversionRaw.equals("1.1"))
				HTTPversion = 1;
			else if (HTTPversionRaw.equals("1.0"))
				HTTPversion = 0;

			// remove " HTTP/x.x"
			f = f.substring(0, versionp - 1);
			if (server.debug)
				server.writeLog("-->" + f + "<--");
		} else {
			// bad request: no "HTTP/xxx" at the end of the line
			HTTPversionRaw = "";
		}

		/*
		 * pos = f.indexOf(" "); // locate space, should be the space before //
		 * "HTTP/1.1" if (pos == -1) { // buggy request statuscode =
		 * Jhttpp2HTTPSession.SC_CLIENT_ERROR; errordescription =
		 * "Invalid request: \"" + a + "\""; return null; } f = f.substring(0,
		 * pos); // removes all after space
		 */

		pos = f.indexOf("/"); // locate the first slash
		if (pos != -1) {
			url = f.substring(pos); // saves path without host name
			r_host_name = f.substring(0, pos); // reduce string to the host name
		} else {
			url = "/";
			r_host_name = f;
		}

		if (server.debug)
			server.writeLog("#->" + url);

		// search for bracket in host name (IPv6, RFC 2732)
		ipv6bracket = r_host_name.indexOf("[");
		if (ipv6bracket == 0) {
			r_host_name = r_host_name.substring(1);
			ipv6bracket = r_host_name.indexOf("]");
			r_port = r_host_name.substring(ipv6bracket + 1);
			r_host_name = r_host_name.substring(0, ipv6bracket);

			if (server.debug)
				server.writeLog("ipv6 bracket ->" + r_host_name + "<--");

			// URL with brackets, must be IPv6 address
			ipv6reference = true;

			// detect the remote port number, if any
			pos = r_port.indexOf(":");
			if (pos != -1) {
				r_port = r_port.substring(pos + 1);
			} else {
				r_port = null;
			}

		} else {
			// no IPv6 reference with brackets according to RFC 2732
			ipv6reference = false;
			pos = r_host_name.indexOf(":");
			if (pos != -1) {
				r_port = r_host_name.substring(pos + 1);
				r_host_name = r_host_name.substring(0, pos);
			} else
				r_port = null;
		}

		// Port number: parse String and convert to integer
		if (r_port != null && !r_port.equals("")) {
			try {
				remotePort = Integer.parseInt(r_port);
			} catch (NumberFormatException e_get_host) {
				if (server.debug)
					server.writeLog("get_Host :" + e_get_host
							+ " Failed to parse remote port numer!");
				remotePort = 80;
			}
		} else
			remotePort = 80;

		if (server.debug)
			server.writeLog(method + " " + url + " " + HTTPversionRaw);

		remoteHostName = r_host_name;
		InetAddress address = null;

		if (server.log_access)
			server.logAccess(String.format("REQUEST %s %s %s", connection.getLocalSocket().getInetAddress()
					.getHostAddress(), method, getFullUrl()));

		// Resolve host name
		try {
			address = InetAddress.getByName(remoteHostName);
		} catch (UnknownHostException e_u_host) {
			boolean needProxy = server.isHostNeedProxy(remoteHostName);
			if (!/* server.useProxy */needProxy)
				statuscode = Jhttpp2HTTPSession.SC_HOST_NOT_FOUND;
		}

		if (remotePort == server.port && address != null
				&& address.equals(connection.serveraddress)) {
			if (url.indexOf(server.WEB_CONFIG_FILE) != -1
					&& (method_index == 0 || method_index == 1))
				statuscode = Jhttpp2HTTPSession.SC_CONFIG_RQ;
			else if (method_index > 0) {
				statuscode = Jhttpp2HTTPSession.SC_INTERNAL_SERVER_ERROR;
				errordescription = "This WWW proxy supports only the \"GET\" method while acting as webserver.";
			} else
				statuscode = Jhttpp2HTTPSession.SC_FILE_REQUEST;
		}

		return address;
	}

	/**
	 * @return boolean whether the current connection was established with the
	 *         CONNECT method.
	 * @since 0.2.21
	 */
	public boolean isTunnel() {
		return ssl;
	}

	/**
	 * @return the full qualified URL of the actual request.
	 * @since 0.4.0
	 */
	public String getFullUrl() {
		return getBaseUrl() + url;
	}

	public String getBaseUrl() {
		return "http"
				+ (ssl ? "s" : "")
				+ "://"
				+ (ipv6reference ? "[" + getRemoteHostName() + "]"
						: getRemoteHostName())
				+ (remotePort != 80 ? (":" + remotePort) : "");
	}

	/**
	 * @return status-code for the current request
	 * @since 0.3.5
	 */
	public int getStatusCode() {
		return statuscode;
	}

	/**
	 * @return the (optional) error description for this request
	 */
	public String getErrorDescription() {
		return errordescription;
	}
	/*
	 * public void close() throws IOException { if (server != null)
	 * server.writeLog("close() in Jhttpp2ClientInputStream"); this.connection =
	 * null; // this.server = null; super.close(); }
	 */
}
