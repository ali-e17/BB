-- MySQL 8 / MariaDB schema for the PHP API. Use utf8mb4 and InnoDB.
SET NAMES utf8mb4;

CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(15) NOT NULL UNIQUE,
    national_id CHAR(10) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','TEACHER','STUDENT') NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    admin_guard TINYINT GENERATED ALWAYS AS (CASE WHEN role = 'ADMIN' THEN 1 ELSE NULL END) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_single_admin (admin_guard)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_profiles (
    user_id CHAR(36) PRIMARY KEY,
    student_code VARCHAR(30) NOT NULL UNIQUE,
    registration_date DATE NOT NULL,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classes (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    teacher_id CHAR(36) NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    session_count SMALLINT UNSIGNED NOT NULL,
    status ENUM('ACTIVE','COMPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    CONSTRAINT chk_class_time CHECK (end_time > start_time),
    CONSTRAINT chk_session_count CHECK (session_count > 0),
    CONSTRAINT fk_class_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_days (
    class_id CHAR(36) NOT NULL,
    weekday TINYINT UNSIGNED NOT NULL COMMENT '1=Saturday ... 7=Friday',
    PRIMARY KEY (class_id, weekday),
    CONSTRAINT chk_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT fk_class_day FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollments (
    id CHAR(36) PRIMARY KEY,
    student_id CHAR(36) NOT NULL,
    class_id CHAR(36) NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at DATETIME NULL,
    active_student_id CHAR(36) GENERATED ALWAYS AS (CASE WHEN left_at IS NULL THEN student_id ELSE NULL END) STORED,
    UNIQUE KEY uq_one_active_class_per_student (active_student_id),
    KEY idx_enrollment_history (student_id, joined_at),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_sessions (
    id CHAR(36) PRIMARY KEY,
    class_id CHAR(36) NOT NULL,
    attendance_date DATE NOT NULL,
    teacher_id CHAR(36) NOT NULL,
    finalized_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_class_attendance_date (class_id, attendance_date),
    CONSTRAINT fk_attendance_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_items (
    session_id CHAR(36) NOT NULL,
    student_id CHAR(36) NOT NULL,
    status ENUM('PRESENT','LATE','ABSENT') NOT NULL,
    delay_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, student_id),
    CONSTRAINT chk_delay_minutes CHECK (
        (status = 'LATE' AND delay_minutes > 0) OR (status <> 'LATE' AND delay_minutes = 0)
    ),
    CONSTRAINT fk_attendance_item_session FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_item_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE announcements (
    id CHAR(36) PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    sender_id CHAR(36) NOT NULL,
    audience_type ENUM('ALL_STUDENTS','ALL_TEACHERS','CLASS','STUDENT') NOT NULL,
    target_class_id CHAR(36) NULL,
    target_student_id CHAR(36) NULL,
    message_type ENUM('TEXT_ONLY','FILE_UPLOAD','ASSIGNMENT') NOT NULL DEFAULT 'TEXT_ONLY',
    attachment_name VARCHAR(255) NULL,
    attachment_path VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_announcement_class (target_class_id, created_at),
    KEY idx_announcement_student (target_student_id, created_at),
    CONSTRAINT fk_announcement_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_class FOREIGN KEY (target_class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_student FOREIGN KEY (target_student_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_templates (
    id CHAR(36) PRIMARY KEY,
    class_id CHAR(36) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_template_admin FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_criteria (
    id CHAR(36) PRIMARY KEY,
    template_id CHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    max_score SMALLINT UNSIGNED NOT NULL,
    sort_order SMALLINT UNSIGNED NOT NULL,
    CONSTRAINT chk_max_score CHECK (max_score > 0),
    CONSTRAINT fk_criteria_template FOREIGN KEY (template_id) REFERENCES report_templates(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_cards (
    id CHAR(36) PRIMARY KEY,
    template_id CHAR(36) NOT NULL,
    student_id CHAR(36) NOT NULL,
    published_by CHAR(36) NOT NULL,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_report_student_template (template_id, student_id),
    CONSTRAINT fk_report_template FOREIGN KEY (template_id) REFERENCES report_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_admin FOREIGN KEY (published_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_scores (
    report_card_id CHAR(36) NOT NULL,
    criterion_id CHAR(36) NOT NULL,
    score DECIMAL(6,2) NOT NULL,
    PRIMARY KEY (report_card_id, criterion_id),
    CONSTRAINT chk_score_nonnegative CHECK (score >= 0),
    CONSTRAINT fk_score_report FOREIGN KEY (report_card_id) REFERENCES report_cards(id) ON DELETE RESTRICT,
    CONSTRAINT fk_score_criterion FOREIGN KEY (criterion_id) REFERENCES report_criteria(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
CREATE TRIGGER attendance_sessions_no_update BEFORE UPDATE ON attendance_sessions
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Finalized attendance cannot be updated'; END$$
CREATE TRIGGER attendance_sessions_no_delete BEFORE DELETE ON attendance_sessions
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Finalized attendance cannot be deleted'; END$$
CREATE TRIGGER attendance_items_no_update BEFORE UPDATE ON attendance_items
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Finalized attendance cannot be updated'; END$$
CREATE TRIGGER attendance_items_no_delete BEFORE DELETE ON attendance_items
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Finalized attendance cannot be deleted'; END$$
DELIMITER ;

