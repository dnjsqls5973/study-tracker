package com.wonbin.study_tracker.domain.log.repository;

import com.wonbin.study_tracker.domain.log.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findBySessionId(Long sessionId);

    @Modifying
    @Query("DELETE FROM ActivityLog a WHERE a.session.id IN (SELECT s.id FROM StudySession s WHERE s.user.id = :userId)")
    void deleteBySessionUserId(@Param("userId") Long userId);

    @Query("SELECT a.appName, SUM(a.durationSec) As total " +
            "FROM ActivityLog a " +
            "WHERE a.session.user.id = :userId " +
            "AND a.startedAt BETWEEN :start AND :end " +
            "AND a.category = 'DISTRACT' " +
            "GROUP BY a.appName " +
            "ORDER BY total DESC")
    List<Object[]> findTopDistractApps(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT a.appName, SUM(a.durationSec) As total " +
            "FROM ActivityLog a " +
            "WHERE a.session.user.id = :userId " +
            "AND a.startedAt BETWEEN :start AND :end " +
            "AND a.category = :category " +
            "GROUP BY a.appName " +
            "ORDER BY total DESC")
    List<Object[]> findTopAppsByCategory(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("category") String category);

}
