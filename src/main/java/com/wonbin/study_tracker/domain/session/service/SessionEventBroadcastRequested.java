package com.wonbin.study_tracker.domain.session.service;

public record SessionEventBroadcastRequested(Long userId, SessionEventType eventType, Long sessionId) {
}
