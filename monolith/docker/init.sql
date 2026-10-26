CREATE TABLE IF NOT EXISTS courses (
    id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS processing_tasks (
    id VARCHAR(36) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    active_course_id VARCHAR(36) NULL,
    failure_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT uk_tasks_active_course UNIQUE (active_course_id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_tasks_active CHECK (
        (status IN ('PENDING', 'RUNNING') AND active_course_id IS NOT NULL AND active_course_id = course_id)
        OR (status IN ('SUCCEEDED', 'FAILED') AND active_course_id IS NULL)
    ),
    CONSTRAINT ck_tasks_failure CHECK (
        (status = 'FAILED' AND failure_reason IS NOT NULL AND CHAR_LENGTH(TRIM(failure_reason)) > 0)
        OR (status <> 'FAILED' AND failure_reason IS NULL)
    ),
    INDEX idx_tasks_course_created (course_id, created_at)
) ENGINE=InnoDB;
