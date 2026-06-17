const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

function createPool() {
  const url = process.env.MYSQL_URL || process.env.DATABASE_URL;
  if (url && (url.startsWith('mysql://') || url.startsWith('mysql2://'))) {
    return mysql.createPool({
      uri: url,
      waitForConnections: true,
      connectionLimit: 10,
      ssl: url.includes('rlwy.net') || url.includes('railway') ? { rejectUnauthorized: false } : undefined,
    });
  }
  if (process.env.MYSQLHOST) {
    return mysql.createPool({
      host: process.env.MYSQLHOST,
      port: Number(process.env.MYSQLPORT || 3306),
      user: process.env.MYSQLUSER,
      password: process.env.MYSQLPASSWORD,
      database: process.env.MYSQLDATABASE,
      waitForConnections: true,
      connectionLimit: 10,
      ssl:
        process.env.MYSQLHOST.includes('rlwy.net') || process.env.MYSQLHOST.includes('railway')
          ? { rejectUnauthorized: false }
          : undefined,
    });
  }
  return null;
}

const pool = createPool();

async function query(sql, params = []) {
  const [rows] = await pool.query(sql, params);
  return rows;
}

async function execute(sql, params = []) {
  const [result] = await pool.query(sql, params);
  return result;
}

async function initDb() {
  const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  const statements = schema
    .split(';')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  for (const stmt of statements) {
    await pool.query(stmt);
  }
  await migrateDb();
}

async function migrateDb() {
  const alters = [
    'ALTER TABLE students ADD COLUMN password VARCHAR(255) NULL',
    'ALTER TABLE submissions ADD COLUMN link_url TEXT NULL',
    'ALTER TABLE submissions ADD COLUMN attachments_json TEXT NULL',
  ];
  for (const sql of alters) {
    try {
      await pool.query(sql);
    } catch (e) {
      if (e.code !== 'ER_DUP_FIELDNAME') throw e;
    }
  }
}

function randomCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}

async function ensureSalonsForTeacher(teacherId) {
  const nums = [601, 602, 603, 604, 605];
  for (const n of nums) {
    const existing = await query(
      'SELECT id FROM salons WHERE teacher_id = ? AND salon_number = ?',
      [teacherId, n]
    );
    if (existing.length === 0) {
      let code = randomCode();
      for (let tries = 0; tries < 5; tries++) {
        try {
          await execute(
            'INSERT INTO salons (teacher_id, salon_number, invite_code) VALUES (?, ?, ?)',
            [teacherId, n, code]
          );
          break;
        } catch (e) {
          if (e.code === 'ER_DUP_ENTRY') code = randomCode();
          else throw e;
        }
      }
    }
  }
}

module.exports = { pool, query, execute, initDb, ensureSalonsForTeacher, randomCode };
