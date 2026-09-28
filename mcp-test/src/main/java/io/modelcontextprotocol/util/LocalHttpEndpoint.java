/*
 * Copyright 2026 - 2026 the original author or authors.
 */

package io.modelcontextprotocol.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

public final class LocalHttpEndpoint implements AutoCloseable {

	private final HttpServer server;

	private LocalHttpEndpoint(HttpServer server) {
		this.server = server;
	}

	public static LocalHttpEndpoint serving(String responseBody) {
		byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", exchange -> {
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream responseStream = exchange.getResponseBody()) {
					responseStream.write(body);
				}
			});
			server.start();
			return new LocalHttpEndpoint(server);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public URI uri() {
		InetSocketAddress address = this.server.getAddress();
		try {
			return new URI("http", null, address.getAddress().getHostAddress(), address.getPort(), "/", null, null);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
