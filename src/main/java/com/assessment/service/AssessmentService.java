package com.assessment.service;

import com.assessment.engine.AdaptiveEngine;
import com.assessment.engine.SessionState;
import com.assessment.entity.*;
import com.assessment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssessmentService {

    private final AdaptiveEngine adaptiveEngine;
    private final AssessmentSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ThetaEstimateRepository thetaRepository;
    private final SkillMasteryRepository skillMasteryRepository;
    private final SessionAnswerRepository answerRepository;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssessmentService(AdaptiveEngine adaptiveEngine,
                             AssessmentSessionRepository sessionRepository,
                             UserRepository userRepository,
                             ThetaEstimateRepository thetaRepository,
                             SkillMasteryRepository skillMasteryRepository,
                             SessionAnswerRepository answerRepository,
                             ReportRepository reportRepository) {
        this.adaptiveEngine = adaptiveEngine;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.thetaRepository = thetaRepository;
        this.skillMasteryRepository = skillMasteryRepository;
        this.answerRepository = answerRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional
    public Map<String, Object> startAssessment(Long studentId, List<String> topics) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        sessionRepository.findByStudentIdAndStatus(studentId, AssessmentSession.SessionStatus.IN_PROGRESS)
            .ifPresent(s -> {
                s.setStatus(AssessmentSession.SessionStatus.ABANDONED);
                s.setEndedAt(LocalDateTime.now());
                sessionRepository.save(s);
                adaptiveEngine.removeSession(s.getId());
            });

        AssessmentSession session = new AssessmentSession();
        session.setStudent(student);
        session.setCurrentTopic(topics.get(0));
        session.setCurrentSkillTag(Question.SkillTag.concept);
        session.setCurrentDifficulty(3);
        try { session.setTopicsAssessed(objectMapper.writeValueAsString(topics)); }
        catch (Exception e) { session.setTopicsAssessed("[]"); }
        session = sessionRepository.save(session);

        SessionState state = adaptiveEngine.startSession(session.getId(), studentId, topics);
        Map<String, Object> nextQ = adaptiveEngine.getNextQuestion(session.getId());
        nextQ.put("sessionId", session.getId());
        return nextQ;
    }

    @Transactional
    public Map<String, Object> submitAnswer(Long sessionId, Long questionId,
                                            String answer, Integer timeTaken) {
        AssessmentSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        Map<String, Object> result = adaptiveEngine.processAnswer(sessionId, questionId, answer, timeTaken, session);

        if (Boolean.TRUE.equals(result.get("sessionComplete"))) {
            return completeSession(sessionId, session, result);
        }

        if (Boolean.TRUE.equals(result.get("scaffolding"))) {
            result.put("sessionId", sessionId);
            return result;
        }

        Map<String, Object> nextQ = adaptiveEngine.getNextQuestion(sessionId);
        if (Boolean.TRUE.equals(nextQ.get("noQuestion"))) {
            return completeSession(sessionId, session, result);
        }

        result.putAll(nextQ);
        result.put("sessionId", sessionId);
        return result;
    }

    @Transactional
    public Map<String, Object> completeSession(Long sessionId, AssessmentSession session,
                                               Map<String, Object> existing) {
        SessionState state = adaptiveEngine.getState(sessionId);

        for (Map.Entry<String, Double> entry : state.getThetaPerTopic().entrySet()) {
            ThetaEstimate te = thetaRepository
                .findByStudentIdAndTopic(session.getStudent().getId(), entry.getKey())
                .orElse(new ThetaEstimate());
            te.setStudent(session.getStudent());
            te.setTopic(entry.getKey());
            te.setCurrentTheta(entry.getValue());
            te.setLastUpdated(LocalDateTime.now());
            thetaRepository.save(te);
        }

        session.setStatus(AssessmentSession.SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        Map<String, Object> report = generateReport(sessionId, session, state);
        adaptiveEngine.removeSession(sessionId);

        existing.put("sessionComplete", true);
        existing.put("report", report);
        return existing;
    }

    @Transactional
    public Map<String, Object> generateReport(Long sessionId, AssessmentSession session,
                                               SessionState state) {
        List<SessionAnswer> answers = answerRepository.findBySessionId(sessionId);
        int total = answers.size();
        long correct = answers.stream().filter(SessionAnswer::getIsCorrect).count();
        long scaffolded = answers.stream().filter(SessionAnswer::getScaffoldingTriggered).count();

        Map<String, Object> skillBreakdown = new LinkedHashMap<>();
        for (String topic : state.getTopicQueue()) {
            Map<String, Object> topicData = new LinkedHashMap<>();
            for (Question.SkillTag skill : Question.SkillTag.values()) {
                long topicTotal = answerRepository.countTotalBySessionTopicSkill(sessionId, topic, skill);
                long topicCorrect = answerRepository.countCorrectBySessionTopicSkill(sessionId, topic, skill);
                if (topicTotal == 0) {
                    topicData.put(skill.name(), "NOT_ASSESSED");
                } else {
                    double accuracy = (double) topicCorrect / topicTotal;
                    String status = accuracy >= 0.8 ? "STRONG" : accuracy >= 0.5 ? "DEVELOPING" : "NEEDS_SUPPORT";
                    topicData.put(skill.name(), Map.of(
                        "status", status, "accuracy", Math.round(accuracy * 100),
                        "correct", topicCorrect, "total", topicTotal));
                    updateSkillMastery(session.getStudent(), topic, skill, accuracy, (int) topicTotal, (int) topicCorrect);
                }
            }
            skillBreakdown.put(topic, topicData);
        }

        List<String> recommendations = new ArrayList<>();
        skillBreakdown.forEach((topic, topicObj) -> {
            Map<String, Object> skills = (Map<String, Object>) topicObj;
            skills.forEach((skill, val) -> {
                if (val instanceof Map) {
                    String status = (String) ((Map<?, ?>) val).get("status");
                    if ("NEEDS_SUPPORT".equals(status))
                        recommendations.add("Revisit " + topic + " — " + skill + ": practice difficulty 1-2.");
                    else if ("DEVELOPING".equals(status))
                        recommendations.add("Continue " + topic + " — " + skill + ": push to difficulty 3-4.");
                }
            });
        });
        if (recommendations.isEmpty()) recommendations.add("Excellent! All areas are strong.");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sessionId", sessionId);
        report.put("studentName", session.getStudent().getName());
        report.put("totalQuestions", total);
        report.put("correctAnswers", correct);
        report.put("scaffoldingEvents", scaffolded);
        report.put("overallAccuracy", total > 0 ? Math.round((double) correct / total * 100) : 0);
        report.put("thetaPerTopic", state.getThetaPerTopic());
        report.put("skillBreakdown", skillBreakdown);
        report.put("recommendations", recommendations);
        report.put("generatedAt", LocalDateTime.now().toString());

        try {
            Report savedReport = new Report();
            savedReport.setSession(session);
            savedReport.setStudent(session.getStudent());
            savedReport.setTotalQuestions(total);
            savedReport.setCorrectAnswers((int) correct);
            savedReport.setScaffoldingEvents((int) scaffolded);
            savedReport.setSummaryJson(objectMapper.writeValueAsString(report));
            reportRepository.save(savedReport);
        } catch (Exception e) {
            System.out.println("Error saving report: " + e.getMessage());
        }
        return report;
    }

    @Transactional
    private void updateSkillMastery(User student, String topic, Question.SkillTag skill,
                                    double accuracy, int total, int correct) {
        SkillMastery mastery = skillMasteryRepository
            .findByStudentIdAndTopicAndSkillTag(student.getId(), topic, skill)
            .orElse(new SkillMastery());
        mastery.setStudent(student);
        mastery.setTopic(topic);
        mastery.setSkillTag(skill);
        mastery.setTotalAttempts(total);
        mastery.setCorrectAttempts(correct);
        mastery.setAccuracy(accuracy);
        mastery.setLastUpdated(LocalDateTime.now());
        mastery.setStatus(accuracy >= 0.8 ? SkillMastery.MasteryStatus.STRONG
            : accuracy >= 0.5 ? SkillMastery.MasteryStatus.DEVELOPING
            : SkillMastery.MasteryStatus.NEEDS_SUPPORT);
        skillMasteryRepository.save(mastery);
    }

    public Map<String, Object> getLatestReport(Long studentId) {
        List<Report> reports = reportRepository.findByStudentIdOrderByGeneratedAtDesc(studentId);
        if (reports.isEmpty()) return Collections.emptyMap();
        try { return objectMapper.readValue(reports.get(0).getSummaryJson(), Map.class); }
        catch (Exception e) { return Collections.emptyMap(); }
    }

    public Map<String, Object> getReportBySession(Long sessionId) {
        return reportRepository.findBySessionId(sessionId).map(r -> {
            try { return (Map<String, Object>) objectMapper.readValue(r.getSummaryJson(), Map.class); }
            catch (Exception e) { return new HashMap<String, Object>(); }
        }).orElse(Collections.emptyMap());
    }

    public Map<String, Object> getStudentProfile(Long studentId) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        List<ThetaEstimate> thetas = thetaRepository.findByStudentId(studentId);
        List<AssessmentSession> sessions = sessionRepository.findByStudentId(studentId);
        Map<String, Double> thetaMap = new HashMap<>();
        for (ThetaEstimate t : thetas) thetaMap.put(t.getTopic(), t.getCurrentTheta());
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", student.getId());
        profile.put("name", student.getName());
        profile.put("email", student.getEmail());
        profile.put("role", student.getRole().name());
        profile.put("thetaPerTopic", thetaMap);
        profile.put("totalSessions", sessions.size());
        profile.put("completedSessions", sessions.stream()
            .filter(s -> s.getStatus() == AssessmentSession.SessionStatus.COMPLETED).count());
        return profile;
    }
}
