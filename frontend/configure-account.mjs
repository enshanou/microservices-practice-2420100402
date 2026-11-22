import { randomBytes, scryptSync } from 'node:crypto';
import { readFile, writeFile, rename, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const [username, userId] = process.argv.slice(2);
const password = process.env.COURSELENS_NEW_PASSWORD ?? '';
delete process.env.COURSELENS_NEW_PASSWORD;
if (!username || username.length > 100 || !/^[a-f0-9-]{36}$/.test(userId ?? '')
    || password.length < 12 || password.length > 256) {
  console.error('Use an existing user UUID and a password of 12–256 characters.');
  process.exitCode = 1;
} else {
  const file = fileURLToPath(new URL('.workbench-settings.json', import.meta.url));
  const settings = JSON.parse((await readFile(file, 'utf8').catch(error => {
    if (error.code === 'ENOENT') return '{}';
    throw error;
  })).replace(/^\uFEFF/, ''));
  const accounts = new Map(Object.entries(settings.accounts ?? {}));
  const salt = randomBytes(16);
  const previous = accounts.get(username);
  accounts.set(username, { ...previous, userId, salt: salt.toString('hex'),
    passwordHash: scryptSync(password, salt, 64).toString('hex') });
  settings.accounts = Object.fromEntries(accounts);
  settings.requireLogin = true;
  const temporary = file + '.' + randomBytes(8).toString('hex') + '.tmp';
  try {
    await writeFile(temporary, JSON.stringify(settings, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
    await rename(temporary, file);
  } finally { await rm(temporary, { force: true }); }
  console.log('CourseLens login configured for ' + username);
}
