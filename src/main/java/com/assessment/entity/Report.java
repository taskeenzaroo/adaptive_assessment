package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "total_questions")
    private Integer totalQuestions = 0;

    @Column(name = "correct_answers")
    private Integer correctAnswers = 0;

    @Column(name = "scaffolding_events")
    private Integer scaffoldingEvents = 0;

    @Column(name = "summary_json", columnDefinition = "JSON")
    private String summaryJson;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    public Report() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AssessmentSession getSession() { return session; }
    public void setSession(AssessmentSession session) { this.session = session; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
    public Integer getCorrectAnswers() { return correctAnswers; }
    public void setCorrectAnswers(Integer correctAnswers) { this.correctAnswers = correctAnswers; }
    public Integer getScaffoldingEvents() { return scaffoldingEvents; }
    public void setScaffoldingEvents(Integer scaffoldingEvents) { this.scaffoldingEvents = scaffoldingEvents; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
