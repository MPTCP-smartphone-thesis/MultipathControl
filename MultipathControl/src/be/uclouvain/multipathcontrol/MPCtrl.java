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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

public class MPCtrl {

	public static final String PREFS_NAME = "MultipathControl";
	public static final String PREFS_STATUS = "enableMultiInterfaces";
	public static final String PREFS_DEFAULT_DATA = "defaultData";
	public static final String PREFS_DATA_BACKUP = "dataBackup";

	private static final String DEFAULT_DATA_IFACE = "rmnet0"; // TODO: will not work when using 2 SIMs cards...
	private static final String DEFAULT_WLAN_IFACE = "wlan0";

	private HashMap<String, Integer> mIntfState;

	private boolean mEnabled;
	private boolean defaultRouteData;
	private boolean dataBackup;

	private Context context;
	private Notifications notif;
	private final Handler handler;
	private static long lastTimeHandler;

	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(Manager.TAG, "BroadcastReceiver");
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			if (pm.isScreenOn())
				handler.post(runnable);
			monitorInterfaces();
		}
	};

	public MPCtrl(Context context) {
		this.context = context;
		Log.i(Manager.TAG, "new MPCtrl");

		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		mEnabled = settings.getBoolean(PREFS_STATUS, true);
		defaultRouteData = settings.getBoolean(PREFS_DEFAULT_DATA, false);
		dataBackup = settings.getBoolean(PREFS_DATA_BACKUP, false);

		initInterfaces();

		handler = new Handler();
		initHandler();

		/*
		 * mConnReceiver will be called each time a change of connectivity
		 * happen
		 */
		context.registerReceiver(mConnReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));

		notif = new Notifications(context);
		if (mEnabled)
			notif.showNotification();
	}

	public void destroy() {
		try {
			context.unregisterReceiver(mConnReceiver);
		} catch (IllegalArgumentException e) {
		}

		Log.i(Manager.TAG, "destroy MPCtrl");

		handler.getLooper().quit();

		notif.hideNotification();

		saveStatus();
	}

	public boolean getEnabled() {
		return mEnabled;
	}

	public boolean setStatus(boolean isChecked) {
		if (isChecked == mEnabled)
			return false;

		Log.i(Manager.TAG, "set new status "
				+ (isChecked ? "enable" : "disable"));
		mEnabled = isChecked;
		saveStatus();

		if (isChecked) {
			notif.showNotification();
			monitorInterfaces();
		} else {
			notif.hideNotification();
		}

		return true;
	}

	public boolean getDefaultData() {
		return defaultRouteData;
	}

	public String getDefaultIFace() {
		if (defaultRouteData)
			return DEFAULT_DATA_IFACE;
		else
			return DEFAULT_WLAN_IFACE;
	}

	public boolean setDefaultData(boolean isChecked) {
		if (isChecked == defaultRouteData)
			return false;
		defaultRouteData = isChecked;
		saveStatus();

		return setDefaultRoute();
	}

	public boolean getDataBackup() {
		return dataBackup;
	}

	public boolean setDataBackup(boolean isChecked) {
		if (isChecked == dataBackup)
			return false;
		dataBackup = isChecked;
		saveStatus();

		String status;
		if (dataBackup)
			status = "backup";
		else
			status = "on";

		try {
			runAsRoot("ip link set dev " + DEFAULT_DATA_IFACE + " multipath "
					+ status);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private void saveStatus() {
		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFS_STATUS, mEnabled);
		editor.putBoolean(PREFS_DEFAULT_DATA, defaultRouteData);
		editor.putBoolean(PREFS_DATA_BACKUP, dataBackup);
		editor.commit();
	}

	// Will not be executed in deep sleep, nice, no need to use both connections
	// in deep-sleep
	private void initHandler() {
		lastTimeHandler = System.currentTimeMillis();

		// First check
		handler.post(runnable);
	}

	/*
	 * Ensures that the data interface and WiFi are connected at the same time.
	 */
	private Runnable runnable = new Runnable() {
		final long fiveSecondsMs = 5 * 1000;
		@Override
		public void run() {
			long nowTime = System.currentTimeMillis();
			// do not try keep mobile data active in deep sleep mode
			if (mEnabled && nowTime - lastTimeHandler < fiveSecondsMs * 2)
				setMobileDataActive(); // to not disable cellular iface
			lastTimeHandler = nowTime;
			handler.postDelayed(this, fiveSecondsMs);
		}
	};

	private boolean isWifiConnected() {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnectedOrConnecting();
	}

	/* Check whether Mobile Data has been disabled in the System Preferences */
	private boolean isMobileDataEnabled() {
		boolean mobileDataEnabled = false;
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		try {
			Class<?> cmClass = Class.forName(cm.getClass().getName());
			Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
			method.setAccessible(true);
			mobileDataEnabled = (Boolean) method.invoke(cm);
		} catch (Exception e) {
		}
		return mobileDataEnabled;
	}

	/* Enable having WiFi and 3G/LTE enabled at the same time */
	private void setMobileDataActive() {
		Log.d(Manager.TAG, "setMobileDataActive " + new Date());
		ConnectivityManager cManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (isMobileDataEnabled() && isWifiConnected() && mEnabled)
			cManager.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
					"enableHIPRI");
		else
			cManager.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
					"enableHIPRI");
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
					if (defaultRouteData)
						runAsRoot("ip route change default via " + gateway
								+ " dev " + iface.getName());
					if (dataBackup)
						runAsRoot("ip link set dev " + iface.getName()
								+ " multipath backup");
				}
			} catch (Exception e) {
			}
		}

	}

	private void initInterfaces() {
		mIntfState = new HashMap<String, Integer>();
		monitorInterfaces();
	}

	private void monitorInterfaces() {
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface
					.getNetworkInterfaces())) {
				int addrs = mEnabled ? iface.getInterfaceAddresses().hashCode()
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
		if (defaultRouteData)
			setDefaultRoute();
	}

	private boolean setDefaultRoute() {
		String iface = getDefaultIFace();

		String gateway = getGateway(iface);
		if (gateway == null)
			return false;
		try {
			runAsRoot("ip route change default via " + gateway + " dev "
					+ iface);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}




	}
}
