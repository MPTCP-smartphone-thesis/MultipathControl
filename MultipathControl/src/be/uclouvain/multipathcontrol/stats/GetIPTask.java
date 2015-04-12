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
		HttpClient httpClient = HttpUtils.getHttpClient();
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
