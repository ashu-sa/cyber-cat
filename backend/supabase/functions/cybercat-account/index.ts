// Username + 4-digit PIN gateway. No admin key or derived password reaches a client.
// Deploy with JWT verification OFF; login/signup are public and rate limited.
const url = Deno.env.get('SUPABASE_URL')!;
const admin = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const pepper = Deno.env.get('PIN_PEPPER')!;
const publicKey = Deno.env.get('SUPABASE_ANON_KEY')!;
const headers = { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type' };
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {status, headers});
async function api(path: string, body: unknown, key = admin) {
 const response = await fetch(url+path,{method:'POST',headers:{'Content-Type':'application/json',apikey:key,Authorization:'Bearer '+key},body:JSON.stringify(body)});
 return {ok:response.ok,status:response.status,data:await response.json()};
}
async function digest(value: string) {
 const key = await crypto.subtle.importKey('raw',new TextEncoder().encode(pepper),{name:'HMAC',hash:'SHA-256'},false,['sign']);
 return Array.from(new Uint8Array(await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(value)))).map(x=>x.toString(16).padStart(2,'0')).join('');
}
Deno.serve(async (req: Request) => {
 if(req.method==='OPTIONS') return new Response('ok',{headers});
 if(req.method!=='POST') return reply(405,{error:'POST required'});
 try {
  if(Number(req.headers.get('content-length')||0)>4096) return reply(413,{error:'Request too large'});
  const raw=await req.text(); if(raw.length>4096) return reply(413,{error:'Request too large'});
  const body=JSON.parse(raw);
  if(body.action==='refresh') {
   const result=await api('/auth/v1/token?grant_type=refresh_token',{refresh_token:body.refresh_token},publicKey);
   return result.ok?reply(200,result.data):reply(401,{error:'Please sign in again. Your offline history is safe.'});
  }
  const username=String(body.username||'').trim().toLowerCase(); const pin=String(body.pin||'');
  if(!/^[a-z0-9_]{3,24}$/.test(username)||!/^\d{4}$/.test(pin)||!['login','signup'].includes(body.action)) return reply(400,{error:'Use a username with 3–24 letters, numbers or underscores, and a 4-digit PIN.'});
  // Usernames are canonicalized before throttling. IP comes from the trusted edge proxy.
  const ip=req.headers.get('x-forwarded-for')?.split(',')[0]?.trim()||'unknown';
  const accountLimit=await api('/rest/v1/rpc/account_attempt',{bucket_id:'user:'+await digest(username),max_attempts:5});
  const ipLimit=await api('/rest/v1/rpc/account_attempt',{bucket_id:'ip:'+await digest(ip),max_attempts:30});
  if(!accountLimit.ok||!ipLimit.ok) return reply(503,{error:'Sign-in is temporarily unavailable. Try again later.'});
  if(!accountLimit.data||!ipLimit.data) return reply(429,{error:'Too many attempts. Try again in 15 minutes.'});
  const email=username+'@accounts.cybercat.invalid';
  const password=await digest('cybercat-pin-v1:'+username+':'+pin);
  if(body.action==='signup') {
   const created=await api('/auth/v1/admin/users',{email,password,email_confirm:true,user_metadata:{username}});
   if(!created.ok) return reply(409,{error:'Could not create this account. Try another username, or sign in if it is yours.'});
  }
  const signed=await api('/auth/v1/token?grant_type=password',{email,password},publicKey);
  if(!signed.ok) return reply(401,{error:'Username or PIN is incorrect.'});
  await api('/rest/v1/rpc/account_success',{bucket_id:'user:'+await digest(username)});
  return reply(200,signed.data);
 } catch { return reply(503,{error:'Unable to connect. Please try again.'}); }
});
