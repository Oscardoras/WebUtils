package me.oscardoras.webutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public class WebRequest {
	
	protected final HttpExchange exchange;
	
	public WebRequest(HttpExchange exchange) {
		this.exchange = exchange;
	}
	
	public HttpExchange getHttpExchange() {
		return exchange;
	}
	
	public URI getRequestURI() {
		return exchange.getRequestURI();
	}
	
	public String getPath() {
		String path = getRequestURI().getPath();
		if (!path.equals("/") && path.endsWith("/")) path = path.substring(0, path.length() - 1);
		return path;
	}
	
	public String getRequestMethod() {
		return exchange.getRequestMethod().toUpperCase();
	}
	
	public String getProtocol() {
		return exchange.getProtocol();
	}
	
	public InetSocketAddress getRemoteAddress() {
		return exchange.getRemoteAddress();
	}
	
	public InetSocketAddress getLocalAddress() {
		return exchange.getLocalAddress();
	}
	
	public void sendResponseHeaders(int code) throws IOException {
		exchange.sendResponseHeaders(code, !getRequestMethod().equals("HEAD") ? 0l : -1l);
	}
	
	public int getResponseCode() {
		return exchange.getResponseCode();
	}
	
	public Headers getRequestHeaders() {
		return exchange.getRequestHeaders();
	}
	
	public InputStream getRequestBody() {
		return exchange.getRequestBody();
	}
	
	public Headers getResponseHeaders() {
		return exchange.getResponseHeaders();
	}
	
	public OutputStream getResponseBody() {
		return exchange.getResponseBody();
	}
	
	public void close() {
		exchange.close();
	}
	
	public String getLanguage() {
		Headers headers = getRequestHeaders();
		if (headers.containsKey("Accept-Language")) return headers.get("Accept-Language").get(0).split(";")[0].split("-")[0].split("_")[0];
		return "en";
	}
	
	public Map<String, String> get() {
		String query = getRequestURI().getQuery();
		Map<String, String> get = new HashMap<String, String>();
		if (query != null) {
			for (String s : query.split("&")) {
				try {
					String[] string = s.split("=");
					get.put(URLDecoder.decode(string[0], "UTF-8"), URLDecoder.decode(string[1], "UTF-8"));
				} catch (Exception ex) {}
			}
		}
		return get;
	}
	
	public Map<String, String> post() {
		Map<String, String> post = new HashMap<String, String>();
		try {
			BufferedReader br = null;
			StringBuilder sb = new StringBuilder();
			String line;
			br = new BufferedReader(new InputStreamReader(getRequestBody()));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			if (br != null) {
				br.close();
			}
			for (String s : sb.toString().split("&")) {
				try {
					String[] string = s.split("=");
					post.put(URLDecoder.decode(string[0], "UTF-8"), URLDecoder.decode(string[1], "UTF-8"));
				} catch (Exception ex) {}
			}
		} catch (IOException e) {}
		return post;
	}
	
	public Map<String, String> getCookies() {
		Headers requestHeader = getRequestHeaders();
		Map<String, String> post = new HashMap<String, String>();
		for (String s : requestHeader.keySet()) {
			if (s.equalsIgnoreCase("cookie")) {
				try {
					for (String cookie : requestHeader.get(s).get(0).split(";")) {
						String[] string = cookie.split("=");
						post.put(string[0], string[1]);
					}
				} catch (Exception ex) {}
			}
		}
		return post;
	}
	
	public void setCookie(String key, String value) {
		List<String> cookies = new ArrayList<String>();
		Map<String, String> cookiesMap = getCookies();
		boolean found = false;
		for (String s : cookiesMap.keySet()) {
			if (s.equals(key)) {
				cookies.add(key + "=" + value);
				found = true;
			} else {
				cookies.add(s + "=" + cookiesMap.get(s));
			}
		}
		if (!found) {
			cookies.add(key + "=" + value);
		}
		String cookieString = "";
		for (String cookie : cookies) {
			if (cookieString.equals("")) {
				cookieString = cookieString + cookie;
			} else {
				cookieString = cookieString + ";" + cookie;
			}
		}
		getResponseHeaders().set("Set-Cookie", cookieString);
	}
	
	public void respondFile(File file) throws IOException {
		Headers responseHeader = getResponseHeaders();
		Path path = Paths.get(file.getPath());
		String mime = Files.probeContentType(path);
		if (mime == null) mime = "text/html";
		responseHeader.set("Content-Type", mime);
		responseHeader.set("Last-Modified", Files.getLastModifiedTime(path).toString());
		sendResponseHeaders(200);
		if (!getRequestMethod().equals("HEAD")) getResponseBody().write(Files.readAllBytes(Paths.get(file.getPath())));
		close();
	}
	
	public void respond400() throws IOException {
		Headers responseHeader = getResponseHeaders();
		responseHeader.set("Content-Type", "text/html");
		sendResponseHeaders(400);
		if (!getRequestMethod().equals("HEAD")) getResponseBody().write("<html><head><title>Error 400 (Bad Request)</title></head><body><h1>Error 400 (Bad Request)</h1></body></html>".getBytes());
		close();
	}
	
	public void respond403() throws IOException {
		Headers responseHeader = getResponseHeaders();
		responseHeader.set("Content-Type", "text/html");
		sendResponseHeaders(403);
		if (!getRequestMethod().equals("HEAD")) getResponseBody().write("<html><head><title>Error 403 (Forbidden)</title></head><body><h1>Error 403 (Forbidden)</h1></body></html>".getBytes());
		close();
	}
	
	public void respond404() throws IOException {
		Headers responseHeader = getResponseHeaders();
		responseHeader.set("Content-Type", "text/html");
		sendResponseHeaders(404);
		if (!getRequestMethod().equals("HEAD")) getResponseBody().write("<html><head><title>Error 404 (Not Found)</title></head><body><h1>Error 404 (Not Found)</h1></body></html>".getBytes());
		close();
	}
	
	public void respond500(Exception e1) {
		e1.printStackTrace();
		try {
			sendResponseHeaders(500);
			if (!getRequestMethod().equals("HEAD")) getResponseBody().write("<html><head><title>Error 500 (Internal Server Error)</title></head><body><h1>Error 500 (Internal Server Error)</h1></body></html>".getBytes());
			close();
		} catch (IOException e2) {
			e2.printStackTrace();
		}
	}
	
}