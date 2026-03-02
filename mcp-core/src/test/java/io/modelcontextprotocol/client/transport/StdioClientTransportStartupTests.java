/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.client.transport;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.spec.McpTransportProcessException;
import io.modelcontextprotocol.spec.json.gson.GsonMcpJsonMapper;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StdioClientTransport} startup failure handling.
 * <p>
 * Verifies that when a process fails to start (e.g. command not found), the transport:
 * <ul>
 * <li>Throws {@link McpTransportProcessException} with the correct command</li>
 * <li>Forwards the error to the registered exception handler</li>
 * </ul>
 */
class StdioClientTransportStartupTests {

	private static final String NONEXISTENT_COMMAND = "nonexistent-command-that-does-not-exist-12345";

	private static final GsonMcpJsonMapper JSON_MAPPER = new GsonMcpJsonMapper();

	@Test
	void connectShouldThrowMcpTransportProcessExceptionForInvalidCommand() {
		var params = ServerParameters.builder(NONEXISTENT_COMMAND).build();
		var transport = new StdioClientTransport(params, JSON_MAPPER);

		StepVerifier.create(transport.connect(mono -> mono)).expectErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpTransportProcessException.class);
			McpTransportProcessException processError = (McpTransportProcessException) error;
			assertThat(processError.getCommand()).isEqualTo(NONEXISTENT_COMMAND);
			assertThat(processError.getMessage()).contains(NONEXISTENT_COMMAND);
			assertThat(processError.getCause()).isNotNull();
		}).verify(Duration.ofSeconds(5));
	}

	@Test
	void connectShouldForwardStartupErrorToExceptionHandler() {
		var params = ServerParameters.builder(NONEXISTENT_COMMAND).build();
		var transport = new StdioClientTransport(params, JSON_MAPPER);
		AtomicReference<Throwable> capturedError = new AtomicReference<>();

		transport.setExceptionHandler(capturedError::set);

		StepVerifier.create(transport.connect(mono -> mono))
			.expectError(McpTransportProcessException.class)
			.verify(Duration.ofSeconds(5));

		assertThat(capturedError.get()).isNotNull();
		assertThat(capturedError.get()).isInstanceOf(McpTransportProcessException.class);
		assertThat(((McpTransportProcessException) capturedError.get()).getCommand()).isEqualTo(NONEXISTENT_COMMAND);
	}

}
