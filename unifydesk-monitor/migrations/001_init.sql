CREATE TABLE IF NOT EXISTS request_events (
 id INTEGER PRIMARY KEY,
 service TEXT NOT NULL,
 method TEXT NOT NULL,
 path TEXT NOT NULL,
 status INTEGER NOT NULL CHECK(status BETWEEN 100 AND 599),
 latency_ms INTEGER NOT NULL CHECK(latency_ms >= 0),
 client_ip TEXT,
 created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_created ON request_events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_service_created ON request_events(service, created_at);
CREATE INDEX IF NOT EXISTS idx_events_status_created ON request_events(status, created_at);
