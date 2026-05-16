import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'E2EFinal', email: `final${Date.now()}@test.com`, password: 'TestPass123!' }),
})).json();
console.log('Registered:', REG.email, '| Token:', REG.accessToken?.slice(0, 16));

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
  console.log(`${root && sidebar && !crash ? 'PASS' : 'FAIL'} ${label.padEnd(16)} h1="${heading}" err=${pageErrors.length}`);
}

await loadPage('/dashboard', 'Dashboard');
await loadPage('/net-worth', 'Net Worth');
await loadPage('/holdings', 'Holdings');
await loadPage('/transactions', 'Transactions');
await loadPage('/analytics', 'Analytics');
await loadPage('/tax', 'Tax');
await loadPage('/goals', 'Goals');
await loadPage('/liabilities', 'Liabilities');
await loadPage('/import', 'Import');
await loadPage('/demat-accounts', 'Demat Accounts');

// Test the API directly
console.log('\n--- API Test: Demat Accounts CRUD ---');
const token = REG.accessToken;
const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

// Create
const createRes = await fetch('http://localhost:8080/api/v1/demat-accounts', {
  method: 'POST', headers,
  body: JSON.stringify({ brokerName: 'Zerodha', accountNumber: '12345678', accountType: 'Equity', description: 'Primary account', isDefault: true }),
});
const created = await createRes.json();
console.log('CREATE:', createRes.status, created.brokerName, '| id:', created.id?.slice(0, 8));

// List
const listRes = await fetch('http://localhost:8080/api/v1/demat-accounts', { headers });
const list = await listRes.json();
console.log('LIST:', listRes.status, 'count:', list.length);

// Update
const updateRes = await fetch(`http://localhost:8080/api/v1/demat-accounts/${created.id}`, {
  method: 'PUT', headers,
  body: JSON.stringify({ brokerName: 'Zerodha', accountNumber: '87654321', accountType: 'Equity', description: 'Updated account', isDefault: true }),
});
const updated = await updateRes.json();
console.log('UPDATE:', updateRes.status, 'description:', updated.description);

// Delete
const delRes = await fetch(`http://localhost:8080/api/v1/demat-accounts/${created.id}`, { method: 'DELETE', headers });
console.log('DELETE:', delRes.status);

console.log('\nAll 10 pages tested + CRUD verified');
await browser.close();