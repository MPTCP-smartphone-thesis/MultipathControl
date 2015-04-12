package be.uclouvain.multipathcontrol.stats;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import be.uclouvain.multipathcontrol.global.ConfigServer;

public class HttpUtils {

	public static final String BASEURI = "http://" + ConfigServer.hostname
			+ ":" + ConfigServer.port;

	private HttpUtils() {
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
}
