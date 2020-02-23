package org.webutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public abstract class WebServer {
	
	private final HttpServer httpServer;
	private final HttpsServer httpsServer;
	private final String address;
	
	public WebServer(Plugin plugin) throws IOException {
		System.setProperty("java.net.preferIPv4Stack", "true");
		
		
    	Plugin webProxy = ProxyServer.getInstance().getPluginManager().getPlugin("WebProxy");
    	if (webProxy.equals(plugin)) webProxy = null;
		
    	Configuration config;
    	File file = new File(plugin.getDataFolder() + "/config.yml");
    	file.setReadable(true);
		file.setWritable(true);
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
		}
		config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		
		if (!config.contains("http.address")) config.set("http.address", "localhost:8123");
		if (webProxy == null) {
			if (!config.contains("http.http_host")) config.set("http.http_host", "0.0.0.0:8123");
			if (!config.contains("http.https_host")) config.set("http.https_host", "0.0.0.0:8124");
			if (!config.contains("http.tls_password")) config.set("http.tls_password", "");
		}
		ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
		
		this.address = config.getString("http.address");
		
		HttpServer httpServer = null;
		HttpsServer httpsServer = null;
		
		if (webProxy == null) {
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
								File keystore = new File(plugin.getDataFolder() + "/keystore.jks");
								{
									httpServer = HttpServer.create(httpHost, 0);
							    	httpServer.createContext("/", new HttpHandler() {
										public void handle(HttpExchange exchange) throws IOException {
											exchange.getResponseHeaders().set("Location", "https://" + address + exchange.getRequestURI().toString());
											exchange.sendResponseHeaders(307, !exchange.getRequestMethod().equalsIgnoreCase("head") ? 0 : -1);
											exchange.close();
										}
									});
							    	httpServer.setExecutor(Executors.newCachedThreadPool());
							    	httpServer.start();
							    	
							    	
							    	httpsServer = HttpsServer.create(httpsHost, 0);
							    	
									SSLContext sslContext = SSLContext.getInstance("TLS");
									char[] chars = tlsPassword.toCharArray();
									KeyStore ks = KeyStore.getInstance("JKS");
									FileInputStream fis = new FileInputStream(keystore);
									ks.load(fis, chars);
									KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
									kmf.init(ks, chars);
									TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
									tmf.init(ks);
									sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
									httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
							            public void configure(HttpsParameters params) {
							                try {
							                    // initialise the SSL context
							                    SSLContext c = SSLContext.getDefault();
							                    SSLEngine engine = c.createSSLEngine();
							                    params.setNeedClientAuth(false);
							                    params.setCipherSuites(engine.getEnabledCipherSuites());
							                    params.setProtocols(engine.getEnabledProtocols());

							                    // get the default parameters
							                    SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
							                    params.setSSLParameters(defaultSSLParameters);
							                } catch (Exception ex) {
							                    ex.printStackTrace();
							                }
							            }
							        });
									
									httpsServer.createContext("/", new HttpHandler() {
										public void handle(HttpExchange exchange) throws IOException {
											WebRequest webrequest = new WebRequest(exchange);
											try {
												onRequest(webrequest);
											} catch (Exception ex) {
												ex.printStackTrace();
											}
										}
									});
									httpsServer.setExecutor(Executors.newCachedThreadPool());
									httpsServer.start();
								}
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
								{
									httpServer = HttpServer.create(httpHost, 0);
							    	httpServer.createContext("/", new HttpHandler() {
										public void handle(HttpExchange exchange) throws IOException {
											WebRequest webrequest = new WebRequest(exchange);
											try {
												onRequest(webrequest);
											} catch (Exception ex) {
												ex.printStackTrace();
											}
										}
									});
							    	httpServer.setExecutor(Executors.newCachedThreadPool());
							    	httpServer.start();
							    	
							    	
							    	httpsServer = null;
								}
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
		} else {
			try {
				@SuppressWarnings("unchecked")
				Map<String, WebServer> redirections = (Map<String, WebServer>) webProxy.getClass().getField("redirections").get(webProxy);
				redirections.put("auth", this);
			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			}
		}
		
		this.httpServer = httpServer;
		this.httpsServer = httpsServer;
	}
	
    public WebServer(InetSocketAddress httpHost, String address) throws IOException {
    	System.setProperty("java.net.preferIPv4Stack", "true");
    	
    	
    	httpServer = HttpServer.create(httpHost, 0);
    	httpServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				WebRequest webrequest = new WebRequest(exchange);
				try {
					onRequest(webrequest);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
    	httpServer.setExecutor(Executors.newCachedThreadPool());
    	httpServer.start();
    	
    	
    	httpsServer = null;
    	
    	
    	this.address = address;
	}
	
    public WebServer(InetSocketAddress httpHost, String address, InetSocketAddress httpsHost, File keystore, String tlsPassword) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, KeyManagementException {
    	System.setProperty("java.net.preferIPv4Stack", "true");
    	
    	
    	httpServer = HttpServer.create(httpHost, 0);
    	httpServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().set("Location", "https://" + address + exchange.getRequestURI().toString());
				exchange.sendResponseHeaders(307, !exchange.getRequestMethod().equalsIgnoreCase("head") ? 0 : -1);
				exchange.close();
			}
		});
    	httpServer.setExecutor(Executors.newCachedThreadPool());
    	httpServer.start();
    	
    	
    	httpsServer = HttpsServer.create(httpsHost, 0);
    	
		SSLContext sslContext = SSLContext.getInstance("TLS");
		char[] chars = tlsPassword.toCharArray();
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream fis = new FileInputStream(keystore);
		ks.load(fis, chars);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, chars);
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(ks);
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            public void configure(HttpsParameters params) {
                try {
                    // initialise the SSL context
                    SSLContext c = SSLContext.getDefault();
                    SSLEngine engine = c.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());

                    // get the default parameters
                    SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                    params.setSSLParameters(defaultSSLParameters);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
		
		httpsServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				WebRequest webrequest = new WebRequest(exchange);
				try {
					onRequest(webrequest);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		httpsServer.setExecutor(Executors.newCachedThreadPool());
		httpsServer.start();
		
		
		this.address = address;
	}
    
    public final void stop() {
    	if (httpServer != null) httpServer.stop(1);
    	if (httpsServer != null) httpsServer.stop(1);
    }
    
    public final boolean hasHttps() {
    	return httpsServer != null;
    }
    
    public final String getAddress() {
    	return address;
    }
    
    public abstract void onRequest(WebRequest request) throws Exception;
	
}