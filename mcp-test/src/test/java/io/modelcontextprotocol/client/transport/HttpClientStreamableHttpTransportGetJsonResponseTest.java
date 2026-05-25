/*
 * Copyright 2025-2025 the original author or authors.
 */

package io.modelcontextprotocol.client.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import io.modelcontextprotocol.server.transport.TomcatTestUtil;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the issue where the GET "listening" SSE stream of the Streamable HTTP
 * transport receives a 200 OK response with {@code Content-Type: application/json} (a
 * pretty-printed JSON body) instead of {@code text/event-stream}.
 * <p>
 * This mirrors the behaviour of https://docs.langchain.com/mcp, where a cached
 * Cloudflare/Vercel response to the GET endpoint returns JSON. The SSE body subscriber
 * then tries to parse the first JSON line ("{") as an SSE field and fails with:
 * {@code McpTransportException: Invalid SSE response. Status code: 200 Line: {}.
 *
 * @author Veai Agent
 */
public class HttpClientStreamableHttpTransportGetJsonResponseTest {

	private static final int PORT = TomcatTestUtil.findAvailablePort();

	private static final String HOST = "http://localhost:" + PORT;

	/** Pretty-printed JSON, as returned by docs.langchain.com for GET /mcp. */
	private static final String GET_JSON_BODY = "{\n  \"server\": {\n    \"name\": \"Docs by LangChain\",\n    \"version\": \"1.0.0\",\n    \"transport\": \"http\"\n  }\n}";

	private HttpServer server;

	private McpClientTransport transport;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(PORT), 0);

		server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();
			if ("GET".equals(method)) {
				// The server advertises SSE via Accept but actually serves JSON
				// (e.g. a cached Cloudflare response). This is the failing case.
				byte[] body = GET_JSON_BODY.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			else if ("POST".equals(method)) {
				String response = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"test-id\"}";
				byte[] body = response.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			else {
				exchange.sendResponseHeaders(200, 0);
			}
			exchange.close();
		});

		server.setExecutor(null);
		server.start();

		transport = HttpClientStreamableHttpTransport.builder(HOST).build();
	}

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	@Timeout(10)
	void getStreamReturningJsonShouldNotRaiseInvalidSseError() throws InterruptedException {
		AtomicReference<Throwable> caughtException = new AtomicReference<>();
		transport.setExceptionHandler(caughtException::set);

		StepVerifier.create(transport.connect(msg -> msg)).verifyComplete();
		// sendMessage triggers the GET listening-stream reconnect()
		StepVerifier.create(transport.sendMessage(createInitializeRequest())).verifyComplete();

		Thread.sleep(500); // wait for async reconnect() GET stream

		StepVerifier.create(transport.closeGracefully()).verifyComplete();

		Throwable error = caughtException.get();
		assertThat(error)
			.withFailMessage("GET stream returning application/json must not produce an Invalid SSE error, but got: %s",
					error)
			.isNull();
	}

	private McpSchema.JSONRPCRequest createInitializeRequest() {
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("Test Client", "1.0.0"));
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
