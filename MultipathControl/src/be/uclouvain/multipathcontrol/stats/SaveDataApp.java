package be.uclouvain.multipathcontrol.stats;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import be.uclouvain.multipathcontrol.global.Config;

public class SaveDataApp extends SaveDataAbstract {

	public static final String PREFS_VERSION_NAME = "versionName";
	public static final String PREFS_VERSION_CODE = "versionCode";
	public static final String PREFS_LAST_UPDATE  = "lastUpdate";
	public static final String PREFS_STATUS       = "enable";
	public static final String PREFS_DEFAULT_DATA = "defRouteCell";
	public static final String PREFS_DATA_BACKUP  = "cellBackup";
	public static final String PREFS_SAVE_BATTERY = "saveBattery";
	public static final String PREFS_IPV6         = "ipv6";
	public static final String PREFS_TCPCC        = "TCPCCAlgo";

	private static PackageInfo info = null;

	public SaveDataApp(Context context) {
		super(context, StatsCategories.STARTUP);

		setVersion(context);
		setPref();

		save();
	}

	private static synchronized PackageInfo getInfo(Context context) {
		if (info == null) {
			try {
				info = context.getPackageManager().getPackageInfo(
						context.getPackageName(), 0);
			} catch (NameNotFoundException e) {
				// should not happen... our package
				e.printStackTrace();
				return null;
			}
		}
		return info;
	}

	private void setVersion(Context context) {
		if (getInfo(context) == null)
			return;

		editor.putString(PREFS_VERSION_NAME, info.versionName);
		editor.putInt(PREFS_VERSION_CODE, info.versionCode);
		editor.putLong(PREFS_LAST_UPDATE, info.lastUpdateTime);
	}

	private void setPref() {
		editor.putBoolean(PREFS_STATUS, Config.mEnabled);
		editor.putBoolean(PREFS_DEFAULT_DATA, Config.defaultRouteData);
		editor.putBoolean(PREFS_DATA_BACKUP, Config.dataBackup);
		editor.putBoolean(PREFS_SAVE_BATTERY, Config.saveBattery);
		editor.putBoolean(PREFS_IPV6, Config.ipv6);
		editor.putString(PREFS_TCPCC, Config.tcpcc);
	}

}
