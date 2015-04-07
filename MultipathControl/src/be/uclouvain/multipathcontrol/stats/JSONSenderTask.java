package be.uclouvain.multipathcontrol.stats;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.ConfigServer;
import be.uclouvain.multipathcontrol.global.Manager;

public class JSONSenderTask extends
		AsyncTask<JSONSender, Void, Collection<String>> {

	private SharedPreferences settings;
	private StatsCategories category;
	private HttpClient httpClient;

	public JSONSenderTask(SharedPreferences settings, StatsCategories category) {
		super();
		this.settings = settings;
		this.category = category;
		this.httpClient = getHttpClient();
	}

	public static HttpClient getHttpClient() {
		DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
		if (ConfigServer.username != null && !ConfigServer.username.isEmpty()) {
			Credentials creds = new UsernamePasswordCredentials(
					ConfigServer.username, ConfigServer.password);
			AuthScope scope = new AuthScope(ConfigServer.hostname,
					ConfigServer.port);
			CredentialsProvider credProvider = defaultHttpClient
					.getCredentialsProvider();
			credProvider.setCredentials(scope, creds);
		}
		return defaultHttpClient;
	}

	@Override
	protected Collection<String> doInBackground(JSONSender... jsonSenders) {
		// list of name of prefs that have to be removed
		Collection<String> collection = new ArrayList<String>(
				jsonSenders.length);
		for (JSONSender jsonSender : jsonSenders) {
			Log.d(Manager.TAG, "Try sending: " + jsonSender.getJSONObject());

			// remove it also is we had problem when creating JSONSender object
			if (jsonSender.getJSONObject() == null
					|| jsonSender.send(httpClient)) {
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
