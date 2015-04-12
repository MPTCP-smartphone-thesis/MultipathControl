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

	public LocationState(Context context) {
		this.context = context;
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
			if (isGPSAvailable()) {
				LocationRequest request = new LocationRequest();
				// TODO: option to get better accuracy
				request.setPriority(LocationRequest.PRIORITY_LOW_POWER);
				LocationServices.FusedLocationApi.requestLocationUpdates(
						googleApiClient, request, locationListener);
			}
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
		}
	};
}
