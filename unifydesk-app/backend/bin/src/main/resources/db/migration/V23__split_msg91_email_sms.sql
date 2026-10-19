update channel_connections
set provider_type = 'MSG91_SMS'
where provider_type = 'MSG91';

update broadcast_sender_connections
set provider = 'MSG91_SMS'
where provider = 'MSG91';

update broadcast_senders
set provider = 'MSG91_SMS'
where provider = 'MSG91';

update broadcast_runs
set provider_type = 'MSG91_SMS'
where provider_type = 'MSG91';

update broadcast_delivery_attempts
set provider_type = 'MSG91_SMS'
where provider_type = 'MSG91';
