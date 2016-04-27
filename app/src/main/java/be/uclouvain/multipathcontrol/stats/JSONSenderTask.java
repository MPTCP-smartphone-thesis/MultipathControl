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

import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.client.HttpClient;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
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
		this.httpClient = HttpUtils.getHttpClient();
	}

	@Override
	protected Collection<String> doInBackground(JSONSender... jsonSenders) {
		// list of name of prefs that have to be removed
		if (httpClient == null)
			return null;

		Collection<String> collection = new ArrayList<String>(
				jsonSenders.length);
		for (JSONSender jsonSender : jsonSenders) {
			Log.d(Manager.TAG, "Try sending: " + jsonSender.getName() + " - "
					+ jsonSender.getCategory());

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
		if (collection != null)
			SaveDataAbstract.removeFromPrefs(settings, collection, category);
		JSONSender.stopSending();
	}
}
