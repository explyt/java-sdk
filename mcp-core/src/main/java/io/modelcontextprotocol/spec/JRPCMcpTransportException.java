/*
 * Copyright 2025 - 2025 the original author or authors.
 */
package io.modelcontextprotocol.spec;

import reactor.util.annotation.Nullable;

public class JRPCMcpTransportException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public final Integer statusCode;

	@Nullable
	public final String jrpcException;

	public JRPCMcpTransportException(String message, Integer statusCode, String jrpcMessage) {
		super(message);
		this.statusCode = statusCode;
		this.jrpcException = jrpcMessage;
	}

}
