package com.wonbin.study_tracker.domain.session.service;

public interface SessionEventBroadcaster {
    void broadcast(Long userId, SessionEventType eventType, Long sessionId);
}
