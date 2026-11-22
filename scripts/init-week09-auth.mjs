import { pbkdf2Sync, randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const envFile = new URL('../.env', import.meta.url);
let contents;
try {
  contents = readFileSync(envFile, 'utf8');
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
  console.error('Create .env and configure the existing MySQL and Nacos settings first.');
  process.exit(1);
}

const keys = ['WEEK09_AUTH_ENABLED', 'WEEK09_JWT_SECRET',
  'WEEK09_USER_PASSWORD_HASH', 'WEEK09_ADMIN_PASSWORD_HASH'];
const isAuthSetting = /^\s*(?:export\s+)?(WEEK09_[A-Z_]+)\s*=\s*(.*?)\s*$/;
const lines = contents.split(/\r?\n/);
const entries = new Map();
for (const line of lines) {
  const match = isAuthSetting.exec(line);
  if (match && keys.includes(match[1])) entries.set(match[1], match[2]);
}

if (keys.slice(1).some(key => entries.get(key)) && process.argv[2] !== '--rotate') {
  if (entries.get(keys[0]) === 'true' && keys.slice(1).every(key => entries.get(key))) {
    console.log('Week 09 auth is already configured. Use --rotate if you lost the test passwords.');
    process.exit(0);
  }
  console.error('Week 09 auth settings are incomplete. Run again with --rotate to generate a new set.');
  process.exit(1);
}

function demoAccount() {
  const password = randomBytes(18).toString('base64url');
  const salt = randomBytes(16);
  const hash = pbkdf2Sync(password, salt, 600_000, 32, 'sha256');
  return { password, stored: `${salt.toString('base64url')}:${hash.toString('base64url')}` };
}

const user = demoAccount();
const admin = demoAccount();
const settings = ['true', randomBytes(32).toString('base64url'), user.stored, admin.stored];
const newline = contents.includes('\r\n') ? '\r\n' : '\n';
const preserved = lines.filter(line => !keys.includes(isAuthSetting.exec(line)?.[1]));
while (preserved.at(-1) === '') preserved.pop();
const updated = [...preserved, '', ...keys.map((key, index) => `${key}=${settings[index]}`), ''];
writeFileSync(envFile, updated.join(newline));
console.log('Save these local demo passwords now. Only their hashes are written to .env.');
console.log(`week09-user  (USER)  ${user.password}`);
console.log(`week09-admin (ADMIN) ${admin.password}`);
