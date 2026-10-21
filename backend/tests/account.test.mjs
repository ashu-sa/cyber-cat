import test from 'node:test';
import assert from 'node:assert/strict';
let handler;
globalThis.Deno={env:{get:name=>({SUPABASE_URL:'https://backend.example.test',SUPABASE_SERVICE_ROLE_KEY:'server-only-admin',SUPABASE_ANON_KEY:'public-anon',PIN_PEPPER:'test-pepper-do-not-use-in-production'}[name])},serve:fn=>{handler=fn;}};
await import('../supabase/functions/cybercat-account/index.ts');
let limited=false, invalid=false, calls=[];
globalThis.fetch=async (url,options)=>{
 calls.push({url,body:JSON.parse(options.body)});
 if(url.endsWith('/account_attempt')) return Response.json(!limited);
 if(url.endsWith('/account_success')) return new Response(null,{status:204});
 if(url.endsWith('/admin/users')) return Response.json({id:'test-user'});
 if(url.includes('/token')) return invalid ? Response.json({error:'invalid'},{status:400}) : Response.json({access_token:'user-access',refresh_token:'user-refresh',user:{id:'test-user',user_metadata:{username:'alice'}}});
 throw Error('Unexpected request');
};
function request(body) { return handler(new Request('https://app.example.test',{method:'POST',headers:{'Content-Type':'application/json','x-forwarded-for':'192.0.2.1'},body:JSON.stringify(body)})); }
test('PIN signup succeeds when account reset RPC returns empty HTTP 204',async()=>{
 const r=await request({action:'signup',username:'Alice',pin:'0042'}); assert.equal(r.status,200);
 const data=await r.json(); assert.equal(data.access_token,'user-access'); assert.equal(data.user.user_metadata.username,'alice');
 const create=calls.find(c=>c.url.endsWith('/admin/users')); assert.equal(create.body.email,'alice@accounts.cybercat.invalid'); assert.equal(create.body.password.length,64); assert.notEqual(create.body.password,'0042'); assert.ok(!JSON.stringify(data).includes('server-only-admin'));
});
test('invalid usernames or non-four-digit PINs do not reach auth',async()=>{
 calls=[]; const r=await request({action:'login',username:'bad username',pin:'12345'}); assert.equal(r.status,400); assert.equal(calls.length,0);
});
test('attempt limit blocks authentication',async()=>{
 limited=true; calls=[]; const r=await request({action:'login',username:'alice',pin:'0042'}); limited=false;
 assert.equal(r.status,429); assert.ok(calls.every(c=>c.url.endsWith('/account_attempt')));
});
test('incorrect PIN uses a generic error',async()=>{
 invalid=true; const r=await request({action:'login',username:'alice',pin:'0000'}); invalid=false;
 assert.equal(r.status,401); assert.equal((await r.json()).error,'Username or PIN is incorrect.');
});
test('refresh returns a user session without a PIN',async()=>{
 const r=await request({action:'refresh',refresh_token:'user-refresh'}); assert.equal(r.status,200); assert.equal((await r.json()).access_token,'user-access');
});
