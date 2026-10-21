-- Preserve conflict detection when two clients insert the same UUID concurrently.
create or replace function public.sync_focus_sessions(changes jsonb default '[]') returns jsonb
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
   on conflict do nothing returning * into r;
   if not found then
    select * into r from public.focus_sessions where user_id=auth.uid() and id=(c->>'id')::uuid for update;
    if r.name<>c->>'name' or r.deleted<>coalesce((c->>'deleted')::boolean,false) then conflicts:=conflicts||jsonb_build_array(c->>'id'); end if;
   elsif coalesce((c->>'deleted')::boolean,false) then
    update public.focus_sessions set deleted=true where user_id=auth.uid() and id=r.id;
   end if;
  elsif r.name=c->>'name' and r.deleted=coalesce((c->>'deleted')::boolean,false) then null; -- successful replay
  elsif r.revision=coalesce((c->>'revision')::bigint,0) then
   update public.focus_sessions set name=c->>'name',deleted=coalesce((c->>'deleted')::boolean,false) where user_id=auth.uid() and id=r.id;
  else conflicts:=conflicts||jsonb_build_array(c->>'id');
  end if;
 end loop;
 return jsonb_build_object('records',coalesce((select jsonb_agg(to_jsonb(s) order by s.id) from public.focus_sessions s where user_id=auth.uid()),'[]'::jsonb),'conflicts',conflicts);
end $$;
