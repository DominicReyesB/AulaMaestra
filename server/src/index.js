require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const multer = require('multer');
const QRCode = require('qrcode');
const { pool, query, execute, initDb, ensureSalonsForTeacher } = require('./db');
const { hashPassword, verifyPassword } = require('./passwords');

const app = express();
const PORT = process.env.PORT || 3000;
const APP_DOWNLOAD_URL =
  process.env.APP_DOWNLOAD_URL ||
  'https://github.com/DominicReyesB/AulaMaestra/releases/download/v1.5.0/AulaMaestra-v1.5.0.apk';

app.set('trust proxy', 1);
app.use(cors());
app.use(express.json({ limit: '15mb' }));
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 40 * 1024 * 1024 } });
const helpRequests = new Map();

function publicBase(req) {
  const configured = process.env.PUBLIC_URL;
  return (configured || `${req.protocol}://${req.get('host')}`).replace(/\/$/, '');
}

function cleanFileName(value) {
  return path.basename(String(value || 'archivo')).replace(/[\r\n]/g, '_').slice(0, 255) || 'archivo';
}

function normalizeWebUrl(value) {
  const raw = String(value || '').trim();
  if (!raw) return null;
  const clean = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(raw) ? raw : `https://${raw}`;
  try {
    const parsed = new URL(clean);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.toString() : null;
  } catch (_e) {
    return null;
  }
}

function deduplicateAttachmentsJson(value, primaryLink) {
  if (!value) return null;
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    if (!Array.isArray(parsed)) return null;
    const seen = new Set(primaryLink ? [primaryLink.trim()] : []);
    const unique = [];
    for (const attachment of parsed) {
      if (!attachment || typeof attachment.url !== 'string') continue;
      const url = attachment.url.trim();
      if (!url || seen.has(url)) continue;
      seen.add(url);
      unique.push({
        kind: String(attachment.kind || 'file').slice(0, 20),
        url,
        name: attachment.name == null ? null : String(attachment.name).slice(0, 255),
      });
    }
    return unique.length > 0 ? JSON.stringify(unique) : null;
  } catch (_e) {
    return null;
  }
}

async function upgradePassword(table, id, password, stored) {
  if (stored && !stored.startsWith('scrypt$')) {
    await execute(`UPDATE ${table} SET password = ? WHERE id = ?`, [await hashPassword(password), id]);
  }
}

function salonRow(r) {
  return {
    id: Number(r.id),
    teacherId: Number(r.teacher_id),
    number: r.salon_number,
    inviteCode: r.invite_code,
  };
}

function postRow(r) {
  return {
    id: Number(r.id),
    type: r.post_type,
    title: r.title,
    body: r.body,
    filePath: r.file_path,
    linkUrl: r.link_url,
    createdAt: Number(r.created_at),
  };
}

function studentRow(r) {
  return { id: Number(r.id), displayName: r.display_name };
}

function submissionRow(r) {
  return {
    submissionId: Number(r.submission_id),
    postId: Number(r.post_id),
    assignmentTitle: r.assignment_title,
    studentId: Number(r.student_id),
    studentName: r.student_name,
    textAnswer: r.text_answer,
    filePath: r.file_path,
    linkUrl: r.link_url,
    attachmentsJson: r.attachments_json,
    submittedAt: Number(r.submitted_at),
    score: r.score == null ? null : Number(r.score),
    feedback: r.feedback,
  };
}

function messageRow(r) {
  return {
    id: Number(r.id),
    fromTeacher: !!r.from_teacher,
    body: r.body,
    createdAt: Number(r.created_at),
  };
}

app.get('/', (_req, res) => {
  res.json({
    ok: true,
    message: 'API Aula Maestra en línea. Prueba /health',
    health: '/health',
  });
});

app.get('/health', (_req, res) => res.json({ ok: true, db: 'mysql' }));

app.get('/download', (_req, res) => {
  res.redirect(302, APP_DOWNLOAD_URL);
});

app.get('/download/qr', async (req, res) => {
  try {
    const png = await QRCode.toBuffer(`${publicBase(req)}/download`, {
      type: 'png',
      width: 720,
      margin: 3,
      errorCorrectionLevel: 'H',
      color: { dark: '#202124', light: '#FFFFFF' },
    });
    res.set({
      'Content-Type': 'image/png',
      'Content-Length': png.length,
      'Cache-Control': 'public, max-age=86400',
    });
    res.send(png);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo generar el código QR' });
  }
});

app.get('/files/:fileId/:fileName?', async (req, res) => {
  try {
    if (!/^\d+$/.test(req.params.fileId)) {
      return res.status(410).send(
        '<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width">' +
        '<title>Archivo no disponible</title><body style="font-family:system-ui;padding:32px;line-height:1.5">' +
        '<h2>Este archivo era de una versión anterior</h2>' +
        '<p>El adjunto ya no está disponible. Pide a la maestra o maestro que vuelva a publicarlo.</p></body></html>'
      );
    }
    const rows = await query(
      'SELECT original_name, mime_type, data FROM uploaded_files WHERE id = ? LIMIT 1',
      [req.params.fileId]
    );
    if (rows.length === 0) {
      return res.status(404).json({ error: 'Archivo no encontrado' });
    }
    const file = rows[0];
    const fileName = cleanFileName(file.original_name);
    const asciiName = fileName.replace(/[^\x20-\x7E]/g, '_').replace(/["\\]/g, '_');
    res.set({
      'Content-Type': file.mime_type || 'application/octet-stream',
      'Content-Length': file.data.length,
      'Content-Disposition': `inline; filename="${asciiName}"; filename*=UTF-8''${encodeURIComponent(fileName)}`,
      'Cache-Control': 'private, max-age=3600',
      'X-Content-Type-Options': 'nosniff',
    });
    res.send(file.data);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo abrir el archivo' });
  }
});

app.get('/api/help/status', (_req, res) => {
  res.json({ available: Boolean(process.env.OPENAI_API_KEY) });
});

app.post('/api/help/ask', async (req, res) => {
  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ error: 'El asistente de IA aún no está configurado' });
  }
  const question = String(req.body.question || '').trim();
  if (!question) return res.status(400).json({ error: 'Escribe una pregunta' });
  if (question.length > 600) {
    return res.status(400).json({ error: 'La pregunta es demasiado larga' });
  }

  const requester = req.ip || 'unknown';
  const now = Date.now();
  const recent = (helpRequests.get(requester) || []).filter((time) => now - time < 10 * 60 * 1000);
  if (recent.length >= 10) {
    return res.status(429).json({ error: 'Espera unos minutos antes de hacer otra pregunta' });
  }
  recent.push(now);
  helpRequests.set(requester, recent);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 25000);
  try {
    const response = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: process.env.OPENAI_HELP_MODEL || 'gpt-4o-mini',
        store: false,
        max_output_tokens: 350,
        instructions:
          'Eres el asistente de Aula Maestra. Responde en español claro, amable y breve. ' +
          'Ayuda a alumnos y docentes con el uso de la app, tareas y dudas académicas de humanidades. ' +
          'La app tiene Anuncios, Pendientes, Entregadas, Calificar y Mensajes. ' +
          'No inventes datos del salón, calificaciones ni fechas. No pidas contraseñas ni datos personales.',
        input: question,
      }),
    });
    const data = await response.json();
    if (!response.ok) {
      console.error('OpenAI help error', response.status, data.error?.message || data);
      return res.status(502).json({ error: 'La IA no pudo responder en este momento' });
    }
    let answer = '';
    for (const item of data.output || []) {
      for (const content of item.content || []) {
        if (content.type === 'output_text' && content.text) answer += content.text;
      }
    }
    if (!answer.trim()) return res.status(502).json({ error: 'La IA no generó una respuesta' });
    res.json({ answer: answer.trim() });
  } catch (e) {
    console.error('OpenAI help request failed', e.message);
    res.status(502).json({ error: 'La IA no está disponible en este momento' });
  } finally {
    clearTimeout(timeout);
  }
});

app.get('/api/stats', async (_req, res) => {
  try {
    const teachers = await query('SELECT COUNT(*) AS n FROM teachers');
    const students = await query('SELECT COUNT(*) AS n FROM students');
    const salons = await query('SELECT COUNT(*) AS n FROM salons');
    const posts = await query('SELECT COUNT(*) AS n FROM posts');
    res.json({
      ok: true,
      teachers: Number(teachers[0].n),
      students: Number(students[0].n),
      salons: Number(salons[0].n),
      posts: Number(posts[0].n),
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo leer la base de datos' });
  }
});

app.post('/api/teachers/register', async (req, res) => {
  try {
    const username = String(req.body.username || '').trim().toLowerCase();
    const password = String(req.body.password || '');
    if (!username || !password) {
      return res.status(400).json({ error: 'Usuario y contraseña requeridos' });
    }
    const result = await execute(
      'INSERT INTO teachers (username, password) VALUES (?, ?)',
      [username, await hashPassword(password)]
    );
    const teacherId = result.insertId;
    await ensureSalonsForTeacher(teacherId);
    res.json({ id: Number(teacherId) });
  } catch (e) {
    if (e.code === 'ER_DUP_ENTRY') return res.status(409).json({ error: 'Usuario ya existe' });
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/teachers/login', async (req, res) => {
  try {
    const username = String(req.body.username || '').trim().toLowerCase();
    const password = String(req.body.password || '');
    const rows = await query('SELECT id, password FROM teachers WHERE username = ?', [username]);
    if (rows.length === 0 || !(await verifyPassword(password, rows[0].password))) {
      return res.status(401).json({ error: 'Credenciales incorrectas' });
    }
    await upgradePassword('teachers', rows[0].id, password, rows[0].password);
    const teacherId = rows[0].id;
    await ensureSalonsForTeacher(teacherId);
    res.json({ id: Number(teacherId) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

async function authenticateStudent(username, password) {
  try {
    const rows = await query(
      `SELECT id, display_name, password FROM students
       WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))
       ORDER BY id DESC`,
      [username]
    );
    if (rows.length === 0) {
      return null;
    }
    const student = rows[0];
    if (student.password == null || student.password === '') {
      return { error: 'Tu cuenta no tiene contraseña. Ve a Registrarse y usa el mismo nombre para actualizarla.' };
    }
    if (!(await verifyPassword(password, student.password))) {
      return null;
    }
    await upgradePassword('students', student.id, password, student.password);
    return student;
  } catch (e) {
    if (e.code !== 'ER_BAD_FIELD_ERROR') {
      throw e;
    }
    const rows = await query(
      `SELECT id, display_name FROM students
       WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))
       ORDER BY id DESC`,
      [username]
    );
    if (rows.length === 0) {
      return null;
    }
    return { error: 'El servidor aún no guarda contraseñas. Ve a Registrarse con tu código de salón.' };
  }
}

async function lastSalonForStudent(studentId) {
  const salons = await query(
    `SELECT e.salon_id, sal.salon_number FROM enrollments e
     INNER JOIN salons sal ON sal.id = e.salon_id
     WHERE e.student_id = ? ORDER BY e.id DESC LIMIT 1`,
    [studentId]
  );
  return salons.length > 0 ? salons[0] : null;
}

async function saveStudentPassword(studentId, password) {
  if (!password) {
    return;
  }
  try {
    await execute('UPDATE students SET password = ? WHERE id = ?', [await hashPassword(password), studentId]);
  } catch (e) {
    if (e.code !== 'ER_BAD_FIELD_ERROR') {
      throw e;
    }
  }
}

async function createStudentWithPassword(displayName, password) {
  try {
    const created = await execute('INSERT INTO students (display_name, password) VALUES (?, ?)', [
      displayName,
      await hashPassword(password),
    ]);
    return Number(created.insertId);
  } catch (e) {
    if (e.code !== 'ER_BAD_FIELD_ERROR') {
      throw e;
    }
    const created = await execute('INSERT INTO students (display_name) VALUES (?)', [displayName]);
    return Number(created.insertId);
  }
}

async function buildStudentLoginResponse(student) {
  const salon = await lastSalonForStudent(student.id);
  if (!salon) {
    return {
      error: 'No encontramos tu salón. Ve a Registrarse y confirma tu nombre y contraseña con el código del salón.',
      status: 404,
    };
  }
  return {
    body: {
      role: 'student',
      studentId: Number(student.id),
      displayName: student.display_name,
      salonId: Number(salon.salon_id),
      salonNumber: salon.salon_number,
    },
  };
}

/** Login unificado: nombre + contraseña (maestra o alumno). */
app.post('/api/auth/login', async (req, res) => {
  try {
    const username = String(req.body.username || req.body.displayName || '').trim();
    const password = String(req.body.password || '');
    if (!username || !password) {
      return res.status(400).json({ error: 'Nombre y contraseña requeridos' });
    }

    const teachers = await query(
      'SELECT id, password FROM teachers WHERE LOWER(username) = LOWER(?)',
      [username]
    );
    if (teachers.length > 0 && await verifyPassword(password, teachers[0].password)) {
      const teacherId = Number(teachers[0].id);
      await upgradePassword('teachers', teacherId, password, teachers[0].password);
      await ensureSalonsForTeacher(teacherId);
      return res.json({ role: 'teacher', teacherId });
    }

    const student = await authenticateStudent(username, password);
    if (student && student.error) {
      return res.status(401).json({ error: student.error });
    }
    if (!student) {
      return res.status(401).json({ error: 'Nombre o contraseña incorrectos' });
    }
    const login = await buildStudentLoginResponse(student);
    if (login.error) {
      return res.status(login.status).json({ error: login.error });
    }
    res.json(login.body);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

/** Login de alumno: solo nombre + contraseña (sin código de salón). */
app.post('/api/students/login', async (req, res) => {
  try {
    const username = String(req.body.username || req.body.displayName || '').trim();
    const password = String(req.body.password || '');
    if (!username || !password) {
      return res.status(400).json({ error: 'Nombre y contraseña requeridos' });
    }
    const student = await authenticateStudent(username, password);
    if (student && student.error) {
      return res.status(401).json({ error: student.error });
    }
    if (!student) {
      return res.status(401).json({ error: 'Nombre o contraseña incorrectos' });
    }
    const login = await buildStudentLoginResponse(student);
    if (login.error) {
      return res.status(login.status).json({ error: login.error });
    }
    res.json(login.body);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

/** Registro de alumno: nombre, contraseña y código del salón. */
app.post('/api/students/register', async (req, res) => {
  try {
    const displayName = String(req.body.displayName || '').trim();
    const password = String(req.body.password || '');
    const inviteCode = String(req.body.inviteCode || '').trim().toUpperCase();
    if (!displayName || !password) {
      return res.status(400).json({ error: 'Nombre y contraseña requeridos' });
    }
    if (!inviteCode) {
      return res.status(400).json({ error: 'Código del salón requerido' });
    }

    const salonRows = await query('SELECT * FROM salons WHERE invite_code = ?', [inviteCode]);
    if (salonRows.length === 0) {
      return res.status(404).json({ error: 'Código del salón no válido' });
    }
    const salon = salonRows[0];

    const dup = await query(
      'SELECT id, password FROM students WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?)) ORDER BY id DESC',
      [displayName]
    );
    if (dup.length > 0) {
      const studentId = Number(dup[0].id);
      const enrollment = await query(
        'SELECT salon_id FROM enrollments WHERE student_id = ? LIMIT 1',
        [studentId]
      );
      if (enrollment.length > 0 && Number(enrollment[0].salon_id) !== Number(salon.id)) {
        return res.status(409).json({ error: 'Este alumno ya pertenece a otro salón' });
      }
      if (dup[0].password != null && dup[0].password !== '') {
        return res.status(409).json({ error: 'Ese nombre ya está registrado. Inicia sesión con tu contraseña.' });
      }
      await saveStudentPassword(studentId, password);
      await execute('INSERT IGNORE INTO enrollments (student_id, salon_id) VALUES (?, ?)', [
        studentId,
        salon.id,
      ]);
      return res.json({
        studentId,
        salonId: Number(salon.id),
        displayName,
        salonNumber: salon.salon_number,
      });
    }

    const studentId = await createStudentWithPassword(displayName, password);
    await execute('INSERT IGNORE INTO enrollments (student_id, salon_id) VALUES (?, ?)', [
      studentId,
      salon.id,
    ]);

    res.json({
      studentId,
      salonId: Number(salon.id),
      displayName,
      salonNumber: salon.salon_number,
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/teachers/:teacherId/salons', async (req, res) => {
  try {
    const rows = await query(
      'SELECT * FROM salons WHERE teacher_id = ? ORDER BY salon_number',
      [req.params.teacherId]
    );
    res.json(rows.map(salonRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/:salonId', async (req, res) => {
  try {
    const rows = await query('SELECT * FROM salons WHERE id = ?', [req.params.salonId]);
    if (rows.length === 0) return res.status(404).json({ error: 'Salón no encontrado' });
    res.json(salonRow(rows[0]));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/by-code/:code', async (req, res) => {
  try {
    const code = String(req.params.code || '').trim().toUpperCase();
    const rows = await query('SELECT * FROM salons WHERE invite_code = ?', [code]);
    if (rows.length === 0) return res.status(404).json({ error: 'Código no válido' });
    res.json(salonRow(rows[0]));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/students', async (req, res) => {
  try {
    const displayName = String(req.body.displayName || '').trim();
    if (!displayName) return res.status(400).json({ error: 'Nombre requerido' });
    const result = await execute('INSERT INTO students (display_name) VALUES (?)', [displayName]);
    res.json({ id: Number(result.insertId) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

/** Busca alumno por nombre: primero inscrito en el salón; si no, fila en students (datos viejos sin enrollment). */
async function findStudentByNameForSalon(salonId, displayName) {
  const enrolled = await query(
    `SELECT s.id, s.display_name FROM students s
     INNER JOIN enrollments e ON e.student_id = s.id AND e.salon_id = ?
     WHERE LOWER(TRIM(s.display_name)) = LOWER(TRIM(?))
     ORDER BY s.id ASC`,
    [salonId, displayName]
  );
  if (enrolled.length > 0) {
    return enrolled[0];
  }

  const byName = await query(
    `SELECT id, display_name FROM students
     WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))
     ORDER BY id DESC`,
    [displayName]
  );
  if (byName.length === 0) {
    return null;
  }
  return byName[0];
}

app.post('/api/students/join-salon', async (req, res) => {
  try {
    const inviteCode = String(req.body.inviteCode || '').trim().toUpperCase();
    const displayName = String(req.body.displayName || '').trim();
    const password = String(req.body.password || '');
    const studentId = req.body.studentId != null ? Number(req.body.studentId) : null;
    const mode = String(req.body.mode || 'register').toLowerCase();

    if (!inviteCode) {
      return res.status(400).json({ error: 'Código del salón requerido' });
    }

    const salonRows = await query('SELECT * FROM salons WHERE invite_code = ?', [inviteCode]);
    if (salonRows.length === 0) {
      return res.status(404).json({ error: 'Código no válido' });
    }
    const salon = salonRows[0];

    let finalStudentId = studentId;
    let finalName = displayName;

    if (finalStudentId != null && finalStudentId > 0) {
      const st = await query('SELECT id, display_name FROM students WHERE id = ?', [finalStudentId]);
      if (st.length === 0) {
        return res.status(404).json({ error: 'No encontramos tu registro. Usa tu nombre en Iniciar sesión.' });
      }
      finalName = st[0].display_name;
    } else {
      if (!finalName) {
        return res.status(400).json({
          error: mode === 'login' ? 'Escribe el nombre con el que te registraste' : 'Escribe tu nombre para registrarte',
        });
      }

      const existing = await findStudentByNameForSalon(salon.id, finalName);

      if (mode === 'login') {
        if (!existing) {
          return res.status(404).json({
            error: 'No hay ningún alumno con ese nombre. Regístrate la primera vez abajo.',
          });
        }
        finalStudentId = Number(existing.id);
        finalName = existing.display_name;
      } else if (existing) {
        finalStudentId = Number(existing.id);
        finalName = existing.display_name;
        await saveStudentPassword(finalStudentId, password);
      } else {
        finalStudentId = await createStudentWithPassword(finalName, password);
      }
    }

    const currentEnrollment = await query(
      'SELECT salon_id FROM enrollments WHERE student_id = ? LIMIT 1',
      [finalStudentId]
    );
    if (currentEnrollment.length > 0 &&
        Number(currentEnrollment[0].salon_id) !== Number(salon.id)) {
      return res.status(409).json({ error: 'Este alumno ya pertenece a otro salón' });
    }

    await execute('INSERT IGNORE INTO enrollments (student_id, salon_id) VALUES (?, ?)', [
      finalStudentId,
      salon.id,
    ]);

    res.json({
      studentId: finalStudentId,
      salonId: Number(salon.id),
      displayName: finalName,
      salonNumber: salon.salon_number,
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.put('/api/students/:studentId', async (req, res) => {
  try {
    const displayName = String(req.body.displayName || '').trim();
    await execute('UPDATE students SET display_name = ? WHERE id = ?', [
      displayName,
      req.params.studentId,
    ]);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/students/:studentId', async (req, res) => {
  try {
    const rows = await query('SELECT * FROM students WHERE id = ?', [req.params.studentId]);
    if (rows.length === 0) return res.status(404).json({ error: 'Alumno no encontrado' });
    res.json(studentRow(rows[0]));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/salons/:salonId/enroll', async (req, res) => {
  try {
    const studentId = Number(req.body.studentId);
    const current = await query(
      'SELECT salon_id FROM enrollments WHERE student_id = ? LIMIT 1',
      [studentId]
    );
    if (current.length > 0 && Number(current[0].salon_id) !== Number(req.params.salonId)) {
      return res.status(409).json({ error: 'Este alumno ya pertenece a otro salón' });
    }
    await execute('INSERT IGNORE INTO enrollments (student_id, salon_id) VALUES (?, ?)', [
      studentId,
      req.params.salonId,
    ]);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/:salonId/students', async (req, res) => {
  try {
    const rows = await query(
      `SELECT s.id, s.display_name FROM students s
       INNER JOIN enrollments e ON e.student_id = s.id
       WHERE e.salon_id = ? ORDER BY s.display_name`,
      [req.params.salonId]
    );
    res.json(rows.map(studentRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

async function deleteStudent(req, res) {
  try {
    const enrolled = await query(
      'SELECT id FROM enrollments WHERE salon_id = ? AND student_id = ?',
      [req.params.salonId, req.params.studentId]
    );
    if (enrolled.length === 0) {
      return res.status(404).json({ error: 'El alumno no pertenece a este salón' });
    }
    await execute('DELETE FROM students WHERE id = ?', [req.params.studentId]);
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo eliminar al alumno' });
  }
}

app.delete('/api/salons/:salonId/students/:studentId', deleteStudent);
app.post('/api/salons/:salonId/students/:studentId/delete', deleteStudent);

app.get('/api/salons/:salonId/posts', async (req, res) => {
  try {
    res.set('Cache-Control', 'no-store');
    const type = req.query.type;
    let rows;
    if (type === undefined || type === null || type === '') {
      rows = await query(
        'SELECT * FROM posts WHERE salon_id = ? ORDER BY created_at DESC',
        [req.params.salonId]
      );
    } else {
      rows = await query(
        'SELECT * FROM posts WHERE salon_id = ? AND post_type = ? ORDER BY created_at DESC',
        [req.params.salonId, Number(type)]
      );
    }
    res.json(rows.map(postRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/salons/:salonId/posts', async (req, res) => {
  try {
    const { postType, title, body, filePath, linkUrl } = req.body;
    const normalizedLink = normalizeWebUrl(linkUrl);
    if (linkUrl && !normalizedLink) {
      return res.status(400).json({ error: 'El enlace adjunto no es válido' });
    }
    const result = await execute(
      `INSERT INTO posts (salon_id, post_type, title, body, file_path, link_url, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [req.params.salonId, postType, title, body || null, filePath || null, normalizedLink, Date.now()]
    );
    res.json({ id: Number(result.insertId) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

async function deletePost(req, res) {
  try {
    const result = await execute('DELETE FROM posts WHERE id = ?', [req.params.postId]);
    if (result.affectedRows === 0) {
      return res.status(404).json({ error: 'Publicación no encontrada' });
    }
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo eliminar la publicación' });
  }
}

app.delete('/api/posts/:postId', deletePost);
app.post('/api/posts/:postId/delete', deletePost);

app.post('/api/posts/:postId/submissions', async (req, res) => {
  try {
    const postId = Number(req.params.postId);
    const studentId = Number(req.body.studentId);
    const textAnswer = req.body.textAnswer || null;
    const filePath = req.body.filePath || null;
    const linkUrl = normalizeWebUrl(req.body.linkUrl);
    if (req.body.linkUrl && !linkUrl) {
      return res.status(400).json({ error: 'El enlace adjunto no es válido' });
    }
    const attachmentsJson = deduplicateAttachmentsJson(req.body.attachmentsJson, linkUrl);
    const now = Date.now();
    const existing = await query(
      'SELECT id FROM submissions WHERE post_id = ? AND student_id = ?',
      [postId, studentId]
    );
    if (existing.length > 0) {
      return res.status(409).json({ error: 'Esta tarea ya fue entregada. Solo se permite una entrega por tarea.' });
    }
    try {
      const result = await execute(
        `INSERT INTO submissions (post_id, student_id, text_answer, file_path, link_url, attachments_json, submitted_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [postId, studentId, textAnswer, filePath, linkUrl, attachmentsJson, now]
      );
      res.json({ id: Number(result.insertId) });
    } catch (colErr) {
      if (colErr.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'Esta tarea ya fue entregada. Solo se permite una entrega por tarea.' });
      }
      if (colErr.code !== 'ER_BAD_FIELD_ERROR') {
        throw colErr;
      }
      const result = await execute(
        `INSERT INTO submissions (post_id, student_id, text_answer, file_path, submitted_at)
         VALUES (?, ?, ?, ?, ?)`,
        [postId, studentId, textAnswer, filePath, now]
      );
      res.json({ id: Number(result.insertId) });
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

async function deleteSubmission(req, res) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    const [owned] = await connection.query(
      'SELECT id FROM submissions WHERE id = ? AND student_id = ? FOR UPDATE',
      [req.params.submissionId, req.params.studentId]
    );
    if (owned.length === 0) {
      await connection.rollback();
      return res.status(404).json({ error: 'Entrega no encontrada' });
    }
    const [grades] = await connection.query(
      'SELECT id FROM grades WHERE submission_id = ? LIMIT 1',
      [req.params.submissionId]
    );
    if (grades.length > 0) {
      await connection.rollback();
      return res.status(409).json({ error: 'Una entrega ya calificada no se puede eliminar' });
    }
    await connection.query('DELETE FROM grades WHERE submission_id = ?', [req.params.submissionId]);
    const [result] = await connection.query(
      'DELETE FROM submissions WHERE id = ? AND student_id = ?',
      [req.params.submissionId, req.params.studentId]
    );
    if (result.affectedRows === 0) {
      await connection.rollback();
      return res.status(404).json({ error: 'Entrega no encontrada' });
    }
    await connection.commit();
    res.json({ ok: true });
  } catch (e) {
    await connection.rollback();
    console.error(e);
    res.status(500).json({ error: 'No se pudo eliminar la entrega' });
  } finally {
    connection.release();
  }
}

app.delete('/api/submissions/:submissionId/students/:studentId', deleteSubmission);
app.post('/api/submissions/:submissionId/students/:studentId/delete', deleteSubmission);

app.get('/api/salons/:salonId/submissions', async (req, res) => {
  try {
    let rows;
    try {
      rows = await query(
        `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
                sub.student_id, st.display_name AS student_name,
                sub.text_answer, sub.file_path, sub.link_url, sub.attachments_json,
                sub.submitted_at, g.score, g.feedback
         FROM submissions sub
         INNER JOIN posts p ON p.id = sub.post_id
         INNER JOIN students st ON st.id = sub.student_id
         LEFT JOIN grades g ON g.submission_id = sub.id
         WHERE p.salon_id = ? AND p.post_type = 1
         ORDER BY sub.submitted_at DESC`,
        [req.params.salonId]
      );
    } catch (colErr) {
      if (colErr.code !== 'ER_BAD_FIELD_ERROR') {
        throw colErr;
      }
      rows = await query(
        `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
                sub.student_id, st.display_name AS student_name,
                sub.text_answer, sub.file_path, NULL AS link_url, NULL AS attachments_json,
                sub.submitted_at, g.score, g.feedback
         FROM submissions sub
         INNER JOIN posts p ON p.id = sub.post_id
         INNER JOIN students st ON st.id = sub.student_id
         LEFT JOIN grades g ON g.submission_id = sub.id
         WHERE p.salon_id = ? AND p.post_type = 1
         ORDER BY sub.submitted_at DESC`,
        [req.params.salonId]
      );
    }
    res.json(rows.map(submissionRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/:salonId/students/:studentId/submissions', async (req, res) => {
  try {
    let rows;
    try {
      rows = await query(
        `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
                sub.student_id, st.display_name AS student_name,
                sub.text_answer, sub.file_path, sub.link_url, sub.attachments_json,
                sub.submitted_at, g.score, g.feedback
         FROM submissions sub
         INNER JOIN posts p ON p.id = sub.post_id
         INNER JOIN students st ON st.id = sub.student_id
         LEFT JOIN grades g ON g.submission_id = sub.id
         WHERE p.salon_id = ? AND sub.student_id = ?
         ORDER BY sub.submitted_at DESC`,
        [req.params.salonId, req.params.studentId]
      );
    } catch (colErr) {
      if (colErr.code !== 'ER_BAD_FIELD_ERROR') {
        throw colErr;
      }
      rows = await query(
        `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
                sub.student_id, st.display_name AS student_name,
                sub.text_answer, sub.file_path, NULL AS link_url, NULL AS attachments_json,
                sub.submitted_at, g.score, g.feedback
         FROM submissions sub
         INNER JOIN posts p ON p.id = sub.post_id
         INNER JOIN students st ON st.id = sub.student_id
         LEFT JOIN grades g ON g.submission_id = sub.id
         WHERE p.salon_id = ? AND sub.student_id = ?
         ORDER BY sub.submitted_at DESC`,
        [req.params.salonId, req.params.studentId]
      );
    }
    res.json(rows.map(submissionRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.put('/api/submissions/:submissionId/grade', async (req, res) => {
  try {
    const { score, feedback } = req.body;
    const numericScore = Number(score);
    if (!Number.isFinite(numericScore) || numericScore < 0 || numericScore > 10) {
      return res.status(400).json({ error: 'La calificación debe ser de 0 a 10' });
    }
    await execute(
      `INSERT INTO grades (submission_id, score, feedback) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE score = VALUES(score), feedback = VALUES(feedback)`,
      [req.params.submissionId, numericScore, feedback || null]
    );
    res.json({ ok: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/:salonId/students/:studentId/messages', async (req, res) => {
  try {
    const rows = await query(
      `SELECT id, from_teacher, body, created_at FROM messages
       WHERE salon_id = ? AND student_id = ? ORDER BY created_at ASC`,
      [req.params.salonId, req.params.studentId]
    );
    res.json(rows.map(messageRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/salons/:salonId/students/:studentId/messages', async (req, res) => {
  try {
    const { fromTeacher, body } = req.body;
    const result = await execute(
      `INSERT INTO messages (salon_id, student_id, from_teacher, body, created_at)
       VALUES (?, ?, ?, ?, ?)`,
      [req.params.salonId, req.params.studentId, fromTeacher ? 1 : 0, body, Date.now()]
    );
    res.json({ id: Number(result.insertId) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/upload', upload.single('file'), async (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'Archivo requerido' });
    const fileName = cleanFileName(req.file.originalname);
    const result = await execute(
      `INSERT INTO uploaded_files (original_name, mime_type, data, created_at)
       VALUES (?, ?, ?, ?)`,
      [fileName, req.file.mimetype || 'application/octet-stream', req.file.buffer, Date.now()]
    );
    const url = `${publicBase(req)}/files/${result.insertId}/${encodeURIComponent(fileName)}`;
    res.json({ path: url, fileName });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo guardar el archivo' });
  }
});

app.use((err, _req, res, next) => {
  if (err instanceof multer.MulterError && err.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: 'El archivo supera el límite de 40 MB' });
  }
  if (err) {
    console.error(err);
    return res.status(500).json({ error: 'Error del servidor' });
  }
  next();
});

async function start() {
  if (!pool) {
    console.error(
      'Falta conexión MySQL. En Railway: añade MySQL y enlaza MYSQL_URL (o MYSQLHOST, MYSQLUSER, etc.).'
    );
    process.exit(1);
  }
  await initDb();
  app.listen(PORT, () => console.log(`API Aula Maestra (MySQL) en puerto ${PORT}`));
}

start().catch((e) => {
  console.error(e);
  process.exit(1);
});
