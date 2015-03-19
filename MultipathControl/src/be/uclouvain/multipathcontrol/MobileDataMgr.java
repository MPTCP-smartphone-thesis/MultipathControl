package be.uclouvain.multipathcontrol;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.util.Log;

public class MobileDataMgr {

	private final Context context;
	/*
	 * We need an existing domain that nobody uses in order to have a existing
	 * route assigned to the cellular connection. The goal is to not disable
	 * Cellular interface when switching to a new Wi-Fi. example.org domain is
	 * reserved by IANA, nobody will want to use it!
	 */
	private static final String DEFAULT_LOOKUP_HOST = "example.org";

	public MobileDataMgr(Context context) {
		this.context = context;
	}

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
	public void setMobileDataActive(boolean mEnabled) {
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

	private static int getAddr(InetAddress inetAddress) {
		byte[] addrBytes;
		int addr;
		addrBytes = inetAddress.getAddress();
		addr = ((addrBytes[3] & 0xff) << 24) | ((addrBytes[2] & 0xff) << 16)
				| ((addrBytes[1] & 0xff) << 8) | (addrBytes[0] & 0xff);
		return addr;
	}

	/**
	 * Transform host name in int value used by
	 * {@link ConnectivityManager.requestRouteToHost} method
	 *
	 * @param hostname
	 * @return -1 if the host doesn't exists, elsewhere its translation to an
	 *         integer
	 */
	private static int lookupHost(String hostname) {
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getByName(hostname);
		} catch (UnknownHostException e) {
			return -1;
		}
		return getAddr(inetAddress);
	}

	/**
	 * Enable mobile connection even when switching to WiFi
	 *
	 * Source: http://stackoverflow.com/a/4756630
	 *
	 * @param address
	 *            the address to enable
	 * @return true for success, else false
	 */
	public boolean keepMobileConnectionAlive() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (null == connectivityManager) {
			Log.d(Manager.TAG,
					"ConnectivityManager is null, cannot try to force a mobile connection");
			return false;
		}

		// create a route for the specified address
		int hostAddress = lookupHost(DEFAULT_LOOKUP_HOST);
		if (-1 == hostAddress) {
			Log.e(Manager.TAG,
					"Wrong host address transformation, result was -1");
			return false;
		}
		// wait some time needed to connection manager for waking up
		try {
			for (int counter = 0; counter < 30; counter++) {
				State checkState = connectivityManager.getNetworkInfo(
						ConnectivityManager.TYPE_MOBILE_HIPRI).getState();
				Log.d(Manager.TAG, "TYPE_MOBILE_HIPRI network state: "
						+ checkState);
				if (0 == checkState.compareTo(State.CONNECTED))
					break;
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			// nothing to do
		}
		boolean resultBool = connectivityManager.requestRouteToHost(
				ConnectivityManager.TYPE_MOBILE_HIPRI, hostAddress);
		Log.d(Manager.TAG, "requestRouteToHost result: " + resultBool);
		if (!resultBool)
			Log.e(Manager.TAG,
					"Wrong requestRouteToHost result: expected true, but was false");

		return resultBool;
	}
}
