const C='vnd-v1',F=['./','index.html','manifest.json','icon.svg'];
self.addEventListener('install',e=>e.waitUntil(caches.open(C).then(c=>c.addAll(F))));
self.addEventListener('fetch',e=>{
  if(e.request.url.includes('er-api'))return;
  e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request)));
});
