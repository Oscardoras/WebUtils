package me.oscardoras.webutils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import me.oscardoras.webutils.WebServer.Responder;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public abstract class BungeeWebServer {
	
	public static WebServer newBungeeWebServer(Plugin plugin, Responder responder) throws IOException {
		WebServer server = null;
		
		File file = new File(plugin.getDataFolder() + "/config.yml");
    	file.setReadable(true);
		file.setWritable(true);
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
		}
		
		Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		if (!config.contains("http.http_host")) config.set("http.http_host", "0.0.0.0:8123");
		if (!config.contains("http.https_host")) config.set("http.https_host", "0.0.0.0:8124");
		if (!config.contains("http.tls_password")) config.set("http.tls_password", "");
		ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
		
		try {
			String[] httpAddress = config.getString("http.http_host").split(":");
			InetSocketAddress httpHost = new InetSocketAddress(httpAddress[0], Integer.parseInt(httpAddress[1]));
			int httpPort = httpHost.getPort();
			if (httpPort != 0) {
				try {
					String[] httpsAddress = config.getString("http.https_host").split(":");
					InetSocketAddress httpsHost = new InetSocketAddress(httpsAddress[0], Integer.parseInt(httpsAddress[1]));
					int httpsPort = httpsHost.getPort();
				
					String tlsPassword = config.getString("http.tls_password");
					if (httpsPort != 0 && !tlsPassword.isEmpty()) {
						try {
							server = WebServer.newHttpsServer(httpHost, responder, httpsHost, new File(plugin.getDataFolder() + "/keystore.jks"), tlsPassword);
							plugin.getLogger().info("The https server is listening on " + httpsHost.getHostString() + ":" + httpsPort);
							plugin.getLogger().info("The http server is listening on " + httpHost.getHostString() + ":" + httpPort);
						} catch (FileNotFoundException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | CertificateException | KeyManagementException ex) {
							plugin.getLogger().severe("An error occurred while loading the TLS certificate.");
							ex.printStackTrace();
						} catch (IOException ex) {
							plugin.getLogger().severe("The http or https server's port is incorect or already used.");
						}
					} else {
						try {
							server = WebServer.newHttpServer(httpHost, responder);
							plugin.getLogger().info("The http server is listening on " + httpHost.getHostString() + ":" + httpPort);
						} catch (IOException ex) {
							plugin.getLogger().severe("The http server's port is incorect or already used.");
						}
					}
				} catch (ArrayIndexOutOfBoundsException ex) {
					plugin.getLogger().severe("The https server's host is incorect.");
				}
			}
		} catch (ArrayIndexOutOfBoundsException ex) {
			plugin.getLogger().severe("The http server's host is incorect.");
		}
		
		return server;
	}
	
}
