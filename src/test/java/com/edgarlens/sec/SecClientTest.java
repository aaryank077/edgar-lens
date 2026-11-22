package com.edgarlens.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a real local HTTP server that replays scripted responses. */
class SecClientTest {

    private HttpServer server;
    private final Deque<Integer> statuses = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private SecClient client;

    private static final String TICKERS = """
            {"0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."},
             "1": {"cik_str": 1018724, "ticker": "AMZN", "title": "AMAZON COM INC"}}
            """;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            int status = statuses.isEmpty() ? 200 : statuses.poll();
            String body = exchange.getRequestURI().getPath().endsWith("tickers.json") ? TICKERS : "{\"ok\": true}";
            byte[] bytes = status == 200 ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        SecProperties props = new SecProperties("EdgarLens test", base, base + "/tickers.json", 1000, 3, Duration.ofMillis(5));
        client = new SecClient(props, HttpClient.newHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void retriesRateLimitAndServerErrorsThenSucceeds() {
        statuses.add(429);
        statuses.add(503);
        JsonNode node = client.companyFacts("0000320193");
        assertThat(node.path("ok").asBoolean()).isTrue();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        statuses.add(500);
        statuses.add(500);
        statuses.add(500);
        assertThatThrownBy(() -> client.companyFacts("0000320193"))
                .isInstanceOf(SecUnavailableException.class)
                .hasMessageContaining("after 3 attempts");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        statuses.add(403);
        assertThatThrownBy(() -> client.companyFacts("0000320193"))
                .isInstanceOf(SecUnavailableException.class)
                .hasMessageContaining("user-agent");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void sendsConfiguredUserAgent() {
        client.companyFacts("0000320193");
        assertThat(userAgent.get()).isEqualTo("EdgarLens test");
    }

    @Test
    void looksUpTickersCaseInsensitivelyAndPadsCik() {
        Company amzn = client.lookup(" amzn ");
        assertThat(amzn.cik()).isEqualTo("0001018724");
        assertThat(amzn.name()).isEqualTo("AMAZON COM INC");
        client.lookup("AAPL");
        assertThat(calls.get()).as("ticker file is fetched once").isEqualTo(1);
    }

    @Test
    void unknownTickerThrows() {
        assertThatThrownBy(() -> client.lookup("NOPE")).isInstanceOf(UnknownTickerException.class);
    }
}
