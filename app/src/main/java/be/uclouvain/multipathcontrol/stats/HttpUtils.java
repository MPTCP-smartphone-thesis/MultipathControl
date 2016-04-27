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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import be.uclouvain.multipathcontrol.global.ConfigServer;

public class HttpUtils {

	public static final String BASEURI = "http://" + ConfigServer.hostname
			+ ":" + ConfigServer.port;

	private HttpUtils() {
	}

	public static HttpClient getHttpClient(int timeout) {
		if (ConfigServer.hostname.isEmpty())
			return null;

		DefaultHttpClient defaultHttpClient;
		if (timeout > 0)
			defaultHttpClient = new DefaultHttpClient(getParamTimeout(timeout));
		else
			defaultHttpClient = new DefaultHttpClient();
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

	public static HttpClient getHttpClient() {
		return getHttpClient(0);
	}

	/**
	 * @param timeout in ms
	 * @return BasicHttpParams with timeout for the connection and the socket
	 */
	private static HttpParams getParamTimeout(int timeout) {
		HttpParams httpParameters = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParameters, timeout);
		HttpConnectionParams.setSoTimeout(httpParameters, timeout);
		return httpParameters;
	}
}
