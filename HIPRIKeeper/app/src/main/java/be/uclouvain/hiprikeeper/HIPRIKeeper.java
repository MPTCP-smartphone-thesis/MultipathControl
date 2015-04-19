/*
 * This file is part of HIPRI Keeper.
 *
 * Copyright 2015 UCLouvain - Matthieu Baerts <first.last@student.uclouvain.be>
 *
 * This application is free software; you can redistribute it and/or modify
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

package be.uclouvain.hiprikeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

/**
 * Created by Matthieu Baerts on 19/04/15.
 */
public class HIPRIKeeper {

	private Context context;
	private final Notifications notif;
	private final MobileDataMgr mobileDataMgr;
	private final Handler handler;
	private static long lastTimeHandler;

	public HIPRIKeeper(Context context) {
		this.context = context;

		Config.getDefaultConfig(context);
		notif = new Notifications(context);
		mobileDataMgr = new MobileDataMgr(context);
		Log.i(Manager.TAG, "new HIPRI Keeper");

		handler = new Handler();
		initHandler();

		/*
		 * mConnReceiver will be called each time a change of connectivity
		 * happen
		 */
		context.registerReceiver(mConnReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));

		if (Config.enable)
			notif.showNotification();
		if (! Config.saveBattery)
			mobileDataMgr.keepMobileConnectionAlive();
	}

	public void destroy() {
		try {
			context.unregisterReceiver(mConnReceiver);
		} catch (IllegalArgumentException e) {
		}

		Log.i(Manager.TAG, "destroy MPCtrl");

		handler.getLooper().quit();

		notif.hideNotification();
	}

	public boolean setStatus(boolean isChecked) {
		if (isChecked == Config.enable)
			return false;

		Log.i(Manager.TAG, "set new status "
				+ (isChecked ? "enable" : "disable"));
		Config.enable = isChecked;
		Config.saveStatus(context);

		if (isChecked)
			notif.showNotification();
		else
			notif.hideNotification();

		return true;
	}

	public boolean setSaveBattery(boolean isChecked) {
		if (isChecked == Config.saveBattery)
			return false;
		Config.saveBattery = isChecked;
		Config.saveStatus(context);
		return true; // nothing to do here: we need to restart app
	}

	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(Manager.TAG, "BroadcastReceiver " + intent);
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			if (pm.isScreenOn())
				mobileDataMgr.setMobileDataActive(Config.enable);
		}
	};

	/*
	 * Will not be executed in deep sleep, nice, no need to use both connections
	 * in deep-sleep
	 */
	private void initHandler() {
		lastTimeHandler = System.currentTimeMillis();

		// First check
		handler.post(runnableSetMobileDataActive);
	}

	/*
	 * Ensures that the data interface and WiFi are connected at the same time.
	 */
	private Runnable runnableSetMobileDataActive = new Runnable() {
		final long fiveSecondsMs = 5 * 1000;

		@Override
		public void run() {
			long nowTime = System.currentTimeMillis();
			// do not try keep mobile data active in deep sleep mode
			if (Config.enable
					&& nowTime - lastTimeHandler < fiveSecondsMs * 2)
				// to not disable cellular iface
				mobileDataMgr.setMobileDataActive(Config.enable);
			lastTimeHandler = nowTime;
			handler.postDelayed(this, fiveSecondsMs);
		}
	};
}
