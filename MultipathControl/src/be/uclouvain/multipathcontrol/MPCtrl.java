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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class MPCtrl {

	public static final String PREFS_NAME = "MultipathControl";
	public static final String PREFS_STATUS = "enableMultiInterfaces";
	private HashMap<String, Integer> mIntfState;
	private boolean mEnabled;
	private Thread mThread;
	private Context context;

	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(Manager.TAG, "BroadcastReceiver");
			setMobileDataActive(); // disable/enable
			monitorInterfaces();
		}
	};

	public MPCtrl(Context context) {
		this.context = context;
		Log.i(Manager.TAG, "new MPCtrl");

		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		mEnabled = settings.getBoolean(PREFS_STATUS, true);

		initInterfaces();

		mThread = new Thread(new CheckMobileData());
		mThread.start();

		/*
		 * mConnReceiver will be called each time a change of connectivity
		 * happen
		 */
		context.registerReceiver(mConnReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));

		if (mEnabled)
			showNotification();
	}

	public void destroy() {
		try {
			context.unregisterReceiver(mConnReceiver);
		} catch (IllegalArgumentException e) {
		}

		Log.i(Manager.TAG, "destroy MPCtrl");

		mThread.interrupt();

		hideNotification();

		saveStatus();
	}

	public boolean getEnabled() {
		return mEnabled;
	}

	public void setStatus(boolean isChecked) {
		if (isChecked == mEnabled)
			return;

		Log.i(Manager.TAG, "set new status "
				+ (isChecked ? "enable" : "disable"));
		mEnabled = isChecked;
		// TODO: context.unregisterReceiver(mConnReceiver);

		if (isChecked) {
			showNotification();
			monitorInterfaces();
		} else {
			hideNotification();
		}

		saveStatus();
	}

	private void saveStatus() {
		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFS_STATUS, mEnabled);
		editor.commit();
	}

	private boolean isWifiConnected() {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnected();
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
		Log.v(Manager.TAG, "setMobileDataActive");
		ConnectivityManager cManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (isMobileDataEnabled() && isWifiConnected() && mEnabled)
			cManager.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
					"enableHIPRI");
		else
			cManager.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
					"enableHIPRI");
	}

	private int mapIfaceToTable(NetworkInterface iface) {
		return Math.abs(iface.getName().hashCode()) % 32765 + 1;
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

	private String getGateway(NetworkInterface iface) {
		/* Unfortunately there is no clean/easy way to do this in Android :-( */
		String gateway = getSystemProperty("net." + iface.getName() + ".gw");
		gateway = gateway.isEmpty() ? getSystemProperty("dhcp."
				+ iface.getName() + ".gateway") : gateway;
		return gateway;
	}

	private void runAsRoot(String[] cmds) throws Exception {
		for (String cmd : cmds) {
			Log.d("mpctrl", "command: " + cmd);
			Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
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
				Log.i(Manager.TAG, "monitor " + name);

				if (iface.isLoopback())
					continue;

				if (!mIntfState.containsKey(name)) {
					if (addrs != 1) /* hashcode of an empty List is 1 */
						setupRule(iface, false);
					mIntfState.put(name, addrs);
					continue;
				}

				if (addrs != mIntfState.get(name))
					setupRule(iface, true);

				mIntfState.put(name, addrs);
			}
		} catch (SocketException e) {
		}
	}

	/* Ensures that the data interface and WiFi are connected at the same time. */
	private class CheckMobileData implements Runnable {
		public void run() {
			while (true) {
				if (mEnabled)
					setMobileDataActive(); // to not disable cellular iface

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	private void showNotification() {
		NotificationManager mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent intent = new Intent(context, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 1,
				intent, 0);

		Notification notif = new Notification.Builder(context)
				.setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(
						context.getResources().getString(
								R.string.notification_title))
				.setContentText(
						context.getResources().getString(
								R.string.notification_text))
				.setContentIntent(pendingIntent).build();

		notif.flags |= Notification.FLAG_NO_CLEAR;

		mNotification.notify(1, notif);
	}

	private void hideNotification() {
		NotificationManager mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotification.cancelAll();
	}
}
