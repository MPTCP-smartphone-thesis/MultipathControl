package be.uclouvain.multipathcontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class IPRoute {

	private static final String DEFAULT_DATA_IFACE = "rmnet0"; // TODO: will not work when using 2 SIMs cards...
	private static final String DEFAULT_WLAN_IFACE = "wlan0";

	private final MobileDataMgr mobileDataMgr;

	private HashMap<String, Integer> mIntfState;

	public IPRoute(MobileDataMgr mobileDataMgr) {
		mIntfState = new HashMap<String, Integer>();
		this.mobileDataMgr = mobileDataMgr;
		monitorInterfaces();
	}

	private String getDefaultIFace() {
		if (Config.defaultRouteData)
			return DEFAULT_DATA_IFACE;
		else
			return DEFAULT_WLAN_IFACE;
	}

	private int mapIfaceToTable(String ifaceName) {
		return Math.abs(ifaceName.hashCode()) % 32765 + 1;
	}

	private int mapIfaceToTable(NetworkInterface iface) {
		return mapIfaceToTable(iface.getName());
	}

	private int packAddress(InetAddress addr) {
		return ByteBuffer.wrap(addr.getAddress()).getInt();
	}

	private InetAddress unpackAddress(int addr) throws UnknownHostException {
		return InetAddress.getByAddress(new byte[] {
				(byte) ((addr >>> 24) & 0xff), (byte) ((addr >>> 16) & 0xff),
				(byte) ((addr >>> 8) & 0xff), (byte) ((addr) & 0xff) });
	}

	/* Return the value of a system property key */
	private String getSystemProperty(String key) {
		try {
			Class<?> spClass = Class.forName("android.os.SystemProperties");
			Method method = spClass.getDeclaredMethod("get", String.class);
			return (String) method.invoke(spClass, key);
		} catch (Exception e) {
		}
		return null;
	}

	private String getGateway(String ifaceName) {
		/* Unfortunately there is no clean/easy way to do this in Android :-( */
		String gateway = getSystemProperty("net." + ifaceName + ".gw");
		gateway = gateway.isEmpty() ? getSystemProperty("dhcp." + ifaceName
				+ ".gateway") : gateway;
		return gateway;
	}

	private String getGateway(NetworkInterface iface) {
		return getGateway(iface.getName());
	}

	private Process runAsRoot(String cmd) throws Exception {
		Log.d(Manager.TAG, "command: " + cmd);
		return Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
	}

	private void runAsRoot(String[] cmds) throws Exception {
		for (String cmd : cmds) {
			runAsRoot(cmd);
		}
	}

	private InetAddress toSubnet(InetAddress addr, int prefix)
			throws UnknownHostException {
		int address = packAddress(addr);
		int mask = 0xffffffff << (32 - prefix);
		int subnet = address & mask;
		return unpackAddress(subnet);
	}

	private List<Integer> existingRules(int table) {
		Pattern pa = Pattern.compile("^([0-9]+):.* lookup " + table + " $");
		List<Integer> rules = new ArrayList<Integer>();

		try {
			String line;
			Process p = Runtime.getRuntime().exec(
					new String[] { "ip", "rule", "show" });

			BufferedReader in = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			while ((line = in.readLine()) != null) {
				Matcher m = pa.matcher(line);
				if (m.matches())
					rules.add(Integer.parseInt(m.group(1)));
			}
			in.close();
		} catch (IOException e) {
		}
		return rules;
	}

	private void resetRule(NetworkInterface iface) {
		int table = mapIfaceToTable(iface);
		String[] cmds;
		/* Unfortunately ip rule delete table X doesn't work :-( */
		List<Integer> rules = existingRules(table);

		cmds = new String[rules.size() + 1];
		cmds[0] = "ip route flush table " + table;

		for (int i = 1; i < cmds.length; ++i)
			cmds[i] = "ip rule delete prio " + rules.get(i - 1);

		try {
			runAsRoot(cmds);
		} catch (Exception e) {
		}
	}

	private boolean isMobile(String ifaceName) {
		return ifaceName.startsWith("rmnet");
	}

	private boolean isMobile(NetworkInterface iface) {
		return isMobile(iface.getName());
	}

	/* Add Policy routing for interface */
	private void setupRule(NetworkInterface iface, boolean update) {
		int table = mapIfaceToTable(iface);

		if (update)
			resetRule(iface);

		if (iface.getInterfaceAddresses().isEmpty())
			return;

		for (InterfaceAddress intfAddr : iface.getInterfaceAddresses()) {
			InetAddress addr = intfAddr.getAddress();
			int prefix = intfAddr.getNetworkPrefixLength();
			InetAddress subnet;
			String gateway = getGateway(iface);

			if (gateway == null)
				continue;

			try {
				subnet = toSubnet(addr, prefix);
			} catch (UnknownHostException e) {
				continue;
			}

			if (addr.isLinkLocalAddress())
				continue;

			try {
				runAsRoot(new String[] {
						"ip rule add from " + addr.getHostAddress() + " table "
								+ table,
						"ip route add " + subnet.getHostAddress() + "/"
								+ prefix + " dev " + iface.getName()
								+ " scope link table " + table,
						"ip route add default via " + gateway + " dev "
								+ iface.getName() + " table " + table, });
				if (isMobile(iface)) {
					if (Config.defaultRouteData) // TODO: used android tools?
						runAsRoot("ip route change default via " + gateway
								+ " dev " + iface.getName());
					if (Config.dataBackup)
						runAsRoot("ip link set dev " + iface.getName()
								+ " multipath backup");
				}
			} catch (Exception e) {
			}
		}

	}

	public void monitorInterfaces() {
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface
					.getNetworkInterfaces())) {
				int addrs = Config.mEnabled ? iface.getInterfaceAddresses()
						.hashCode()
						: 1;
				String name = iface.getName();

				if (iface.isLoopback())
					continue;

				if (!mIntfState.containsKey(name)) {
					if (addrs != 1) /* hashcode of an empty List is 1 */
						setupRule(iface, false);
					Log.i(Manager.TAG, "New monitor " + name + " addrs: "
							+ addrs);
					mIntfState.put(name, addrs);
					continue;
				}

				if (addrs != mIntfState.get(name)) {
					Log.i(Manager.TAG, "New addrs for " + name + " addrs: "
							+ addrs);
					setupRule(iface, true);
				}

				mIntfState.put(name, addrs);
			}
		} catch (SocketException e) {
		}

		// if WLan iface has been modified, default route will be wrong
		if (Config.defaultRouteData)
			setDefaultRoute();
	}

	public boolean setDefaultRoute() {
		String iface = getDefaultIFace();

		String gateway = getGateway(iface);
		if (gateway == null)
			return false;
		try {
			runAsRoot("ip route change default via " + gateway + " dev "
					+ iface);
			// if there where no default route
			runAsRoot("ip route add default via " + gateway + " dev " + iface);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	public boolean setDataBackup() {
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
