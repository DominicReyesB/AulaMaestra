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
const upload = multer({ storage, limits: { fileSize: 50 * 1024 * 1024 } });

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

async function authenticateStudent(username, password) {
  try {
    const rows = await query(
      `SELECT id, display_name, password FROM students
       WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))`,
      [username]
    );
    if (rows.length === 0) {
      return null;
    }
    const student = rows[0];
    if (student.password == null || student.password === '') {
      return { error: 'Tu cuenta no tiene contraseña. Regístrate de nuevo con el código del salón.' };
    }
    if (student.password !== password) {
      return null;
    }
    return student;
  } catch (e) {
    if (e.code !== 'ER_BAD_FIELD_ERROR') {
      throw e;
    }
    const rows = await query(
      `SELECT id, display_name FROM students
       WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))`,
      [username]
    );
    return rows.length > 0 ? rows[0] : null;
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

/** Login unificado: nombre + contraseña (maestra o alumno). */
app.post('/api/auth/login', async (req, res) => {
  try {
    const username = String(req.body.username || req.body.displayName || '').trim();
    const password = String(req.body.password || '');
    if (!username || !password) {
      return res.status(400).json({ error: 'Nombre y contraseña requeridos' });
    }

    const teachers = await query(
      'SELECT id FROM teachers WHERE LOWER(username) = LOWER(?) AND password = ?',
      [username, password]
    );
    if (teachers.length > 0) {
      const teacherId = Number(teachers[0].id);
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
    const salon = await lastSalonForStudent(student.id);
    if (!salon) {
      return res.status(404).json({
        error: 'No estás en ningún salón. Regístrate con el código que te dio tu maestra.',
      });
    }
    res.json({
      role: 'student',
      studentId: Number(student.id),
      displayName: student.display_name,
      salonId: Number(salon.salon_id),
      salonNumber: salon.salon_number,
    });
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
      'SELECT id FROM students WHERE LOWER(TRIM(display_name)) = LOWER(TRIM(?))',
      [displayName]
    );
    if (dup.length > 0) {
      return res.status(409).json({ error: 'Ese nombre ya existe. Inicia sesión o elige otro.' });
    }

    const created = await execute(
      'INSERT INTO students (display_name, password) VALUES (?, ?)',
      [displayName, password]
    ).catch(async (e) => {
      if (e.code !== 'ER_BAD_FIELD_ERROR') {
        throw e;
      }
      return execute('INSERT INTO students (display_name) VALUES (?)', [displayName]);
    });
    const studentId = Number(created.insertId);
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
        // Registro idempotente: ya existe → entrar sin crear duplicado
        finalStudentId = Number(existing.id);
        finalName = existing.display_name;
      } else {
        const created = await execute('INSERT INTO students (display_name) VALUES (?)', [finalName]);
        finalStudentId = Number(created.insertId);
      }
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
    const linkUrl = req.body.linkUrl || null;
    const attachmentsJson = req.body.attachmentsJson || null;
    const now = Date.now();
    const existing = await query(
      'SELECT id FROM submissions WHERE post_id = ? AND student_id = ?',
      [postId, studentId]
    );
    try {
      if (existing.length > 0) {
        await execute(
          `UPDATE submissions SET text_answer = ?, file_path = ?, link_url = ?,
           attachments_json = ?, submitted_at = ? WHERE id = ?`,
          [textAnswer, filePath, linkUrl, attachmentsJson, now, existing[0].id]
        );
        res.json({ id: Number(existing[0].id) });
      } else {
        const result = await execute(
          `INSERT INTO submissions (post_id, student_id, text_answer, file_path, link_url, attachments_json, submitted_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
          [postId, studentId, textAnswer, filePath, linkUrl, attachmentsJson, now]
        );
        res.json({ id: Number(result.insertId) });
      }
    } catch (colErr) {
      if (colErr.code !== 'ER_BAD_FIELD_ERROR') {
        throw colErr;
      }
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
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Error del servidor' });
  }
});

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
