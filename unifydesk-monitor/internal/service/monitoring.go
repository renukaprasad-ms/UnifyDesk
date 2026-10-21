package service

import (
	"context"
	"fmt"
	"log/slog"
	"net/netip"
	"strings"
	"time"
	"unicode"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/model"
)

type Store interface {
	Ping(context.Context) error
	InsertEvent(context.Context, model.Record) error
	InsertEventsBatch(context.Context, []model.Record) error
	GetStats(context.Context, model.Filter) (model.Stats, error)
	DeleteOldEvents(context.Context, time.Time, int) (int64, error)
}
type Monitoring struct {
	Store Store
}

func Validate(e model.Event) error {
	if strings.TrimSpace(e.Service) == "" || len(e.Service) > 128 || strings.IndexFunc(e.Service, unicode.IsControl) >= 0 {
		return fmt.Errorf("service must contain 1-128 bytes without control characters")
	}
	switch e.Method {
	case "GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH":
	default:
		return fmt.Errorf("invalid HTTP method")
	}
	if !strings.HasPrefix(e.Path, "/") || len(e.Path) > 2048 || strings.IndexFunc(e.Path, unicode.IsControl) >= 0 {
		return fmt.Errorf("path must start with /, contain no control characters and be at most 2048 bytes")
	}
	if e.Status < 100 || e.Status > 599 {
		return fmt.Errorf("status must be between 100 and 599")
	}
	if e.LatencyMS < 0 {
		return fmt.Errorf("latency_ms must be nonnegative")
	}
	if e.ClientIP != "" {
		if len(e.ClientIP) > 45 {
			return fmt.Errorf("invalid client_ip")
		}
		ip, err := netip.ParseAddr(e.ClientIP)
		if err != nil || ip.Zone() != "" {
			return fmt.Errorf("invalid client_ip")
		}
	}
	return nil
}

func (m Monitoring) Insert(ctx context.Context, events []model.Event, single bool) error {
	records := make([]model.Record, len(events))
	now := time.Now().UTC()
	for i, e := range events {
		records[i] = model.Record{Event: e, CreatedAt: now}
	}
	if single {
		return m.Store.InsertEvent(ctx, records[0])
	}
	return m.Store.InsertEventsBatch(ctx, records)
}

// Cleanup limits each cycle to 100 short transactions to avoid monopolizing SQLite.
func (m Monitoring) Cleanup(ctx context.Context, days int) (int64, error) {
	cutoff := time.Now().UTC().Add(-time.Duration(days) * 24 * time.Hour)
	var total int64
	for i := 0; i < 100; i++ {
		n, err := m.Store.DeleteOldEvents(ctx, cutoff, 1000)
		total += n
		if err != nil {
			return total, err
		}
		if n < 1000 {
			break
		}
	}
	return total, nil
}

func (m Monitoring) RunRetention(ctx context.Context, days int, interval time.Duration, log *slog.Logger) {
	run := func() {
		c, cancel := context.WithTimeout(ctx, 30*time.Second)
		defer cancel()
		n, err := m.Cleanup(c, days)
		if err != nil && ctx.Err() == nil {
			log.Error("retention failed", "error", err)
		} else if err == nil {
			log.Info("retention complete", "deleted", n)
		}
	}
	run()
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			run()
		}
	}
}
