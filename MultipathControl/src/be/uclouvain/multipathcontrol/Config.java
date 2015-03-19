package be.uclouvain.multipathcontrol;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {

	public static final String PREFS_NAME = "MultipathControl";
	public static final String PREFS_STATUS = "enableMultiInterfaces";
	public static final String PREFS_DEFAULT_DATA = "defaultData";
	public static final String PREFS_DATA_BACKUP = "dataBackup";
	public static final String PREFS_SAVE_BATTERY = "saveBattery";

	public static boolean mEnabled;
	public static boolean defaultRouteData;
	public static boolean dataBackup;
	public static boolean saveBattery;

	public static void getDefaultConfig(Context context) {
		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		mEnabled = settings.getBoolean(PREFS_STATUS, true);
		defaultRouteData = settings.getBoolean(PREFS_DEFAULT_DATA, false);
		dataBackup = settings.getBoolean(PREFS_DATA_BACKUP, false);
		saveBattery = settings.getBoolean(PREFS_SAVE_BATTERY, false);
	}

	public static void saveStatus(Context context) {
		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFS_STATUS, mEnabled);
		editor.putBoolean(PREFS_DEFAULT_DATA, defaultRouteData);
		editor.putBoolean(PREFS_DATA_BACKUP, dataBackup);
		editor.putBoolean(PREFS_SAVE_BATTERY, saveBattery);
		editor.commit();
	}
}
