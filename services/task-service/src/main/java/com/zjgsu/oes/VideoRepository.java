package com.zjgsu.oes;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<VideoJob, String> {
    record Info(String taskId, String fileName, String sourceUrl) {}

    @Query("select new com.zjgsu.oes.VideoRepository$Info(v.taskId, v.fileName, v.sourceUrl) from VideoJob v")
    List<Info> summaries();

    @Query("""
        select v.taskId from VideoJob v, Task t
        where v.taskId = t.id and t.status = 'PENDING' and v.claimedAt is null
        and (:supportsLinks = true or v.sourceUrl is null)
        order by v.queuedAt
        """)
    List<String> pending(@Param("supportsLinks") boolean supportsLinks, Pageable limit);

    @Query("""
        select v.taskId from VideoJob v, Task t
        where v.taskId = t.id and t.status = 'RUNNING' and v.claimedAt < :deadline
        and not exists (select n from NoteJob n where n.taskId = v.taskId)
        """)
    List<String> expired(@Param("deadline") long deadline);
}
