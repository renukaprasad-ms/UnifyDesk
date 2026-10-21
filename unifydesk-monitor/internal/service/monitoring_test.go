package service

import (
	"strings"
	"testing"

	"github.com/renukaprasad-ms/UnifyDesk/unifydesk-monitor/internal/model"
)

func TestValidate(t *testing.T) {
	base := model.Event{
		Service: "api",
		Method:  "GET",
		Path:    "/",
		Status:  200,
	}
	for name, change := range map[string]func(*model.Event){
		"service length": func(e *model.Event) {
			e.Service = strings.Repeat("s", 129)
		},
		"path length": func(e *model.Event) {
			e.Path = "/" + strings.Repeat("a", 2048)
		},
		"path control": func(e *model.Event) {
			e.Path = "/\n"
		},
		"ip zone": func(e *model.Event) {
			e.ClientIP = "fe80::1%eth0"
		},
		"ip invalid": func(e *model.Event) {
			e.ClientIP = "bad"
		},
		"status": func(e *model.Event) {
			e.Status = 99
		},
		"latency": func(e *model.Event) {
			e.LatencyMS = -1
		},
	} {
		t.Run(name, func(t *testing.T) {
			e := base
			change(&e)
			if Validate(e) == nil {
				t.Fatal("invalid event accepted")
			}
		})
	}
	for _, ip := range []string{"", "203.0.113.1", "2001:db8::1"} {
		e := base
		e.ClientIP = ip
		if err := Validate(e); err != nil {
			t.Fatal(err)
		}
	}
}
