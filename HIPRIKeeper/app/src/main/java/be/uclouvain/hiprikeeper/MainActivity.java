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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

	private HIPRIKeeper hipriKeeper;

	private Switch multiIfaceSwitch;
	private Switch saveBatterySwitch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		multiIfaceSwitch = (Switch) findViewById(R.id.switch_enable);
		saveBatterySwitch = (Switch) findViewById(R.id.switch_save_battery);

		hipriKeeper = Manager.create(getApplicationContext());

		multiIfaceSwitch
				.setOnCheckedChangeListener(onCheckedChangeListenerMultiIface);
		saveBatterySwitch
				.setOnCheckedChangeListener(onCheckedChangeListenerSaveBattery);

		// start a new service if needed
		startService(new Intent(this, MainService.class));
	}

	@Override
	protected void onResume() {
		super.onResume();

		setChecked();
	}

	protected void onDestroy() {
		super.onDestroy();
		Manager.destroy(getApplicationContext());
	}


	private void setChecked() {
		multiIfaceSwitch.setChecked(Config.enable);
		saveBatterySwitch.setChecked(Config.saveBattery);
	}

	private CompoundButton.OnCheckedChangeListener onCheckedChangeListenerMultiIface = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
		                             boolean isChecked) {
			if (hipriKeeper.setStatus(isChecked) && !isChecked)
				Toast.makeText(
						MainActivity.this,
						"The second interface will be disabled in a few seconds",
						Toast.LENGTH_LONG).show();
		}
	};

	private CompoundButton.OnCheckedChangeListener onCheckedChangeListenerSaveBattery = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
		                             boolean isChecked) {
			if (hipriKeeper.setSaveBattery(isChecked))
				Toast.makeText(
						MainActivity.this,
						"Please disconnect/reconnect cellular interface or reboot",
						Toast.LENGTH_LONG).show();
		}
	};
}
