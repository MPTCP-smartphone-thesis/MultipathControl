/*
 * This file is part of MultipathControl.
 *
 * Copyright 2012 UCLouvain - Gregory Detal <first.last@uclouvain.be>
 * Copyright 2015 UCLouvain - Matthieu Baerts <first.last@student.uclouvain.be>
 *
 * MultipathControl is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * Thanks to Carmen Alvarez for his Network-Monitor project!
 * https://github.com/caarmen/network-monitor/
 */

package be.uclouvain.multipathcontrol.stats;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.system.Cmd;
import be.uclouvain.multipathcontrol.system.IPRouteUtils;

public class SaveDataHandover extends SaveDataAbstract {

	public static final String PREFS_AIRPLANE          = "airplane";
	public static final String PREFS_CELL_BER          = "cellBer";
	public static final String PREFS_CELL_SIGNAL_4     = "cellSignal4";
	public static final String PREFS_CELL_SIGNAL_DBM   = "cellSignaldBm";
	public static final String PREFS_DATA_ACTIVITY     = "dataActivity";
	public static final String PREFS_DATA_STATE        = "dataState";
	public static final String PREFS_CELL_TYPE         = "cellType";
	public static final String PREFS_EXT_IP            = "extIp";
	public static final String PREFS_GSM_CELL_LAC      = "gsmCellLac";
	public static final String PREFS_GSM_FULL_CELL_ID  = "gsmFullCellId";
	public static final String PREFS_GSM_RNC           = "gsmRNC";
	public static final String PREFS_GSM_SHORT_CELL_ID = "gsmShortCellId";
	public static final String PREFS_IFACES            = "ifaces";
	public static final String PREFS_IP_WIFI_V4        = "ipWifi4";
	public static final String PREFS_IP_WIFI_V6        = "ipWifi6";
	public static final String PREFS_IP_RMNET_V4       = "ipRMNet4";
	public static final String PREFS_IP_RMNET_V6       = "ipRMNet6";
	public static final String PREFS_NETSTAT           = "netstat";
	public static final String PREFS_NETWORK_AVAILABLE = "netAvailable";
	public static final String PREFS_NETWORK_CONNECTED = "netConnected";
	public static final String PREFS_NETWORK_DSTATE    = "netDState";
	public static final String PREFS_NETWORK_EXTRAS    = "netExtras";
	public static final String PREFS_NETWORK_FAILOVER  = "netFailover";
	public static final String PREFS_NETWORK_REASON    = "netReason";
	public static final String PREFS_NETWORK_ROAMING   = "netRoaming";
	public static final String PREFS_NETWORK_TYPE      = "netType";
	public static final String PREFS_POS_ACCURACY      = "posAccuracy";
	public static final String PREFS_POS_LATITUDE      = "posLatitude";
	public static final String PREFS_POS_LONGITUDE     = "posLongitude";
	public static final String PREFS_POS_SPEED         = "posSpeed";
	public static final String PREFS_PROC_MPTCP        = "procMPTCP";
	public static final String PREFS_PROC_MPTCP_FM     = "procMPTCPFM";
	public static final String PREFS_SIM_OPERATOR      = "simOperator";
	public static final String PREFS_SIM_STATE         = "simState";
	public static final String PREFS_TRACKING          = "tracking";
	public static final String PREFS_WIFI_BSSID        = "wifiBSSID";
	public static final String PREFS_WIFI_FREQ         = "wifiFreq";
	public static final String PREFS_WIFI_SIGNAL_4     = "wifiSignal4";
	public static final String PREFS_WIFI_SIGNAL_RSSI  = "wifiSignalRSSI";
	public static final String PREFS_WIFI_SPEED        = "wifiSpeed";
	public static final String PREFS_WIFI_SSID         = "wifiSSID";
	public static final String PREFS_WIFI_STATE        = "wifiState";

	private static ConnectivityManager connectivityManager = null;
	private static TelephonyManager telephonyManager = null;
	private static WifiManager wifiManager = null;

	private static PhoneState phoneState = null;
	private static LocationState locationState = null;

	private static synchronized void getStaticVarsSync(Context context) {
		if (connectivityManager == null)
			connectivityManager = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (telephonyManager == null)
			telephonyManager = (TelephonyManager) context
					.getSystemService(Context.TELEPHONY_SERVICE);
		if (wifiManager == null)
			wifiManager = (WifiManager) context
					.getSystemService(Context.WIFI_SERVICE);

		if (phoneState == null)
			phoneState = PhoneState.getInstance(telephonyManager);
		if (locationState == null)
			locationState = LocationState.getInstance(context,
					Config.savePowerGPS);
	}

	private final boolean trackingSec;

	public SaveDataHandover(Context context, boolean trackingSec) {
		super(context, StatsCategories.HANDOVER);

		this.trackingSec = trackingSec;
		editor.putBoolean(PREFS_TRACKING, trackingSec);

		getStaticVarsSync(context);

		fromNetAsync();

		fromConnectivityManager(context);
		fromTelephonyManager(context);
		fromWifiManager(context);

		fromPhoneState();
		fromLocationState();

		fromNetworkInterface();
		fromSystem();

		fromSettings(context);

		save();
	}

	public SaveDataHandover(Context context) {
		this(context, false);
	}

	public static void savePowerGPS(boolean savePowerGPS) {
		if (locationState != null)
			locationState.setPriority(savePowerGPS);
	}

	// Src: https://github.com/caarmen/network-monitor/blob/master/networkmonitor/src/main/java/ca/rmen/android/networkmonitor/app/service/datasources/ActiveNetworkInfoDataSource.java
	private void fromConnectivityManager(Context context) {
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		if (activeNetworkInfo == null)
			return;

		String networkType = activeNetworkInfo.getTypeName();
		String networkSubType = activeNetworkInfo.getSubtypeName();
		if (networkSubType != null && !networkSubType.isEmpty())
			networkType += "/" + networkSubType;

		editor.putString(PREFS_NETWORK_TYPE, networkType);
		editor.putBoolean(PREFS_NETWORK_ROAMING, activeNetworkInfo.isRoaming());
		editor.putBoolean(PREFS_NETWORK_AVAILABLE, activeNetworkInfo.isAvailable());
		editor.putBoolean(PREFS_NETWORK_CONNECTED, activeNetworkInfo.isConnected());
		editor.putBoolean(PREFS_NETWORK_FAILOVER, activeNetworkInfo.isFailover());
		editor.putString(PREFS_NETWORK_DSTATE, activeNetworkInfo.getDetailedState().toString());
		String reason = activeNetworkInfo.getReason();
		if (reason != null && !reason.isEmpty())
			editor.putString(PREFS_NETWORK_REASON, reason);
		editor.putString(PREFS_NETWORK_EXTRAS, activeNetworkInfo.getExtraInfo());
	}

	// Src:
	// https://github.com/caarmen/network-monitor/blob/master/networkmonitor/src/main/java/ca/rmen/android/networkmonitor/app/service/datasources/CellLocationDataSource.java
	private void fromTelephonyManager(Context context) {
		// GSM Cell location
		CellLocation cellLocation = telephonyManager.getCellLocation();
		if (cellLocation instanceof GsmCellLocation) {
			GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
			int cid = gsmCellLocation.getCid();
			// The javadoc says the cell id should be less than FFFF, but this
			// isn't always so. We'll report both the full cell id returned by
			// Android, and the truncated one (taking only the last 2 bytes).
			int shortCid = cid > 0 ? cid & 0xFFFF : cid;
			int rnc = cid > 0 ? cid >> 16 & 0xFFFF : 0;
			editor.putInt(PREFS_GSM_FULL_CELL_ID, cid);
			if (rnc > 0)
				editor.putInt(PREFS_GSM_RNC, rnc);
			editor.putInt(PREFS_GSM_SHORT_CELL_ID, shortCid);
			editor.putInt(PREFS_GSM_CELL_LAC, gsmCellLocation.getLac());
		}
		editor.putString(PREFS_SIM_OPERATOR,
				telephonyManager.getNetworkOperatorName());
	}

	// Src:
	// https://github.com/caarmen/network-monitor/blob/master/networkmonitor/src/main/java/ca/rmen/android/networkmonitor/app/service/datasources/WiFiDataSource.java
	private void fromWifiManager(Context context) {
		WifiInfo connectionInfo = wifiManager.getConnectionInfo();
		if (connectionInfo == null || connectionInfo.getNetworkId() < 0)
			return;

		editor.putString(PREFS_WIFI_SSID, connectionInfo.getSSID());
		String bssid = connectionInfo.getBSSID();
		editor.putString(PREFS_WIFI_BSSID, bssid);
		int signalLevel = WifiManager.calculateSignalLevel(
				connectionInfo.getRssi(), 5);
		editor.putInt(PREFS_WIFI_SIGNAL_4, signalLevel);
		editor.putInt(PREFS_WIFI_SIGNAL_RSSI, connectionInfo.getRssi());
		editor.putInt(PREFS_WIFI_SPEED, connectionInfo.getLinkSpeed());
		editor.putString(PREFS_WIFI_STATE, getWifiState());

		List<ScanResult> scanResults = wifiManager.getScanResults();
		if (scanResults != null) {
			for (ScanResult scanResult : scanResults) {
				if (scanResult.BSSID != null && scanResult.BSSID.equals(bssid)) {
					editor.putInt(PREFS_WIFI_FREQ, scanResult.frequency);
					break;
				}
			}
		}
	}

	private String getWifiState() {
		int state = wifiManager.getWifiState();
		switch (state) {
		case WifiManager.WIFI_STATE_DISABLED:
			return "Disabled";
		case WifiManager.WIFI_STATE_DISABLING:
			return "Disabling";
		case WifiManager.WIFI_STATE_ENABLED:
			return "Enabled";
		case WifiManager.WIFI_STATE_ENABLING:
			return "Enabling";
		case WifiManager.WIFI_STATE_UNKNOWN:
			return "Unknown";
		}
		return "Error";
	}

	private void fromPhoneState() {
		editor.putString(PREFS_CELL_TYPE, phoneState.getNetworkType());
		editor.putString(PREFS_SIM_STATE, phoneState.getSimState());
		editor.putString(PREFS_DATA_STATE, phoneState.getDataState());
		editor.putString(PREFS_DATA_ACTIVITY, phoneState.getDataActivity());

		editor.putInt(PREFS_CELL_SIGNAL_4, phoneState.getLastSignalStrength());
		int dBm = phoneState.getLastSignalStrengthDbm();
		if (dBm != 0)
			editor.putInt(PREFS_CELL_SIGNAL_DBM, dBm);
		int lastBer = phoneState.getLastBer();
		if (lastBer >= 0 && lastBer <= 7 || lastBer == 99)
			editor.putInt(PREFS_CELL_BER, lastBer);
	}

	private void fromLocationState() {
		Location lastLocation = locationState.getLastLocation();
		if (lastLocation == null)
			return;

		editor.putFloat(PREFS_POS_LATITUDE, (float) lastLocation.getLatitude());
		editor.putFloat(PREFS_POS_LONGITUDE,
				(float) lastLocation.getLongitude());
		editor.putFloat(PREFS_POS_ACCURACY, lastLocation.getAccuracy());
		editor.putFloat(PREFS_POS_SPEED, lastLocation.getSpeed());
	}

	// Src: https://github.com/caarmen/network-monitor/blob/master/networkmonitor/src/main/java/ca/rmen/android/networkmonitor/app/service/datasources/NetworkInterfaceDataSource.java
	private void fromNetworkInterface() {

		List<NetworkInterface> activeIfaces = IPRouteUtils.getActiveIfaces();
		if (activeIfaces == null || activeIfaces.isEmpty())
			return;

		StringBuffer ifacesNames = new StringBuffer();
		StringBuffer ipv4WiFi = new StringBuffer();
		StringBuffer ipv4RMNet = new StringBuffer();
		StringBuffer ipv6WiFi = new StringBuffer();
		StringBuffer ipv6RMNet = new StringBuffer();

		for (NetworkInterface networkInterface : activeIfaces) {
			String ifaceName = networkInterface.getName();
			ifacesNames.append(";" + ifaceName);
			boolean isMobile = IPRouteUtils.isMobile(ifaceName);
			Enumeration<InetAddress> inetAddresses = networkInterface
					.getInetAddresses();
			while (inetAddresses.hasMoreElements()) {
				InetAddress inetAddress = inetAddresses.nextElement();
				StringBuffer ip = null;
				if (inetAddress instanceof Inet4Address)
					ip = isMobile ? ipv4RMNet : ipv4WiFi;
				else if (!inetAddress.isLinkLocalAddress())
					ip = isMobile ? ipv6RMNet : ipv6WiFi;
				if (ip != null)
					ip.append(";" + inetAddress.getHostAddress());
			}
		}

		if (ifacesNames.length() > 0)
			editor.putString(PREFS_IFACES, ifacesNames.substring(1));
		if (ipv4WiFi.length() > 0)
			editor.putString(PREFS_IP_WIFI_V4, ipv4WiFi.substring(1));
		if (ipv4RMNet.length() > 0)
			editor.putString(PREFS_IP_RMNET_V4, ipv4RMNet.substring(1));
		if (ipv6WiFi.length() > 0)
			editor.putString(PREFS_IP_WIFI_V6, ipv6WiFi.substring(1));
		if (ipv6RMNet.length() > 0)
			editor.putString(PREFS_IP_RMNET_V6, ipv6RMNet.substring(1));
	}

	private void fromSystem() {
		if (trackingSec)
			return;
		editor.putString(PREFS_NETSTAT, Cmd.getAllLinesString("netstat", ';')
				.replaceAll("\\s+", " "));
		editor.putString(PREFS_PROC_MPTCP,
				getFileContent("/proc/net/mptcp", ';').replaceAll("\\s+", " "));
		editor.putString(PREFS_PROC_MPTCP_FM,
				getFileContent("/proc/net/mptcp_fullmesh", ';'));
	}

	private static String getFileContent(String path, char sep) {
		StringBuffer sBuffer = new StringBuffer();
		BufferedReader bReader = null;
		String line = null;
		try {
			bReader = new BufferedReader(new FileReader(path));
			while ((line = bReader.readLine()) != null) {
				sBuffer.append(line);
				sBuffer.append(sep);
			}
		} catch (IOException e) {
			Log.e(Manager.TAG,
					"getFileContent(" + path + "): " + e.getMessage());
		} finally {
			try {
				if (bReader != null)
					bReader.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return sBuffer.toString();
	}

	private void fromNetAsync() {
		new GetIPTask(editor).execute("myip");
	}

	private void fromSettings(Context context) {
		if (trackingSec)
			return;
		try {
			editor.putBoolean(PREFS_AIRPLANE, Settings.Global.getInt(
					context.getContentResolver(),
					Settings.Global.AIRPLANE_MODE_ON) == 1);
		} catch (SettingNotFoundException e) {
		}
	}
}
