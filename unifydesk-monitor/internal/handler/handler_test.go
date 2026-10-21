package handler_test

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/handler"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/model"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/repository"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/service"
)

const valid = `{"service":"api","method":"GET","path":"/contacts","status":200,"latency_ms":40}`

func setup(t *testing.T) (*repository.Repository, http.Handler) {
	t.Helper()
	db, err := repository.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		db.Close()
	})
	return db, handler.New(service.Monitoring{Store: db}, "secret", 4096, slog.New(slog.NewJSONHandler(io.Discard, nil)))
}

func request(h http.Handler, method, path, body, token string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if token != "" {
		r.Header.Set("Authorization", token)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}

func TestHealthReadinessAuth(t *testing.T) {
	db, h := setup(t)
	for _, p := range []string{"/health", "/ready"} {
		if w := request(h, "GET", p, "", ""); w.Code != 200 {
			t.Fatal(w.Code)
		}
	}
	for _, token := range []string{"", "Bearer wrong", "Basic secret"} {
		for _, p := range []string{"/api/v1/events", "/api/v1/events/batch", "/api/v1/stats"} {
			if w := request(h, "GET", p, "", token); w.Code != 401 {
				t.Fatal(p, w.Code)
			}
		}
	}
	db.Close()
	if w := request(h, "GET", "/ready", "", ""); w.Code != 503 {
		t.Fatal(w.Code)
	}
	if w := request(h, "POST", "/api/v1/events", valid, "Bearer secret"); w.Code != 503 {
		t.Fatal(w.Code)
	}
}

func TestValidationAndBatchAtomicity(t *testing.T) {
	db, h := setup(t)
	for _, body := range []string{
		`{}`,
		`null`,
		strings.Replace(valid, `"GET"`, `"INVALID"`, 1),
		strings.Replace(valid, `200`, `600`, 1),
		strings.Replace(valid, `40`, `-1`, 1),
		strings.Replace(valid, `"/contacts"`, `"relative"`, 1),
		strings.Replace(valid, `"api"`, `""`, 1),
		strings.TrimSuffix(valid, "}") + `,"client_ip":"invalid"}`,
		strings.TrimSuffix(valid, "}") + `,"extra":true}`,
		valid + ` {}`,
		`{"events":[]}`,
	} {
		w := request(h, "POST", "/api/v1/events", body, "Bearer secret")
		if w.Code != 400 {
			t.Fatalf("%s: %d", body, w.Code)
		}
	}
	huge := strings.Replace(valid, "/contacts", "/"+strings.Repeat("a", 5000), 1)
	if w := request(h, "POST", "/api/v1/events", huge, "Bearer secret"); w.Code != 413 {
		t.Fatal(w.Code)
	}
	body := `{"events":[` + valid + `,{}]}`
	if w := request(h, "POST", "/api/v1/events/batch", body, "Bearer secret"); w.Code != 400 {
		t.Fatal(w.Code)
	}
	s, err := db.GetStats(context.Background(), model.Filter{From: time.Now().Add(-time.Hour), To: time.Now().Add(time.Hour)})
	if err != nil || s.Total != 0 {
		t.Fatal(s, err)
	}
	if w := request(h, "POST", "/api/v1/events", valid, "Bearer secret"); w.Code != 201 {
		t.Fatal(w.Code, w.Body)
	}
	body = `{"events":[` + strings.Replace(valid, "200", "404", 1) + `,` + strings.Replace(valid, "200", "500", 1) + `]}`
	if w := request(h, "POST", "/api/v1/events/batch", body, "Bearer secret"); w.Code != 201 {
		t.Fatal(w.Code, w.Body)
	}
	w := request(h, "GET", "/api/v1/stats?service=api&to="+time.Now().Add(time.Hour).UTC().Format(time.RFC3339), "", "Bearer secret")
	var stats model.Stats
	if err = json.Unmarshal(w.Body.Bytes(), &stats); err != nil {
		t.Fatal(err)
	}
	if w.Code != 200 ||
		stats.Total != 3 ||
		stats.Successful != 1 ||
		stats.ClientErrors != 1 ||
		stats.ServerErrors != 1 ||
		stats.AverageLatency != 40 ||
		stats.ErrorRate < 66.6 {
		t.Fatal(w.Code, stats)
	}
}

func TestStatsEmptyAndRanges(t *testing.T) {
	_, h := setup(t)
	w := request(h, "GET", "/api/v1/stats", "", "Bearer secret")
	var s model.Stats
	json.Unmarshal(w.Body.Bytes(), &s)
	if w.Code != 200 || s.Total != 0 || s.ErrorRate != 0 || s.AverageLatency != 0 {
		t.Fatal(w.Code, s)
	}
	for _, q := range []string{
		"from=no",
		"from=2020-01-01T00:00:00Z&to=2020-03-01T00:00:00Z",
		"from=2020-01-02T00:00:00Z&to=2020-01-01T00:00:00Z",
	} {
		if w = request(h, "GET", "/api/v1/stats?"+q, "", "Bearer secret"); w.Code != 400 {
			t.Fatal(q, w.Code)
		}
	}
}

func TestRepositoryRollbackRetentionAndRestart(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "monitor.db")
	db, err := repository.Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	event := model.Record{
		Event: model.Event{
			Service:   "api",
			Method:    "GET",
			Path:      "/",
			Status:    200,
			LatencyMS: 10,
		},
		CreatedAt: time.Now().Add(-8 * 24 * time.Hour),
	}
	bad := event
	bad.Status = 700
	if err = db.InsertEventsBatch(ctx, []model.Record{event, bad}); err == nil {
		t.Fatal("expected constraint failure")
	}
	f := model.Filter{From: time.Now().Add(-30 * 24 * time.Hour), To: time.Now().Add(time.Hour)}
	s, err := db.GetStats(ctx, f)
	if err != nil || s.Total != 0 {
		t.Fatal(s, err)
	}
	if err = db.InsertEvent(ctx, event); err != nil {
		t.Fatal(err)
	}
	event.CreatedAt = time.Now()
	if err = db.InsertEvent(ctx, event); err != nil {
		t.Fatal(err)
	}
	n, err := (service.Monitoring{Store: db}).Cleanup(ctx, 7)
	if err != nil || n != 1 {
		t.Fatal(n, err)
	}
	db.Close()
	db, err = repository.Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	s, err = db.GetStats(ctx, f)
	if err != nil || s.Total != 1 {
		t.Fatal(s, err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if err = db.InsertEvent(cancelled, event); err == nil {
		t.Fatal("expected cancellation")
	}
}

func TestRetentionWorkerCancellation(t *testing.T) {
	db, _ := setup(t)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		defer close(done)
		(service.Monitoring{Store: db}).RunRetention(ctx, 7, time.Hour, slog.New(slog.NewJSONHandler(io.Discard, nil)))
	}()
	cancel()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("worker did not stop")
	}
}

func TestBatchBoundsAndFailure(t *testing.T) {
	db, h := setup(t)
	for _, body := range []string{`{"events":[]}`, `{"events":[` + strings.Repeat(valid+",", 500) + valid + `]}`} {
		w := request(h, "POST", "/api/v1/events/batch", body, "Bearer secret")
		if w.Code != 400 && w.Code != 413 {
			t.Fatal(w.Code)
		}
	}
	db.Close()
	if w := request(h, "POST", "/api/v1/events/batch", `{"events":[`+valid+`]}`, "Bearer secret"); w.Code != 503 {
		t.Fatal(w.Code)
	}
	if w := request(h, "GET", "/api/v1/stats", "", "Bearer secret"); w.Code != 503 {
		t.Fatal(w.Code)
	}
}

func TestBatchMaximum(t *testing.T) {
	db, _ := setup(t)
	h := handler.New(service.Monitoring{Store: db}, "secret", 1<<20, slog.New(slog.NewJSONHandler(io.Discard, nil)))
	for _, n := range []int{500, 501} {
		body := `{"events":[` + strings.Repeat(valid+",", n-1) + valid + `]}`
		w := request(h, "POST", "/api/v1/events/batch", body, "Bearer secret")
		expected := 201
		if n == 501 {
			expected = 400
		}
		if w.Code != expected {
			t.Fatal(n, w.Code, w.Body)
		}
	}
}
