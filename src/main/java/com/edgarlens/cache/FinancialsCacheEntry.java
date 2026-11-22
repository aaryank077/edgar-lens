package com.edgarlens.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Normalized financials for one company, stored as JSON so SEC isn't hit on every request. */
@Entity
@Table(name = "financials_cache")
public class FinancialsCacheEntry {

    @Id
    @Column(length = 10)
    private String cik;

    @Column(nullable = false, length = 16)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected FinancialsCacheEntry() {
    }

    public FinancialsCacheEntry(String cik, String ticker, String name, String payload, Instant fetchedAt) {
        this.cik = cik;
        this.ticker = ticker;
        this.name = name;
        this.payload = payload;
        this.fetchedAt = fetchedAt;
    }

    public String getCik() { return cik; }
    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getPayload() { return payload; }
    public Instant getFetchedAt() { return fetchedAt; }
}
