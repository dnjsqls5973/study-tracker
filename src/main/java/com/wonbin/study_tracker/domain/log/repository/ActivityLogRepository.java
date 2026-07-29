package com.wonbin.study_tracker.domain.log.repository;

import com.wonbin.study_tracker.domain.log.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findBySessionId(Long sessionId);

    // 특정 기간 딴짓 앱 Top 조회

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

}
