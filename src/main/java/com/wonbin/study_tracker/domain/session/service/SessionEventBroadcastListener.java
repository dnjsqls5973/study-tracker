package com.wonbin.study_tracker.domain.session.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SessionEventBroadcastListener {

    private final SessionEventBroadcaster sessionEventBroadcaster;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionEventBroadcastRequested(SessionEventBroadcastRequested event) {
        sessionEventBroadcaster.broadcast(event.userId(), event.eventType(), event.sessionId());
    }
}
