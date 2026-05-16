import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'E2EFinal3', email: `final3${Date.now()}@test.com`, password: 'TestPass123!' }),
})).json();
console.log('Registered:', REG.email);

await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
await page.evaluate((data) => {
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('user', JSON.stringify(data));
}, REG);

const pageErrors = [];
page.on('pageerror', err => pageErrors.push(err.message));
page.on('console', msg => { if (msg.type() === 'error') pageErrors.push(msg.text()); });

async function loadPage(path, label) {
  pageErrors.length = 0;
  try {
    await page.goto(`http://localhost:3000${path}`, { waitUntil: 'networkidle0', timeout: 15000 });
  } catch (e) { if (!e.message.includes('timeout')) throw e; }
  await new Promise(r => setTimeout(r, 2500));
  const root = (await page.evaluate(() => document.getElementById('root')?.innerHTML?.length || 0)) > 200;
  const sidebar = await page.evaluate(() => !!document.querySelector('aside'));
  const heading = await page.evaluate(() => document.querySelector('h1')?.textContent?.trim() || '-');
  const crash = pageErrors.some(e => e.includes('not defined') || e.includes('Cannot read'));
  console.log(`${root && sidebar && !crash ? 'PASS' : 'FAIL'} ${label.padEnd(18)} h1="${heading}"`);
}

const pages = [
  '/dashboard', '/net-worth', '/holdings', '/transactions',
  '/analytics', '/tax', '/goals', '/liabilities', '/import',
  '/demat-accounts', '/documents',
];
for (const p of pages) {
  await loadPage(p, p.replace('/', '').replace('-', ' ').replace(/\b\w/g, c => c.toUpperCase()));
}

// Test document upload via API
console.log('\n--- Document Upload/Download/Delete ---');
const token = REG.accessToken;
const auth = { Authorization: `Bearer ${token}` };

// Create a proper multipart upload using internal fetch
const textEncoder = new TextEncoder();
const boundary = '----FormBoundary' + Math.random().toString(36).slice(2);

const parts = [
  `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="test-invoice.txt"\r\nContent-Type: text/plain\r\n\r\nTest document content for networth tracker - Gold Invoice 2025`,
  `\r\n--${boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nINVOICE`,
  `\r\n--${boundary}\r\nContent-Disposition: form-data; name="description"\r\n\r\nTest gold invoice`,
  `\r\n--${boundary}--\r\n`,
];
const bodyStr = parts.join('');

const uploadRes = await fetch('http://localhost:8080/api/v1/documents/upload', {
  method: 'POST',
  headers: { ...auth, 'Content-Type': `multipart/form-data; boundary=${boundary}` },
  body: bodyStr,
});
const doc = await uploadRes.json();
console.log('1. Upload:', uploadRes.status, doc.originalFilename, `(${doc.fileSize}B)`, doc.category);

const listRes = await fetch('http://localhost:8080/api/v1/documents', { headers: auth });
const list = await listRes.json();
console.log('2. List:', listRes.status, `count=${list.length}`);

const dlRes = await fetch(`http://localhost:8080/api/v1/documents/${doc.id}/download`, { headers: auth });
const dlText = await dlRes.text();
console.log('3. Download:', dlRes.status, `"${dlText.substring(0, 40)}..."`);

const delRes = await fetch(`http://localhost:8080/api/v1/documents/${doc.id}`, { method: 'DELETE', headers: auth });
console.log('4. Delete:', delRes.status);

console.log(`\nAll ${pages.length} pages tested + document CRUD ✅`);
await browser.close();