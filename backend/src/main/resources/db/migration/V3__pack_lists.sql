create table pack_list (
    id         uuid primary key,
    owner_id   uuid not null references app_user (id) on delete cascade,
    name       varchar(100) not null,
    created_at timestamp(6) with time zone not null
);

create index pack_list_owner_id_idx on pack_list (owner_id);

-- The ordered content of a list. Deleting a list or a library item removes its entries.
create table pack_list_entry (
    id       uuid primary key,
    list_id  uuid not null references pack_list (id) on delete cascade,
    item_id  uuid not null references gear_item (id) on delete cascade,
    position integer not null,
    quantity integer not null check (quantity >= 1),
    -- null = use the item's own category, '' = uncategorized
    category varchar(100),
    constraint pack_list_entry_item_once unique (list_id, item_id)
);

create index pack_list_entry_item_id_idx on pack_list_entry (item_id);
