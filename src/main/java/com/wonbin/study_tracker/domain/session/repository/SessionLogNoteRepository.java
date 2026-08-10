package com.wonbin.study_tracker.domain.session.repository;

import com.wonbin.study_tracker.domain.session.entity.SessionLogNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionLogNoteRepository extends JpaRepository<SessionLogNote, Long> {
    List<SessionLogNote> findBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);

    @Modifying
    @Query("DELETE FROM SessionLogNote n WHERE n.session.id IN (SELECT s.id FROM StudySession s WHERE s.user.id = :userId)")
    void deleteBySessionUserId(@Param("userId") Long userId);
}
