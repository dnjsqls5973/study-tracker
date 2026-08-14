package com.wonbin.study_tracker.domain.classification.repository;

import com.wonbin.study_tracker.domain.classification.entity.AppClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppClassificationRepository extends JpaRepository<AppClassification, Long> {

    List<AppClassification> findByUserId(Long userId);

    Optional<AppClassification> findByUserIdAndTypeAndValue(Long userId, String type, String value);

    boolean existsByUserIdAndTypeAndValue(Long userId, String type, String value);

    @Modifying
    @Query("DELETE FROM AppClassification a WHERE a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
