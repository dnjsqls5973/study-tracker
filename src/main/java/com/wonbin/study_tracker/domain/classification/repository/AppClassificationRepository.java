package com.wonbin.study_tracker.domain.classification.repository;

import com.wonbin.study_tracker.domain.classification.entity.AppClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppClassificationRepository extends JpaRepository<AppClassification, Long> {

    // 사용자의 전체 분류 규칙 조회
    List<AppClassification> findByUserId(Long userId);

    // 특정 도메인/앱 분류 규칙 조회
    Optional<AppClassification> findByUserIdAndTypeAndValue(Long userId, String type, String value);

    // 중복 확인
    boolean existsByUserIdAndTypeAndValue(Long userId, String type, String value);

    @Modifying
    @Query("DELETE FROM AppClassification a WHERE a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
