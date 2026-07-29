package com.wonbin.study_tracker.domain.classification.service;


import com.wonbin.study_tracker.domain.classification.entity.AppClassification;
import com.wonbin.study_tracker.domain.classification.repository.AppClassificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wonbin.study_tracker.domain.classification.dto.ClassificationRequest;
import com.wonbin.study_tracker.domain.classification.dto.ClassificationResponse;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClassificationService {

    private final AppClassificationRepository classificationRepository;
    private final UserRepository userRepository;


    // <Custom> 분류 규칙 추가
    @Transactional
    public ClassificationResponse create(Long userId, ClassificationRequest.Create request) {
        if (classificationRepository.existsByUserIdAndTypeAndValue(
                userId, request.getType(), request.getValue())) {
            throw new IllegalStateException("이미 등록된 분류 규칙입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        AppClassification classification = AppClassification.builder()
                .user(user)
                .type(request.getType())
                .value(request.getValue())
                .category(request.getCategory())
                .build();

        classificationRepository.save(classification);
        return ClassificationResponse.from(classification);
    }

    // <Custom> 분류 규칙 수정
    @Transactional
    public ClassificationResponse update(Long userId, Long id, ClassificationRequest.Update request) {
        AppClassification classification = classificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분류 규칙을 찾을 수 없습니다."));

        if (!classification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        classification.updateCategory(request.getCategory());
        return ClassificationResponse.from(classification);
    }

    // <Custom> 분류 규칙 삭제
    @Transactional
    public void delete(Long userId, Long id) {
        AppClassification classification = classificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분류 규칙을 찾을 수 없습니다."));

        if (!classification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        classificationRepository.delete(classification);
    }

    // <Custom> 목록 조회 (응답 DTO로 변환)
    @Transactional(readOnly = true)
    public java.util.List<ClassificationResponse> getList(Long userId) {
        return getUserClassifications(userId).stream()
                .map(ClassificationResponse::from)
                .collect(Collectors.toList());
    }

    // 도메인 목록은 추후 수정이 필요해보임(06.17)

    // 기본 STUDY 도메인 목록
    private static final Set<String> DEFAULT_STUDY_DOMAINS = Set.of(
            "github.com", "stackoverflow.com", "inflearn.com",
            "velog.io", "notion.so", "medium.com", "docs.spring.io",
            "developer.mozilla.org", "w3schools.com", "leetcode.com",
            "programmers.co.kr", "baekjoon.net", "acmicpc.net"
    );

    // 기본 DISTRACT 도메인 목록
    private static final Set<String> DEFAULT_DISTRACT_DOMAINS = Set.of(
            "instagram.com", "youtube.com", "tiktok.com",
            "netflix.com", "twitter.com", "x.com", "facebook.com",
            "twitch.tv", "naver.com", "daum.net"
    );

    // 기본 NEUTRAL 앱 목록
    private static final Set<String> DEFAULT_NEUTRAL_APPS = Set.of(
            "Spotify.exe", "YouTubeMusic.exe",
            "Clock.exe", "Calculator.exe"
    );

    // 기본 STUDY 앱 목록
    private static final Set<String> DEFAULT_STUDY_APPS = Set.of(
            "Code.exe", "idea64.exe", "devenv.exe",
            "Notion.exe", "Obsidian.exe", "pycharm64.exe",
            "WebStorm64.exe", "DataGrip64.exe"
    );

    // 기본 DISTRACT 앱 목록
    private static final Set<String> DEFAULT_DISTRACT_APPS = Set.of(
            "KakaoTalk.exe", "Discord.exe", "Steam.exe"
    );

    // 도메인 분류 (브라우저 로그용)
    public String classifyDomain(Long userId, String domain) {
        // 1. 사용자 커스텀 규칙 우선 적용
        Optional<AppClassification> custom = classificationRepository.findByUserIdAndTypeAndValue(userId, "DOMAIN", domain);
        if (custom.isPresent()) {
            return custom.get().getCategory();
        }

        // 2. 기본 규칙 적용
        if(DEFAULT_STUDY_DOMAINS.contains(domain)) return "STUDY";
        if(DEFAULT_DISTRACT_DOMAINS.contains(domain)) return "DISTRACT";

        return "NEUTRAL";
    }

    // 앱 분류(activity 로그용)
    public String classifyApp (Long userId, String appName, boolean isIdle, String studyType) {

        // 유후 상태 처리
        if(isIdle) {
            //오프라인 공부면 유후 = 공부
            if("OFFLINE".equals(studyType)) return "STUDY";
            return "IDLE";
        }

        // 1. 사용자 커스텀 규칙 우선 적용
        Optional<AppClassification> custom = classificationRepository.findByUserIdAndTypeAndValue(userId, "APP", appName);
        if (custom.isPresent()) {
            return custom.get().getCategory();
        }

        // 2. 기본 규칙 적용
        if (DEFAULT_STUDY_APPS.contains(appName)) return "STUDY";
        if (DEFAULT_DISTRACT_APPS.contains(appName)) return "DISTRACT";
        if (DEFAULT_NEUTRAL_APPS.contains(appName)) return "NEUTRAL";

        return "NEUTRAL";
    }

    // 사용자 분류 규칙 목록 조회
    public List<AppClassification> getUserClassifications(Long userId) {
        return classificationRepository.findByUserId(userId);
    }
}
