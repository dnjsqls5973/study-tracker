package com.wonbin.study_tracker.domain.state.service;

import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.state.dto.StatsResponse;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StudySessionRepository sessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final UserRepository userRepository;

    // 하루 기준 시작/종료 시각 계산(day_change_hour 적용)
    public LocalDateTime[] getDayRange(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int changeHour = user.getDayChangeHour();
        LocalDateTime start = date.atTime(LocalTime.of(changeHour, 0));
        LocalDateTime end = date.plusDays(1).atTime(LocalTime.of(changeHour, 0));
        return new LocalDateTime[]{start, end};
    }

    // 오늘 요약
    @Transactional(readOnly = true)
    public StatsResponse.TodaySummary getTodaySummary(Long userId) {
        LocalDateTime[] range = getDayRange(userId, LocalDate.now());

        // 세션 목록
        List<StudySession> sessions = sessionRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                userId, range[0], range[1]);

        int totalStudySec = sessions.stream()
                .mapToInt(StudySession::getStudySec).sum();
        int totalDistractSec = sessions.stream()
                .mapToInt(StudySession::getDistractSec).sum();

        //딴짓 앱 Top 5
        List<Object[]> distractApps = activityLogRepository.findTopDistractApps(
                userId, range[0], range[1]);
        List<Object[]> distractBrowser = browserLogRepository.findTopDistractDomains(
                userId, range[0], range[1]);

        List<StatsResponse.DistractItem> topDistracts = new ArrayList<>();

        for (Object[] row : distractApps) {
            topDistracts.add(StatsResponse.DistractItem.builder()
                    .name((String) row[0])
                    .totalSec(((Number) row[1]).intValue())
                    .build());
        }

        for (Object[] row : distractBrowser) {
            topDistracts.add(StatsResponse.DistractItem.builder()
                    .name((String) row[0])
                    .totalSec(((Number) row[1]).intValue())
                    .build());
        }

        // 총 딴짓 시간 기준 정렬
        topDistracts.sort((a, b) -> b.getTotalSec() - a.getTotalSec());

        return StatsResponse.TodaySummary.builder()
                .totalStudySec(totalStudySec)
                .totalDistractSec(totalDistractSec)
                .sessionCount(sessions.size())
                .topDistracts(topDistracts.stream().limit(5).collect(Collectors.toList()))
                .build();
    }

    // 특정 날 세션 목록
    @Transactional(readOnly = true)
    public List<StatsResponse.SessionSummary> getSessions(Long userId, LocalDate date) {
        LocalDateTime[] range = getDayRange(userId, date);

        return sessionRepository
                .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                        userId, range[0], range[1])
                .stream()
                .map(s -> StatsResponse.SessionSummary.builder()
                        .sessionId(s.getId())
                        .studyType(s.getStudyType())
                        .startedAt(s.getStartedAt())
                        .endedAt(s.getEndedAt())
                        .studySec(s.getStudySec())
                        .distractSec(s.getDistractSec())
                        .totalSec(s.getTotalSec())
                        .build())
                .collect(Collectors.toList());
    }

    // 주간 통계
    public List<StatsResponse.DailyStat> getWeeklyStats(Long userId, LocalDate startDate) {
        List<StatsResponse.DailyStat> result = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            LocalDateTime[] range = getDayRange(userId, date);

            List<StudySession> sessions = sessionRepository
                    .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                            userId, range[0], range[1]);

            int studySec = sessions.stream()
                    .mapToInt(StudySession::getStudySec).sum();
            int distractSec = sessions.stream()
                    .mapToInt(StudySession::getDistractSec).sum();

            result.add(StatsResponse.DailyStat.builder()
                    .date(date)
                    .totalStudySec(studySec)
                    .totalDistractSec(distractSec)
                    .sessionCount(sessions.size())
                    .build());
        }

        return result;
    }

    // 월간 통계
    @Transactional(readOnly = true)
    public List<StatsResponse.DailyStat> getMonthlyStats(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<StatsResponse.DailyStat> result = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime[] range = getDayRange(userId, date);

            List<StudySession> sessions = sessionRepository
                    .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                            userId, range[0], range[1]);

            int studySec = sessions.stream()
                    .mapToInt(StudySession::getStudySec).sum();
            int distractSec = sessions.stream()
                    .mapToInt(StudySession::getDistractSec).sum();

            result.add(StatsResponse.DailyStat.builder()
                    .date(date)
                    .totalStudySec(studySec)
                    .totalDistractSec(distractSec)
                    .sessionCount(sessions.size())
                    .build());
        }
        return result;
    }

}
