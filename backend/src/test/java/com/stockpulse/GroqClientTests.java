package com.stockpulse;

import com.stockpulse.ai.GroqClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class GroqClientTests {
    private HttpServer server;
    @AfterEach void stop() { if (server != null) server.stop(0); }
    private GroqClient client(int status, String response, Duration timeout, AtomicInteger calls) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            if (status == 429) exchange.getResponseHeaders().set("retry-after", "60");
            if (status == 299) { try { Thread.sleep(150); } catch (InterruptedException ignored) {} }
            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status == 299 ? 200 : status, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        return new GroqClient(new ObjectMapper(), "test-only-key", "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", timeout, 4, Clock.systemUTC());
    }
    @Test void parsesSuccessfulProviderResponse() throws Exception {
        var client = client(200, "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}", Duration.ofSeconds(2), new AtomicInteger());
        assertThat(client.complete("system", "context", null)).isEqualTo("answer");
    }
    @Test void quotaResponseStopsImmediateSubsequentRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = client(429, "quota", Duration.ofSeconds(2), calls);
        assertThatThrownBy(() -> client.complete("system", "context", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.complete("system", "context", null)).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void timeoutIsBounded() throws Exception {
        var client = client(299, "{}", Duration.ofMillis(30), new AtomicInteger());
        assertThatThrownBy(() -> client.complete("system", "context", null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void streamsActualContentDeltas() throws Exception {
        var client = client(200, "data: {\"choices\":[{\"delta\":{\"content\":\"one\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"two\"}}]}\n\ndata: [DONE]\n\n", Duration.ofSeconds(2), new AtomicInteger());
        List<String> tokens = new ArrayList<>();
        assertThat(client.complete("system", "context", tokens::add)).isEqualTo("onetwo");
        assertThat(tokens).containsExactly("one", "two");
    }
}
