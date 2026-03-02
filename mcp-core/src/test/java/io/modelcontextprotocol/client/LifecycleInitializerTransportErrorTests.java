/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.client;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.client.LifecycleInitializer.Initialization;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportProcessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests that transport-level exceptions (e.g. process startup failure) propagated via
 * {@link LifecycleInitializer#handleException(Throwable)} fail initialization immediately
 * rather than waiting for a timeout.
 */
class LifecycleInitializerTransportErrorTests {

	private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(10);

	private static final McpSchema.ClientCapabilities CLIENT_CAPABILITIES = McpSchema.ClientCapabilities.builder()
		.build();

	private static final McpSchema.Implementation CLIENT_INFO = new McpSchema.Implementation("test-client", "1.0.0");

	private static final List<String> PROTOCOL_VERSIONS = List.of("2025-03-26");

	@Mock
	private McpClientSession mockClientSession;

	@Mock
	private Function<ContextView, McpClientSession> mockSessionSupplier;

	@Mock
	private Function<Initialization, Mono<Void>> mockPostInitializationHook;

	private LifecycleInitializer initializer;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(mockPostInitializationHook.apply(any(Initialization.class))).thenReturn(Mono.empty());
		when(mockSessionSupplier.apply(any(ContextView.class))).thenReturn(mockClientSession);
		when(mockClientSession.closeGracefully()).thenReturn(Mono.empty());

		initializer = new LifecycleInitializer(CLIENT_CAPABILITIES, CLIENT_INFO, PROTOCOL_VERSIONS,
				INITIALIZATION_TIMEOUT, mockSessionSupplier, mockPostInitializationHook);
	}

	@Test
	void handleExceptionShouldFailPendingInitializationImmediately() {
		// Simulate a server that never responds to the initialize request,
		// so initialization would hang until timeout if not interrupted.
		when(mockClientSession.sendRequest(eq(McpSchema.METHOD_INITIALIZE), any(), any())).thenReturn(Mono.never());

		var transportError = new McpTransportProcessException("Failed to start process with command: bad-cmd",
				new java.io.IOException("No such file or directory"), "bad-cmd");

		StepVerifier.create(initializer.withInitialization("test", init -> Mono.just(init.initializeResult())))
			// Simulate transport error arriving asynchronously
			.then(() -> initializer.handleException(transportError))
			// Should fail fast with our error wrapped in RuntimeException, NOT timeout
			.expectErrorSatisfies(error -> {
				assertThat(error).isInstanceOf(RuntimeException.class);
				assertThat(error).hasCauseInstanceOf(McpTransportProcessException.class);
				McpTransportProcessException cause = (McpTransportProcessException) error.getCause();
				assertThat(cause.getCommand()).isEqualTo("bad-cmd");
			})
			// Must complete well before the 10s initialization timeout
			.verify(Duration.ofSeconds(3));
	}

	@Test
	void handleExceptionShouldFailPendingInitializationForGenericTransportError() {
		when(mockClientSession.sendRequest(eq(McpSchema.METHOD_INITIALIZE), any(), any())).thenReturn(Mono.never());

		var transportError = new RuntimeException("Connection refused");

		StepVerifier.create(initializer.withInitialization("test", init -> Mono.just(init.initializeResult())))
			.then(() -> initializer.handleException(transportError))
			.expectErrorSatisfies(error -> {
				assertThat(error).isInstanceOf(RuntimeException.class);
				assertThat(error.getMessage()).contains("Client failed to initialize");
				assertThat(error).hasCauseInstanceOf(RuntimeException.class);
				assertThat(error.getCause().getMessage()).isEqualTo("Connection refused");
			})
			.verify(Duration.ofSeconds(3));
	}

	@Test
	void stderrLinesShouldNotFailPendingInitialization() {
		// StdioClientTransport wraps stderr lines as plain Throwable (not Exception).
		// These must NOT abort initialization — they are informational.
		when(mockClientSession.sendRequest(eq(McpSchema.METHOD_INITIALIZE), any(), any())).thenReturn(Mono.never());

		var stderrLine = new Throwable("Starting default (STDIO) server...");

		StepVerifier.create(initializer.withInitialization("test", init -> Mono.just(init.initializeResult())))
			.then(() -> initializer.handleException(stderrLine))
			// Should NOT fail — stderr lines are not transport exceptions.
			// Verify it times out (i.e. the sink was not errored).
			.expectTimeout(Duration.ofSeconds(2))
			.verify(Duration.ofSeconds(3));
	}

}
