package fr.alainco.jhttpp2.core.internal;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 * More Information and documentation: HTTP://jhttp2.sourceforge.net/
 */

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Jhttpp2Server implements Runnable {

	public static final String FILENAME_PUBLIC_SUFFIX_LIST = "public_suffix_list.dat";
	public static final String REGEX_PREFIX = "regex:";
	private final String VERSION = "0.5.7-AlainCo";
	private final String HTTP_VERSION = "HTTP/1.1";

	private final String MAIN_LOGFILE = "server.log";
	private final String DATA_FILE = "server.data";
	private final String SERVER_PROPERTIES_FILE = "server.properties";
	File configDirectory = new File(".").getAbsoluteFile();
	File logDirectory = new File(".").getAbsoluteFile();
	File dataFileName;
	File serverPropertiesFileName;
	File publicSuffixListFileName;

	File logFileName;

	private String httpUserAgent = "Mozilla/4.0 (compatible; MSIE 5.0; WindowsNT 5.1)";
	private ServerSocket listen;
	private BufferedWriter logFile;
	private BufferedWriter accessLogFile;
	private Properties serverproperties = null;

	private volatile long bytesDownload;
	private volatile long bytesUpload;
	private volatile int maxRequestHeaderSize = 0;
	private volatile int numconnections;

	private boolean enable_cookies_by_default = true;
	private WildcardDictionary dic = new WildcardDictionary();
	private Vector<OnURLAction> urlactions = new Vector<OnURLAction>();

	public static final int DEFAULT_SERVER_PORT = 3128;
	public static final String DEFAULT_SERVER_LISTEN_INTERFACE = "localhost";
	public static final String LISTEN_ALL_INTERFACE = "*";

	public static final String WEB_CONFIG_FILE = "admin/jp2-config";
	public static final String DEFAULT_NO_PROXY_HOSTS = "localhost|127.*|10.*|192.168.*";

	public int port = DEFAULT_SERVER_PORT;
	public InetAddress listen_interface_address = null;
	public String listen_interface = DEFAULT_SERVER_LISTEN_INTERFACE;

	public int proxy_prev_timeout = 30000;
	public List<ProxyAccessCache> proxy_prev_cache_list = new ArrayList<>();

	public InetAddress proxy;

	public int proxy_port = 0;
	private String no_proxy_hosts = DEFAULT_NO_PROXY_HOSTS;
	private Pattern no_proxy_hosts_pattern = null;

	public long config_auth = 0;
	public long config_session_id = 0;
	public String config_user = "";
	public String config_password = "";

	public boolean fatalError;
	private String errorMessage;
	private boolean serverRunning = false;

	public boolean useProxy = false;
	public boolean block_urls = false;
	public boolean filter_http = false;
	public boolean debug = false;
	public boolean log_access = true;
	public String log_access_filename = "access.log";
	public boolean webconfig = false;
	public boolean www_server = true;

	public int http_file_response_buffer_size = 4 * 1024;
	public int http_request_buffer_size = 100 * 1024;
	public int http_proxy_response_buffer_size = 100 * 1024;
	public NetstatPollerThread netstatPollerThread;

	public DNSPublicSuffixDB publicSuffixDb = null;

	void init() {

		System.out.printf("jHTTPp2 HTTP Proxy Server Version %s%n", getServerVersion());
		System.out.printf("Copyright (c) 2001-2011 Benjamin Kohl%n"
				+ "This software comes with ABSOLUTELY NO WARRANTY OF ANY KIND.%n"
				+ "http://jhttp2.sourceforge.net/\n modifed by AlainCo %n", getServerVersion());
		System.out.printf("Config directory: %s%n", configDirectory.toString());
		System.out.printf("Log directory: %s%n", logDirectory.toString());
		logFileName = new File(logDirectory, MAIN_LOGFILE);
		dataFileName = new File(configDirectory, DATA_FILE);
		serverPropertiesFileName = new File(configDirectory, SERVER_PROPERTIES_FILE);
		publicSuffixListFileName = new File(configDirectory, FILENAME_PUBLIC_SUFFIX_LIST);

		// create new BufferedWriter instance for logging to file
		try {
			logFile = new BufferedWriter(new FileWriter(logFileName, true));
		} catch (Exception e_logfile) {
			setErrorMsg("Unable to open the main log file.");
			if (logFile == null)
				setErrorMsg("jHTTPp2 need write permission for the file " + logFileName);
			errorMessage += " " + e_logfile.getMessage();
		}
		writeLog("jHTTPp2 proxy server startup...");
		
		// set SSL connection to ignore truststore
		initLooseTruststoreSSLFactory();

		// restore settings from file. If this fails, default settings will be
		// used
		restoreSettings();

		// create now server socket
		try {
			final int backlog = 32;
			if (LISTEN_ALL_INTERFACE.equalsIgnoreCase(listen_interface)) {
				listen = new ServerSocket(port, backlog);
			} else {
				listen_interface_address = InetAddress.getByName(listen_interface);
				listen = new ServerSocket(port, backlog, listen_interface_address);
			}
		} catch (UnknownHostException e) {
			setErrorMsg("The listen interface address " + listen_interface
					+ " cannot be resolved. Use property server.listen_interface=" + LISTEN_ALL_INTERFACE
					+ " for listen all interfaces. default is: " + DEFAULT_SERVER_LISTEN_INTERFACE + " . Exception:"
					+ e);
		} catch (BindException e_bind_socket) {
			setErrorMsg("The socket " + port + " is already in use (Another jHTTPp2 proxy running?) " + e_bind_socket
					+ " interface: " + listen_interface_address);
		} catch (IOException e_io_socket) {
			setErrorMsg("IO Exception occured while creating server socket on port " + port + ". " + e_io_socket
					+ " interface: " + listen_interface_address);
		}

		if (fatalError) {
			writeLog(errorMessage);
			return;
		}
		netstatPollerThread = new NetstatPollerThread(listen);
		netstatPollerThread.initFromProperties();
		if (netstatPollerThread.isUsable()) {
			System.out.println("start Netstat Poller");
			new Thread(netstatPollerThread).start();
		}

		{
			createPublicSufficListFile();
			loadSuffixListDb();
		}

	}

	protected void initLooseTruststoreSSLFactory() {
		TrustManager trm = new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
		        return null;
		    }

		    public void checkClientTrusted(X509Certificate[] certs, String authType) {

		    }

		    public void checkServerTrusted(X509Certificate[] certs, String authType) {
		    }
		};

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, new TrustManager[] { trm }, null);
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			writeLog("initLooseTruststoreSSLFactory: Exception : "+e.toString());
		}
		
	}

	protected void loadSuffixListDb() {
		try {
			if (publicSuffixListFileName.canRead()) {
				try (InputStream in = new FileInputStream(publicSuffixListFileName)) {
					publicSuffixDb = new DNSPublicSuffixDB();
					publicSuffixDb.load(in);
				}
			}
		} catch (IOException e) {
			writeLog(String.format("problem to read %s", publicSuffixListFileName.toString()));
		}
	}

	protected void createPublicSufficListFile() {
		try {
			if (!publicSuffixListFileName.canRead()) {
				try (InputStream in = DNSPublicSuffixDB.class.getResourceAsStream(DNSPublicSuffixDB.RESOURCE_PUBLIC_SUFFIX_LIST);
						OutputStream out = new FileOutputStream(publicSuffixListFileName)) {
					while (true) {
						int c = in.read();
						if (c < 0)
							break;
						out.write(c);
					}
				}
			}
		} catch (IOException e) {
			writeLog(String.format("problem to copy %s", publicSuffixListFileName.toString()));
		}
	}

	public Jhttpp2Server() {
		init();
	}

	public Jhttpp2Server(File configDir, File logDir) {
		configDirectory = configDir;
		logDirectory = logDir;

		init();
	}

	/**
	 * calls init(), sets up the server port and starts for each connection new
	 * Jhttpp2Connection
	 */
	void serve() {

		serverRunning = true;
		SocketAddress localSocketAddress = listen.getLocalSocketAddress();
		writeLog("Server running on " + localSocketAddress + "");
		System.out.println("Server listening on " + localSocketAddress + "");
		try {
			while (serverRunning) {
				Socket client = listen.accept();

				new Jhttpp2HTTPSession(this, client);
			}
		} catch (Exception e) {
			e.printStackTrace();
			writeLog("Exception in Jhttpp2Server.serve(): " + e.toString());
		}
		netstatPollerThread.stop = true;
	}

	public void run() {
		serve();
	}

	public void setErrorMsg(String a) {
		fatalError = true;
		errorMessage = a;
	}

	/**
	 * Tests what method is used with the reqest
	 * 
	 * @return -1 if the server doesn't support the method
	 */
	public int getHttpMethod(String d) {
		if (startsWith(d, "GET") || startsWith(d, "HEAD"))
			return 0;
		if (startsWith(d, "POST") || startsWith(d, "PUT") || startsWith(d, "DELETE"))
			return 1;
		if (startsWith(d, "CONNECT"))
			return 2;
		if (startsWith(d, "OPTIONS"))
			return 3;
		if (startsWith(d, "BCOPY") || startsWith(d, "BDELETE") || startsWith(d, "BMOVE") || startsWith(d, "BPROPFIND")
				|| startsWith(d, "COPY") || startsWith(d, "MOVE") || startsWith(d, "PROPFIND") || startsWith(d, "LOCK")
				|| startsWith(d, "MKCOL") || startsWith(d, "NOTIFY") || startsWith(d, "POLL")
				|| startsWith(d, "PROPPATCH") || startsWith(d, "SEARCH") || startsWith(d, "SUBSCRIBE")
				|| startsWith(d, "UNLOCK") || startsWith(d, "UNSUBSCRIBE") || startsWith(d, "X-MS-ENUMATTS"))
			return 1;

		return -1;/*
					 * No match...
					 * 
					 * Following methods are not implemented: ||
					 * startsWith(d,"TRACE")
					 */
	}

	public boolean startsWith(String a, String what) {
		int l = what.length();
		int l2 = a.length();
		return l2 >= l ? a.substring(0, l).equals(what) : false;
	}

	/**
	 * @return the Server response-header field
	 */
	public String getServerIdentification() {
		return "jHTTPp2/" + getServerVersion();
	}

	public String getServerVersion() {
		return VERSION;
	}

	protected String getProxyPrevPropertyName(int i, String sub) {
		String iStr = i == 0 ? "" : Integer.toString(i + 1);
		String p = String.format("server.http-proxy-prev%s.%s", iStr, sub);
		return p;
	}

	/**
	 * saves all settings with a ObjectOutputStream into a file
	 * 
	 * @since 0.2.10
	 */
	public void saveSettings() {

		Boolean propertiesFileSaved = false;
		Boolean objectFileSaved = false;

		if (serverproperties == null)
			return;

		serverproperties.setProperty("server.http-proxy", new Boolean(useProxy).toString());

		serverproperties.setProperty("server.http-proxy.hostname", proxy != null ? proxy.getHostName() : "");
		serverproperties.setProperty("server.http-proxy.port", new Integer(proxy_port).toString());
		for (int i = 0; i < proxy_prev_cache_list.size(); i++) {
			ProxyAccessCache px = proxy_prev_cache_list.get(i);
			serverproperties.setProperty(getProxyPrevPropertyName(i, "hostname"), px.getProxyHost().getHostName());
			serverproperties.setProperty(getProxyPrevPropertyName(i, "port"), Integer.toString(px.getProxyPort()));
		}

		serverproperties.setProperty("server.http-proxy-prev.timeout", new Integer(proxy_prev_timeout).toString());

		serverproperties.setProperty("server.http-proxy.no_proxy_hosts", getNoProxyHosts());
		serverproperties.setProperty("server.filter.http", new Boolean(filter_http).toString());
		serverproperties.setProperty("server.filter.url", new Boolean(block_urls).toString());
		serverproperties.setProperty("server.filter.http.useragent", httpUserAgent);
		serverproperties.setProperty("server.enable-cookies-by-default",
				new Boolean(enable_cookies_by_default).toString());
		serverproperties.setProperty("server.debug-logging", new Boolean(debug).toString());
		serverproperties.setProperty("server.port", new Integer(port).toString());
		serverproperties.setProperty("server.listen_interface", listen_interface);

		serverproperties.setProperty("server.access.log", new Boolean(log_access).toString());
		serverproperties.setProperty("server.access.log.filename", log_access_filename);
		serverproperties.setProperty("server.webconfig", new Boolean(webconfig).toString());
		serverproperties.setProperty("server.www", new Boolean(www_server).toString());
		serverproperties.setProperty("server.webconfig.username", config_user);
		serverproperties.setProperty("server.webconfig.password", config_password);

		// buffers
		serverproperties.setProperty("server.http-proxy.buffer", Integer.toString(http_proxy_response_buffer_size));
		serverproperties.setProperty("server.www.buffer", Integer.toString(http_file_response_buffer_size));
		serverproperties.setProperty("server.buffer", Integer.toString(http_request_buffer_size));

		try {
			serverproperties.store(new FileOutputStream(serverPropertiesFileName),
					"Jhttpp2Server main properties. Look at the README file for further documentation.");
			propertiesFileSaved = true;
		} catch (IOException IOExceptProperties) {
			writeLog("storeServerProperties(): " + IOExceptProperties.getMessage());
		}

		try {
			ObjectOutputStream file = new ObjectOutputStream(new FileOutputStream(dataFileName));
			file.writeObject(dic);
			file.writeObject(urlactions);
			file.close();
			objectFileSaved = true;
		} catch (IOException IOExceptObjectStream) {
			writeLog("storeServerProperties(): " + IOExceptObjectStream.getMessage());
		}

		if (objectFileSaved && propertiesFileSaved)
			writeLog("Configuration saved successfully");
		else
			writeLog("Failure during saving server properties or object stream");

	}

	/**
	 * restores all Jhttpp2 options from the configuration file
	 * 
	 * @since 0.2.10
	 */
	public void restoreSettings() {
		Boolean propertiesFileLoaded = false;
		Boolean objectFileLoaded = false;

		if (serverproperties == null) {
			serverproperties = new Properties();
			try {
				serverproperties.load(new DataInputStream(new FileInputStream(serverPropertiesFileName)));
				propertiesFileLoaded = true;
			} catch (IOException e) {
				writeLog("getServerProperties(): " + e.getMessage());
			}
		}

		useProxy = new Boolean(serverproperties.getProperty("server.http-proxy", "false")).booleanValue();
		String proxyhostname = serverproperties.getProperty("server.http-proxy.hostname", "127.0.0.1");
		try {
			proxy = InetAddress.getByName(proxyhostname);
		} catch (UnknownHostException e) {
			System.err.printf("PROXY HOST '%s' unknown : %s %n", proxyhostname, e.toString());
		}
		proxy_port = new Integer(serverproperties.getProperty("server.http-proxy.port", "8080")).intValue();
		setNoProxyHosts(serverproperties.getProperty("server.http-proxy.no_proxy_hosts", ""));

		String proxyPrevTimeoutStr = serverproperties.getProperty("server.http-proxy-prev.timeout", "30000");
		if (proxyPrevTimeoutStr != null && !"".equals(proxyPrevTimeoutStr)) {
			proxy_prev_timeout = new Integer(proxyPrevTimeoutStr).intValue();
		}

		for (int i = 0; i < 100; i++) {
			String proxyprevstring = serverproperties.getProperty(getProxyPrevPropertyName(i, "hostname"), "");

			try {
				if (proxyprevstring != null && !"".equals(proxyprevstring)) {
					InetAddress proxy_prev = InetAddress.getByName(proxyprevstring);
					int proxy_prev_port = new Integer(serverproperties.getProperty(getProxyPrevPropertyName(i, "port"), "8080")).intValue();
					File dbProxyPrev = new File(configDirectory, proxy_prev.getHostName() + ";" + proxy_prev_port + ".proxy-access.db");
					File dbInitProxyPrev = new File(configDirectory, proxy_prev.getHostName() + ";" + proxy_prev_port + ".proxy-access.db.init");
					ProxyAccessCache proxy_prev_cache = new ProxyAccessCache(proxy_prev, proxy_prev_port, proxy_prev_timeout, dbProxyPrev, dbInitProxyPrev);
					proxy_prev_cache_list.add(proxy_prev_cache);
				}
			} catch (UnknownHostException e) {
				System.err.printf("PROXY PREV HOST '%s' unknown : %s %n", proxyprevstring, e.toString());
			}
		}

		setNoProxyHosts(serverproperties.getProperty("server.http-proxy.no_proxy_hosts", ""));

		block_urls = new Boolean(serverproperties.getProperty("server.filter.url", "false")).booleanValue();
		httpUserAgent = serverproperties.getProperty("server.filter.http.useragent",
				"Mozilla/4.0 (compatible; MSIE 4.0; WindowsNT 5.0)");

		filter_http = new Boolean(serverproperties.getProperty("server.filter.http", "false")).booleanValue();
		enable_cookies_by_default = new Boolean(
				serverproperties.getProperty("server.enable-cookies-by-default", "true")).booleanValue();
		debug = new Boolean(serverproperties.getProperty("server.debug-logging", "false")).booleanValue();
		port = new Integer(serverproperties.getProperty("server.port", "8088")).intValue();
		listen_interface = serverproperties.getProperty("server.listen_interface");
		if (listen_interface == null) {
			listen_interface = DEFAULT_SERVER_LISTEN_INTERFACE;
		} else {
			listen_interface = listen_interface.trim();
			if ("".equals(listen_interface) || "0".equalsIgnoreCase(listen_interface)) {
				listen_interface = LISTEN_ALL_INTERFACE;
			}
		}

		log_access = new Boolean(serverproperties.getProperty("server.access.log", "true")).booleanValue();
		log_access_filename = serverproperties.getProperty("server.access.log.filename", "access.log");
		webconfig = new Boolean(serverproperties.getProperty("server.webconfig", "false")).booleanValue();
		www_server = new Boolean(serverproperties.getProperty("server.www", "false")).booleanValue();
		config_user = serverproperties.getProperty("server.webconfig.username", "admin");
		// create random password with 16 characters as default value for the
		// web configuration module
		config_password = serverproperties.getProperty("server.webconfig.password", Jhttpp2Utils.randomString(16));
		// buffers
		http_proxy_response_buffer_size = new Integer(serverproperties.getProperty("server.http-proxy.buffer", "65536"))
				.intValue();
		http_file_response_buffer_size = new Integer(serverproperties.getProperty("server.www.buffer", "4096"))
				.intValue();
		http_request_buffer_size = new Integer(serverproperties.getProperty("server.buffer", "65536")).intValue();

		try {

			final File logAccessFile = new File(logDirectory, log_access_filename);
			accessLogFile = new BufferedWriter(new FileWriter(logAccessFile, true));
			// Restore the WildcardDioctionary and the URLActions with the
			// ObjectInputStream (settings.dat)...
			ObjectInputStream objInputStream;
			if (dataFileName.exists()) {
				objInputStream = new ObjectInputStream(new FileInputStream(dataFileName));
				dic = (WildcardDictionary) objInputStream.readObject();
				urlactions = (Vector<OnURLAction>) objInputStream.readObject();
				objInputStream.close();
				// loading successful
				objectFileLoaded = true;
			}

		} catch (Exception exceptObjectInput) {
			setErrorMsg("restoreSettings(): " + exceptObjectInput.getMessage());
		}

		if (!objectFileLoaded || !propertiesFileLoaded) {
			writeLog("Error occured during configuration read, trying to save configuration...");
			saveSettings();
		}

	}

	/**
	 * @return the HTTP version used by jHTTPp2
	 */
	public String getHttpVersion() {
		return HTTP_VERSION;
	}

	/**
	 * the User-Agent header field
	 * 
	 * @since 0.2.17
	 * @return User-Agent String
	 */
	public String getUserAgent() {
		return httpUserAgent;
	}

	public void setUserAgent(String ua) {
		httpUserAgent = ua;
	}

	/**
	 * writes into the server log file and adds a new line
	 * 
	 * @since 0.2.21
	 */
	public void writeLog(String s) {
		writeLog(s, true);
	}

	/**
	 * writes to the server log file
	 * 
	 * @since 0.2.21
	 */
	public void writeLog(String s, boolean new_line) {
		try {
			s = new Date().toString() + " " + s;
			logFile.write(s, 0, s.length());
			if (new_line)
				logFile.newLine();
			logFile.flush();
			if (debug)
				System.out.println(s);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void closeLog() {
		try {
			writeLog("Server shutdown.");
			logFile.flush();
			logFile.close();
			accessLogFile.close();
		} catch (Exception e) {
		}
	}

	public void recordBytesRemote(long download, long upload, String protocol, String remoteHostName, int remotePort, InetAddress proxyHost, int proxyPort) {
		bytesUpload += upload;
		bytesDownload += download;

		try {
			URI u = new URI(protocol, null, remoteHostName, remotePort, null, null, null);

			String proxyUrl = proxyHost != null ? (proxyHost.getHostName() + ":" + proxyPort) : null;
			String prefix = null;
			if (publicSuffixDb != null) {
				prefix=publicSuffixDb.getBaseSuffix(remoteHostName);
			}
			if (log_access) {
				logAccess(String.format("XFER %s %d %d %s %s", u, upload, download, prefix, proxyUrl));
			}
		} catch (URISyntaxException e) {
		}

	}

	public int getServerConnections() {
		return numconnections;
	}

	public long getBytesDownloaded() {
		return bytesDownload;
	}

	public long getBytesUploaded() {
		return bytesUpload;
	}

	public void increaseNumConnections() {
		numconnections++;
	}

	public void decreaseNumConnections() {
		numconnections--;
	}

	public void AuthenticateUser(String u, String p) {
		if (config_user.equals(u) && config_password.equals(p)) {
			config_auth = 1;
		} else
			config_auth = 0;
	}

	public String getGMTString() {
		return new Date().toString();
	}

	public Jhttpp2URLMatch findMatch(String url) {
		return (Jhttpp2URLMatch) dic.get(url);
	}

	public WildcardDictionary getWildcardDictionary() {
		return dic;
	}

	public Vector<OnURLAction> getURLActions() {
		return urlactions;
	}

	public boolean enableCookiesByDefault() {
		return this.enable_cookies_by_default;
	}

	public void enableCookiesByDefault(boolean a) {
		enable_cookies_by_default = a;
	}

	public void resetStat() {
		bytesDownload = 0;
		bytesUpload = 0;
	}

	/**
	 * @since 0.4.10ak
	 */
	public void logAccess(String s) {
		try {
			accessLogFile.write("[" + new Date().toString() + "] " + s + "\r\n");
			accessLogFile.flush();
		} catch (Exception e) {
			writeLog("Jhttpp2Server.access(String): " + e.getMessage());
		}
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void shutdownServer() {
		closeLog();
		System.exit(0);
	}

	public int getMaxRequestHeaderSize() {
		return maxRequestHeaderSize;
	}

	public synchronized void tryIncreaseMaxRequestHeaderSizeTo(int headerSize) {
		if (headerSize > maxRequestHeaderSize)
			maxRequestHeaderSize = headerSize;
	}

	public void setNoProxyHosts(String no_proxy_hosts) {
		this.no_proxy_hosts = no_proxy_hosts;
		this.no_proxy_hosts_pattern = null;
		this.weakMapNeedProxyHosts.clear();
		computeNoProxyHostsPattern();
	}

	public String getNoProxyHosts() {
		return no_proxy_hosts;
	}

	private WeakHashMap<String, Boolean> weakMapNeedProxyHosts = new WeakHashMap<String, Boolean>();

	public boolean isHostNeedProxy(String host) {
		if (!useProxy) {
			return false;
		} else {
			computeNoProxyHostsPattern();
			if (no_proxy_hosts_pattern != null) {
				Boolean isProxyfiedHost = weakMapNeedProxyHosts.get(host);
				if (isProxyfiedHost == null) {
					Matcher m = no_proxy_hosts_pattern.matcher(host.toLowerCase());
					isProxyfiedHost = !m.find();
					if (isProxyfiedHost) {
						// try match ip as noproxy, if can be resolved
						InetAddress ip = null;
						try {
							ip = InetAddress.getByName(host);
						} catch (Exception e) {
							ip = null;
						}
						if (ip != null) {
							String ipString = ip.getHostAddress();
							m = no_proxy_hosts_pattern.matcher(ipString);
							isProxyfiedHost = !m.find();
						} else {
							isProxyfiedHost = true;
						}

					}
					weakMapNeedProxyHosts.put(host, isProxyfiedHost);
				}
				return isProxyfiedHost.booleanValue();
			} else {
				return false;
			}
		}
	}

	protected void computeNoProxyHostsPattern() {
		String nph = getNoProxyHosts();
		if (nph == null) {
			nph = REGEX_PREFIX + "^$";
		}

		if (no_proxy_hosts_pattern == null) {
			Pattern compile = compileHostPattern(nph);
			no_proxy_hosts_pattern = compile;
			no_proxy_hosts = REGEX_PREFIX + compile.pattern();
		}
	}

	public Pattern compileHostPattern(String nph) {
		String pat = nph;
		if (pat.startsWith(REGEX_PREFIX)) {
			pat = pat.substring(REGEX_PREFIX.length());
		} else {
			pat = pat.replaceAll("[|]", "|");
			pat = pat.replaceAll("\\.", "\\\\.");
			pat = pat.replaceAll("\\*", ".*?");
			pat = "^(?i:" + pat + ")$";
		}
		Pattern compile = Pattern.compile(pat);
		return compile;
	}
}