package com.wonbin.study_tracker.domain.device.repository;

import com.wonbin.study_tracker.domain.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceToken(String deviceToken);
    boolean existsByDeviceToken(String deviceToken);
    List<Device> findByUserIdAndPushTokenIsNotNull(Long userId);

    @Modifying
    @Query("DELETE FROM Device d WHERE d.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
