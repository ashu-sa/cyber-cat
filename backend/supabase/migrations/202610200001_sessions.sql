-- Cyber Cat shared history. Run once in your Supabase SQL editor.
-- Times and durations use integer milliseconds on the wire on EVERY platform.
create table public.focus_sessions (
    user_id uuid not null references auth.users(id) on delete cascade,
    id uuid not null,
    name text not null check (length(btrim(name)) between 1 and 200),
    start_ms bigint not null check (start_ms >= 0),
    end_ms bigint not null check (end_ms >= 0),
    -- Duration comes from a monotonic clock. Wall-clock corrections can make
    -- end-start differ from duration (or even make end earlier than start).
    duration_ms bigint not null check (duration_ms >= 0),
    platform text not null check (platform in ('android', 'macos', 'windows', 'linux', 'unknown')),
    device_id uuid not null,
    device_name text not null check (length(btrim(device_name)) between 1 and 80),
    deleted boolean not null default false,
    revision bigint not null default 1 check (revision >= 1),
    updated_at timestamptz not null default clock_timestamp(),
    primary key (user_id, id)
);
create index focus_sessions_history on public.focus_sessions(user_id, start_ms desc);
alter table public.focus_sessions enable row level security;
revoke all on public.focus_sessions from anon, authenticated;
grant select, insert, update on public.focus_sessions to authenticated;
create policy own_sessions_read on public.focus_sessions for select to authenticated
    using ((select auth.uid()) = user_id);
create policy own_sessions_insert on public.focus_sessions for insert to authenticated
    with check ((select auth.uid()) = user_id and revision = 1 and deleted = false);
create policy own_sessions_update on public.focus_sessions for update to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- A client sends its last known revision in both the PATCH filter and body.
-- A stale write must reload, never silently overwrite another device's edit.
create function public.focus_session_revision() returns trigger
language plpgsql set search_path = '' as $$
begin
    if tg_op = 'INSERT' then
        new.revision := 1;
    else
        if new.revision <> old.revision then
            raise exception 'Session changed on another device' using errcode = '40001';
        end if;
        if (new.user_id, new.id, new.start_ms, new.end_ms, new.duration_ms,
            new.platform, new.device_id, new.device_name) is distinct from
           (old.user_id, old.id, old.start_ms, old.end_ms, old.duration_ms,
            old.platform, old.device_id, old.device_name) then
            raise exception 'Session origin and timing are immutable' using errcode = '22023';
        end if;
        new.revision := old.revision + 1;
    end if;
    new.updated_at := clock_timestamp();
    return new;
end;
$$;
revoke all on function public.focus_session_revision() from public;
create trigger focus_session_revision before insert or update on public.focus_sessions
    for each row execute function public.focus_session_revision();
-- DELETE is intentionally not granted: deletion is a versioned tombstone.
-- Keep tombstones so an offline client cannot resurrect a deleted session.
