package com.edgarlens.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal client for SEC EDGAR. Every request goes through a shared rate limiter
 * and is retried with exponential backoff on HTTP 429 and 5xx responses.
 */
public class SecClient {

    private static final Logger log = LoggerFactory.getLogger(SecClient.class);

    private final SecProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final RateLimiter rateLimiter;

    private volatile Map<String, Company> tickerIndex;

    public SecClient(SecProperties props, HttpClient http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
        this.rateLimiter = new RateLimiter(props.requestsPerSecond());
    }

    /** Resolves a ticker (case-insensitive) to its SEC registrant. */
    public Company lookup(String ticker) {
        String key = ticker.trim().toUpperCase(Locale.ROOT);
        return Optional.ofNullable(tickerIndex().get(key))
                .orElseThrow(() -> new UnknownTickerException(ticker));
    }

    /** Fetches every XBRL fact the company has ever reported. */
    public JsonNode companyFacts(String cik) {
        return getJson(props.dataBaseUrl() + "/api/xbrl/companyfacts/CIK" + cik + ".json");
    }

    private Map<String, Company> tickerIndex() {
        Map<String, Company> index = tickerIndex;
        if (index == null) {
            synchronized (this) {
                if (tickerIndex == null) {
                    tickerIndex = loadTickerIndex();
                }
                index = tickerIndex;
            }
        }
        return index;
    }

    private Map<String, Company> loadTickerIndex() {
        // Shape: {"0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."}, ...}
        JsonNode root = getJson(props.tickersUrl());
        Map<String, Company> index = new HashMap<>();
        root.forEach(entry -> {
            String ticker = entry.path("ticker").asText().toUpperCase(Locale.ROOT);
            String cik = String.format("%010d", entry.path("cik_str").asLong());
            index.putIfAbsent(ticker, new Company(ticker, cik, entry.path("title").asText()));
        });
        log.info("Loaded {} tickers from SEC", index.size());
        return Map.copyOf(index);
    }

    JsonNode getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", props.userAgent())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        Duration backoff = props.initialBackoff();
        Exception lastError = null;
        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            try {
                rateLimiter.acquire();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 200) {
                    return mapper.readTree(response.body());
                }
                if (status == 404) {
                    throw new SecUnavailableException("SEC returned 404 for " + url);
                }
                if (status != 429 && status < 500) {
                    throw new SecUnavailableException("SEC returned HTTP " + status + " for " + url
                            + (status == 403 ? " (check edgar.sec.user-agent)" : ""));
                }
                lastError = new SecUnavailableException("SEC returned HTTP " + status);
                log.warn("SEC returned {} for {} (attempt {}/{})", status, url, attempt, props.maxAttempts());
            } catch (IOException e) {
                lastError = e;
                log.warn("I/O error calling {} (attempt {}/{}): {}", url, attempt, props.maxAttempts(), e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecUnavailableException("Interrupted while calling SEC", e);
            }

            if (attempt < props.maxAttempts()) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        throw new SecUnavailableException("SEC request failed after " + props.maxAttempts() + " attempts: " + url, lastError);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecUnavailableException("Interrupted during backoff", e);
        }
    }
}
