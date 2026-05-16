import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();

const REG = await (await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'ProfileTest', email: `pro${Date.now()}@test.com`, password: 'TestPass123!' }),
})).json();

await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
await page.evaluate((data) => {
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('user', JSON.stringify(data));
}, REG);

const errors = [];
page.on('pageerror', err => errors.push(err.message));
page.on('console', msg => { if (msg.type() === 'error' && !msg.text().includes('favicon')) errors.push(msg.text()); });

async function loadPage(path, label) {
  errors.length = 0;
  try {
    await page.goto(`http://localhost:3000${path}`, { waitUntil: 'networkidle0', timeout: 15000 });
  } catch (e) { if (!e.message.includes('timeout')) throw e; }
  await new Promise(r => setTimeout(r, 3000));
  const root = await page.evaluate(() => document.getElementById('root')?.innerHTML?.length || 0);
  const h1 = await page.evaluate(() => document.querySelector('h1')?.textContent?.trim() || '-');
  const crash = errors.some(e => e.includes('not defined') || e.includes('Cannot read'));
  console.log(`${root > 200 && !crash ? 'PASS' : 'FAIL'} ${label.padEnd(18)} h1="${h1}" err=${errors.length}`);
}

// Test key pages
await loadPage('/dashboard', 'Dashboard');
await loadPage('/profile', 'Profile');
await loadPage('/demat-accounts', 'Demat Accts');
await loadPage('/documents', 'Documents');

// Test sidebar click navigates to profile
await page.goto('http://localhost:3000/dashboard', { waitUntil: 'networkidle0' });
await new Promise(r => setTimeout(r, 2000));

await page.evaluate(() => {
  const nameDivs = document.querySelectorAll('[class*="cursor-pointer"]');
  for (const div of nameDivs) {
    if (div.textContent.includes('@')) {
      div.click();
      return;
    }
  }
});
await new Promise(r => setTimeout(r, 2000));
const url = page.url();
console.log(`\nSidebar click navigated to: ${url.includes('profile') ? '✅ /profile' : '❌ ' + url}`);

console.log('\nAll tests complete ✅');
await browser.close();