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

package be.uclouvain.multipathcontrol.services;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import be.uclouvain.multipathcontrol.MPCtrl;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.system.Cmd;

public class MainService extends Service {
	public static final String CONFIG_FILE = "mptcp_ctrl.conf";
	private static final String SERVER_IP = "PUT YOUR IP HERE"; // Nothing better found now...
	private static final int NB_CONFIGS = 4;
    private static final boolean[] DATA_BACKUP = {false, true, true, true};
    private static final String[] TCP_RETRIES3 = {"16", "16", "16", "16"};
    private static final String[] MPTCP_ACTIVE_BK = {"0", "0", "0", "1"};
    private static final String[] MPTCP_OLD_BK = {"0", "0", "1", "0"};
    private static final String[] OPEN_BUP = {"1", "1", "1", "0"};

	private static final long THREE_HOURS_MS = 3 * 60 * 60 * 1000;

	private MPCtrl mpctrl;
	private Random generator;
	private Timer timer;
	private int configId;

	private static MainService currentService;

	@Override
	public IBinder onBind(Intent intent) {
		return null; // not allow binding
	}

	public void onCreate() {
		super.onCreate();
		mpctrl = Manager.create(getApplicationContext());
		Log.i(Manager.TAG, "Create service");
		if (mpctrl == null) {
			Toast.makeText(this,
					"MPControl: It seems this is not a rooted device",
					Toast.LENGTH_LONG).show();
			stopSelf();
			return;
		}
		// Configure!
		// Schedule timer (in configure)
		generator = new Random();
        currentService = this;
		configureAndReschedule();
	}

	class ConfigureTask extends TimerTask {
		public void run() { configureAndReschedule(); };
	}


	public String getDeviceId() {
		return Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
	}

	public static MainService getService() {
		return currentService;
	}

	public int getConfigNumber() {
		return configId;
	}

	public Date getDateCurrentTimeZone(long timestamp) {
		try{
			Calendar calendar = Calendar.getInstance();
			TimeZone tz = TimeZone.getDefault();
			calendar.setTimeInMillis(timestamp * 1000);
			calendar.add(Calendar.MILLISECOND, tz.getOffset(calendar.getTimeInMillis()));
			return calendar.getTime();
		}catch (Exception e) {
		}
		return null;
	}

	private boolean checkConfigFile() {
		/* Return true if the configuration can be changed */
		final File file = new File(Environment.getExternalStorageDirectory()
				.getAbsolutePath(), CONFIG_FILE);
		if (!file.exists()) {
			Log.d("MAINSERVICE", "Config file not found");
			return true;
		}
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			configId = Integer.parseInt(br.readLine());
			String serverIp = br.readLine();
			Date lastModified = getDateCurrentTimeZone(Long.parseLong(br.readLine()));

			// If less than 3 hours, don't change the config
			if (getDateDiff(lastModified, new Date(), TimeUnit.MILLISECONDS) <= THREE_HOURS_MS)
				return false;

		} catch (FileNotFoundException e) {
			Log.e("MAINSERVICE", "Config file not found but file exists...");
			return true;
		} catch (IOException e) {
			Log.e("MAINSERVICE", "IOException: " + e);
			return true;
		} catch (NumberFormatException e) {
			Log.e("MAINSERVICE", "NumberFormatException: " + e);
			return true;
		} finally {
			try {
				// Don't forget to close the file!
				if (br != null)
					br.close();
			} catch (IOException e) {}
		}
		return true;
	}

	private void configure() {
		// Select random number
		configId = generator.nextInt(NB_CONFIGS);
        Log.d("MAINSERVICE", "Selected config " + configId);
        mpctrl.setDataBackup(DATA_BACKUP[configId]);
        Config.dataBackup = DATA_BACKUP[configId];
        Config.saveStatus(this);
        try {
            Cmd.runAsRootSafe("sysctl -w net.ipv4.tcp_retries3=" + TCP_RETRIES3[configId]);
            Cmd.runAsRootSafe("sysctl -w net.mptcp.mptcp_active_bk=" + MPTCP_ACTIVE_BK[configId]);
            Cmd.runAsRootSafe("sysctl -w net.mptcp.mptcp_old_bk=" + MPTCP_OLD_BK[configId]);
            Cmd.runAsRootSafe("echo " + OPEN_BUP[configId] + " | tee /sys/module/mptcp_fullmesh/parameters/open_bup");
        } catch (Exception e) {
            Log.e("MAINSERVICE", "Holy shit: " + e.toString());
        }
	}

	private void writeConfigFile() {
		File file = new File(Environment.getExternalStorageDirectory()
				.getAbsolutePath(), CONFIG_FILE);
		try {
			PrintWriter pw = new PrintWriter(new FileWriter(file, false));
			pw.println(configId);
			pw.println(SERVER_IP);
			pw.println(System.currentTimeMillis());
			pw.close();
		} catch(IOException e) {
			Log.e("MAINSERVICE", "IOException: " + e);
		}

	}

	/**
	 * Get a diff between two dates
	 * @param date1 the oldest date
	 * @param date2 the newest date
	 * @param timeUnit the unit in which you want the diff
	 * @return the diff value, in the provided unit
	 */
	public static long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
		long diffInMillies = date2.getTime() - date1.getTime();
		return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
	}

	public static Date addDays(Date date, int days)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.DATE, days); //minus number would decrement the days
		return cal.getTime();
	}

	private void reschedule() {
		// Reschedule to reconfigure at 3 o'clock, except if device is up since less than 3 hours
		timer = new Timer(true);
		Date now = new Date();
		long toAdd = 0;
		if (now.getHours() < 3)
			toAdd = 86400000;
		Date next = addDays(now, 1);
		next.setHours(3);
		next.setMinutes(0);
		long diff = getDateDiff(now, next, TimeUnit.MILLISECONDS);
		timer.schedule(new ConfigureTask(), diff + toAdd);
	}

	public void configureAndReschedule() {
		if (checkConfigFile()) {
			configure();
			writeConfigFile();
			reschedule();
		}
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_REDELIVER_INTENT;
	}

	public void onDestroy() {
		super.onDestroy();
		if (mpctrl != null) {
			Manager.destroy(getApplicationContext());
			Log.i(Manager.TAG, "Destroy service");
		}
	}
}
