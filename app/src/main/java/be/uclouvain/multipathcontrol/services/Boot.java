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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Boot extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			context.startService(new Intent(context, MainService.class));
		}
	}
}