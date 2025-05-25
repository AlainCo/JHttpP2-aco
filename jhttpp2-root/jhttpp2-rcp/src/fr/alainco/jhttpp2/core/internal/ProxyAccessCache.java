package fr.alainco.jhttpp2.core.internal;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

public class ProxyAccessCache {

	private int connectTimeout = 60000;

	private InetAddress proxyHost;
	private int proxyPort;
	private Proxy proxy;
	private File cacheDatabase;
	private File cacheInitDatabase;
	private static final Pattern PATTERN_DB_LINE = Pattern.compile("^(.*)?=(true|false)$");
	//Unable to tunnel through proxy.  Proxy returns "HTTP/1.1 403 Forbidden"
	private static final Pattern PATTERN_IOERROR_HTTP = Pattern.compile("HTTP/[0-9\\.]+ ([0-9]+)");
	private static final Pattern PATTERN_SOCKETERROR_EOF = Pattern.compile("(" + Pattern.quote("Unexpected end of file from server") + ")");

	private SoftReference<Map<String, Boolean>> accessCacheRef = new SoftReference<>(new TreeMap<>(SiteComparator.SITE_COMPARATOR));

	public static class SiteComparator implements Comparator<String> {
		private SiteComparator() {
		}

		private static final Pattern PATTERN_SITE = Pattern.compile("([a-z]+)://([^:/]+):([0-9]+)(/.*)?$");

		public static final SiteComparator SITE_COMPARATOR = new SiteComparator();
		public static final String PORT_FORMAT = "0000000000";

		protected String canonize(String s) {
			Matcher m = PATTERN_SITE.matcher(s);
			if (m.matches()) {
				String protocol = m.group(1);
				String port = m.group(3);
				if (port.length() < PORT_FORMAT.length())
					port = PORT_FORMAT.substring(port.length()) + port;
				String host = m.group(2);
				String nameTok[] = host.split("\\.");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < nameTok.length; i++) {
					sb.insert(0, "/");
					sb.insert(0, nameTok[i]);
				}

				sb.append(protocol);
				sb.append(":");
				sb.append(port);

				return sb.toString();
			} else {
				return s;
			}
		}

		@Override
		public int compare(String o1, String o2) {
			return canonize(o1).compareTo(canonize(o2));
		}

	}

	public ProxyAccessCache(InetAddress host, int port, int timeout, File cacheDb, File cacheInitDb) {
		this.proxyHost = host;
		this.proxyPort = port;
		proxy = new Proxy(Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
		if (timeout > 0)
			this.connectTimeout = timeout;
		cacheDatabase = cacheDb;
		this.cacheInitDatabase = cacheInitDb;
		loadDb();
	}

	protected void loadDb() {

		Map<String, Boolean> c = getAccessCache();
		loadDbFromFile(c, cacheDatabase);
		if (cacheInitDatabase != null && !cacheInitDatabase.canRead()) {
			try (FileOutputStream db = new FileOutputStream(cacheInitDatabase, true)) {
				PrintStream ps = new PrintStream(db);
				db.close();
			} catch (Throwable e) {
				System.err.println("ProxyAccessCache (db file=" + cacheInitDatabase
						+ ") : " + e.toString());
			}
		}
		loadDbFromFile(c, cacheInitDatabase);
		
		
		rewriteDb(c);
	}

	protected void rewriteDb(Map<String, Boolean> c) {
		cacheDatabase.delete();
		for (Entry<String, Boolean> entry : c.entrySet()) {
			if (entry.getValue())
				writeDbEntry(entry.getKey(), true);
		}
		for (Entry<String, Boolean> entry : c.entrySet()) {
			if (!entry.getValue())
				writeDbEntry(entry.getKey(), false);
		}
	}

	protected void loadDbFromFile(Map<String, Boolean> c, File f) {
		if (f != null && f.canRead()) {
			try (FileInputStream db = new FileInputStream(f)) {
				InputStreamReader r0 = new InputStreamReader(db);
				BufferedReader reader = new BufferedReader(r0, 8192);
				while (true) {
					String line = reader.readLine();
					if (line == null)
						break;
					try {
						Matcher m = PATTERN_DB_LINE.matcher(line);
						if (m.matches()) {
							String key = m.group(1);
							String accessStr = m.group(2);
							Boolean access = Boolean.valueOf(accessStr);
							c.put(key, access);
						}
					} catch (Exception e) {
					}
				}
				db.close();
			} catch (Throwable e) {
				System.err.println("ProxyAccessCache (db file=" + f
						+ ") : " + e.toString());
			}
		}
	}

	public InetAddress getProxyHost() {
		return proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	protected Map<String, Boolean> getAccessCache() {
		// no problem if concurrent update, one of the map will be kept and other entries lost, it's a cache
		Map<String, Boolean> cache = accessCacheRef.get();
		if (cache == null) {
			cache = new TreeMap<>(SiteComparator.SITE_COMPARATOR);
			accessCacheRef = new SoftReference<>(cache);
		}
		return cache;
	}

	public boolean canAccess(Jhttpp2ClientInputStream in) {

		String protocol = in.isTunnel() ? "https" : "http";
		String host = in.getRemoteHostName();
		int port = in.getRemotePort();
		String key = protocol + "://" + host + ":" + port;
		Map<String, Boolean> c = getAccessCache();
		Boolean access = c.get(key);
		if (access != null) {
			return access;
		}
		try {
			URL u = new URL(protocol, host, port, "/favicon.ico");
			HttpURLConnection conn = (HttpURLConnection) u.openConnection(proxy);
			conn.setConnectTimeout(connectTimeout);
			try {
				conn.connect();
				int status = conn.getResponseCode();
				if (status == 404 || status < 400 || status == 401) {
					access = true;
				} else if (status == 403) {
					// NB: proxy returns 403 of blocked sites, but genuine site may answer 403 sometimes. no solution.
					access = false;
				} else {
					access = null;
				}
			} catch (SocketTimeoutException e) {
				System.err.println("ProxyAccessCache(" + key
						+ ") : " + e.toString());
				access = null;
			} catch (SSLHandshakeException e) {
				if(e.getCause() instanceof EOFException) {
					access=false;
					//"Remote host closed connection during handshake"
				} else {
					access = null;
				}
			} catch (SSLException e) {
				access = null;
			} catch (SocketException e) {
				Matcher m = PATTERN_SOCKETERROR_EOF.matcher(e.getMessage());
				if (m.find()) {
					//Unexpected end of file from server
					access = true;
				} else {
					System.err.println("ProxyAccessCache(" + key + ") : " + e.toString());
					access = null;
				}
			} catch (IOException e) {

				String msg = e.getMessage();
				Matcher m = PATTERN_IOERROR_HTTP.matcher(msg);
				//Unable to tunnel through proxy. Proxy returns "HTTP/1.1 403 Forbidden"

				if (m.find()) {
					String httpCodeStr = m.group(1);
					int httpCode = Integer.parseInt(httpCodeStr);
					if (httpCode == 403) {
						access = false;
					} else {
						System.err.println("ProxyAccessCache(" + key
								+ ") : " + e.toString());
						access = null;
					}
				} else {
					System.err.println("ProxyAccessCache(" + key
							+ ") : " + e.toString());
					access = null;
				}
			} catch (Exception e) {
				System.err.println("ProxyAccessCache(" + key + ") : " + e.toString());
				access = null;
			}

		} catch (MalformedURLException e) {
			System.err.println("ProxyAccessCache(" + key + ") : " + e.toString());
			access = false;
		} catch (IOException e) {
			System.err.println("ProxyAccessCache(" + key + ") : " + e.toString());
			access = null;
		} catch (Throwable e) {
			System.err.println("ProxyAccessCache(" + key + ") : " + e.toString());
			access = null;
		}
		if (access == null) {
			c.put(key, false);// don't retry but no write in db
			return false;
		}
		c.put(key, access);
		writeDbEntry(key, access);
		return access;
	}

	protected void writeDbEntry(String key, Boolean access) {
		if (cacheDatabase != null) {
			synchronized (cacheDatabase) {
				try (FileOutputStream db = new FileOutputStream(cacheDatabase, true)) {
					PrintStream ps = new PrintStream(db);
					ps.format("%s=%s%n", key, access);
					db.close();
				} catch (Throwable e) {
					System.err.println("ProxyAccessCache (db file=" + cacheDatabase
							+ ") : " + e.toString());
				}
			}
		}
	}
}
