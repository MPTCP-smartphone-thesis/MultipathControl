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

package be.uclouvain.multipathcontrol.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import be.uclouvain.multipathcontrol.R;
import be.uclouvain.multipathcontrol.activities.MainActivity;

public class Notifications {

	private final NotificationManager mNotification;
	private final Context context;

	public Notifications(Context context) {
		this.context = context;
		mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void showNotification() {
		Intent intent = new Intent(context, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 1,
				intent, 0);

		Notification notif = new Notification.Builder(context)
				.setWhen(System.currentTimeMillis())
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(
						context.getResources().getString(
								R.string.notification_title))
				.setContentText(
						context.getResources().getString(
								R.string.notification_text))
				.setContentIntent(pendingIntent).build();

		notif.flags |= Notification.FLAG_NO_CLEAR;

		mNotification.notify(1, notif);
	}

	public void hideNotification() {
		NotificationManager mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotification.cancelAll();
	}
}
