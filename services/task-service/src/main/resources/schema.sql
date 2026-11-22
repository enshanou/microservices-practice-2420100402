CREATE TABLE IF NOT EXISTS processing_tasks (
 id VARCHAR(36) PRIMARY KEY, course_id VARCHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL, active_course_id VARCHAR(36) UNIQUE,
 failure_reason VARCHAR(1000),
 CONSTRAINT ck_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT ck_active CHECK ((status IN ('PENDING','RUNNING') AND active_course_id IS NOT NULL AND active_course_id=course_id) OR (status IN ('SUCCEEDED','FAILED') AND active_course_id IS NULL)),
 CONSTRAINT ck_failure CHECK ((status='FAILED' AND failure_reason IS NOT NULL) OR (status<>'FAILED' AND failure_reason IS NULL))
);
CREATE TABLE IF NOT EXISTS note_jobs (
 task_id VARCHAR(36) PRIMARY KEY,
 file_name VARCHAR(200) NOT NULL,
 title VARCHAR(200) NOT NULL,
 subtitle_text VARCHAR(7000) NOT NULL,
 model VARCHAR(150) NOT NULL,
 queued_at BIGINT NOT NULL,
 started_at BIGINT,
 result_json VARCHAR(8000),
 FOREIGN KEY (task_id) REFERENCES processing_tasks(id)
);
CREATE TABLE IF NOT EXISTS note_job_long_content (
 task_id VARCHAR(36) PRIMARY KEY,
 long_transcript MEDIUMTEXT,
 long_result_json MEDIUMTEXT,
 FOREIGN KEY (task_id) REFERENCES note_jobs(task_id)
);
CREATE TABLE IF NOT EXISTS video_jobs (
 task_id VARCHAR(36) PRIMARY KEY,
 file_name VARCHAR(200) NOT NULL,
 title VARCHAR(200) NOT NULL,
 queued_at BIGINT NOT NULL,
 claimed_at BIGINT,
 lease VARCHAR(36),
 FOREIGN KEY (task_id) REFERENCES processing_tasks(id)
);
CREATE TABLE IF NOT EXISTS note_job_sources (
 task_id VARCHAR(36) PRIMARY KEY,
 source_video_task_id VARCHAR(36),
 FOREIGN KEY (task_id) REFERENCES note_jobs(task_id),
 FOREIGN KEY (source_video_task_id) REFERENCES video_jobs(task_id)
);
CREATE TABLE IF NOT EXISTS video_job_links (
 task_id VARCHAR(36) PRIMARY KEY,
 source_url VARCHAR(500) NOT NULL,
 FOREIGN KEY (task_id) REFERENCES video_jobs(task_id)
);
CREATE TABLE IF NOT EXISTS video_collections (
 id VARCHAR(36) PRIMARY KEY,
 course_id VARCHAR(36) NOT NULL,
 active_course_id VARCHAR(36) UNIQUE,
 title VARCHAR(200) NOT NULL,
 entries_json MEDIUMTEXT NOT NULL,
 next_index INT NOT NULL,
 current_task_id VARCHAR(36),
 stopped BOOLEAN NOT NULL,
 created_at BIGINT NOT NULL,
 FOREIGN KEY (current_task_id) REFERENCES processing_tasks(id)
);

-- Separate extension tables preserve existing course and task volumes on upgrade.
CREATE TABLE IF NOT EXISTS note_job_workflow (
 task_id VARCHAR(36) PRIMARY KEY,
 generation_options TEXT,
 progress_at BIGINT,
 recovery_count INT,
 style_owner VARCHAR(36),
 style_saved_at BIGINT,
 style_signal VARCHAR(120),
 FOREIGN KEY (task_id) REFERENCES note_jobs(task_id)
);
CREATE TABLE IF NOT EXISTS video_job_workflow (
 task_id VARCHAR(36) PRIMARY KEY,
 generation_options TEXT,
 recovery_count INT,
 upload_task_id VARCHAR(36),
 FOREIGN KEY (task_id) REFERENCES video_jobs(task_id)
);
CREATE TABLE IF NOT EXISTS video_collection_workflow (
 id VARCHAR(36) PRIMARY KEY,
 generation_options TEXT,
 FOREIGN KEY (id) REFERENCES video_collections(id)
);
