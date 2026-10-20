USE courselens;
INSERT INTO courses (id, owner_id, title, source_reference, created_at)
VALUES ('11111111-1111-4111-8111-111111111111', 'oes', 'Microservices demo', 'imports/demo.srt', '2026-10-01 00:00:00')
ON DUPLICATE KEY UPDATE id = id;
INSERT INTO processing_tasks (id, course_id, status, active_course_id, failure_reason, created_at, updated_at)
VALUES ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'SUCCEEDED', NULL, NULL, '2026-10-01 00:00:00', '2026-10-01 00:01:00')
ON DUPLICATE KEY UPDATE id = id;
