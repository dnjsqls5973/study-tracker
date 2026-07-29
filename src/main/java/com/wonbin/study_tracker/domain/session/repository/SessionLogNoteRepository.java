package com.wonbin.study_tracker.domain.session.repository;

import com.wonbin.study_tracker.domain.session.entity.SessionLogNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionLogNoteRepository extends JpaRepository<SessionLogNote, Long> {
    List<SessionLogNote> findBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
