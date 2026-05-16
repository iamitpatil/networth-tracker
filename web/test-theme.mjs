import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'ThemeTest', email: `theme${Date.now()}@test.com`, password: 'TestPass123!' }),
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
  const crash = pageErrors.some(e => e.includes('not defined') || e.includes('Cannot read') || e.includes('is not defined'));
  const result = root && sidebar && !crash ? 'PASS' : 'FAIL';
  if (pageErrors.length > 0) console.log(`${result} ${label.padEnd(18)} h1="${heading}" err=${pageErrors[0].slice(0, 60)}`);
  else console.log(`${result} ${label.padEnd(18)} h1="${heading}"`);
}

const pages = [
  '/dashboard', '/net-worth', '/holdings', '/transactions',
  '/analytics', '/tax', '/goals', '/liabilities', '/import',
  '/demat-accounts', '/documents',
];
for (const p of pages) {
  await loadPage(p, p.replace('/', ''));
}

// Test theme toggle
console.log('\n--- Theme Toggle Test ---');
await page.goto('http://localhost:3000/dashboard', { waitUntil: 'networkidle0' });
await new Promise(r => setTimeout(r, 2000));

// Check initial theme
let theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
console.log('Initial theme:', theme);

// Find and click theme toggle
const toggleResult = await page.evaluate(() => {
  const buttons = document.querySelectorAll('button');
  for (const btn of buttons) {
    if (btn.textContent.includes('Light') || btn.textContent.includes('Dark')) {
      btn.click();
      return 'clicked: ' + btn.textContent.trim();
    }
  }
  return 'not found';
});
console.log('Toggle:', toggleResult);
await new Promise(r => setTimeout(r, 500));

theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
console.log('Theme after toggle:', theme);

// Toggle back
await page.evaluate(() => {
  const buttons = document.querySelectorAll('button');
  for (const btn of buttons) {
    if (btn.textContent.includes('Light') || btn.textContent.includes('Dark')) {
      btn.click();
      return;
    }
  }
});
await new Promise(r => setTimeout(r, 500));
theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
console.log('Theme toggled back:', theme);

console.log(`\nAll ${pages.length} pages tested ✅`);
await browser.close();