package be.uclouvain.multipathcontrol;

import java.io.BufferedReader;
import java.io.File;
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

import android.app.Activity;
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
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ToggleButton;

public class Main extends Activity {

	public static final String PREFS_NAME = "MultipathControl";
	private HashMap<String, Integer> mIntfState;
	private static boolean mEnabled;
	private Thread mThread;
	private ToggleButton b;

	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			monitorInterfaces();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		b = (ToggleButton) findViewById(R.id.enable_multiiface);
		mEnabled = settings.getBoolean("enableMultiInterfaces", false);		
		b.setChecked(mEnabled);
		if (mEnabled)
			showNotification();

		initInterfaces();

		mThread = new Thread(new CheckMobileData());
		mThread.start();

		/* mConnReceiver will be called each time a change of connectivity
		 * happen
		 */
		registerReceiver(mConnReceiver, 
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	@Override
	protected void onStop() {
		super.onStop();

		try {
			unregisterReceiver(mConnReceiver);
		} catch (IllegalArgumentException e) {}

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("enableMultiInterfaces", mEnabled);
		editor.commit();
	}

	protected void onDestroy() {
		mThread.interrupt();
		hideNotification();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void enableMultiInterfaces(View v) {
		mEnabled = !mEnabled;
		if (mEnabled) {
			showNotification();
			monitorInterfaces();
		} else
			hideNotification();
	}

	private void showNotification() {
		NotificationManager mNotification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		Intent intent = new Intent(this, Main.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, 0);

		Notification notif = new Notification.Builder(this)
		.setWhen(System.currentTimeMillis())
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(getResources().getString(R.string.notification_title))
		.setContentText(getResources().getString(R.string.notification_text))
		.setContentIntent(pendingIntent)
		.build();

		notif.flags |= Notification.FLAG_NO_CLEAR;

		mNotification.notify(1, notif);
	}

	private void hideNotification() {
		NotificationManager mNotification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNotification.cancelAll();
	}

	private boolean isWifiConnected() {
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnected();
	}

	/* Check whether Mobile Data has been disabled in the System Preferences */
	private boolean isMobileDataEnabled() {
		boolean mobileDataEnabled = false;
		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		try {
			Class<?> cmClass = Class.forName(cm.getClass().getName());
			Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
			method.setAccessible(true);
			mobileDataEnabled = (Boolean)method.invoke(cm);
		} catch (Exception e) {}
		return mobileDataEnabled;
	}

	/* Enable having WiFi and 3G/LTE enabled at the same time */
	private void setMobileDataActive() {
		ConnectivityManager cManager =
				(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
				(byte)((addr >>> 24) & 0xff),
				(byte)((addr >>> 16) & 0xff),
				(byte)((addr >>>  8) & 0xff),
				(byte)((addr       ) & 0xff)
		});
	}

	/* Return the value of a system property key */
	private String getSystemProperty(String key) {
		try {
			Class<?> spClass = Class.forName("android.os.SystemProperties");
			Method method = spClass.getDeclaredMethod("get", String.class);
			return (String) method.invoke(spClass, key);
		} catch (Exception e) {}
		return null;
	}

	private String getGateway(NetworkInterface iface) {
		/* Unfortunately there is no clean/easy way to do this in Android :-( */
		String gateway = getSystemProperty("net." + iface.getName() + ".gw");
		gateway = gateway.isEmpty() ? getSystemProperty("dhcp." + iface.getName() + ".gateway") : gateway; 
		return gateway;
	}

	public boolean checkRoot() {
		return new File("/system/xbin/su").exists();
	}

	public void runAsRoot(String[] cmds) throws Exception {
		for (String cmd : cmds)
			Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
	}

	private InetAddress toSubnet(InetAddress addr, int prefix) throws UnknownHostException {
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
			Process p = Runtime.getRuntime().exec(new String[] {"ip", "rule", "show"});

			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = in.readLine()) != null) {
				Matcher m = pa.matcher(line);
				if (m.matches())
					rules.add(Integer.parseInt(m.group(1)));
			}
			in.close();
		} catch (IOException e) {}
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
		} catch (Exception e) {}
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
						"ip rule add from " + addr.getHostAddress() + " table " + table,
						"ip route add " + subnet.getHostAddress() + "/" + prefix + " dev " + iface.getName() + " scope link table " + table,
						"ip route add default via " + gateway + " dev " + iface.getName() + " table " + table,
				});
			} catch (Exception e) {}
		}

	}

	private void initInterfaces() {
		mIntfState = new HashMap<String, Integer>();
		monitorInterfaces();
	}

	private void monitorInterfaces() {
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				int addrs = mEnabled ? iface.getInterfaceAddresses().hashCode() : 1;
				String name = iface.getName();

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
		} catch (SocketException e) {}
	}

	/* Ensures that the data interface and WiFi are connected at the same time. */
	private class CheckMobileData implements Runnable {
		public void run() {
			while (true) {
				setMobileDataActive();

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
}
