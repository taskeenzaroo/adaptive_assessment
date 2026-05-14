package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_mastery",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "topic", "skill_tag"}))
public class SkillMastery {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_tag", nullable = false)
    private Question.SkillTag skillTag;

    @Enumerated(EnumType.STRING)
    private MasteryStatus status = MasteryStatus.NOT_ASSESSED;

    private Double accuracy = 0.0;

    @Column(name = "total_attempts")
    private Integer totalAttempts = 0;

    @Column(name = "correct_attempts")
    private Integer correctAttempts = 0;

    @Column(name = "scaffolding_count")
    private Integer scaffoldingCount = 0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();

    public enum MasteryStatus { NOT_ASSESSED, STRONG, DEVELOPING, NEEDS_SUPPORT }

    public SkillMastery() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Question.SkillTag getSkillTag() { return skillTag; }
    public void setSkillTag(Question.SkillTag skillTag) { this.skillTag = skillTag; }
    public MasteryStatus getStatus() { return status; }
    public void setStatus(MasteryStatus status) { this.status = status; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
    public Integer getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(Integer totalAttempts) { this.totalAttempts = totalAttempts; }
    public Integer getCorrectAttempts() { return correctAttempts; }
    public void setCorrectAttempts(Integer correctAttempts) { this.correctAttempts = correctAttempts; }
    public Integer getScaffoldingCount() { return scaffoldingCount; }
    public void setScaffoldingCount(Integer scaffoldingCount) { this.scaffoldingCount = scaffoldingCount; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
