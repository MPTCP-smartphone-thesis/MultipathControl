package be.uclouvain.multipathcontrol.stats;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import be.uclouvain.multipathcontrol.global.Config;

/**
 * The goal of this abstract class is to define a new environment to save data.
 *
 * Simply extends this class and put new data via the editor.
 *
 * The name of the new prefs will be stored in Config.PREFS_STATS_SET_category
 */
public abstract class SaveDataAbstract {
	public static final String PREFS_TIMESTAMP = "timestamp";
	public static final String PREFS_WIFI_MAC = "wifiMac";

	private static String wifiMac = null;
	private static final Lock mutex = new ReentrantLock(true);

	protected Editor editor;
	private String key;
	private long timestamp;

	public SaveDataAbstract(Context context, StatsCategories category) {
		this.timestamp = new Date().getTime();

		String sharedPrefName = Long.toString(timestamp);
		this.editor = context.getSharedPreferences(sharedPrefName,
				Context.MODE_PRIVATE).edit();
		editor.putLong(PREFS_TIMESTAMP, timestamp);
		editor.putString(PREFS_WIFI_MAC, getWiFiMac(context));

		SharedPreferences settings = context.getSharedPreferences(
				Config.PREFS_NAME, Context.MODE_PRIVATE);
		addPrefToList(settings, sharedPrefName, category);
	}

	private static String getWiFiMac(Context context) {
		if (wifiMac != null)
			return wifiMac;
		WifiManager wifiMan = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInf = wifiMan.getConnectionInfo();
		wifiMac = wifiInf.getMacAddress();
		return wifiMac;
	}

	private void addPrefToList(SharedPreferences settings,
			String sharedPrefName, StatsCategories category) {
		key = Config.PREFS_STATS_SET + '_' + category;

		mutex.lock();

		Set<String> statsSet = settings.getStringSet(key, null);
		if (statsSet == null)
			statsSet = new HashSet<String>(1);
		else
			// We need a copy: see doc about SharedPreferences.getStringSet()
			statsSet = new HashSet<String>(statsSet);

		statsSet.add(sharedPrefName);
		settings.edit().putStringSet(key, statsSet).commit();

		mutex.unlock();
	}

	public static void removeFromPrefs(SharedPreferences settings,
			Collection<String> sharedPrefNames, StatsCategories category) {
		String key = Config.PREFS_STATS_SET + '_' + category;

		mutex.lock();

		Set<String> statsSet = settings.getStringSet(key, null);
		if (statsSet != null) { // should not be null...
			statsSet = new HashSet<String>(statsSet);
			statsSet.removeAll(sharedPrefNames);
			settings.edit().putStringSet(key, statsSet).commit();
		}

		mutex.unlock();
	}

	public String getKey() {
		return key;
	}

	public long getTimestamp() {
		return timestamp;
	}

	protected void save() {
		editor.apply();
	}
}
