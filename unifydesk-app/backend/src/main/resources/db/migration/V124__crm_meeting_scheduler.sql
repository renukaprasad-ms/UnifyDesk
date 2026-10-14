-- V124__crm_meeting_scheduler.sql
-- Meeting Scheduler, Shareable Booking Links & Calendar Sync

CREATE TABLE IF NOT EXISTS crm_booking_types (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(140) NOT NULL,
    description VARCHAR(1000),
    duration_minutes INT NOT NULL DEFAULT 30,
    location_type VARCHAR(32) NOT NULL DEFAULT 'GOOGLE_MEET',
    location_details VARCHAR(500),
    host_user_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    working_hours_start VARCHAR(10) NOT NULL DEFAULT '09:00',
    working_hours_end VARCHAR(10) NOT NULL DEFAULT '18:00',
    days_of_week JSONB NOT NULL DEFAULT '["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"]'::jsonb,
    buffer_before_minutes INT NOT NULL DEFAULT 0,
    buffer_after_minutes INT NOT NULL DEFAULT 10,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_booking_types_company 
    ON crm_booking_types (company_id, is_active);

CREATE INDEX IF NOT EXISTS idx_crm_booking_types_slug 
    ON crm_booking_types (slug);

CREATE TABLE IF NOT EXISTS crm_meeting_bookings (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    booking_type_id BIGINT NOT NULL REFERENCES crm_booking_types(id) ON DELETE CASCADE,
    host_user_id BIGINT NOT NULL,
    contact_id BIGINT,
    guest_name VARCHAR(140) NOT NULL,
    guest_email VARCHAR(140) NOT NULL,
    guest_phone VARCHAR(40),
    notes TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    meeting_link VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_bookings_company_time 
    ON crm_meeting_bookings (company_id, start_time DESC);

CREATE INDEX IF NOT EXISTS idx_crm_bookings_host_time 
    ON crm_meeting_bookings (host_user_id, start_time);

CREATE INDEX IF NOT EXISTS idx_crm_bookings_contact 
    ON crm_meeting_bookings (company_id, contact_id);
