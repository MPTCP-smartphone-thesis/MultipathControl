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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Manager;

public class GetIPTask extends AsyncTask<String, Void, String> {

	private final Editor editor;

	public GetIPTask(Editor editor) {
		super();
		this.editor = editor;
	}

	@Override
	protected String doInBackground(String... arg0) {
		HttpClient httpClient = HttpUtils.getHttpClient(1000);
		if (httpClient == null)
			return null;

		HttpGet httpGet = new HttpGet(HttpUtils.BASEURI + '/' + arg0[0]);
		BufferedReader bReader = null;
		String line = null;
		try {
			HttpResponse response = httpClient.execute(httpGet);
			bReader = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));
			line = bReader.readLine();
			bReader.close();
		} catch (IOException e) {
			Log.e(Manager.TAG, "Error when getting ip: " + e.getMessage());
		} finally {
			try {
				if (bReader != null)
					bReader.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return line;
	}

	protected void onPostExecute(String ip) {
		if (ip != null && !ip.isEmpty()) {
			editor.putString(SaveDataHandover.PREFS_EXT_IP, ip);
			editor.commit();
		}
	}
}
