package be.uclouvain.multipathcontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class IPRouteUtils {

	private static final String DEFAULT_DATA_IFACE = "rmnet0"; // TODO: will not work when using 2 SIMs cards...
	private static final String DEFAULT_WLAN_IFACE = "wlan0";
	private static final int ipVersions[] = { 4, 6 };

	public static String getDefaultIFace() {
		if (Config.defaultRouteData)
			return DEFAULT_DATA_IFACE;
		else
			return DEFAULT_WLAN_IFACE;
	}

	public static int mapIfaceToTable(String ifaceName) {
		return Math.abs(ifaceName.hashCode()) % 32765 + 1;
	}

	public static int mapIfaceToTable(NetworkInterface iface) {
		return mapIfaceToTable(iface.getName());
	}

	public static int packAddress(InetAddress addr) {
		return ByteBuffer.wrap(addr.getAddress()).getInt();
	}

	public static InetAddress unpackAddress(int addr)
			throws UnknownHostException {
		return InetAddress.getByAddress(new byte[] {
				(byte) ((addr >>> 24) & 0xff), (byte) ((addr >>> 16) & 0xff),
				(byte) ((addr >>> 8) & 0xff), (byte) ((addr) & 0xff) });
	}

	/* Return the value of a system property key */
	public static String getSystemProperty(String key) {
		try {
			Class<?> spClass = Class.forName("android.os.SystemProperties");
			Method method = spClass.getDeclaredMethod("get", String.class);
			return (String) method.invoke(spClass, key);
		} catch (Exception e) {
		}
		return null;
	}

	public static String getGateway(String ifaceName) {
		/* Unfortunately there is no clean/easy way to do this in Android :-( */
		String gateway = getSystemProperty("net." + ifaceName + ".gw");
		gateway = gateway.isEmpty() ? getSystemProperty("dhcp." + ifaceName
				+ ".gateway") : gateway;
		return gateway;
	}

	public static String getGateway(NetworkInterface iface) {
		return getGateway(iface.getName());
	}

	public static Process runAsRoot(String cmd) throws Exception {
		Log.d(Manager.TAG, "command: " + cmd);
		return Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
	}

	public static void runAsRoot(String[] cmds) throws Exception {
		for (String cmd : cmds) {
			runAsRoot(cmd);
		}
	}

	public static InetAddress toSubnet(InetAddress addr, int prefix)
			throws UnknownHostException {
		int address = packAddress(addr);
		int mask = 0xffffffff << (32 - prefix);
		int subnet = address & mask;
		return unpackAddress(subnet);
	}

	public static List<List<Integer>> existingRules(int table) {
		Pattern pa = Pattern.compile("^([0-9]+):.* lookup " + table + " $");
		// We cannot use an array of lists :-)
		List<List<Integer>> allRules = new ArrayList<List<Integer>>(2);

		try {
			String line;
			for (int i = 0; i < ipVersions.length; i++) {
				List<Integer> rules = new ArrayList<Integer>();
				allRules.add(i, rules);
				Process p = Runtime.getRuntime().exec(
						new String[] { "ip", "-" + ipVersions[i], "rule",
								"show" });
				BufferedReader in = new BufferedReader(new InputStreamReader(
						p.getInputStream()));
				while ((line = in.readLine()) != null) {
					Matcher m = pa.matcher(line);
					if (m.matches())
						rules.add(Integer.parseInt(m.group(1)));
				}
				in.close();
			}
		} catch (IOException e) {
		}
		return allRules;
	}

	public static void resetRule(NetworkInterface iface) {
		int table = mapIfaceToTable(iface);
		String[] cmds;
		/* Unfortunately ip rule delete table X doesn't work :-( */
		List<List<Integer>> allRules = existingRules(table);

		for (int ip = 0; ip < ipVersions.length; ip++) {
			List<Integer> rules = allRules.get(ip);

			cmds = new String[rules.size() + 1];
			cmds[0] = "ip -" + ipVersions[ip] + " route flush table " + table;

			for (int i = 1; i < cmds.length; ++i)
				cmds[i] = "ip -" + ipVersions[ip] + " rule delete prio "
						+ rules.get(i - 1);

			try {
				runAsRoot(cmds);
			} catch (Exception e) {
			}
		}
	}

	public static boolean isMobile(String ifaceName) {
		return ifaceName.startsWith("rmnet");
	}

	public static boolean isMobile(NetworkInterface iface) {
		return isMobile(iface.getName());
	}

	public static boolean isIPv6(String hostAddr) {
		return hostAddr.contains(":");
	}

	public static String getIPVersion(String hostAddr) {
		if (isIPv6(hostAddr))
			return "-6";
		return "-4";
	}

	public static String removeScope(String hostAddr) {
		if (hostAddr == null)
			return null;
		int pos = hostAddr.indexOf("%");
		if (pos == -1)
			return hostAddr;
		return hostAddr.substring(0, pos);
	}

	public static boolean setDefaultRoute(String iface, String gateway,
			boolean withScope) {
		if (gateway == null || gateway.isEmpty())
			return false;
		gateway = removeScope(gateway);
		try {
			runAsRoot("ip " + getIPVersion(gateway)
					+ " route change default via " + gateway + " dev " + iface);
			// if there where no default route
			runAsRoot("ip " + getIPVersion(gateway) + " route add default via "
					+ gateway + " dev " + iface);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public static boolean setDefaultRoute() {
		String iface = getDefaultIFace();

		String gateway = getGateway(iface);
		return setDefaultRoute(iface, gateway, true);
	}

	public static boolean setDataBackup() {
		String status;
		if (Config.dataBackup)
			status = "backup";
		else
			status = "on";

		try {
			runAsRoot("ip link set dev " + DEFAULT_DATA_IFACE + " multipath "
					+ status);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
