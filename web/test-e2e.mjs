import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

// Register fresh user
const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'E2EFull', email: `e2efull${Date.now()}@test.com`, password: 'TestPass123!' }),
})).json();
console.log('Registered:', REG.email, '| Token:', REG.accessToken?.slice(0, 16));

// Inject auth into localStorage
await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
await page.evaluate((data) => {
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('user', JSON.stringify(data));
}, REG);

// Track errors
const pageErrors = [];
page.on('pageerror', err => pageErrors.push(err.message));
page.on('console', msg => { if (msg.type() === 'error') pageErrors.push(msg.text()); });

async function loadPage(path, label) {
  pageErrors.length = 0;
  const TIMEOUT = 15000;
  try {
    await page.goto(`http://localhost:3000${path}`, { waitUntil: 'networkidle0', timeout: TIMEOUT });
  } catch (e) {
    if (!e.message.includes('timeout')) throw e;
  }
  // Wait for React to hydrate
  await new Promise(r => setTimeout(r, 2500));
  const root = await page.evaluate(() => document.getElementById('root')?.innerHTML?.length || 0);
  const sidebar = await page.evaluate(() => !!document.querySelector('aside'));
  const heading = await page.evaluate(() => {
    const h1 = document.querySelector('h1');
    return h1?.textContent?.trim() || '-';
  });
  const ok = root > 200 && sidebar && !pageErrors.some(e => e.includes('not defined') || e.includes('Cannot read'));
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}  | root:${root} sidebar:${sidebar} h1:"${heading}" errors:${pageErrors.length}`);
  if (!ok && pageErrors.length) console.log('  ->', pageErrors[0].slice(0, 120));
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

await browser.close();