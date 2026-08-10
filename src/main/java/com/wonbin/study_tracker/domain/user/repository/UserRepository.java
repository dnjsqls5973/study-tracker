package com.wonbin.study_tracker.domain.user.repository;

import com.wonbin.study_tracker.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByGoogleId(String googleId);

    @Modifying
    @Query(value = "DELETE FROM time_blocks WHERE user_id = :userId", nativeQuery = true)
    void deleteTimeBlocksByUserId(@Param("userId") Long userId);
}
