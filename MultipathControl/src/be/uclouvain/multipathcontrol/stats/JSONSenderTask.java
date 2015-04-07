package be.uclouvain.multipathcontrol.stats;

import java.util.ArrayList;
import java.util.Collection;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Manager;

public class JSONSenderTask extends
		AsyncTask<JSONSender, Void, Collection<String>> {

	private SharedPreferences settings;
	private StatsCategories category;

	public JSONSenderTask(SharedPreferences settings, StatsCategories category) {
		super();
		this.settings = settings;
		this.category = category;
	}

	@Override
	protected Collection<String> doInBackground(JSONSender... jsonSenders) {
		// list of name of prefs that have to be removed
		Collection<String> collection = new ArrayList<String>(
				jsonSenders.length);
		for (JSONSender jsonSender : jsonSenders) {
			Log.d(Manager.TAG, "Try sending: " + jsonSender.getJSONObject());
			// remove it also is we had problem when creating JSONSender object
			if (jsonSender.getJSONObject() == null || jsonSender.send()) {
				Log.d(Manager.TAG, "To be cleared: " + jsonSender.getName());
				collection.add(jsonSender.getName());
				jsonSender.clear();
			} else {
				Log.w(Manager.TAG, "Not able to send: " + jsonSender.getName());
			}
		}
		return collection;
	}

	protected void onPostExecute(Collection<String> collection) {
		SaveDataAbstract.removeFromPrefs(settings, collection, category);
		JSONSender.stopSending();
	}
}
