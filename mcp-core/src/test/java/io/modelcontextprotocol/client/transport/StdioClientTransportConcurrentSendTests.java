/*
 * Copyright 2026 - 2026 the original author or authors.
 */

package io.modelcontextprotocol.client.transport;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.json.gson.GsonMcpJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class StdioClientTransportConcurrentSendTests {

	private static final int MESSAGE_COUNT = 500;

	private final StdioClientTransport transport = new StdioClientTransport(
			ServerParameters.builder("sh").args(List.of("-c", "cat")).build(), new GsonMcpJsonMapper());

	@AfterEach
	void closeTransport() {
		this.transport.closeGracefully().block(Duration.ofSeconds(5));
	}

	@Test
	void concurrentSendersAreAllDeliveredToTheServerProcess() throws InterruptedException {
		CountDownLatch echoedMessages = new CountDownLatch(MESSAGE_COUNT);
		StepVerifier.create(this.transport.connect(message -> message.doOnNext(echoed -> echoedMessages.countDown())))
			.verifyComplete();

		Flux<Integer> concurrentSends = Flux.range(0, MESSAGE_COUNT)
			.parallel(16)
			.runOn(Schedulers.parallel())
			.flatMap(i -> this.transport
				.sendMessage(
						new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, "test/notification", Map.of()))
				.thenReturn(i))
			.sequential();

		StepVerifier.create(concurrentSends).expectNextCount(MESSAGE_COUNT).verifyComplete();
		assertThat(echoedMessages.await(30, TimeUnit.SECONDS))
			.as("%d of %d messages never reached the server process", echoedMessages.getCount(), MESSAGE_COUNT)
			.isTrue();
	}

}
