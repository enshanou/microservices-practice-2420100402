package com.zjgsu.oes.repository;

import com.zjgsu.oes.model.Course;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findAllByOrderByCreatedAtAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findForUpdate(@Param("id") UUID id);
}
