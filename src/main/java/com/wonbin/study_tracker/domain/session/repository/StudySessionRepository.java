package com.wonbin.study_tracker.domain.session.repository;

import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    Optional<StudySession> findByUserIdAndEndedAtIsNull(Long userId);

    Optional<StudySession> findFirstByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(Long userId);

    List<StudySession> findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(s.studySec), 0) FROM StudySession s " +
            "WHERE s.user.id = :userId " +
            "AND s.startedAt BETWEEN :start AND :end " +
            "AND s.endedAt IS NOT NULL")
    int sumStudySecByUserIdAndPeriod(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Long user(User user);

    void deleteByUserId(Long userId);
}
