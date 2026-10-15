alter table user_preferences drop constraint ck_user_preferences_appearance_mode;
alter table user_preferences add constraint ck_user_preferences_appearance_mode
  check (appearance_mode in ('SYSTEM','LIGHT','DARK'));
alter table user_preferences alter column appearance_mode set default 'SYSTEM';
