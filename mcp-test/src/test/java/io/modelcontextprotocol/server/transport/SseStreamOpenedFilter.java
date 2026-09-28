/*
 * Copyright 2026 - 2026 the original author or authors.
 */

package io.modelcontextprotocol.server.transport;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

public class SseStreamOpenedFilter implements Filter {

	private final AtomicBoolean sseStreamOpened = new AtomicBoolean();

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		chain.doFilter(request, response);
		if (request instanceof HttpServletRequest httpRequest && "GET".equals(httpRequest.getMethod())
				&& httpRequest.isAsyncStarted()) {
			this.sseStreamOpened.set(true);
		}
	}

	public boolean isSseStreamOpened() {
		return this.sseStreamOpened.get();
	}

}
