package config

import (
	"fmt"
	"log/slog"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port, DatabasePath, APIKey string
	RetentionDays              int
	CleanupInterval            time.Duration
	MaxBodyBytes               int64
	LogLevel                   slog.Level
}

func Load() (Config, error) {
	c := Config{
		Port:            env("PORT", "8080"),
		DatabasePath:    env("DATABASE_PATH", "./data/monitor.db"),
		APIKey:          os.Getenv("MONITOR_API_KEY"),
		RetentionDays:   7,
		CleanupInterval: time.Hour,
		MaxBodyBytes:    1048576,
	}
	if c.APIKey == "" {
		return c, fmt.Errorf("MONITOR_API_KEY is required")
	}
	p, e := strconv.Atoi(c.Port)
	if e != nil || p < 1 || p > 65535 {
		return c, fmt.Errorf("invalid PORT")
	}
	if s := os.Getenv("EVENT_RETENTION_DAYS"); s != "" {
		c.RetentionDays, e = strconv.Atoi(s)
		if e != nil || c.RetentionDays < 1 || c.RetentionDays > 3650 {
			return c, fmt.Errorf("invalid EVENT_RETENTION_DAYS")
		}
	}
	if s := os.Getenv("CLEANUP_INTERVAL"); s != "" {
		c.CleanupInterval, e = time.ParseDuration(s)
		if e != nil || c.CleanupInterval < time.Second {
			return c, fmt.Errorf("invalid CLEANUP_INTERVAL")
		}
	}
	if s := os.Getenv("MAX_BODY_BYTES"); s != "" {
		c.MaxBodyBytes, e = strconv.ParseInt(s, 10, 64)
		if e != nil || c.MaxBodyBytes < 1024 || c.MaxBodyBytes > 16<<20 {
			return c, fmt.Errorf("invalid MAX_BODY_BYTES")
		}
	}
	if e = c.LogLevel.UnmarshalText([]byte(env("LOG_LEVEL", "info"))); e != nil {
		return c, fmt.Errorf("invalid LOG_LEVEL")
	}
	return c, nil
}

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
