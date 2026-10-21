package handler

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/middleware"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/model"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/service"
)

type Handler struct {
	monitoring service.Monitoring
	maxBody    int64
	log        *slog.Logger
}

func New(m service.Monitoring, key string, maxBody int64, log *slog.Logger) http.Handler {
	h := Handler{m, maxBody, log}
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		middleware.Error(w, 404, "not found")
	})
	register := func(path, method string, fn http.HandlerFunc, auth bool) {
		var next http.Handler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != method {
				w.Header().Set("Allow", method)
				middleware.Error(w, 405, "method not allowed")
				return
			}
			fn(w, r)
		})
		if auth {
			next = middleware.Auth(key, next)
		}
		mux.Handle(path, next)
	}
	register("/health", "GET", func(w http.ResponseWriter, r *http.Request) {
		middleware.JSON(w, 200, map[string]string{"status": "UP", "service": "unifydesk-monitor"})
	}, false)
	register("/ready", "GET", h.ready, false)
	register("/api/v1/events", "POST", h.event, true)
	register("/api/v1/events/batch", "POST", h.batch, true)
	register("/api/v1/stats", "GET", h.stats, true)
	return middleware.Observe(log, mux)
}

func (h Handler) ready(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if h.monitoring.Store.Ping(ctx) != nil {
		middleware.Error(w, 503, "unavailable")
		return
	}
	middleware.JSON(w, 200, map[string]string{"status": "UP"})
}

func (h Handler) decode(w http.ResponseWriter, r *http.Request, v any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, h.maxBody)
	defer r.Body.Close()
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	err := d.Decode(v)
	if err == nil {
		var extra any
		err = d.Decode(&extra)
		if err == io.EOF {
			return true
		}
	}
	var size *http.MaxBytesError
	if errors.As(err, &size) {
		middleware.Error(w, 413, "request body too large")
	} else {
		middleware.Error(w, 400, "invalid JSON, unknown fields or trailing data")
	}
	return false
}

func (h Handler) event(w http.ResponseWriter, r *http.Request) {
	var e model.Event
	if !h.decode(w, r, &e) {
		return
	}
	h.insert(w, r, []model.Event{e}, true)
}

func (h Handler) batch(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Events []model.Event `json:"events"`
	}
	if !h.decode(w, r, &b) {
		return
	}
	if len(b.Events) < 1 || len(b.Events) > 500 {
		middleware.Error(w, 400, "batch must contain 1-500 events")
		return
	}
	h.insert(w, r, b.Events, false)
}

func (h Handler) insert(w http.ResponseWriter, r *http.Request, events []model.Event, single bool) {
	for _, e := range events {
		if err := service.Validate(e); err != nil {
			middleware.Error(w, 400, err.Error())
			return
		}
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	if err := h.monitoring.Insert(ctx, events, single); err != nil {
		h.failure(w, err)
		return
	}
	middleware.JSON(w, 201, map[string]int{"inserted": len(events)})
}

func (h Handler) failure(w http.ResponseWriter, err error) {
	h.log.Error("database operation failed", "error", err)
	w.Header().Set("Retry-After", "1")
	middleware.Error(w, 503, "database unavailable")
}

func (h Handler) stats(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	now := time.Now().UTC()
	f := model.Filter{Service: q.Get("service"), From: now.Add(-24 * time.Hour), To: now}
	var err error
	for k, v := range q {
		if k != "service" && k != "from" && k != "to" || len(v) != 1 {
			middleware.Error(w, 400, "invalid query parameters")
			return
		}
	}
	if len(f.Service) > 128 {
		middleware.Error(w, 400, "service is too long")
		return
	}
	if s := q.Get("to"); s != "" {
		f.To, err = time.Parse(time.RFC3339, s)
		if err != nil {
			middleware.Error(w, 400, "invalid to timestamp")
			return
		}
		f.From = f.To.Add(-24 * time.Hour)
	}
	if s := q.Get("from"); s != "" {
		f.From, err = time.Parse(time.RFC3339, s)
		if err != nil {
			middleware.Error(w, 400, "invalid from timestamp")
			return
		}
	}
	// Normalize offsets before querying UTC timestamps.
	f.From = f.From.UTC()
	f.To = f.To.UTC()
	if !f.From.Before(f.To) || f.To.Sub(f.From) > 31*24*time.Hour {
		middleware.Error(w, 400, "time range must be positive and at most 31 days")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	s, err := h.monitoring.Store.GetStats(ctx, f)
	if err != nil {
		h.failure(w, err)
		return
	}
	middleware.JSON(w, 200, s)
}
