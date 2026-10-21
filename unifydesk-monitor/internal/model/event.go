package model

import "time"

type Event struct {
	Service   string `json:"service"`
	Method    string `json:"method"`
	Path      string `json:"path"`
	Status    int    `json:"status"`
	LatencyMS int64  `json:"latency_ms"`
	ClientIP  string `json:"client_ip,omitempty"`
}
type Record struct {
	Event
	CreatedAt time.Time
}
type Filter struct {
	Service  string
	From, To time.Time
}
type Stats struct {
	Service        string  `json:"service"`
	Total          int64   `json:"total_requests"`
	Successful     int64   `json:"successful_requests"`
	ClientErrors   int64   `json:"client_error_requests"`
	ServerErrors   int64   `json:"server_error_requests"`
	AverageLatency float64 `json:"average_latency_ms"`
	ErrorRate      float64 `json:"error_rate_percent"`
}
