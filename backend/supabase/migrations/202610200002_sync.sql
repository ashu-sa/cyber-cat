-- Atomic reconciliation; RLS remains in force because this is SECURITY INVOKER.
create function public.sync_focus_sessions(changes jsonb default '[]') returns jsonb
language plpgsql set search_path='' as $$
declare c jsonb; r public.focus_sessions; conflicts jsonb := '[]';
begin
 if auth.uid() is null then raise exception 'Sign in required'; end if;
 if jsonb_typeof(changes) <> 'array' or jsonb_array_length(changes)>100 then raise exception 'Invalid batch'; end if;
 for c in select value from jsonb_array_elements(changes) loop
  select * into r from public.focus_sessions where user_id=auth.uid() and id=(c->>'id')::uuid for update;
  if not found then
   if coalesce((c->>'revision')::bigint,0)<>0 then conflicts:=conflicts||jsonb_build_array(c->>'id'); continue; end if;
   insert into public.focus_sessions(user_id,id,name,start_ms,end_ms,duration_ms,platform,device_id,device_name)
   values(auth.uid(),(c->>'id')::uuid,c->>'name',(c->>'start_ms')::bigint,(c->>'end_ms')::bigint,(c->>'duration_ms')::bigint,c->>'platform',(c->>'device_id')::uuid,c->>'device_name')
   on conflict do nothing;
   if coalesce((c->>'deleted')::boolean,false) then update public.focus_sessions set deleted=true where user_id=auth.uid() and id=(c->>'id')::uuid; end if;
  elsif r.name=c->>'name' and r.deleted=coalesce((c->>'deleted')::boolean,false) then null; -- successful replay
  elsif r.revision=coalesce((c->>'revision')::bigint,0) then
   update public.focus_sessions set name=c->>'name',deleted=coalesce((c->>'deleted')::boolean,false) where user_id=auth.uid() and id=r.id;
  else conflicts:=conflicts||jsonb_build_array(c->>'id');
  end if;
 end loop;
 return jsonb_build_object('records',coalesce((select jsonb_agg(to_jsonb(s) order by s.id) from public.focus_sessions s where user_id=auth.uid()),'[]'::jsonb),'conflicts',conflicts);
end $$;
revoke all on function public.sync_focus_sessions(jsonb) from public,anon;
grant execute on function public.sync_focus_sessions(jsonb) to authenticated;

-- Only the server-side account gateway may consume rate-limit buckets.
create schema if not exists private;
create table private.login_limits(bucket text primary key, attempts integer not null, resets_at timestamptz not null);
alter table private.login_limits enable row level security;
create function public.account_attempt(bucket_id text, max_attempts integer) returns boolean
language plpgsql security definer set search_path='' as $$
declare n integer;
begin
 insert into private.login_limits values(bucket_id,1,now()+interval '15 minutes')
 on conflict(bucket) do update set attempts=case when private.login_limits.resets_at<now() then 1 else private.login_limits.attempts+1 end,
 resets_at=case when private.login_limits.resets_at<now() then now()+interval '15 minutes' else private.login_limits.resets_at end returning attempts into n;
 delete from private.login_limits where resets_at<now()-interval '1 day';
 return n<=max_attempts;
end $$;
revoke all on function public.account_attempt(text,integer) from public,anon,authenticated;
grant execute on function public.account_attempt(text,integer) to service_role;
create function public.account_success(bucket_id text) returns void
language sql security definer set search_path='' as $$ delete from private.login_limits where bucket=bucket_id; $$;
revoke all on function public.account_success(text) from public,anon,authenticated;
grant execute on function public.account_success(text) to service_role;
