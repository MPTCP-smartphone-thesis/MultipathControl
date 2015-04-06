package be.uclouvain.multipathcontrol.system;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Sysctl {

	private static final String BASE = "/proc/sys";

	private Sysctl() {
	}

	public static String getSysctl(String key) {
		// String line = Cmd.getFirstLine("sysctl " + key); // busybox is needed
		String path = BASE + '/' + key.replace('.', '/');
		BufferedReader br = null;
		String line = null;
		try {
			br = new BufferedReader(new FileReader(path));
			line = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		if (line == null || line.isEmpty())
			return null;
		return line;
	}

	public static boolean setSysctl(String key, String value) {
		int rc = 1;
		try {
			String path = BASE + '/' + key.replace('.', '/');
			rc = Cmd.runAsRoot("echo " + value + " > " + path).exitValue();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return rc == 0; // delayed... getSysctl(key).equals(value);
	}

	public static String[] getAvailableCC() {
		String line = getSysctl("net.ipv4.tcp_available_congestion_control");
		if (line == null)
			return new String[] { "Error" };
		return line.split(" ");
	}

	public static String getCC() {
		return getSysctl("net.ipv4.tcp_congestion_control");
	}

	public static boolean setCC(String value) {
		return setSysctl("net.ipv4.tcp_congestion_control", value);
	}
}
