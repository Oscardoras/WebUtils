package me.oscardoras.webutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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

public class WebServer {
	
	@FunctionalInterface
	public static interface Responder {
		void respond(WebRequest request) throws Exception;
	}
	
	
	private final HttpServer httpServer;
	private final HttpsServer httpsServer;
	
	protected WebServer(HttpServer httpServer, HttpsServer httpsServer) {
		this.httpServer = httpServer;
		this.httpsServer = httpsServer;
	}
	
    public static WebServer newHttpServer(InetSocketAddress httpHost, Responder responder) throws IOException {
    	HttpServer httpServer = HttpServer.create(httpHost, 0);
    	httpServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				WebRequest webrequest = new WebRequest(exchange);
				try {
					responder.respond(webrequest);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
    	httpServer.setExecutor(Executors.newCachedThreadPool());
    	httpServer.start();
    	
    	return new WebServer(httpServer, null);
	}
	
    public static WebServer newHttpsServer(InetSocketAddress httpHost, Responder responder, InetSocketAddress httpsHost, File keystore, String tlsPassword) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, KeyManagementException {
    	HttpServer httpServer = HttpServer.create(httpHost, 0);
    	httpServer.createContext("/", new HttpHandler() {
			public void handle(HttpExchange exchange) throws IOException {
				WebRequest webrequest = new WebRequest(exchange);
				try {
					responder.respond(webrequest);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
    	httpServer.setExecutor(Executors.newCachedThreadPool());
    	httpServer.start();
    	
    	
    	HttpsServer httpsServer = HttpsServer.create(httpsHost, 0);
    	
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
					responder.respond(webrequest);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		httpsServer.setExecutor(Executors.newCachedThreadPool());
		httpsServer.start();
		
		return new WebServer(httpServer, httpsServer);
	}
    
    public final void stop() {
    	if (httpServer != null) httpServer.stop(1);
    	if (httpsServer != null) httpsServer.stop(1);
    }
    
    public final boolean hasHttps() {
    	return httpsServer != null;
    }
    
}