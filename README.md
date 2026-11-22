# EDGAR Lens

A Spring Boot service that turns raw SEC EDGAR XBRL filings into clean annual financials and a
discounted cash flow (DCF) valuation for any US public company.

```
GET /api/companies/AAPL/valuation
→ FY2026 FCF $136.1B · growth 5.5% (revenue CAGR FY2021–FY2026) · EV $2.44T · $163.14/share
```

## How it works

```
ticker ──► SecClient ──► XbrlNormalizer ──► Postgres cache ──► DcfModel ──► JSON
           rate-limited   tag fallback,       24h TTL           projections,
           + retries      restatements,                         terminal value,
                          full-year filter                      sensitivity grid
```

- **`SecClient`**: resolves tickers to CIKs and fetches `companyfacts` JSON. All calls share a
  rate limiter (SEC allows 10 requests/second) and retry 429/5xx responses with exponential backoff.
- **`XbrlNormalizer`**: the hard part. Companies report the same figure under different XBRL tags,
  and those tags change over time (e.g. `SalesRevenueNet` → `RevenueFromContractWithCustomerExcludingAssessedTax`
  after ASC 606). Each period takes the highest-priority tag with a value, and records which tag it used.
  It also drops quarterly figures embedded in 10-Ks, prefers the latest filing so restatements win, and
  keys years by period end date (SEC's `fy` field is the *filing's* year, which is wrong for comparatives).
- **`DcfModel`**: projects free cash flow (operating cash flow − capex), adds a Gordon-growth terminal
  value, discounts to present, and bridges enterprise value to equity value per share. When no growth
  rate is given, it uses the 5-year revenue CAGR, clamped to [-5%, 15%]. Returns a 3×3 sensitivity grid
  over discount rate and terminal growth.
- **Cache**: normalized results are stored in Postgres (schema managed by Flyway) so repeat requests don't hit SEC.

## API

| Endpoint | Description |
|---|---|
| `GET /api/companies/{ticker}/financials` | Normalized annual history, with the source XBRL tag for every figure |
| `GET /api/companies/{ticker}/valuation` | DCF. Optional query params: `growth`, `discountRate` (default 0.09), `terminalGrowth` (default 0.025), `years` (default 5) |
| `GET /actuator/health` | Health check |

Errors use RFC 9457 problem details: 404 unknown ticker, 400 invalid assumptions, 422 not enough data
(e.g. negative free cash flow), 503 SEC unavailable.

## Running it

SEC requires a User-Agent with contact info. Use your own name and email:

```bash
export SEC_USER_AGENT="Your Name your-email@example.com"
docker compose up --build
curl localhost:8080/api/companies/MSFT/valuation
```

Set `APP_PORT=8081` (or `DB_PORT`) if 8080 or 5432 is already in use.

Without Java installed locally, run the tests in a container:

```bash
docker run --rm -v "$PWD":/src -w /src maven:3.9-eclipse-temurin-21 mvn -B verify
```

## Testing

32 tests, ~90% instruction coverage (JaCoCo report in `target/site/jacoco`):

- Normalizer tests against a fixture that covers tag switches, restatements and quarterly noise
- DCF math checked against a hand-computed example
- `SecClient` tested against a real local HTTP server replaying 429/503/403 responses
- Controller tests for every error mapping
- A full Spring context test with H2 + Flyway for cache hits and TTL expiry

CI (GitHub Actions) runs `mvn verify` and builds the Docker image on every push and PR.

## Known limitations

- **Single-year base FCF.** The projection starts from the latest year's free cash flow, so a year of
  unusually heavy capex (Amazon in FY2025) drags the valuation down. Normalizing over several years is next.
- **Debt** is `LongTermDebt` only; leases and short-term borrowings aren't included yet.
- **Discount rate** is an input, not a computed WACC.
- Only US GAAP filers (most foreign private issuers file IFRS 20-Fs).

## Roadmap

- [ ] Normalize base FCF (multi-year average or FCF margin × revenue)
- [ ] Compute WACC from beta, risk-free rate and capital structure
- [ ] Deploy to AWS ECS Fargate + RDS; scheduled Lambda that refreshes cached companies when new 10-Ks land
- [ ] Accuracy check: compare parsed figures to reported values across the S&P 500
- [ ] Peer comparison endpoint
