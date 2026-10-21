package model

import (
	"bytes"
	"encoding/json"
	"fmt"
)

// Require latency explicitly: an omitted value differs from a measured zero.
func (e *Event) UnmarshalJSON(data []byte) error {
	type plain Event
	v := struct {
		*plain
		Latency *int64 `json:"latency_ms"`
	}{plain: (*plain)(e)}
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if err := d.Decode(&v); err != nil {
		return err
	}
	if v.Latency == nil {
		return fmt.Errorf("latency_ms is required")
	}
	e.LatencyMS = *v.Latency
	return nil
}
