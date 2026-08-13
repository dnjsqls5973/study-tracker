package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.domain.classification.repository.AppClassificationRepository;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final SessionLogNoteRepository sessionLogNoteRepository;
    private final StudySessionRepository studySessionRepository;
    private final DeviceRepository deviceRepository;
    private final AppClassificationRepository appClassificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void deleteAccount(Long userId) {
        activityLogRepository.deleteBySessionUserId(userId);
        browserLogRepository.deleteBySessionUserId(userId);
        sessionLogNoteRepository.deleteBySessionUserId(userId);
        studySessionRepository.deleteByUserId(userId);
        deviceRepository.deleteByUserId(userId);
        appClassificationRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }
}
