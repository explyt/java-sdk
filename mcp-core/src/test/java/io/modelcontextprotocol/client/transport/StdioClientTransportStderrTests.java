/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.client.transport;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.spec.json.gson.GsonMcpJsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that stderr output from a running process does NOT propagate to the registered
 * exception handler. Only process-termination / startup failures should do so.
 *
 * <p>
 * The scenario: a live process writes diagnostic lines to its stderr stream (which is
 * completely normal for MCP servers that use stderr for logging). Those lines must be
 * silently consumed (logged) by the transport and must NOT be forwarded to the handler
 * set via {@link StdioClientTransport#setExceptionHandler}.
 */
class StdioClientTransportStderrTests {

	private static final GsonMcpJsonMapper JSON_MAPPER = new GsonMcpJsonMapper();

	/**
	 * Creates a transport whose process writes two lines to stderr and then stays alive
	 * (blocking on stdin via {@code cat}), so it is a "healthy" running server.
	 */
	private static StdioClientTransport transportWithStderrEmittingProcess() {
		// Use 'sh -c' to write stderr lines and then block on stdin (simulating a live
		// server). 'cat' keeps stdin open so the process does not exit on its own.
		var params = ServerParameters.builder("sh")
			.args(List.of("-c", "echo 'INFO: server starting' >&2; echo 'DEBUG: ready' >&2; cat"))
			.build();

		return new StdioClientTransport(params, JSON_MAPPER);
	}

	@Test
	void stderrLinesShouldNotBeForwardedToExceptionHandler() throws InterruptedException {
		var transport = transportWithStderrEmittingProcess();

		List<Throwable> capturedErrors = new CopyOnWriteArrayList<>();
		transport.setExceptionHandler(capturedErrors::add);

		// Connect and let the process run briefly so stderr lines have time to arrive.
		transport.connect(mono -> mono).subscribe();

		// Wait long enough for the stderr reader thread to process both lines.
		Thread.sleep(500);

		transport.closeGracefully().block(Duration.ofSeconds(5));

		assertThat(capturedErrors).as("stderr log lines from a running process must NOT reach the exception handler")
			.isEmpty();
	}

	@Test
	void stderrLinesShouldBeAvailableViaErrorSink() throws InterruptedException {
		var transport = transportWithStderrEmittingProcess();

		List<String> capturedLines = new CopyOnWriteArrayList<>();
		CountDownLatch latch = new CountDownLatch(2);

		// Subscribe to the error sink BEFORE connecting so we capture all lines.
		transport.getErrorSink().asFlux().subscribe(line -> {
			capturedLines.add(line);
			latch.countDown();
		});

		transport.connect(mono -> mono).subscribe();

		// Wait for both expected stderr lines.
		assertThat(latch.await(5, TimeUnit.SECONDS)).as("Both stderr lines should be emitted to the error sink")
			.isTrue();

		transport.closeGracefully().block(Duration.ofSeconds(5));

		assertThat(capturedLines).hasSize(2);
		assertThat(capturedLines).anyMatch(l -> l.contains("server starting"));
		assertThat(capturedLines).anyMatch(l -> l.contains("ready"));
	}

}
