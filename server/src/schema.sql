CREATE TABLE IF NOT EXISTS teachers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(191) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS salons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    salon_number INT NOT NULL,
    invite_code VARCHAR(16) NOT NULL UNIQUE,
    UNIQUE KEY uk_teacher_salon (teacher_id, salon_number),
    CONSTRAINT fk_salons_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS enrollments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    salon_id BIGINT NOT NULL,
    UNIQUE KEY uk_enrollment (student_id, salon_id),
    CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_enroll_salon FOREIGN KEY (salon_id) REFERENCES salons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    salon_id BIGINT NOT NULL,
    post_type INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    body TEXT,
    file_path TEXT,
    created_at BIGINT NOT NULL,
    CONSTRAINT fk_posts_salon FOREIGN KEY (salon_id) REFERENCES salons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    text_answer TEXT,
    file_path TEXT,
    submitted_at BIGINT NOT NULL,
    UNIQUE KEY uk_submission (post_id, student_id),
    CONSTRAINT fk_sub_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grades (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL UNIQUE,
    score DOUBLE NOT NULL,
    feedback TEXT,
    CONSTRAINT fk_grade_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    salon_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    from_teacher TINYINT(1) NOT NULL,
    body TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT fk_msg_salon FOREIGN KEY (salon_id) REFERENCES salons (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
