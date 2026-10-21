package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/config"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/handler"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/repository"
	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/service"
)

func main() {
	if err := run(); err != nil {
		slog.New(slog.NewJSONHandler(os.Stderr, nil)).Error("server stopped", "error", err)
		os.Exit(1)
	}
}

func run() error {
	c, err := config.Load()
	if err != nil {
		return err
	}
	log := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: c.LogLevel}))
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	initCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	db, err := repository.Open(initCtx, c.DatabasePath)
	cancel()
	if err != nil {
		return err
	}
	defer db.Close()
	m := service.Monitoring{Store: db}
	workerCtx, stopWorker := context.WithCancel(ctx)
	done := make(chan struct{})
	go func() {
		defer close(done)
		m.RunRetention(workerCtx, c.RetentionDays, c.CleanupInterval, log)
	}()
	defer func() {
		stopWorker()
		<-done
	}()
	srv := &http.Server{
		Addr:              ":" + c.Port,
		Handler:           handler.New(m, c.APIKey, c.MaxBodyBytes, log),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      20 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}
	failures := make(chan error, 1)
	go func() {
		log.Info("listening", "port", c.Port)
		failures <- srv.ListenAndServe()
	}()
	select {
	case err = <-failures:
		if !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	case <-ctx.Done():
	}
	shutdownCtx, finish := context.WithTimeout(context.Background(), 25*time.Second)
	defer finish()
	if err = srv.Shutdown(shutdownCtx); err != nil {
		_ = srv.Close()
		return err
	}
	return nil
}
