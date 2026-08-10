package com.wonbin.study_tracker.domain.log.repository;

import com.wonbin.study_tracker.domain.log.entity.ActivityLog;
import com.wonbin.study_tracker.domain.log.entity.BrowserLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BrowserLogRepository extends JpaRepository<BrowserLog, Long> {

    List<BrowserLog> findBySessionId(Long sessionId);

    @Modifying
    @Query("DELETE FROM BrowserLog b WHERE b.session.id IN (SELECT s.id FROM StudySession s WHERE s.user.id = :userId)")
    void deleteBySessionUserId(@Param("userId") Long userId);

    // 특정 기간 딴짓 앱 Top 조회

    @Query("SELECT b.domain, SUM(b.durationSec) As total " +
            "FROM BrowserLog b " +
            "WHERE b.session.user.id = :userId " +
            "AND b.startedAt BETWEEN :start AND :end " +
            "AND b.category = 'DISTRACT' " +
            "GROUP BY b.domain " +
            "ORDER BY total DESC")
    List<Object[]> findTopDistractDomains(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // 특정 기간 카테고리별 도메인 시간 집계 (STUDY/DISTRACT 공용)
    @Query("SELECT b.domain, SUM(b.durationSec) As total " +
            "FROM BrowserLog b " +
            "WHERE b.session.user.id = :userId " +
            "AND b.startedAt BETWEEN :start AND :end " +
            "AND b.category = :category " +
            "GROUP BY b.domain " +
            "ORDER BY total DESC")
    List<Object[]> findTopDomainsByCategory(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("category") String category);

}
