package com.wonbin.study_tracker.domain.classification.entity;

import com.wonbin.study_tracker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_classifications")
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AppClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false, length = 10)
    private String type; // DOMAIN or APP

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "category", nullable = false, length = 10)
    private String category; // STUDY or DISTRACT or NEUTRAL

    public void updateCategory(String category) {
        this.category = category;
    }

}
