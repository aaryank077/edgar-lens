CREATE TABLE financials_cache (
    cik        VARCHAR(10)              PRIMARY KEY,
    ticker     VARCHAR(16)              NOT NULL,
    name       VARCHAR(255)             NOT NULL,
    payload    TEXT                     NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL
);
