package com.zjgsu.oes;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<NoteJob, String> {
    record Info(String taskId, String fileName, long queuedAt, boolean ready) {}

    @Query("""
        select new com.zjgsu.oes.NoteRepository$Info(n.taskId, n.fileName, n.queuedAt,
            case when n.resultJson is null and n.longResultJson is null then false else true end)
        from NoteJob n order by n.queuedAt desc
        """)
    List<Info> summaries();

    @Query("select n.styleSignal from NoteJob n where n.styleOwner = :owner and n.styleSignal is not null order by n.styleSavedAt desc")
    List<String> styleSamples(@Param("owner") String owner, Pageable limit);

    @Query("""
        select n.taskId from NoteJob n, Task t
        where n.taskId = t.id and t.status = 'PENDING'
        order by n.queuedAt
        """)
    List<String> pending(Pageable page);

    @Query("""
        select n.taskId from NoteJob n, Task t
        where n.taskId = t.id and t.status = 'RUNNING'
            and coalesce(n.progressAt, n.startedAt) < :deadline
        """)
    List<String> expired(@Param("deadline") long deadline);
}
