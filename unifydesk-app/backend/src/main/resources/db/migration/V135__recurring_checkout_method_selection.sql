ALTER TABLE payment_provider_authorizations
    DROP CONSTRAINT ck_authorization_mandate_method;

ALTER TABLE payment_provider_authorizations
    ADD CONSTRAINT ck_authorization_mandate_method
    CHECK (mandate_method IN ('auto', 'card', 'emandate', 'upi'));
