package be.uclouvain.multipathcontrol;


public class Sysctl {

	private Sysctl() {
	}

	public static String getSysctl(String key) {
		String line = Cmd.getFirstLine("sysctl " + key);
		if (line == null || line.isEmpty())
			return null;
		return line.substring(key.length() + 3);
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
		try {
			Cmd.runAsRoot("sysctl -w net.ipv4.tcp_congestion_control="
					+ value);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true; // delayed... getCC().equals(value);
	}
}
