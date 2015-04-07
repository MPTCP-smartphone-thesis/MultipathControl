package be.uclouvain.multipathcontrol.stats;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.ConfigServer;

public class JSONSender {

	private JSONObject jsonObject;
	private SharedPreferences prefs;
	private StatsCategories category;

	public JSONSender(Context context, String name, StatsCategories category) {
		this.category = category;
		this.prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);

		Map<String, ?> map = prefs.getAll();
		if (map == null || !map.containsKey(SaveDataAbstract.PREFS_TIMESTAMP))
			this.jsonObject = null;
		else
			this.jsonObject = new JSONObject(map);
	}

	public JSONSender(JSONObject jsonObject, StatsCategories category) {
		this.jsonObject = jsonObject;
		this.category = category;
		this.prefs = null;
	}

	/**
	 * Get all sets of data that have to be send, prepare the JSON and send it.
	 * If correctly sent, clear data from settings.
	 */
	public static void sendAll(Context context) {
		SharedPreferences settings = context.getSharedPreferences(
				Config.PREFS_NAME, Context.MODE_PRIVATE);

		for (StatsCategories category : StatsCategories.values()) {
			String key = Config.PREFS_STATS_SET + '_' + category;
			Set<String> statsSet = settings.getStringSet(key, null);
			if (statsSet == null)
				continue;

			int size = 0;
			// Get all data sets and send them
			for (String name : statsSet) {
				size++;
				JSONSender jsonSender = new JSONSender(context, name, category);
				if (jsonSender.getJSONObject() == null || jsonSender.send()) {
					jsonSender.clear();
					statsSet.remove(name);
					size--;
				}
			}

			// update or remove sets of data that have to be sent next time.
			Editor settingsEditor = settings.edit();
			if (size > 0) {
				settingsEditor.putStringSet(key, new HashSet<String>(statsSet));
			} else
				settingsEditor.remove(key);
			settingsEditor.apply();
		}
	}

	public JSONObject getJSONObject() {
		return jsonObject;
	}

	public StatsCategories getCategory() {
		return category;
	}

	/**
	 * Send a POST request to the server (using ConfigServer class) with the
	 * JSON string from jsonObject
	 *
	 * @return true if the server return status code 200
	 */
	public boolean send() {
		HttpClient client = new DefaultHttpClient();
		String uri = "http://" + ConfigServer.hostname + ":"
				+ ConfigServer.port + "/" + category;
		HttpPost httpPost = new HttpPost(uri);
		try {
			httpPost.setEntity(new StringEntity(jsonObject.toString()));
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = client.execute(httpPost);
			return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void clear() {
		if (prefs != null)
			prefs.edit().clear().apply();
	}
}
