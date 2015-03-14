package be.uclouvain.multipathcontrol;

import java.io.File;

import android.content.Context;
import android.util.Log;

public class Manager {
	private static MPCtrl mpctrl = null;
	private static int instances = 0;
	private static Context usedContext;
	public static final String TAG = "mpctrl";

	private static boolean checkRoot() {
		return new File("/system/xbin/su").canExecute();
	}

	/**
	 * Create a new instance of MPCtrl.
	 *
	 * @return null if you're not root.
	 */
	public static MPCtrl create(Context context) {
		if (mpctrl == null) {
			if (!checkRoot()) {
				Log.e(TAG, "It seems it's not a rooted device...");
				return null;
			}
			usedContext = context;
			mpctrl = new MPCtrl(context);
		}
		instances++;
		return mpctrl;
	}

	/**
	 * Destroy the instance only if we are using this context.
	 *
	 * @return true if the instance has really been fully destroyed
	 */
	public static boolean destroy(Context context) {
		if (context != usedContext || mpctrl == null)
			return false;
		instances--;
		if (instances != 0)
			Log.e(TAG, "destroying the non last instance");
		mpctrl.destroy();
		mpctrl = null;
		return true;
	}
}
