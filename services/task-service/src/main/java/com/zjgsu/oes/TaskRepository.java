package com.zjgsu.oes;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskRepository extends JpaRepository<Task, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Task t where t.id = :id")
    java.util.Optional<Task> findLocked(@org.springframework.data.repository.query.Param("id") String id);
 }
