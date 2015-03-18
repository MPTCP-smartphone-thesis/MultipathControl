package be.uclouvain.multipathcontrol;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class Notifications {

	public static void showNotification(Context context) {
		NotificationManager mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
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

	public static void hideNotification(Context context) {
		NotificationManager mNotification = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotification.cancelAll();
	}
}
