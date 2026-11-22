package com.zjgsu.oes;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VideoCollectionRepository extends JpaRepository<VideoCollection, String> {
    boolean existsByActiveCourseId(String courseId);
    List<VideoCollection> findAllByOrderByCreatedAtDesc();

    @Query("select c.id from VideoCollection c where c.activeCourseId is not null")
    List<String> activeIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VideoCollection c where c.id = :id")
    Optional<VideoCollection> findLocked(@Param("id") String id);
}
