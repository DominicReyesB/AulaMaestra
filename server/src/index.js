require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const { pool, query, execute, initDb, ensureSalonsForTeacher } = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;
const uploadDir = path.join(__dirname, '..', 'uploads');
fs.mkdirSync(uploadDir, { recursive: true });

app.use(cors());
app.use(express.json({ limit: '15mb' }));
app.use('/files', express.static(uploadDir));

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, uploadDir),
  filename: (_req, file, cb) => {
    const safe = Date.now() + '-' + (file.originalname || 'file').replace(/[^\w.\-]/g, '_');
    cb(null, safe);
  },
});
const upload = multer({ storage, limits: { fileSize: 20 * 1024 * 1024 } });

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

app.get('/health', (_req, res) => res.json({ ok: true, db: 'mysql' }));

app.post('/api/teachers/register', async (req, res) => {
  try {
    const username = String(req.body.username || '').trim().toLowerCase();
    const password = String(req.body.password || '');
    if (!username || !password) {
      return res.status(400).json({ error: 'Usuario y contraseña requeridos' });
    }
    const result = await execute(
      'INSERT INTO teachers (username, password) VALUES (?, ?)',
      [username, password]
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
    const rows = await query(
      'SELECT id FROM teachers WHERE username = ? AND password = ?',
      [username, password]
    );
    if (rows.length === 0) {
      return res.status(401).json({ error: 'Credenciales incorrectas' });
    }
    const teacherId = rows[0].id;
    await ensureSalonsForTeacher(teacherId);
    res.json({ id: Number(teacherId) });
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

app.get('/api/salons/:salonId/posts', async (req, res) => {
  try {
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
    const { postType, title, body, filePath } = req.body;
    const result = await execute(
      `INSERT INTO posts (salon_id, post_type, title, body, file_path, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [req.params.salonId, postType, title, body || null, filePath || null, Date.now()]
    );
    res.json({ id: Number(result.insertId) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.post('/api/posts/:postId/submissions', async (req, res) => {
  try {
    const postId = Number(req.params.postId);
    const studentId = Number(req.body.studentId);
    const textAnswer = req.body.textAnswer || null;
    const filePath = req.body.filePath || null;
    const now = Date.now();
    const existing = await query(
      'SELECT id FROM submissions WHERE post_id = ? AND student_id = ?',
      [postId, studentId]
    );
    if (existing.length > 0) {
      await execute(
        'UPDATE submissions SET text_answer = ?, file_path = ?, submitted_at = ? WHERE id = ?',
        [textAnswer, filePath, now, existing[0].id]
      );
      res.json({ id: Number(existing[0].id) });
    } else {
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

app.get('/api/salons/:salonId/submissions', async (req, res) => {
  try {
    const rows = await query(
      `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
              sub.student_id, st.display_name AS student_name,
              sub.text_answer, sub.file_path, sub.submitted_at, g.score, g.feedback
       FROM submissions sub
       INNER JOIN posts p ON p.id = sub.post_id
       INNER JOIN students st ON st.id = sub.student_id
       LEFT JOIN grades g ON g.submission_id = sub.id
       WHERE p.salon_id = ? AND p.post_type = 1
       ORDER BY sub.submitted_at DESC`,
      [req.params.salonId]
    );
    res.json(rows.map(submissionRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.get('/api/salons/:salonId/students/:studentId/submissions', async (req, res) => {
  try {
    const rows = await query(
      `SELECT sub.id AS submission_id, sub.post_id, p.title AS assignment_title,
              sub.student_id, st.display_name AS student_name,
              sub.text_answer, sub.file_path, sub.submitted_at, g.score, g.feedback
       FROM submissions sub
       INNER JOIN posts p ON p.id = sub.post_id
       INNER JOIN students st ON st.id = sub.student_id
       LEFT JOIN grades g ON g.submission_id = sub.id
       WHERE p.salon_id = ? AND sub.student_id = ?
       ORDER BY sub.submitted_at DESC`,
      [req.params.salonId, req.params.studentId]
    );
    res.json(rows.map(submissionRow));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

app.put('/api/submissions/:submissionId/grade', async (req, res) => {
  try {
    const { score, feedback } = req.body;
    await execute(
      `INSERT INTO grades (submission_id, score, feedback) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE score = VALUES(score), feedback = VALUES(feedback)`,
      [req.params.submissionId, score, feedback || null]
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

app.post('/api/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'Archivo requerido' });
  const base = process.env.PUBLIC_URL || `${req.protocol}://${req.get('host')}`;
  const url = `${base.replace(/\/$/, '')}/files/${req.file.filename}`;
  res.json({ path: url, fileName: req.file.originalname });
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
