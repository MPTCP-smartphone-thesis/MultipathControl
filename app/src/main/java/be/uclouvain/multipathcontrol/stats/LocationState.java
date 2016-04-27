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

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Manager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocationState {

	private final GoogleApiClient googleApiClient;
	private final Context context;
	private int priority;

	private static LocationState instance = null;

	public static LocationState getInstance(Context context, int priority) {
		if (instance == null)
			instance = new LocationState(context, priority);
		return instance;
	}

	public static LocationState getInstance(Context context) {
		return getInstance(context, LocationRequest.PRIORITY_LOW_POWER);
	}

	public static LocationState getInstance(Context context,
			boolean savePowerGPS) {
		return getInstance(context, getPriority(savePowerGPS));
	}

	private LocationState(Context context, int priority) {
		this.context = context;
		this.priority = priority;
		googleApiClient = new GoogleApiClient.Builder(context)
				.addConnectionCallbacks(googleApiLocationConnectionCallbacks)
				.addOnConnectionFailedListener(
						googleApiLocationCConnectionFailedListener)
				.addApi(LocationServices.API).build();
		googleApiClient.connect();
	}

	public Location getLastLocation() {
		if (isGPSAvailable() && googleApiClient.isConnected())
			return LocationServices.FusedLocationApi
					.getLastLocation(googleApiClient);
		return null;
	}

	public static int getPriority(boolean savePowerGPS) {
		return savePowerGPS ? LocationRequest.PRIORITY_LOW_POWER
				: LocationRequest.PRIORITY_HIGH_ACCURACY;
	}

	public int getPriority() {
		return priority;
	}

	/**
	 * @param priority see {@link LocationRequest}
	 */
	public void setPriority(int priority) {
		if (this.priority == priority)
			return;
		this.priority = priority;

		// no need to change priority when not assigned
		if (!googleApiClient.isConnected())
			return;

		registerLocationListener();
	}

	public void setPriority(boolean savePowerGPS) {
		setPriority(getPriority(savePowerGPS));
	}

	/**
	 * RequestLocationUpdates depending of the priority.
	 *
	 * @pre googleApiClient has to be connected
	 */
	private synchronized void registerLocationListener() {
		if (!isGPSAvailable())
			return;

		// seems we can remove it even if it not exists
		LocationServices.FusedLocationApi.removeLocationUpdates(
				googleApiClient, locationListener);

		LocationRequest request = new LocationRequest();
		request.setPriority(priority);
		Log.d(Manager.TAG, "New priority: " + priority);
		// we need update
		if (priority == LocationRequest.PRIORITY_HIGH_ACCURACY)
			request.setInterval(1000).setFastestInterval(1000);

		LocationServices.FusedLocationApi.requestLocationUpdates(
				googleApiClient, request, locationListener);
	}

	private boolean isGPSAvailable() {
		int returnCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(context);
		if (returnCode != ConnectionResult.SUCCESS) {
			Log.e(Manager.TAG,
					GooglePlayServicesUtil.getErrorString(returnCode));
			return false;
		}
		return true;
	}

	private ConnectionCallbacks googleApiLocationConnectionCallbacks = new ConnectionCallbacks() {

		@Override
		public void onConnectionSuspended(int i) {
			Log.w(Manager.TAG, "GooglePlayService connection suspended: " + i);
			googleApiClient.connect();
		}

		@Override
		public void onConnected(Bundle arg0) {
			Log.d(Manager.TAG, "GooglePlayService connected");
			registerLocationListener();
		}
	};
	private OnConnectionFailedListener googleApiLocationCConnectionFailedListener = new OnConnectionFailedListener() {

		@Override
		public void onConnectionFailed(ConnectionResult r) {
			Log.w(Manager.TAG, "GooglePlayService connection failed: " + r);
		}
	};

	private LocationListener locationListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location arg0) {
			// do nothing, we will use getLastLocation()...
			Log.d(Manager.TAG, "New location: " + arg0);
		}
	};
}
