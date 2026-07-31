package com.wonbin.study_tracker.domain.session.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionEventBroadcastListenerTest {

    @Mock
    private SessionEventBroadcaster sessionEventBroadcaster;

    @InjectMocks
    private SessionEventBroadcastListener listener;

    @Test
    void 이벤트를_받으면_브로드캐스터에_위임한다() {
        SessionEventBroadcastRequested event = new SessionEventBroadcastRequested(1L, SessionEventType.STARTED, 200L);

        listener.onSessionEventBroadcastRequested(event);

        verify(sessionEventBroadcaster).broadcast(1L, SessionEventType.STARTED, 200L);
    }
}
