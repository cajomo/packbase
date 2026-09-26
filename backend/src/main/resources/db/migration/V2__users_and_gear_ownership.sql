create table app_user (
    id            uuid primary key,
    email         varchar(254) not null,
    password_hash varchar(255) not null,
    created_at    timestamp(6) with time zone not null,
    constraint app_user_email_unique unique (email),
    -- emails are normalized (trimmed, lower-cased) by the application; enforce it here too
    constraint app_user_email_normalized check (email = lower(btrim(email)))
);

-- Every gear item belongs to exactly one user. Pre-auth dev rows have no owner,
-- so reset the local database (docker compose down -v) before migrating.
alter table gear_item
    add column owner_id uuid not null references app_user (id) on delete cascade;

create index gear_item_owner_id_idx on gear_item (owner_id);
