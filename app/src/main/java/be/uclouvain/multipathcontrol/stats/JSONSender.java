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

package be.uclouvain.multipathcontrol.stats;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
			if (isSending || ConfigServer.hostname.isEmpty())
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
		if (jsonObject == null || ConfigServer.hostname.isEmpty())
			return false;
		HttpPost httpPost = new HttpPost(HttpUtils.BASEURI + "/"
				+ category.name().toLowerCase(Locale.ENGLISH));
		String jsonString = jsonObject.toString();
		try {
			httpPost.setEntity(new StringEntity(jsonString));
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(httpPost);
			int rc = response.getStatusLine().getStatusCode();
			Log.d(Manager.TAG, "Get answer: " + rc);
			response.getEntity().consumeContent();
			return rc == HttpStatus.SC_OK;
		} catch (IOException e) {
			Log.e(Manager.TAG,
					"Error when sending [" + jsonString + "]: "
							+ e.getMessage());
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
