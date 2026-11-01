CREATE TABLE IF NOT EXISTS processing_tasks (
 id VARCHAR(36) PRIMARY KEY, course_id VARCHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL, active_course_id VARCHAR(36) UNIQUE,
 failure_reason VARCHAR(1000),
 CONSTRAINT ck_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT ck_active CHECK ((status IN ('PENDING','RUNNING') AND active_course_id IS NOT NULL AND active_course_id=course_id) OR (status IN ('SUCCEEDED','FAILED') AND active_course_id IS NULL)),
 CONSTRAINT ck_failure CHECK ((status='FAILED' AND failure_reason IS NOT NULL) OR (status<>'FAILED' AND failure_reason IS NULL))
);
