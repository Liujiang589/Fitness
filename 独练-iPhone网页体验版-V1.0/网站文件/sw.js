const PREFIX = 'duolian-web-' + new URL(self.registration.scope).pathname.replace(/\W/g,'_') + '-';
const CACHE = PREFIX + '1.0.0-2';
const FILES = ['./','./index.html','./style.css','./app.js','./core.js','./catalog.js','./manifest.webmanifest','./icon.svg','./icon-180.png','./icon-192.png','./icon-512.png'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES))));
self.addEventListener('activate', event => event.waitUntil((async()=>{
  for(const key of await caches.keys())if(key.startsWith(PREFIX)&&key!==CACHE)await caches.delete(key);
  await self.clients.claim();
})()));
self.addEventListener('message',event=>{if(event.data?.type==='ACTIVATE')self.skipWaiting();});
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url),scope=new URL(self.registration.scope);
  if(event.request.method!=='GET'||url.origin!==scope.origin||!url.pathname.startsWith(scope.pathname))return;
  // Only the explicit public app shell is cached. Never cache records, backups, or arbitrary URLs.
  const known=FILES.some(file=>new URL(file,self.registration.scope).pathname===url.pathname);
  if(!known)return;
  event.respondWith((async()=>{const cache=await caches.open(CACHE);return await cache.match(new Request(url.origin+url.pathname))||fetch(event.request);})());
});
