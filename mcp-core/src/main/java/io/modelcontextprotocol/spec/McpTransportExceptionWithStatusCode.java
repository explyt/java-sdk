/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.spec;

public class McpTransportExceptionWithStatusCode extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public final Integer statusCode;

	public McpTransportExceptionWithStatusCode(String message, Integer statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

}
