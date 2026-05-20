package com.assessment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scaffolding_log")
public class ScaffoldingLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_question_id", nullable = false)
    private Question originalQuestion;

    @Column(name = "generated_question", nullable = false, columnDefinition = "TEXT")
    private String generatedQuestion;

    @Column(name = "student_answer", length = 500)
    private String studentAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "attempt_number")
    private Integer attemptNumber = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "diagnostic_step")
    private Integer diagnosticStep;

    @Column(name = "prerequisite_tested", columnDefinition = "TEXT")
    private String prerequisiteTested;

    @Column(name = "failure_meaning", columnDefinition = "TEXT")
    private String failureMeaning;

    public Integer getDiagnosticStep() {
        return diagnosticStep;
    }

    public void setDiagnosticStep(Integer diagnosticStep) {
        this.diagnosticStep = diagnosticStep;
    }

    public String getPrerequisiteTested() {
        return prerequisiteTested;
    }

    public void setPrerequisiteTested(String prerequisiteTested) {
        this.prerequisiteTested = prerequisiteTested;
    }

    public String getFailureMeaning() {
        return failureMeaning;
    }

    public void setFailureMeaning(String failureMeaning) {
        this.failureMeaning = failureMeaning;
    }

    public ScaffoldingLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AssessmentSession getSession() { return session; }
    public void setSession(AssessmentSession session) { this.session = session; }
    public Question getOriginalQuestion() { return originalQuestion; }
    public void setOriginalQuestion(Question originalQuestion) { this.originalQuestion = originalQuestion; }
    public String getGeneratedQuestion() { return generatedQuestion; }
    public void setGeneratedQuestion(String generatedQuestion) { this.generatedQuestion = generatedQuestion; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
