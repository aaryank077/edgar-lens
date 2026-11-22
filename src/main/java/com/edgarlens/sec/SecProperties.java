package com.edgarlens.sec;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for talking to SEC EDGAR.
 *
 * @param userAgent         SEC asks every client to identify itself as "Name contact@example.com".
 * @param dataBaseUrl       host serving the XBRL "companyfacts" API.
 * @param tickersUrl        JSON file mapping tickers to CIK numbers.
 * @param requestsPerSecond SEC's fair-access limit is 10 requests per second.
 * @param maxAttempts       attempts per request before giving up (retries 429 and 5xx).
 * @param initialBackoff    first retry delay; doubles on each attempt.
 */
@ConfigurationProperties(prefix = "edgar.sec")
public record SecProperties(
        String userAgent,
        String dataBaseUrl,
        String tickersUrl,
        double requestsPerSecond,
        int maxAttempts,
        Duration initialBackoff) {
}
