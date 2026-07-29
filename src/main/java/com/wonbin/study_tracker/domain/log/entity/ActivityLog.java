package com.wonbin.study_tracker.domain.log.entity;

import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "app_name", nullable = false, length = 255)
    private String appName;

    @Column(name = "window_title", length = 500)
    private String windowTitle;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "is_idle", nullable = false)
    private boolean isIdle;

    @Column(name = "category", nullable = false, length = 10)
    private String category;

    public void updateCategory(String category) {
        this.category = category;
    }

}
