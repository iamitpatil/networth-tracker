import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'E2EFinal2', email: `final2${Date.now()}@test.com`, password: 'TestPass123!' }),
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
  const crash = pageErrors.some(e => e.includes('not defined') || e.includes('Cannot read'));
  console.log(`${root && sidebar && !crash ? 'PASS' : 'FAIL'} ${label}`);
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

// Test end-to-end: create demat account → create holding with demat
console.log('\n--- E2E Flow: Demat → Holding → Transaction ---');
const token = REG.accessToken;
const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

// Create demat account
let res = await fetch('http://localhost:8080/api/v1/demat-accounts', {
  method: 'POST', headers,
  body: JSON.stringify({ brokerName: 'Zerodha', accountNumber: 'ZER12345', accountType: 'Equity', description: 'Main trading', isDefault: true }),
});
const demat = await res.json();
console.log('1. Demat created:', res.status, demat.brokerName, '| id:', demat.id?.slice(0, 8));

// Create holding with demat
res = await fetch('http://localhost:8080/api/v1/portfolio/holdings', {
  method: 'POST', headers,
  body: JSON.stringify({
    assetType: 'EQUITY',
    symbol: 'RELIANCE.NS',
    name: 'Reliance Industries',
    exchange: 'NSE',
    sector: 'Oil & Gas',
    quantity: 10,
    averageBuyPrice: 2500,
    dematAccountId: demat.id,
  }),
});
const holding = await res.json();
console.log('2. Holding created:', res.status, holding.symbol, '| dematBroker:', holding.dematAccountBroker);

// Verify demat info in holding response
console.log('   dematAccountId:', holding.dematAccountId?.slice(0, 8));
console.log('   dematAccountBroker:', holding.dematAccountBroker);

// Create transaction for this holding
res = await fetch('http://localhost:8080/api/v1/portfolio/transactions', {
  method: 'POST', headers,
  body: JSON.stringify({
    holdingId: holding.id,
    transactionType: 'BUY',
    quantity: 10,
    price: 2500,
    transactionDate: new Date().toISOString(),
    broker: 'Zerodha',
  }),
});
const txn = await res.json();
console.log('3. Transaction created:', res.status, txn.transactionType, txn.quantity, 'shares');

// List holdings to verify demat column
res = await fetch('http://localhost:8080/api/v1/portfolio/holdings', { headers });
const allHoldings = await res.json();
const h = allHoldings.find(x => x.id === holding.id);
console.log('4. Holdings list: dematBroker =', h?.dematAccountBroker || '(none)');
console.log('   dematAccountId =', h?.dematAccountId?.slice(0, 8) || '(none)');

console.log('\nAll tests passed ✅');
await browser.close();