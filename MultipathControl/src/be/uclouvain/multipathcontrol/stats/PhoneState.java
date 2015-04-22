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
 */

package be.uclouvain.multipathcontrol.stats;

import java.lang.reflect.Method;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

public class PhoneState {

	private static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN = 0;

	private static final int SIGNAL_STRENGTH_POOR = 1;
	private static final int SIGNAL_STRENGTH_MODERATE = 2;
	private static final int SIGNAL_STRENGTH_GOOD = 3;
	private static final int SIGNAL_STRENGTH_GREAT = 4;

	private static final int GSM_SIGNAL_STRENGTH_GREAT = 12;
	private static final int GSM_SIGNAL_STRENGTH_GOOD = 8;
	private static final int GSM_SIGNAL_STRENGTH_MODERATE = 8; // = good?

	private TelephonyManager telephonyManager;

	private int lastSignalStrength;
	private int lastSignalStrengthDbm;
	private int lastBer;

	private static PhoneState instance = null;

	public static PhoneState getInstance(TelephonyManager telephonyManager) {
		if (instance == null)
			instance = new PhoneState(telephonyManager);
		return instance;
	}

	private PhoneState(TelephonyManager telephonyManager) {
		this.telephonyManager = telephonyManager;
		telephonyManager.listen(phoneStateListener,
				PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
						| PhoneStateListener.LISTEN_SERVICE_STATE);
	}

	public int getLastSignalStrength() {
		return lastSignalStrength;
	}

	public int getLastSignalStrengthDbm() {
		return lastSignalStrengthDbm;
	}

	public int getLastBer() {
		return lastBer;
	}

	// src: http://stackoverflow.com/a/19965362
	public String getNetworkType() {
		int networkType = telephonyManager.getNetworkType();
		switch (networkType) {
		case TelephonyManager.NETWORK_TYPE_1xRTT:
			return "1xRTT";
		case TelephonyManager.NETWORK_TYPE_CDMA:
			return "CDMA";
		case TelephonyManager.NETWORK_TYPE_EDGE:
			return "EDGE";
		case TelephonyManager.NETWORK_TYPE_EHRPD:
			return "eHRPD";
		case TelephonyManager.NETWORK_TYPE_EVDO_0:
			return "EVDO rev. 0";
		case TelephonyManager.NETWORK_TYPE_EVDO_A:
			return "EVDO rev. A";
		case TelephonyManager.NETWORK_TYPE_EVDO_B:
			return "EVDO rev. B";
		case TelephonyManager.NETWORK_TYPE_GPRS:
			return "GPRS";
		case TelephonyManager.NETWORK_TYPE_HSDPA:
			return "HSDPA";
		case TelephonyManager.NETWORK_TYPE_HSPA:
			return "HSPA";
		case TelephonyManager.NETWORK_TYPE_HSPAP:
			return "HSPA+";
		case TelephonyManager.NETWORK_TYPE_HSUPA:
			return "HSUPA";
		case TelephonyManager.NETWORK_TYPE_IDEN:
			return "iDen";
		case TelephonyManager.NETWORK_TYPE_LTE:
			return "LTE";
		case TelephonyManager.NETWORK_TYPE_UMTS:
			return "UMTS";
		case TelephonyManager.NETWORK_TYPE_UNKNOWN:
			return "Unknown";
		}
		return "Error";
	}

	public String getSimState() {
		int simState = telephonyManager.getSimState();
		switch (simState) {
		case TelephonyManager.SIM_STATE_ABSENT:
			return "Absent";
		case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
			return "NetworkLocked";
		case TelephonyManager.SIM_STATE_PIN_REQUIRED:
			return "PinRequired";
		case TelephonyManager.SIM_STATE_PUK_REQUIRED:
			return "PukRequired";
		case TelephonyManager.SIM_STATE_READY:
			return "Ready";
		case TelephonyManager.SIM_STATE_UNKNOWN:
			return "Unknown";
		}
		return "Error";
	}

	public String getDataState() {
		int dataState = telephonyManager.getDataState();
		switch (dataState) {
		case TelephonyManager.DATA_CONNECTED:
			return "Connected";
		case TelephonyManager.DATA_CONNECTING:
			return "Connecting";
		case TelephonyManager.DATA_DISCONNECTED:
			return "Disconnected";
		case TelephonyManager.DATA_SUSPENDED:
			return "Suspended";
		}
		return "Error";
	}

	public String getDataActivity() {
		int dataActivity = telephonyManager.getDataActivity();
		switch (dataActivity) {
		case TelephonyManager.DATA_ACTIVITY_DORMANT:
			return "Dormant";
		case TelephonyManager.DATA_ACTIVITY_IN:
			return "In";
		case TelephonyManager.DATA_ACTIVITY_INOUT:
			return "InOut";
		case TelephonyManager.DATA_ACTIVITY_NONE:
			return "None";
		case TelephonyManager.DATA_ACTIVITY_OUT:
			return "Out";
		}
		return "Error";
	}

	/**
	 * @return a value between 0 {@link #SIGNAL_STRENGTH_NONE_OR_UNKNOWN} and 4
	 *         {@link #SIGNAL_STRENGTH_GREAT}.
	 */
	private static int getLevel(SignalStrength signalStrength) {
		if (signalStrength.isGsm())
			return getGSMSignalStrength(signalStrength);
		return 0;
	}

	/**
	 * @return the signal strength as dBm.
	 */
	private static int getDbm(TelephonyManager telephonyManager,
			SignalStrength signalStrength) {
		if (!signalStrength.isGsm())
			return 0;

		if (getLteLevel(telephonyManager, signalStrength) == SIGNAL_STRENGTH_NONE_OR_UNKNOWN)
			return getGsmDbm(signalStrength);
		else
			return getLteDbm(signalStrength);
	}

	/**
	 * Get signal level as an int from 0..4
	 */
	private static int getGSMSignalStrength(SignalStrength signalStrength) {
		// ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
		// asu = 0 (-113dB or less) is very weak
		// signal, its better to show 0 bars to the user in such cases.
		// asu = 99 is a special case, where the signal strength is unknown.
		int asu = signalStrength.getGsmSignalStrength();
		if (asu <= 2 || asu == 99)
			return SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
		else if (asu >= GSM_SIGNAL_STRENGTH_GREAT)
			return SIGNAL_STRENGTH_GREAT;
		else if (asu >= GSM_SIGNAL_STRENGTH_GOOD)
			return SIGNAL_STRENGTH_GOOD;
		else if (asu >= GSM_SIGNAL_STRENGTH_MODERATE)
			return SIGNAL_STRENGTH_MODERATE;
		else
			return SIGNAL_STRENGTH_POOR;
	}

	/**
	 * Get the GSM signal strength as dBm
	 */
	private static int getGsmDbm(SignalStrength signalStrength) {
		int dBm;

		int level = signalStrength.getGsmSignalStrength();
		int asu = level == 99 ? SIGNAL_STRENGTH_NONE_OR_UNKNOWN : level;
		if (asu != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
			dBm = -113 + 2 * asu;
		} else {
			dBm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
		}
		return dBm;
	}

	/**
	 * Get LTE as level 0..4
	 */
	private static int getLteLevel(TelephonyManager telephonyManager,
			SignalStrength signalStrength) {
		// For now there's no other way besides reflection :( The getLteLevel()
		// method
		// in the SignalStrength class access private fields.
		// On some Samsung devices, getLteLevel() can actually return 4 (the
		// highest signal strength) even if we're not on Lte.
		// It seems that Samsung has reimplemented getLteLevel(). So we add an
		// extra check to make sure we only use Lte level if we're on LTE.

		if (telephonyManager.getNetworkType() != TelephonyManager.NETWORK_TYPE_LTE) {
			return SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
		}
		try {
			Method methodGetLteLevel = SignalStrength.class
					.getMethod("getLteLevel");
			return (Integer) methodGetLteLevel.invoke(signalStrength);
		} catch (Throwable t) {
			return SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
		}
	}

	/**
	 * Get LTE as dBm
	 */
	private static int getLteDbm(SignalStrength signalStrength) {
		// For now there's no other way besides reflection :( The getLteDbm()
		// method
		// in the SignalStrength class returns a private field which is not
		// accessible in any public, non-hidden methods.
		try {
			Method methodGetLteDbm = SignalStrength.class
					.getMethod("getLteDbm");
			return (Integer) methodGetLteDbm.invoke(signalStrength);
		} catch (Throwable t) {
			return SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
		}
	}

	private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			lastSignalStrength = getLevel(signalStrength);
			lastSignalStrengthDbm = getDbm(telephonyManager, signalStrength);
			lastBer = signalStrength.getGsmBitErrorRate();
		}

		@Override
		public void onServiceStateChanged(ServiceState serviceState) {
			if (serviceState.getState() != ServiceState.STATE_IN_SERVICE) {
				lastSignalStrength = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
				lastSignalStrengthDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
				lastBer = 0;
			}
		}
	};
}
