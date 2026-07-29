package com.wonbin.study_tracker.domain.device.repository;

import com.wonbin.study_tracker.domain.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceToken(String deviceToken);
    boolean existsByDeviceToken(String deviceToken);
}
