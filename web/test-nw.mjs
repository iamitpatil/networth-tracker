import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const browserPage = await browser.newPage();

// Register fresh user
const registerRes = await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'NWTest', email: `nwtest${Date.now()}@test.com`, password: 'TestPass123!' }),
});
const authData = await registerRes.json();
console.log('Registered:', authData.name);

await browserPage.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
await browserPage.evaluate((data) => {
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('user', JSON.stringify(data));
}, authData);

const errors = [];
browserPage.on('console', msg => {
  if (msg.type() === 'error') errors.push(msg.text());
});
browserPage.on('pageerror', err => errors.push(err.message));

await browserPage.goto('http://localhost:3000/net-worth', { waitUntil: 'networkidle0', timeout: 15000 });
await new Promise(r => setTimeout(r, 2000));

const bodyHTML = await browserPage.evaluate(() => document.body.innerHTML);
console.log('Body length:', bodyHTML.length);
console.log('Has sidebar:', bodyHTML.includes('<aside'));
console.log('Has Net Worth text:', bodyHTML.includes('Net Worth'));
console.log('Has Total Net Worth:', bodyHTML.includes('Total Net Worth'));

const apiErrors = errors.filter(e => e.includes('403') || e.includes('Failed'));
if (apiErrors.length > 0) {
  console.log('\nAPI errors:', apiErrors.length);
}

await browser.close();