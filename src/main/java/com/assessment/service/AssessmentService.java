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
    private final ScaffoldingLogRepository scaffoldingLogRepository;
    private final AiReportService aiReportService;


    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssessmentService(AdaptiveEngine adaptiveEngine,
                             AssessmentSessionRepository sessionRepository,
                             UserRepository userRepository,
                             ThetaEstimateRepository thetaRepository,
                             SkillMasteryRepository skillMasteryRepository,
                             SessionAnswerRepository answerRepository,
                             ReportRepository reportRepository,
                             ScaffoldingLogRepository scaffoldingLogRepository, AiReportService aiReportService) {

        this.adaptiveEngine = adaptiveEngine;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.thetaRepository = thetaRepository;
        this.skillMasteryRepository = skillMasteryRepository;
        this.answerRepository = answerRepository;
        this.reportRepository = reportRepository;
        this.scaffoldingLogRepository = scaffoldingLogRepository;
        this.aiReportService = aiReportService;
    }

    @Transactional
    public Map<String, Object> startAssessment(Long studentId, List<String> topics) {

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Optional<AssessmentSession> existing =
                sessionRepository.findByStudentIdAndStatus(
                        studentId,
                        AssessmentSession.SessionStatus.IN_PROGRESS
                );

        if (existing.isPresent()) {
            AssessmentSession old = existing.get();
            old.setStatus(AssessmentSession.SessionStatus.ABANDONED);
            old.setEndedAt(LocalDateTime.now());
            sessionRepository.save(old);

            adaptiveEngine.removeSession(old.getId());
        }

        AssessmentSession session = new AssessmentSession();
        session.setStudent(student);
        session.setCurrentTopic(topics.get(0));
        session.setCurrentSkillTag(Question.SkillTag.concept);
        session.setCurrentDifficulty(5); //changed it to highest difficulty
        System.out.println("STARTING DIFFICULTY = " + session.getCurrentDifficulty());
        session.setStatus(AssessmentSession.SessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());

        try {
            session.setTopicsAssessed(objectMapper.writeValueAsString(topics));
        } catch (Exception e) {
            session.setTopicsAssessed("[]");
        }

        session = sessionRepository.save(session);

        adaptiveEngine.startSession(session.getId(), studentId, topics);

        Map<String, Object> next = adaptiveEngine.getNextQuestion(session.getId());
        next.put("sessionId", session.getId());

        return next;
    }

    public Optional<AssessmentSession> getActiveSession(Long studentId) {
        return sessionRepository.findByStudentIdAndStatus(
                studentId,
                AssessmentSession.SessionStatus.IN_PROGRESS
        );
    }

    private boolean isExpired(AssessmentSession session) {
        return session.getStartedAt()
                .plusMinutes(10)
                .isBefore(LocalDateTime.now());
    }

    @Transactional
    public Map<String, Object> submitAnswer(Long sessionId,
                                            Long questionId,
                                            String answer,
                                            Integer timeTaken) {

        AssessmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != AssessmentSession.SessionStatus.IN_PROGRESS) {
            return Map.of("error", "Session already ended");
        }

        if (isExpired(session)) {
            session.setStatus(AssessmentSession.SessionStatus.ABANDONED);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);

            adaptiveEngine.removeSession(sessionId);

            return Map.of(
                    "sessionComplete", true,
                    "status", "TIME_UP",
                    "message", "Assessment ended because time is up"
            );
        }

        Map<String, Object> result =
                adaptiveEngine.processAnswer(sessionId, questionId, answer, timeTaken, session);

        if (Boolean.TRUE.equals(result.get("scaffolding"))) {
            result.put("sessionId", sessionId);
            result.put("sessionComplete", false);
            return result;
        }

        Integer totalAnswered = result.get("totalQuestionsAnswered") != null
                ? Integer.valueOf(result.get("totalQuestionsAnswered").toString())
                : 0;

        if (Boolean.TRUE.equals(result.get("sessionComplete")) && totalAnswered >= 7) {
            return completeSession(sessionId, session, result);
        }

        if (Boolean.TRUE.equals(result.get("sessionComplete")) && totalAnswered < 7) {
            result.put("sessionComplete", false);
        }

        Map<String, Object> nextQ = adaptiveEngine.getNextQuestion(sessionId);

        if (Boolean.TRUE.equals(nextQ.get("noQuestion"))) {
            return completeSession(sessionId, session, result);
        }

        result.putAll(nextQ);
        result.put("sessionId", sessionId);
        result.put("sessionComplete", false);

        return result;
    }

    @Transactional
    public Map<String, Object> abandonAssessment(Long sessionId) {

        AssessmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != AssessmentSession.SessionStatus.IN_PROGRESS) {
            return Map.of(
                    "message", "Assessment already ended",
                    "sessionId", sessionId,
                    "status", session.getStatus().name()
            );
        }

        SessionState state = adaptiveEngine.getState(sessionId);

        session.setStatus(AssessmentSession.SessionStatus.ABANDONED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        Map<String, Object> report = generateReport(sessionId, session, state);

        adaptiveEngine.removeSession(sessionId);

        return Map.of(
                "message", "Assessment ended successfully",
                "sessionId", sessionId,
                "status", "ABANDONED",
                "reportGenerated", true,
                "report", report
        );
    }


    @Transactional
    public Map<String, Object> completeSession(Long sessionId,
                                               AssessmentSession session,
                                               Map<String, Object> data) {

        if (session.getStatus() != AssessmentSession.SessionStatus.IN_PROGRESS) {
            return data;
        }

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

        data.put("sessionComplete", true);
        data.put("report", report);

        adaptiveEngine.removeSession(sessionId);

        return data;
    }

    @Transactional
    public Map<String, Object> generateReport(Long sessionId,
                                              AssessmentSession session,
                                              SessionState state) {

        List<SessionAnswer> answers = answerRepository.findBySessionId(sessionId);
        List<ScaffoldingLog> scaffoldLogs = scaffoldingLogRepository.findBySessionId(sessionId);

        int total = answers.size();

        long correct = answers.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsCorrect()))
                .count();

        int overallAccuracy = total > 0
                ? (int) Math.round((double) correct / total * 100)
                : 0;

        List<Report> previousReports =
                reportRepository.findByStudentIdOrderByGeneratedAtDesc(
                        session.getStudent().getId()
                );

        int assessmentsTaken = previousReports.size() + 1;

        double previousAverage = previousReports.stream()
                .mapToInt(r -> {
                    try {
                        Map<String, Object> oldReport =
                                objectMapper.readValue(r.getSummaryJson(), Map.class);

                        Object score = oldReport.get("overallAccuracy");

                        if (score instanceof Number) {
                            return ((Number) score).intValue();
                        }

                        return 0;

                    } catch (Exception e) {
                        return 0;
                    }
                })
                .average()
                .orElse(0.0);

        double averageScore = previousReports.isEmpty()
                ? overallAccuracy
                : Math.round(((previousAverage * previousReports.size()) + overallAccuracy)
                / assessmentsTaken);

        Set<String> topicsCovered = new LinkedHashSet<>();

        for (Report r : previousReports) {
            try {
                Map<String, Object> oldReport =
                        objectMapper.readValue(r.getSummaryJson(), Map.class);

                Object topics = oldReport.get("topicsAssessed");

                if (topics instanceof List<?>) {
                    for (Object t : (List<?>) topics) {
                        topicsCovered.add(String.valueOf(t));
                    }
                }

            } catch (Exception ignored) {
            }
        }

        topicsCovered.addAll(state.getTopicQueue());

        Map<String, Object> report = new LinkedHashMap<>();

        report.put("sessionId", sessionId);
        report.put("studentName", session.getStudent().getName());
        report.put("topicsAssessed", state.getTopicQueue());
        report.put("totalQuestions", total);
        report.put("correctAnswers", correct);
        report.put("overallAccuracy", overallAccuracy);
        report.put("scaffoldingEvents", scaffoldLogs.size());
        report.put("thetaPerTopic", state.getThetaPerTopic());
        report.put("generatedAt", LocalDateTime.now().toString());

        report.put("assessmentStatus", session.getStatus().name());

        if (session.getStatus() == AssessmentSession.SessionStatus.ABANDONED) {
            report.put(
                    "terminationNote",
                    "Assessment was terminated after " + total + " questions."
            );
        }

        report.put("assessmentsTaken", assessmentsTaken);
        report.put("averageScore", averageScore);
        report.put("topicsCovered", topicsCovered);

        Map<String, Object> topicBreakdown = new LinkedHashMap<>();
        List<String> recommendations = new ArrayList<>();

        for (String topic : state.getTopicQueue()) {

            Map<String, Object> topicData = new LinkedHashMap<>();
            Map<String, Object> skillData = new LinkedHashMap<>();

            List<String> strengths = new ArrayList<>();
            List<String> weaknesses = new ArrayList<>();
            List<String> misconceptions = new ArrayList<>();

            for (Question.SkillTag skill : Question.SkillTag.values()) {

                long skillTotal = answers.stream()
                        .filter(a -> topic.equals(a.getTopic()))
                        .filter(a -> skill == a.getSkillTag())
                        .count();

                long skillCorrect = answers.stream()
                        .filter(a -> topic.equals(a.getTopic()))
                        .filter(a -> skill == a.getSkillTag())
                        .filter(a -> Boolean.TRUE.equals(a.getIsCorrect()))
                        .count();

                boolean scaffoldingUsed = scaffoldLogs.stream()
                        .anyMatch(log ->
                                log.getOriginalQuestion() != null &&
                                        topic.equals(log.getOriginalQuestion().getTopic()) &&
                                        skill == log.getOriginalQuestion().getSkillTag()
                        );

                if (skillTotal == 0) {
                    skillData.put(skill.name(), Map.of(
                            "status", "NOT_ASSESSED",
                            "accuracy", 0,
                            "correct", 0,
                            "total", 0,
                            "scaffoldingUsed", scaffoldingUsed
                    ));
                    continue;
                }

                int accuracy = (int) Math.round((double) skillCorrect / skillTotal * 100);

                String status;

                if (accuracy >= 80 && !scaffoldingUsed) {
                    status = "STRONG";
                    strengths.add("Strong understanding of " + skill.name().toLowerCase() + " in " + topic);
                } else if (accuracy >= 50) {
                    status = "DEVELOPING";
                    weaknesses.add("Needs more practice with " + skill.name().toLowerCase() + " in " + topic);
                } else {
                    status = "NEEDS_SUPPORT";
                    weaknesses.add("Struggles with " + skill.name().toLowerCase() + " in " + topic);
                }

                skillData.put(skill.name(), Map.of(
                        "status", status,
                        "accuracy", accuracy,
                        "correct", skillCorrect,
                        "total", skillTotal,
                        "scaffoldingUsed", scaffoldingUsed
                ));

                updateSkillMastery(
                        session.getStudent(),
                        topic,
                        skill,
                        accuracy / 100.0,
                        (int) skillTotal,
                        (int) skillCorrect
                );
            }

            for (ScaffoldingLog log : scaffoldLogs) {

                if (log.getOriginalQuestion() == null) continue;
                if (!topic.equals(log.getOriginalQuestion().getTopic())) continue;

                try {
                    Map<String, Object> scaffoldJson =
                            objectMapper.readValue(log.getGeneratedQuestion(), Map.class);

                    Object misconception = scaffoldJson.get("diagnosedMisconception");
                    if (misconception != null && !misconception.toString().isBlank()) {
                        misconceptions.add(misconception.toString());
                    }

                    Object weakness = scaffoldJson.get("suspectedWeakness");
                    if (weakness != null && !weakness.toString().isBlank()) {
                        weaknesses.add("Possible weakness: " + weakness);
                    }

                    Object failureMeaning = scaffoldJson.get("failureMeaning");
                    if (failureMeaning != null && !failureMeaning.toString().isBlank()) {
                        weaknesses.add("Diagnostic gap: " + failureMeaning);
                    }

                } catch (Exception ignored) {
                }
            }

            if (strengths.isEmpty()) {
                strengths.add("No clear strength identified yet for this topic.");
            }

            if (weaknesses.isEmpty()) {
                weaknesses.add("No major weakness detected for this topic.");
            }

            if (!misconceptions.isEmpty()) {
                recommendations.add("Revise " + topic + " using visual models and scaffolded examples.");
            }

            topicData.put("skills", skillData);
            topicData.put("strengths", strengths);
            topicData.put("weaknesses", weaknesses);
            topicData.put("diagnosedMisconceptions", misconceptions);

            topicBreakdown.put(topic, topicData);
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Good progress. Continue practicing mixed question types.");
        }

        report.put("topicBreakdown", topicBreakdown);
        report.put("recommendations", recommendations);
        Map<String, Object> aiReport = aiReportService.generateAiReport(session, report);
        report.put("aiReport", aiReport);

        try {
            Report savedReport = new Report();

            savedReport.setSession(session);
            savedReport.setStudent(session.getStudent());
            savedReport.setTotalQuestions(total);
            savedReport.setCorrectAnswers((int) correct);
            savedReport.setScaffoldingEvents(scaffoldLogs.size());
            savedReport.setSummaryJson(objectMapper.writeValueAsString(report));

            // ADD THIS
            savedReport.setGeneratedAt(LocalDateTime.now());

            reportRepository.save(savedReport);

        } catch (Exception e) {
            System.out.println("Error saving report: " + e.getMessage());
        }

        return report;
    }

    @Transactional
    private void updateSkillMastery(User student,
                                    String topic,
                                    Question.SkillTag skill,
                                    double accuracy,
                                    int total,
                                    int correct) {

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

        mastery.setStatus(
                accuracy >= 0.8 ? SkillMastery.MasteryStatus.STRONG
                        : accuracy >= 0.5 ? SkillMastery.MasteryStatus.DEVELOPING
                        : SkillMastery.MasteryStatus.NEEDS_SUPPORT
        );

        skillMasteryRepository.save(mastery);
    }

    public Map<String, Object> getLatestReport(Long studentId) {
        List<Report> reports =
                reportRepository.findByStudentIdOrderByGeneratedAtDesc(studentId);

        if (reports.isEmpty()) return Collections.emptyMap();

        Report latest = reports.stream()
                .max(Comparator.comparing(Report::getId))
                .orElse(reports.get(0));

        try {
            return objectMapper.readValue(
                    latest.getSummaryJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getReportBySession(Long sessionId) {
        return reportRepository.findBySessionId(sessionId)
                .map(r -> {
                    try {
                        return objectMapper.readValue(
                                r.getSummaryJson(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                        );
                    } catch (Exception e) {
                        return new HashMap<String, Object>();
                    }
                })
                .orElse(new HashMap<String, Object>());
    }
}