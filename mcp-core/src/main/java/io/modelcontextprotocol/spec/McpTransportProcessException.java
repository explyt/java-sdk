/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.spec;

import java.util.List;

/**
 * Exception thrown when a stdio server process fails to start.
 * <p>
 * Carries the command and arguments so that callers can present actionable diagnostics
 * (e.g. "executable not found") instead of a generic error message.
 *
 * @author Veai Agent
 */
public class McpTransportProcessException extends McpTransportException {

	private static final long serialVersionUID = 1L;

	private final String command;

	public McpTransportProcessException(String message, Throwable cause, String command) {
		super(message, cause);
		this.command = command;
	}

	public String getCommand() {
		return this.command;
	}

}
