package fr.alainco.jhttpp2.console;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.File;
import java.util.Arrays;

import fr.alainco.jhttpp2.core.internal.Jhttpp2Server;

/**
 * Title: jHTTPp2: Java HTTP Filter Proxy Description: starts proxy server
 * Copyright: Copyright (c) 2001-2011
 * 
 * @author Benjamin Kohl
 */

public class Jhttpp2Launcher {

	public static final int RETCODE_OK = 0;
	public static final int RETCODE_RUN = 1;
	public static final int RETCODE_USAGE = 2;
	static Jhttpp2Server server;

	public static void main(String[] args) {
		int ret = runWithArgs(args);
		if (ret != fr.alainco.jhttpp2.console.Jhttpp2Launcher.RETCODE_OK) {
			System.exit(ret);
		}
	}

	public static int runWithArgs(String[] args) {
		printUsage();

		System.out.printf("arguments: %s%n", Arrays.asList(args).toString());

		File currentDirectory = new File(".").getAbsoluteFile();
		System.out.printf("Current directory: %s%n", currentDirectory.toString());
		if (args.length == 0) {
			server = new Jhttpp2Server(currentDirectory, currentDirectory);
		} else if (args.length >= 1) {
			File configDir = new File(args[0]);
			if (!configDir.isDirectory()) {
				System.out.printf("Config directory: %s is not a directory\n", configDir);
				server = null;
			}
			System.out.printf("Config directory: %s \n", configDir);
			if (args.length >= 2) {
				File logDir = new File(args[1]);
				if (!logDir.isDirectory()) {
					System.out.printf("Log directory: %s is not a directory\n", logDir);
					server = null;
				} else {
					if (args.length >= 3) {
						server = null;
						System.out.printf("Too many parameters: \n", Arrays.asList(args));
					} else {

						System.out.printf("Log directory: %s \n", logDir);
						server = new Jhttpp2Server(configDir, logDir);
					}
				}
			} else {
				server = new Jhttpp2Server(configDir, currentDirectory);
			}
		}
		if (server == null) {
			System.out.println("server not created");
			printUsage();
			return RETCODE_USAGE;
		} else if (server.fatalError) {
			System.out.println("Error: " + server.getErrorMessage());
			printUsage();
			return RETCODE_RUN;
		} else {
			new Thread(server).start();
			System.out.printf("Running on port %d interface: %s %n", server.port, server.listen_interface);
			return RETCODE_OK;
		}
	}

	public static void printUsage() {
		System.out.println();
		System.out.println("Usage: Jhttpp2Launcher ");
		System.out.println("Usage: Jhttpp2Launcher <configdirectory>");
		System.out.println("Usage: Jhttpp2Launcher <configdirectory> <logDirectory>");
		System.out.println();
	}
}