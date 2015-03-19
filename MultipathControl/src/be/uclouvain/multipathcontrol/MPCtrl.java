package be.uclouvain.multipathcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

public class MPCtrl {

	private final Context context;
	private final Notifications notif;
	private final MobileDataMgr mobileDataMgr;
	private final Handler handler;
	private final IPRoute iproute;
	private static long lastTimeHandler;

	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(Manager.TAG, "BroadcastReceiver");
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			if (pm.isScreenOn())
				handler.post(runnable);
			iproute.monitorInterfaces();
		}
	};

	public MPCtrl(Context context) {
		this.context = context;
		Config.getDefaultConfig(context);
		notif = new Notifications(context);
		mobileDataMgr = new MobileDataMgr(context);
		iproute = new IPRoute(mobileDataMgr);
		Log.i(Manager.TAG, "new MPCtrl");

		handler = new Handler();
		initHandler();

		/*
		 * mConnReceiver will be called each time a change of connectivity
		 * happen
		 */
		context.registerReceiver(mConnReceiver, new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION));

		if (Config.mEnabled)
			notif.showNotification();
	}

	public void destroy() {
		try {
			context.unregisterReceiver(mConnReceiver);
		} catch (IllegalArgumentException e) {
		}

		Log.i(Manager.TAG, "destroy MPCtrl");

		handler.getLooper().quit();

		notif.hideNotification();

		Config.saveStatus(context);
	}

	public boolean getEnabled() {
		return Config.mEnabled;
	}

	public boolean setStatus(boolean isChecked) {
		if (isChecked == Config.mEnabled)
			return false;

		Log.i(Manager.TAG, "set new status "
				+ (isChecked ? "enable" : "disable"));
		Config.mEnabled = isChecked;
		Config.saveStatus(context);

		if (isChecked) {
			notif.showNotification();
			iproute.monitorInterfaces();
		} else {
			notif.hideNotification();
		}

		return true;
	}

	public boolean getDefaultData() {
		return Config.defaultRouteData;
	}

	public boolean setDefaultData(boolean isChecked) {
		if (isChecked == Config.defaultRouteData)
			return false;
		Config.defaultRouteData = isChecked;
		Config.saveStatus(context);

		return iproute.setDefaultRoute();
	}

	public boolean getDataBackup() {
		return Config.dataBackup;
	}

	public boolean setDataBackup(boolean isChecked) {
		if (isChecked == Config.dataBackup)
			return false;
		Config.dataBackup = isChecked;
		Config.saveStatus(context);

		return iproute.setDataBackup();
	}

	// Will not be executed in deep sleep, nice, no need to use both connections
	// in deep-sleep
	private void initHandler() {
		lastTimeHandler = System.currentTimeMillis();

		// First check
		handler.post(runnable);
	}

	/*
	 * Ensures that the data interface and WiFi are connected at the same time.
	 */
	private Runnable runnable = new Runnable() {
		final long fiveSecondsMs = 5 * 1000;
		@Override
		public void run() {
			long nowTime = System.currentTimeMillis();
			// do not try keep mobile data active in deep sleep mode
			if (Config.mEnabled
					&& nowTime - lastTimeHandler < fiveSecondsMs * 2)
				// to not disable cellular iface
				mobileDataMgr.setMobileDataActive(Config.mEnabled);
			lastTimeHandler = nowTime;
			handler.postDelayed(this, fiveSecondsMs);
		}
	};

}
