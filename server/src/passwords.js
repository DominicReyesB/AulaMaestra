const crypto = require('crypto');
const { promisify } = require('util');

const scrypt = promisify(crypto.scrypt);

async function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = await scrypt(password, salt, 32);
  return `scrypt$${salt}$${derived.toString('hex')}`;
}

async function verifyPassword(password, stored) {
  if (!stored) return false;
  if (!stored.startsWith('scrypt$')) {
    const supplied = Buffer.from(password);
    const legacy = Buffer.from(stored);
    return supplied.length === legacy.length && crypto.timingSafeEqual(supplied, legacy);
  }
  const parts = stored.split('$');
  if (parts.length !== 3) return false;
  const expected = Buffer.from(parts[2], 'hex');
  const supplied = await scrypt(password, parts[1], expected.length);
  return supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);
}

module.exports = { hashPassword, verifyPassword };
