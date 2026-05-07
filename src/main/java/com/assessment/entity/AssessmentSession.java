package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_sessions")
public class AssessmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "topics_assessed", columnDefinition = "JSON")
    private String topicsAssessed;

    @Column(name = "current_topic", length = 100)
    private String currentTopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_skill_tag")
    private Question.SkillTag currentSkillTag;

    @Column(name = "current_difficulty")
    private Integer currentDifficulty = 3;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public enum SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }

    public AssessmentSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public String getTopicsAssessed() { return topicsAssessed; }
    public void setTopicsAssessed(String topicsAssessed) { this.topicsAssessed = topicsAssessed; }
    public String getCurrentTopic() { return currentTopic; }
    public void setCurrentTopic(String currentTopic) { this.currentTopic = currentTopic; }
    public Question.SkillTag getCurrentSkillTag() { return currentSkillTag; }
    public void setCurrentSkillTag(Question.SkillTag currentSkillTag) { this.currentSkillTag = currentSkillTag; }
    public Integer getCurrentDifficulty() { return currentDifficulty; }
    public void setCurrentDifficulty(Integer currentDifficulty) { this.currentDifficulty = currentDifficulty; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
