package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.classification.service.ClassificationService;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.dto.SessionRequest;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceBroadcastTest {

    @Mock private StudySessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private BrowserLogRepository browserLogRepository;
    @Mock private SessionLogNoteRepository sessionLogNoteRepository;
    @Mock private ClassificationService classificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void 세션_시작_시_STARTED_브로드캐스트_이벤트를_발행한다() {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();

        when(sessionRepository.findByUserIdAndEndedAtIsNull(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession s = invocation.getArgument(0);
            setId(s, 200L);
            return s;
        });

        SessionRequest.Start request = new SessionRequest.Start();
        setField(request, "studyType", "ONLINE");
        setField(request, "targetSec", 3600);

        sessionService.start(1L, request);

        verify(eventPublisher).publishEvent(new SessionEventBroadcastRequested(1L, SessionEventType.STARTED, 200L));
    }

    @Test
    void 세션_종료_시_ENDED_브로드캐스트_이벤트를_발행한다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        StudySession session = StudySession.builder()
                .user(user)
                .studyType("ONLINE")
                .startedAt(LocalDateTime.now().minusHours(1))
                .isAutoEnded(false)
                .totalSec(0).studySec(0).distractSec(0).pauseSec(0)
                .build();
        setId(session, 200L);

        when(sessionRepository.findById(200L)).thenReturn(Optional.of(session));

        sessionService.end(1L, 200L, false);

        verify(eventPublisher).publishEvent(new SessionEventBroadcastRequested(1L, SessionEventType.ENDED, 200L));
    }

    private void setId(StudySession session, Long id) throws Exception {
        setField(session, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
