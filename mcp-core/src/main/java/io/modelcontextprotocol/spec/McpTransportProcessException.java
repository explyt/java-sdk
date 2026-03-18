/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.spec;

import java.util.List;

/**
 * Exception thrown when a stdio server process fails to start.
 * <p>
 * Previously, process startup failures were thrown as a generic {@link RuntimeException},
 * making it impossible for callers (e.g. {@code LifecycleInitializer}) to distinguish a
 * fatal "executable not found" from a recoverable transport error. A typed exception lets
 * callers apply the right recovery strategy and present actionable diagnostics instead of
 * an opaque message.
 * <p>
 * Extends {@link McpTransportException} so the existing {@code isFatalForInit} guard in
 * {@code LifecycleInitializer.handleException} can special-case it via
 * {@code instanceof McpTransportProcessException}.
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
