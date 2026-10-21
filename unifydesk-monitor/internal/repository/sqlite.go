package repository

import (
	"context"
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/model"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/migrations"
	_ "modernc.org/sqlite"
)

type Repository struct {
	db *sql.DB
}

func Open(ctx context.Context, path string) (*Repository, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0750); err != nil {
		return nil, err
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return nil, err
	}
	u := url.URL{Scheme: "file", Path: filepath.ToSlash(abs)}
	q := u.Query()
	q.Add("_pragma", "busy_timeout(5000)")
	q.Add("_pragma", "foreign_keys(1)")
	u.RawQuery = q.Encode()
	db, err := sql.Open("sqlite", u.String())
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	fail := func(e error) (*Repository, error) {
		db.Close()
		return nil, e
	}
	var mode string
	if err = db.QueryRowContext(ctx, "PRAGMA journal_mode=WAL").Scan(&mode); err != nil {
		return fail(err)
	}
	if mode != "wal" {
		return fail(fmt.Errorf("WAL unavailable"))
	}
	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return fail(err)
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, migrations.Initial); err != nil {
		return fail(err)
	}
	if err = tx.Commit(); err != nil {
		return fail(err)
	}
	return &Repository{db: db}, nil
}

func (r *Repository) Close() error {
	return r.db.Close()
}

func (r *Repository) Ping(ctx context.Context) error {
	return r.db.PingContext(ctx)
}

func (r *Repository) InsertEvent(ctx context.Context, e model.Record) error {
	return r.InsertEventsBatch(ctx, []model.Record{e})
}

func (r *Repository) InsertEventsBatch(ctx context.Context, events []model.Record) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO request_events (
			service, method, path, status, latency_ms, client_ip, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?)
	`)
	if err != nil {
		return err
	}
	defer stmt.Close()
	for _, e := range events {
		if _, err = stmt.ExecContext(ctx, e.Service, e.Method, e.Path, e.Status, e.LatencyMS, e.ClientIP, stamp(e.CreatedAt)); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func stamp(t time.Time) string {
	return t.UTC().Format("2006-01-02T15:04:05.000000000Z")
}

func (r *Repository) GetStats(ctx context.Context, f model.Filter) (model.Stats, error) {
	s := model.Stats{Service: f.Service}
	q := `
		SELECT
			COUNT(*),
			COALESCE(SUM(status BETWEEN 200 AND 399), 0),
			COALESCE(SUM(status BETWEEN 400 AND 499), 0),
			COALESCE(SUM(status BETWEEN 500 AND 599), 0),
			COALESCE(AVG(latency_ms), 0)
		FROM request_events
		WHERE created_at >= ? AND created_at < ?
	`
	args := []any{stamp(f.From), stamp(f.To)}
	if f.Service != "" {
		q += " AND service = ?"
		args = append(args, f.Service)
	}
	err := r.db.QueryRowContext(ctx, q, args...).Scan(&s.Total, &s.Successful, &s.ClientErrors, &s.ServerErrors, &s.AverageLatency)
	if s.Total > 0 {
		s.ErrorRate = float64(s.ClientErrors+s.ServerErrors) / float64(s.Total) * 100
	}
	return s, err
}

func (r *Repository) DeleteOldEvents(ctx context.Context, before time.Time, limit int) (int64, error) {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM request_events
		WHERE id IN (
			SELECT id FROM request_events
			WHERE created_at < ?
			ORDER BY created_at
			LIMIT ?
		)
	`, stamp(before), limit)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}
