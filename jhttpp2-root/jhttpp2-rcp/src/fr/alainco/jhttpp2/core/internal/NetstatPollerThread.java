package fr.alainco.jhttpp2.core.internal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetstatPollerThread implements Runnable {

	private static final String SUBPATTERN_HOST_PORT = "((?:[^:\\s]+)|(?:\\[[a-z0-9:%]+\\])):([\\d]+)";
	private static final String ACTOR_LOCALHOST_PREXIX = "localhost:";
	private static final String ACTOR_PROCESS_PREXIX = "process#";
	public boolean stop = false;
	public static long periodMillis = 3 * 1000L;
	public static long periodListingMillis = 2 * 60 * 1000L;
	public static long inactivityMillis = 6 * 60 * 1000L;

	public String netstatCmd = null;
	public String netstatArgs = "-n -o";
	public String pvCmd = null;
	public String pvArgs = "-o\"%u %f\" -i ";

	public InetAddress listeningAddress;
	public int listeningPort;

	public NetstatPollerThread(ServerSocket listenSocket) {
		listeningAddress = listenSocket.getInetAddress();
		listeningPort = listenSocket.getLocalPort();
	}

	Pattern netstatLinePattern = Pattern.compile("^\\s*(TCP)\\s+"
			+ SUBPATTERN_HOST_PORT + "\\s+" + SUBPATTERN_HOST_PORT
			+ "\\s+(\\w+)\\s+([\\d]+)\\s*$");
	protected Set<String> localInterfaces = new HashSet<String>();
	protected Map<String, String> mapLocalPortToProcess = new TreeMap<String, String>();
	Pattern pvLinePattern = Pattern.compile("^\\s*([^\\s]+)\\s+(.*)$");

	public void initFromProperties() {
		netstatCmd = System.getProperty("exec.netstat");
		pvCmd = System.getProperty("exec.pv");
	}

	public boolean isUsable() {
		if (netstatCmd == null)
			return false;
		return true;
	}

	@Override
	public void run() {
		if (!isUsable())
			return;

		initLocalInterfaces();

		Map<String, Long> previousActorConnectedList = new TreeMap<String, Long>();

		long lastListing = 0;
		while (!stop) {

			long start = System.currentTimeMillis();
			try {
				// System.out.println("---------------------");
				List<NetStatEntry> entryList = getNetStat();

				Map<String, Long> actorConnectedList = new TreeMap<String, Long>();

				for (NetStatEntry entry : entryList) {
					if (localInterfaces.contains(entry.remoteIp)
							&& entry.remotePort.equals(Integer
									.toString(listeningPort))) {
						String guessedProcess = entry.localProcess;
						if (guessedProcess == null) {
							guessedProcess = mapLocalPortToProcess
									.get(entry.localPort);
						}
						String actor;
						if (guessedProcess == null) {
							if (!"TIME_WAIT".equalsIgnoreCase(entry.status)) {
								actor = ACTOR_LOCALHOST_PREXIX
										+ entry.localPort;
								actorConnectedList.put(actor, start);
							}
						} else {
							actor = ACTOR_PROCESS_PREXIX + guessedProcess;
							actorConnectedList.put(actor, start);
						}

					}
					if (entry.localPort.equals(Integer.toString(listeningPort))
							&& !localInterfaces.contains(entry.remoteIp)) {
						String ACTOR_PORT_SEPARATOR = ":";
						InetAddress remoteAddr = InetAddress
								.getByName(entry.remoteIp);
						String remoteCanonicalName = remoteAddr
								.getCanonicalHostName();
						String actor = remoteCanonicalName
								+ ACTOR_PORT_SEPARATOR + entry.remotePort;
						actorConnectedList.put(actor, start);
					}
				}

				boolean changed = false;
				for (String actor : actorConnectedList.keySet()) {
					Long last = previousActorConnectedList.get(actor);
					Long current = actorConnectedList.get(actor);
					if (last == null) {
						System.out.println("new actor connected to  proxy: "
								+ actor);
						changed = true;
					}
					previousActorConnectedList.put(actor, current);
				}
				Set<String> actorRemoved = new HashSet<String>();
				for (String actor : previousActorConnectedList.keySet()) {
					Long last = previousActorConnectedList.get(actor);
					Long current = actorConnectedList.get(actor);
					if (current == null) {
						if (last + inactivityMillis < start) {
							actorRemoved.add(actor);
							System.out
									.println("actor disconnected from proxy: "
											+ actor);
							changed = true;
						}
					}
				}
				for (String actor : actorRemoved) {
					previousActorConnectedList.remove(actor);
				}
				if (changed || lastListing + periodListingMillis < start) {
					showActors(previousActorConnectedList);
					lastListing = start;
				}
			} catch (Exception err) {
				err.printStackTrace();
			}

			long end = System.currentTimeMillis();
			long remain = start + periodMillis - end;
			if (remain > 0) {
				try {
					Thread.sleep(remain);
				} catch (InterruptedException e) {
					// ok
				}
			}
		}
	}

	public void showActors(Map<String, Long> previousActorConnectedList) {

		/*
		 * System.out.print("actors:"); for (String actor :
		 * previousActorConnectedList.keySet()) { System.out.print(" " + actor);
		 * } System.out.println("");
		 */
		System.out.println("actors:");
		for (String actor : previousActorConnectedList.keySet()) {
			if (actor.startsWith(ACTOR_PROCESS_PREXIX)) {
				String process = actor.substring(ACTOR_PROCESS_PREXIX.length());
				if (pvCmd != null) {
					boolean found = false;
					try {
						java.lang.Process p = Runtime.getRuntime().exec(
								pvCmd + " " + pvArgs + " " + process);
						BufferedReader input = new BufferedReader(
								new InputStreamReader(p.getInputStream()));
						String line;
						while ((line = input.readLine()) != null) {
							Matcher m = pvLinePattern.matcher(line);
							if (m.find()) {
								String user = m.group(1);
								String file = m.group(2);
								System.out.println("Process: " + user + " #"
										+ process + " " + file);
								found = true;
							}
						}
					} catch (Exception e) {
						System.err.println("run pv:" + pvCmd + " " + pvArgs
								+ " : " + e);
					}
					if (!found)
						System.out.println("Process: " + actor);
				}
			} else if (actor.startsWith(ACTOR_LOCALHOST_PREXIX)) {
				System.out.println("Local??: " + actor);
			} else {
				System.out.println("Remote : " + actor);

			}
		}
		System.out.println("");
	}

	public List<NetStatEntry> getNetStat() throws IOException {
		String line;
		java.lang.Process p = Runtime.getRuntime().exec(
				netstatCmd + " " + netstatArgs);
		BufferedReader input = new BufferedReader(new InputStreamReader(
				p.getInputStream()));
		List<NetStatEntry> entryList = new ArrayList<NetstatPollerThread.NetStatEntry>();
		while ((line = input.readLine()) != null) {
			Matcher m = netstatLinePattern.matcher(line);
			if (m.find()) {
				int index = 1;
				NetStatEntry entry = new NetStatEntry();

				entry.protocol = m.group(index++);
				entry.localIp = m.group(index++);
				entry.localPort = m.group(index++);
				entry.remoteIp = m.group(index++);
				entry.remotePort = m.group(index++);
				entry.status = m.group(index++);
				entry.localProcess = m.group(index++);
				if ("0".equals(entry.localProcess)) {
					entry.localProcess = null;
				}
				entryList.add(entry);

				localInterfaces.add(entry.localIp);
				if (entry.localProcess != null) {
					mapLocalPortToProcess.put(entry.localPort,
							entry.localProcess);
				}

				/*
				 * System.out.println("process: " + entry.localProcess + " " +
				 * entry.protocol + " " + entry.status + " " + entry.localIp +
				 * ":" + entry.localPort + " <- " + entry.remoteIp + ":" +
				 * entry.remotePort);
				 */

			} else {
				line = null;
			}
		}
		input.close();
		return entryList;
	}

	public void initLocalInterfaces() {
		localInterfaces.add(this.listeningAddress.getHostAddress());

		try {
			localInterfaces.add(InetAddress.getLocalHost().getHostAddress());
		} catch (Exception e) {
		}
		try {
			localInterfaces.add(InetAddress.getByName("localhost")
					.getHostAddress());
		} catch (Exception e) {
		}
		try {
			localInterfaces.add(InetAddress.getByName("127.0.0.1")
					.getHostAddress());
		} catch (Exception e) {
		}
	}

	public static class NetStatEntry {
		String protocol;
		String localIp;
		String localPort;
		String remoteIp;
		String remotePort;
		String status;
		String localProcess;
	}
}
