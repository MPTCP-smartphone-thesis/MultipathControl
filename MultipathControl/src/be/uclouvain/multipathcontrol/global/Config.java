package be.uclouvain.multipathcontrol.global;

import android.content.Context;
import android.content.SharedPreferences;
import be.uclouvain.multipathcontrol.system.Sysctl;

public class Config {

	public static final String PREFS_NAME = "MultipathControl";
	public static final String PREFS_STATUS = "enableMultiInterfaces";
	public static final String PREFS_DEFAULT_DATA = "defaultData";
	public static final String PREFS_DATA_BACKUP = "dataBackup";
	public static final String PREFS_SAVE_BATTERY = "saveBattery";
	public static final String PREFS_IPV6 = "ipv6";
	public static final String PREFS_TCPCC = "tcpcc";
	public static final String PREFS_STATS_SET = "statsSet";

	public static boolean mEnabled;
	public static boolean defaultRouteData;
	public static boolean dataBackup;
	public static boolean saveBattery;
	public static boolean ipv6;
	public static String tcpcc;

	private Config() {
	}

	public static void getDefaultConfig(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME,
				Context.MODE_PRIVATE);
		mEnabled = settings.getBoolean(PREFS_STATUS, true);
		defaultRouteData = settings.getBoolean(PREFS_DEFAULT_DATA, false);
		dataBackup = settings.getBoolean(PREFS_DATA_BACKUP, false);
		saveBattery = settings.getBoolean(PREFS_SAVE_BATTERY, true);

		ipv6 = settings.getBoolean(PREFS_IPV6, false);
		if (ipv6 != Sysctl.getIPv6())
			Sysctl.setIPv6(ipv6);

		tcpcc = settings.getString(PREFS_TCPCC, "lia");
		if (!tcpcc.equals(Sysctl.getCC()))
			Sysctl.setCC(tcpcc);
	}

	public static void getDynamicConfig() {
		ipv6 = Sysctl.getIPv6();
		tcpcc = Sysctl.getCC();
	}

	public static void saveStatus(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME,
				Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFS_STATUS, mEnabled);
		editor.putBoolean(PREFS_DEFAULT_DATA, defaultRouteData);
		editor.putBoolean(PREFS_DATA_BACKUP, dataBackup);
		editor.putBoolean(PREFS_SAVE_BATTERY, saveBattery);
		editor.putBoolean(PREFS_IPV6, ipv6);
		editor.putString(PREFS_TCPCC, tcpcc);
		editor.apply();
	}
}
