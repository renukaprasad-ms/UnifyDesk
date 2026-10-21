package config

import "testing"

func TestLoad(t *testing.T) {
	t.Setenv("MONITOR_API_KEY", "")
	if _, e := Load(); e == nil {
		t.Fatal("missing key accepted")
	}
	t.Setenv("MONITOR_API_KEY", "secret")
	for k, v := range map[string]string{
		"PORT":                 "0",
		"EVENT_RETENTION_DAYS": "-1",
		"CLEANUP_INTERVAL":     "0s",
		"MAX_BODY_BYTES":       "-1",
		"LOG_LEVEL":            "invalid",
	} {
		t.Run(k, func(t *testing.T) {
			t.Setenv(k, v)
			if _, e := Load(); e == nil {
				t.Fatal("invalid config accepted")
			}
		})
	}
}
