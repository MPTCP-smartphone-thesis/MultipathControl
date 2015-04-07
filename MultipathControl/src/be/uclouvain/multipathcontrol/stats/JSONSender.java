package be.uclouvain.multipathcontrol.stats;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.ConfigServer;
import be.uclouvain.multipathcontrol.global.Manager;

public class JSONSender {

	private JSONObject jsonObject;
	private SharedPreferences prefs;
	private StatsCategories category;
	private String name;
	private String xmlFilePath;

	private static boolean isSending = false;

	private static String baseUri = "http://" + ConfigServer.hostname + ":"
			+ ConfigServer.port;

	public JSONSender(Context context, String name, StatsCategories category) {
		this.category = category;
		this.name = name;
		this.prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
		this.xmlFilePath = context.getFilesDir().getParent() + File.separator
				+ "shared_prefs" + File.separator + name + ".xml";

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

	// avoid uploading multiple time
	private static boolean trySending() {
		synchronized (JSONSender.class) {
			if (isSending)
				return false;
			isSending = true;
			return true;
		}
	}

	public static void stopSending() {
		synchronized (JSONSender.class) {
			isSending = false;
		}
	}

	/**
	 * Get all sets of data that have to be send, prepare the JSON and send it.
	 * If correctly sent, clear data from settings.
	 */
	public static void sendAll(Context context) {
		if (!trySending())
			return;

		SharedPreferences settings = context.getSharedPreferences(
				Config.PREFS_NAME, Context.MODE_PRIVATE);

		for (StatsCategories category : StatsCategories.values()) {
			String key = Config.PREFS_STATS_SET + '_' + category;
			Set<String> statsSet = settings.getStringSet(key, null);
			if (statsSet == null)
				continue;

			JSONSender jsonSenders[] = new JSONSender[statsSet.size()];
			int i = 0;
			// Get all data sets and send them
			for (String name : statsSet)
				jsonSenders[i++] = new JSONSender(context, name, category);

			JSONSenderTask jsonSenderTask = new JSONSenderTask(settings,
					category);
			jsonSenderTask.execute(jsonSenders);
		}
	}

	/**
	 * Send a POST request to the server (using ConfigServer class) with the
	 * JSON string from jsonObject
	 *
	 * @return true if the server return status code 200
	 */
	public boolean send(HttpClient httpClient) {
		HttpPost httpPost = new HttpPost(baseUri + "/"
				+ category.name().toLowerCase(Locale.ENGLISH));
		try {
			httpPost.setEntity(new StringEntity(jsonObject.toString()));
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			int rc = response.getStatusLine().getStatusCode();
			Log.d(Manager.TAG, "Get answer: " + rc);
			response.getEntity().consumeContent();
			return rc == HttpStatus.SC_OK;
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
		if (prefs != null) {
			prefs.edit().clear().commit();
			new File(xmlFilePath).delete(); // not needed to keep empty xml file
		}
	}

	public JSONObject getJSONObject() {
		return jsonObject;
	}

	public StatsCategories getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}
}
