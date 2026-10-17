const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 3000;
const HOST = '0.0.0.0';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
  // Enable CORS for all local requests
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-MBX-APIKEY, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // Endpoint: Health check
  if (pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', time: Date.now() }));
    return;
  }

  // Endpoint: Get public IP
  if (pathname === '/api/my-ip') {
    https.get('https://api.ipify.org?format=json', (ipRes) => {
      let data = '';
      ipRes.on('data', chunk => data += chunk);
      ipRes.on('end', () => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(data || JSON.stringify({ ip: '127.0.0.1' }));
      });
    }).on('error', () => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ip: '127.0.0.1' }));
    });
    return;
  }

  // Endpoint: Binance API Proxy to overcome browser CORS and testnet/mainnet geo-blocking
  if (pathname === '/api/binance-proxy') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const targetUrl = payload.url || parsedUrl.query.url;
        if (!targetUrl) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Missing target url parameter' }));
          return;
        }

        const targetParsed = url.parse(targetUrl);
        const method = payload.method || req.method || 'GET';
        const apiKey = payload.apiKey || req.headers['x-mbx-apikey'];
        const reqHeaders = {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
          'Accept': 'application/json, text/plain, */*'
        };
        if (apiKey) {
          reqHeaders['X-MBX-APIKEY'] = apiKey;
        }

        const options = {
          hostname: targetParsed.hostname,
          port: targetParsed.port || (targetParsed.protocol === 'https:' ? 443 : 80),
          path: targetParsed.path,
          method: method,
          headers: reqHeaders,
          timeout: 10000
        };

        const clientModule = targetParsed.protocol === 'https:' ? https : http;
        const proxyReq = clientModule.request(options, (proxyRes) => {
          res.writeHead(proxyRes.statusCode, {
            'Content-Type': proxyRes.headers['content-type'] || 'application/json',
            'Access-Control-Allow-Origin': '*'
          });
          proxyRes.pipe(res);
        });

        proxyReq.on('error', (err) => {
          res.writeHead(502, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Proxy request error: ' + err.message, code: -1001 }));
        });

        proxyReq.on('timeout', () => {
          proxyReq.destroy();
          res.writeHead(504, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Proxy request timeout', code: -1002 }));
        });

        if (payload.data) {
          proxyReq.write(typeof payload.data === 'string' ? payload.data : JSON.stringify(payload.data));
        }
        proxyReq.end();
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }

  // Static file server
  let filePath = path.join(__dirname, pathname === '/' ? 'index.html' : pathname);
  if (!fs.existsSync(filePath)) {
    filePath = path.join(__dirname, 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'text/html; charset=utf-8';

  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end('Error loading ' + pathname);
      return;
    }
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(content);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`CryptoTrader Web Server listening on http://${HOST}:${PORT}`);
});
