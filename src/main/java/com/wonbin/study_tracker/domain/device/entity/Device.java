package com.wonbin.study_tracker.domain.device.entity;

import com.wonbin.study_tracker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 10)
    private String deviceType;

    @Column(name = "device_token", nullable = false, unique = true, length = 255)
    private String deviceToken;

    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }

    public void updatePushToken(String pushToken) {
        this.pushToken = pushToken;
    }
}
