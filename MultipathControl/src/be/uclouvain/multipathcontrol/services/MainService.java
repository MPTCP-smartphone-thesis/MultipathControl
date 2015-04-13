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
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import be.uclouvain.multipathcontrol.MPCtrl;
import be.uclouvain.multipathcontrol.global.Manager;

public class MainService extends Service {

	private MPCtrl mpctrl;

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
	}

	public void onDestroy() {
		super.onDestroy();
		if (mpctrl != null) {
			Manager.destroy(getApplicationContext());
			Log.i(Manager.TAG, "Destroy service");
		}
	}
}
