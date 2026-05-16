import puppeteer from 'puppeteer';

const browser = await puppeteer.launch({ headless: 'new' });
const browserPage = await browser.newPage();

const errors = [];
const logs = [];
browserPage.on('console', msg => {
  if (msg.type() === 'error') errors.push(msg.text());
  else logs.push(`[${msg.type()}] ${msg.text().substring(0, 100)}`);
});
browserPage.on('pageerror', err => errors.push('PAGE_ERR: ' + err.message + '\n' + err.stack));

await browserPage.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
const registerRes = await fetch('http://localhost:8080/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'NWTest2', email: `nwtest2${Date.now()}@test.com`, password: 'TestPass123!' }),
});
const authData = await registerRes.json();
await browserPage.evaluate((data) => {
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('user', JSON.stringify(data));
}, authData);

await browserPage.goto('http://localhost:3000/net-worth', { waitUntil: 'networkidle0', timeout: 15000 });
await new Promise(r => setTimeout(r, 3000));

console.log('Errors:', errors.length);
errors.forEach(e => console.log(e.substring(0, 300)));

console.log('\nRoot HTML:');
const rootHTML = await browserPage.evaluate(() => document.getElementById('root')?.innerHTML || 'NO ROOT');
console.log(rootHTML.substring(0, 300));

await browser.close();