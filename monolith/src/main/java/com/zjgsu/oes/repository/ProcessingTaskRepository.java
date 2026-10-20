package com.zjgsu.oes.repository;

import com.zjgsu.oes.model.ProcessingTask;
import com.zjgsu.oes.model.TaskStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingTaskRepository extends JpaRepository<ProcessingTask, UUID> {
    boolean existsByCourseIdAndStatusIn(UUID courseId, Collection<TaskStatus> statuses);
    List<ProcessingTask> findByCourseIdOrderByCreatedAtAscIdAsc(UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ProcessingTask t where t.id = :id")
    Optional<ProcessingTask> findForUpdate(@Param("id") UUID id);
}
