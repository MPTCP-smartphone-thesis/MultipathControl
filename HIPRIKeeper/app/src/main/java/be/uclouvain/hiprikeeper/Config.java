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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Matthieu Baerts on 19/04/15.
 */
public class Config {
	public static final String PREFS_NAME         = "HIPRIKeeper";
	public static final String PREFS_STATUS       = "enableMultiInterfaces";
	public static final String PREFS_SAVE_BATTERY = "saveBattery";

	public static boolean enable = true;
	public static boolean saveBattery = true;

	private Config() {
	}

	public static void getDefaultConfig(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME,
				Context.MODE_PRIVATE);
		enable = settings.getBoolean(PREFS_STATUS, true);
		saveBattery = settings.getBoolean(PREFS_SAVE_BATTERY, true);
	}

	public static void saveStatus(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME,
				Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();

		editor.putBoolean(PREFS_STATUS, enable);
		editor.putBoolean(PREFS_SAVE_BATTERY, saveBattery);
		editor.apply();
	}
}
