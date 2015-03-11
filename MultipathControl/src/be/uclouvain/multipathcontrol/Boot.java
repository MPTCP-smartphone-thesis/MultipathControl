package be.uclouvain.multipathcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Boot extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
			Intent startServiceIntent = new Intent(context, Main.class);
			startServiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(startServiceIntent);
		}
	}
}