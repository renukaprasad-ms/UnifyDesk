ALTER TABLE payment_provider_authorizations
    ADD COLUMN mandate_method VARCHAR(16) NOT NULL DEFAULT 'upi';

ALTER TABLE payment_provider_authorizations
    ADD CONSTRAINT ck_authorization_mandate_method
    CHECK (mandate_method IN ('card', 'emandate', 'upi'));
