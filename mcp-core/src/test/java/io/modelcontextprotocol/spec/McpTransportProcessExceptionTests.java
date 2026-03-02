/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.spec;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpTransportProcessException}.
 */
class McpTransportProcessExceptionTests {

	@Test
	void shouldCarryCommandAndCause() {
		var cause = new IOException("No such file or directory");
		var exception = new McpTransportProcessException("Failed to start process with command: npx", cause, "npx");

		assertThat(exception.getCommand()).isEqualTo("npx");
		assertThat(exception.getMessage()).isEqualTo("Failed to start process with command: npx");
		assertThat(exception.getCause()).isSameAs(cause);
	}

	@Test
	void shouldExtendMcpTransportException() {
		var exception = new McpTransportProcessException("msg", null, "cmd");

		assertThat(exception).isInstanceOf(McpTransportException.class);
		assertThat(exception).isInstanceOf(RuntimeException.class);
	}

}
