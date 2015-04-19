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
import android.util.Log;

/**
 * Created by Matthieu Baerts on 19/04/15.
 */
public class Manager {
	public static final String TAG = "hiprikeeper";
	public static final boolean DEBUG = false;

	private static HIPRIKeeper hipriKeeper = null;
	private static int instances = 0;
	private static Context usedContext;

	private Manager() {
	}

	/**
	 * Create a new instance of hipriKeeper.
	 *
	 * @return null if you're not root.
	 */
	public static HIPRIKeeper create(Context context) {
		if (hipriKeeper == null) {
			usedContext = context;
			hipriKeeper = new HIPRIKeeper(context);
		}
		instances++;
		return hipriKeeper;
	}

	/**
	 * Destroy the instance only if we are using this context.
	 *
	 * @return true if the instance has really been fully destroyed
	 */
	public static boolean destroy(Context context) {
		if (context != usedContext || hipriKeeper == null)
			return false;
		instances--;
		if (instances != 0) {
			Log.e(TAG, "destroying the non last instance");
			return false;
		}
		hipriKeeper.destroy();
		hipriKeeper = null;
		return true;
	}
}
