package com.zjgsu.oes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TranscriptRepository extends JpaRepository<CourseTranscript, String> {
    record FileInfo(String courseId, String fileName) {}

    @Query("select new com.zjgsu.oes.TranscriptRepository$FileInfo(t.courseId, t.fileName) from CourseTranscript t where not exists (select a from CourseArchive a where a.id = t.courseId)")
    List<FileInfo> files();
}
