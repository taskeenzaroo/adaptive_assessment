package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// ── ThetaEstimate ──────────────────────────────────────────────────────────

@Entity
@Table(name = "theta_estimates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "topic"}))
public class ThetaEstimate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "current_theta")
    private Double currentTheta = 3.0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    public ThetaEstimate() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Double getCurrentTheta() { return currentTheta; }
    public void setCurrentTheta(Double currentTheta) { this.currentTheta = currentTheta; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
