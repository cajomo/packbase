create table gear_item (
    id           uuid primary key,
    name         varchar(200) not null,
    category     varchar(100) not null,
    weight_grams integer,
    quantity     integer not null,
    notes        text,
    created_at   timestamp(6) with time zone not null
);
