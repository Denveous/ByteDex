create
EXTENSION IF not exists "pgcrypto";

create type protocol_kind as enum ('LOGIN', 'GAME', 'CHAT');
create type direction_kind as enum ('C2S', 'S2C');
create type session_status as enum ('open', 'closed');
create type inferred_type as enum (
  'uint8','uint16','uint32','uint64',
  'int8','int16','int32','int64',
  'float','double',
  'string','bytes',
  'array','struct','unknown'
);

create table users
(
    id           uuid primary key     default gen_random_uuid(),
    github_id    bigint      not null unique,
    github_login text        not null,
    display_name text,
    avatar_url   text,
    is_admin     boolean     not null default false,
    created_at   timestamptz not null default now(),
    last_seen_at timestamptz
);

create index users_github_login_idx on users (github_login);

create table refresh_tokens
(
    id             uuid primary key     default gen_random_uuid(),
    user_id        uuid        not null references users (id) on delete cascade,
    token_hash     bytea       not null unique, -- SHA-256 of the raw token
    family_id      uuid        not null,
    replaced_by_id uuid references refresh_tokens (id),
    revoked_at     timestamptz,
    expires_at     timestamptz not null,
    created_at     timestamptz not null default now()
);

create index refresh_tokens_user_idx on refresh_tokens (user_id);
create index refresh_tokens_family_idx on refresh_tokens (family_id);

create table idempotency_keys
(
    key             uuid        not null,
    user_id         uuid        not null references users (id) on delete cascade,
    request_hash    bytea       not null, -- SHA-256 of method + path + body
    response_status smallint    not null,
    response_body   jsonb       not null,
    created_at      timestamptz not null default now(),
    primary key (user_id, key)
);

create index idempotency_keys_created_idx on idempotency_keys (created_at);

create table sessions
(
    id              uuid primary key        default gen_random_uuid(),
    submitted_by    uuid           not null references users (id) on delete cascade,
    game_version    bigint         not null check (game_version between 0 and 4294967295),
    status          session_status not null default 'open',
    description     text check (length(description) <= 2000),
    tags            text[]          not null default '{}',
    client_info     text check (length(client_info) <= 200),
    packet_count    integer        not null default 0,
    first_packet_at timestamptz,
    last_packet_at  timestamptz,
    created_at      timestamptz    not null default now(),
    closed_at       timestamptz,
    check (status = 'open' or closed_at is not null)
);

create index sessions_user_idx on sessions (submitted_by, created_at desc);
create index sessions_game_version_idx on sessions (game_version);
create index sessions_open_idx on sessions (last_packet_at) where status = 'open';
create index sessions_tags_gin on sessions using GIN (tags);

create table packets
(
    id             uuid primary key        default gen_random_uuid(),
    session_id     uuid           not null references sessions (id) on delete cascade,
    submitted_by   uuid           not null references users (id) on delete cascade,
    game_version   bigint         not null check (game_version between 0 and 4294967295),
    protocol       protocol_kind  not null,
    direction      direction_kind not null,
    packet_id      smallint       not null check (packet_id between 0 and 255),
    client_seq     bigint,
    captured_at    timestamptz    not null,
    submitted_at   timestamptz    not null default now(),
    payload        bytea          not null check (octet_length(payload) <= 1048576),
    payload_size   integer        not null,
    payload_sha256 bytea          not null,
    notes          text check (length(notes) <= 2000)
);

create index packets_kind_idx
    on packets (game_version, protocol, direction, packet_id);

create index packets_session_idx on packets (session_id, captured_at);
create index packets_submitter_idx on packets (submitted_by);
create index packets_captured_idx on packets (captured_at);
create index packets_sha256_idx on packets (payload_sha256);
create index packets_size_idx on packets (payload_size);
create index packets_submitter_session_idx on packets (submitted_by, session_id);
create index packets_captured_dir_idx on packets (captured_at, direction);

create table packet_schemas
(
    game_version  bigint         not null,
    protocol      protocol_kind  not null,
    direction     direction_kind not null,
    packet_id     smallint       not null check (packet_id between 0 and 255),
    sample_count  integer        not null,
    session_count integer        not null,
    fields        jsonb          not null,
    generated_at  timestamptz    not null default now(),
    primary key (game_version, protocol, direction, packet_id)
);

create table schema_dirty_queue
(
    game_version bigint         not null,
    protocol     protocol_kind  not null,
    direction    direction_kind not null,
    packet_id    smallint       not null check (packet_id between 0 and 255),
    marked_at    timestamptz    not null default now(),
    primary key (game_version, protocol, direction, packet_id)
);

create table field_annotations
(
    id           uuid primary key        default gen_random_uuid(),
    game_version bigint         not null,
    protocol     protocol_kind  not null,
    direction    direction_kind not null,
    packet_id    smallint       not null check (packet_id between 0 and 255),
    author_id    uuid           not null references users (id) on delete cascade,
    offset_bytes integer        not null check (offset_bytes >= 0),
    length_bytes integer        not null check (length_bytes >= 0),
    type         inferred_type  not null,
    name         text check (length(name) <= 200),
    notes        text check (length(notes) <= 2000),
    created_at   timestamptz    not null default now(),
    foreign key (game_version, protocol, direction, packet_id)
        references packet_schemas (game_version, protocol, direction, packet_id)
        on delete cascade
);

create index field_annotations_packet_idx
    on field_annotations (game_version, protocol, direction, packet_id);
create index field_annotations_author_idx on field_annotations (author_id);

create
or REPLACE function sessions_bump_counters() returns trigger as $$
begin
  IF
TG_OP = 'INSERT' then
update sessions
set packet_count    = packet_count + 1,
    first_packet_at = least(coalesce(first_packet_at, new.captured_at), new.captured_at),
    last_packet_at  = greatest(coalesce(last_packet_at, new.captured_at), new.captured_at)
where id = new.session_id;
ELSIF
TG_OP = 'DELETE' then
update sessions
set packet_count = greatest(packet_count - 1, 0)
where id = old.session_id;
end IF;
return null;
end
$$
language plpgsql;

create trigger packets_session_counter_trg
    after insert or
delete
on packets
  for each row execute function sessions_bump_counters();

create
or REPLACE function packets_mark_schema_dirty() returns trigger as $$
begin
insert into schema_dirty_queue (game_version, protocol, direction, packet_id)
values (new.game_version, new.protocol, new.direction,
        new.packet_id) on CONFLICT (game_version, protocol, direction, packet_id) DO
update
    set marked_at = NOW();
return null;
end
$$
language plpgsql;

create trigger packets_dirty_schema_trg
    after insert
    on packets
    for each row execute function packets_mark_schema_dirty();

insert into users (id, github_id, github_login, display_name, is_admin)
values ('00000000-0000-0000-0000-000000000000',
        0,
        'ghost',
        'Deleted account',
        false) on CONFLICT (id) DO NOTHING;
