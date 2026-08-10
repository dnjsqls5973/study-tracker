package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.domain.classification.repository.AppClassificationRepository;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private BrowserLogRepository browserLogRepository;
    @Mock private SessionLogNoteRepository sessionLogNoteRepository;
    @Mock private StudySessionRepository studySessionRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AppClassificationRepository appClassificationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserDeletionService userDeletionService;

    @Test
    void 계정_삭제_시_자식_데이터부터_부모_순서로_모두_삭제한다() {
        userDeletionService.deleteAccount(1L);

        InOrder order = inOrder(
                activityLogRepository, browserLogRepository, sessionLogNoteRepository,
                studySessionRepository, deviceRepository, appClassificationRepository, userRepository
        );
        order.verify(activityLogRepository).deleteBySessionUserId(1L);
        order.verify(browserLogRepository).deleteBySessionUserId(1L);
        order.verify(sessionLogNoteRepository).deleteBySessionUserId(1L);
        order.verify(studySessionRepository).deleteByUserId(1L);
        order.verify(deviceRepository).deleteByUserId(1L);
        order.verify(appClassificationRepository).deleteByUserId(1L);
        order.verify(userRepository).deleteById(1L);
    }
}
