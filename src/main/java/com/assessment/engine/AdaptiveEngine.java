package com.assessment.engine;

import com.assessment.entity.*;
import com.assessment.repository.*;
import com.assessment.service.ScaffoldingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdaptiveEngine {

    private static final int MAX_QUESTIONS = 5; // change to 3 for quick testing

    private final QuestionRepository questionRepository;
    private final ThetaEstimateRepository thetaRepository;
    private final SessionAnswerRepository answerRepository;
    private final ScaffoldingLogRepository scaffoldingLogRepository;
    private final ScaffoldingService scaffoldingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, SessionState> activeSessions = new ConcurrentHashMap<>();

    public AdaptiveEngine(QuestionRepository questionRepository,
                          ThetaEstimateRepository thetaRepository,
                          SessionAnswerRepository answerRepository,
                          ScaffoldingLogRepository scaffoldingLogRepository,
                          ScaffoldingService scaffoldingService) {
        this.questionRepository = questionRepository;
        this.thetaRepository = thetaRepository;
        this.answerRepository = answerRepository;
        this.scaffoldingLogRepository = scaffoldingLogRepository;
        this.scaffoldingService = scaffoldingService;
    }

    public SessionState startSession(Long sessionId, Long studentId, List<String> topics) {
        SessionState state = new SessionState();
        state.setSessionId(sessionId);
        state.setStudentId(studentId);
        state.setTopicQueue(topics);
        state.setCurrentTopicIndex(0);
        state.setCurrentTopic(topics.get(0));

        for (String topic : topics) {
            thetaRepository.findByStudentIdAndTopic(studentId, topic)
                    .ifPresent(te -> state.getThetaPerTopic().put(topic, te.getCurrentTheta()));
            state.getThetaPerTopic().putIfAbsent(topic, 3.0);
        }

        double startTheta = state.getThetaPerTopic().get(topics.get(0));
        state.setCurrentDifficulty((int) Math.round(Math.min(5, Math.max(1, startTheta))));

        activeSessions.put(sessionId, state);
        return state;
    }

    public Map<String, Object> getNextQuestion(Long sessionId) {
        SessionState state = getState(sessionId);

        if (state.isScaffoldingActive()) {
            Map<String, Object> r = new HashMap<>();
            r.put("isScaffolding", true);
            r.put("scaffolding", true);
            r.put("scaffoldedQuestion", scaffoldFromState(state));
            r.put("originalQuestionId", state.getScaffoldingOriginalQuestionId());
            return r;
        }

        List<Long> usedIds = new ArrayList<>(state.getUsedQuestionIds());
        if (usedIds.isEmpty()) usedIds.add(-1L);

        List<Question> candidates = questionRepository.findAvailableQuestions(
                state.getCurrentTopic(),
                state.getCurrentSkillTag(),
                state.getCurrentDifficulty(),
                usedIds
        );

        if (candidates.isEmpty()) {
            candidates = questionRepository.findByTopicSkillDifficulty(
                    state.getCurrentTopic(),
                    state.getCurrentSkillTag(),
                    state.getCurrentDifficulty()
            );
        }

        if (candidates.isEmpty()) {
            candidates = tryAdjacentDifficulty(state, usedIds);
        }

        if (candidates.isEmpty()) {
            return Map.of("noQuestion", true);
        }

        Question q = candidates.get(0);

        state.setCurrentQuestionId(q.getId());
        state.getUsedQuestionIds().add(q.getId());

        Map<String, Object> r = new HashMap<>();
        r.put("isScaffolding", false);
        r.put("questionId", q.getId());
        r.put("questionText", q.getQuestionText());
        r.put("optionA", q.getOptionA());
        r.put("optionB", q.getOptionB());
        r.put("optionC", q.getOptionC());
        r.put("optionD", q.getOptionD());
        r.put("topic", state.getCurrentTopic());
        r.put("skillTag", state.getCurrentSkillTag().name());
        r.put("difficulty", state.getCurrentDifficulty());
        r.put("currentTheta", state.getThetaPerTopic().getOrDefault(state.getCurrentTopic(), 3.0));

        return r;
    }

    @Transactional
    public Map<String, Object> processAnswer(Long sessionId,
                                             Long questionId,
                                             String studentAnswer,
                                             Integer timeTaken,
                                             AssessmentSession session) {

        SessionState state = getState(sessionId);

        if (state.isScaffoldingActive()) {
            return processScaffoldAnswer(state, studentAnswer, timeTaken, session);
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        boolean correct = evaluateAnswer(question, studentAnswer);

        saveAnswer(session, question, studentAnswer, correct, false, timeTaken);

        state.incrementTotalQuestionsAnswered();

        state.recordAnswer(
                state.getCurrentTopic(),
                state.getCurrentSkillTag(),
                correct,
                false
        );

        Map<String, Object> result = new HashMap<>();
        result.put("wasCorrect", correct);

        if (correct) {
            state.setConsecutiveCorrect(state.getConsecutiveCorrect() + 1);
            state.setConsecutiveWrong(0);
            state.resetScaffolding();

            double newTheta = state.updateTheta(true);
            result.put("newTheta", newTheta);

            state.advanceToNextSkill();

            if (state.allSkillsDone()) {
                double accuracy = state.getAccuracyForCycle();

                if (accuracy >= SessionState.ADVANCE_ACCURACY_THRESHOLD
                        && state.getCurrentDifficulty() < 5) {

                    state.setCurrentDifficulty(state.getCurrentDifficulty() + 1);
                    state.getSkillsDoneThisCycle().clear();
                    state.setCurrentSkillTag(Question.SkillTag.concept);

                    result.put("message", "Difficulty increased to " + state.getCurrentDifficulty());

                } else if (state.hasMoreTopics()) {

                    state.advanceToNextTopic();
                    result.put("message", "Moving to topic: " + state.getCurrentTopic());

                } else {

                    result.put("sessionComplete", true);
                    result.put("message", "Assessment complete");
                    return result;
                }
            } else {
                result.put("message", "Moving to " + state.getCurrentSkillTag().name());
            }

        } else {
            state.setConsecutiveWrong(state.getConsecutiveWrong() + 1);
            state.setConsecutiveCorrect(0);

            String diagnosticPlanJson =
                    scaffoldingService.generateDiagnosticPlan(
                            question.getQuestionText(),
                            question.getTopic(),
                            question.getSkillTag().name(),
                            question.getDifficulty()
                    );

            Map<String, Object> scaffoldQ =
                    scaffoldingService.generateDiagnosticQuestion(
                            question.getQuestionText(),
                            question.getTopic(),
                            question.getSkillTag().name(),
                            question.getDifficulty(),
                            diagnosticPlanJson,
                            1
                    );

            state.setDiagnosticPlanJson(diagnosticPlanJson);

            state.setCurrentDiagnosticStep(1);

            state.setScaffoldingActive(true);
            state.setScaffoldingAttempts(1);
            state.setScaffoldingOriginalQuestionId(question.getId());
            state.setCurrentScaffoldedQuestion(toJson(scaffoldQ));

            result.put("scaffolding", true);
            result.put("diagnosticMode", true);
            result.put("diagnosticStep", 1);
            result.put("sessionComplete", false);
            result.put("scaffoldedQuestion", scaffoldQ);
            result.put("message", "Let us try a simpler version");
        }

        if (state.getTotalQuestionsAnswered() >= MAX_QUESTIONS) {
            result.put("sessionComplete", true);
            result.put("scaffolding", false);
            result.put("message", "Assessment complete. Maximum question limit reached.");
            return result;
        }

        return result;
    }

    @Transactional
    private Map<String, Object> processScaffoldAnswer(SessionState state,
                                                      String studentAnswer,
                                                      Integer timeTaken,
                                                      AssessmentSession session) {

        Map<String, Object> currentScaffold = scaffoldFromState(state);

        String correctAnswer = currentScaffold.get("correctAnswer") != null
                ? currentScaffold.get("correctAnswer").toString().trim().replace("\"", "").replace("'", "")
                : "";

        String submittedAnswer = studentAnswer != null
                ? studentAnswer.trim().replace("\"", "").replace("'", "")
                : "";

        boolean correct = submittedAnswer.equalsIgnoreCase(correctAnswer);

        state.recordAnswer(
                state.getCurrentTopic(),
                state.getCurrentSkillTag(),
                correct,
                true
        );

        ScaffoldingLog log = new ScaffoldingLog();
        log.setSession(session);

        Question origQ = questionRepository
                .findById(state.getScaffoldingOriginalQuestionId())
                .orElse(null);

        log.setOriginalQuestion(origQ);
        log.setGeneratedQuestion(state.getCurrentScaffoldedQuestion());
        log.setStudentAnswer(studentAnswer);
        log.setIsCorrect(correct);
        log.setAttemptNumber(state.getScaffoldingAttempts());

        scaffoldingLogRepository.save(log);

        Map<String, Object> result = new HashMap<>();
        result.put("wasCorrect", correct);

        if (!correct) {
            Object weakness = currentScaffold.get("failureMeaning");

            if (weakness != null) {
                state.addDiagnosedWeakness(weakness.toString());
            }
        }

        if (state.getCurrentDiagnosticStep() < 4) {

            int nextStep = state.getCurrentDiagnosticStep() + 1;

            state.setCurrentDiagnosticStep(nextStep);
            state.setScaffoldingAttempts(nextStep);

            Map<String, Object> newScaffold =
                    scaffoldingService.generateDiagnosticQuestion(
                            origQ != null ? origQ.getQuestionText() : "",
                            state.getCurrentTopic(),
                            state.getCurrentSkillTag().name(),
                            Math.max(1, state.getCurrentDifficulty() - 1),
                            state.getDiagnosticPlanJson(),
                            nextStep
                    );

            state.setCurrentScaffoldedQuestion(toJson(newScaffold));

            result.put("scaffolding", true);
            result.put("diagnosticMode", true);
            result.put("diagnosticStep", nextStep);
            result.put("sessionComplete", false);
            result.put("scaffoldedQuestion", newScaffold);
            result.put("message", "Let us check the next part.");

        } else {

            double newTheta = state.updateTheta(false);

            List<String> finalWeaknesses = new ArrayList<>(state.getDiagnosedWeaknesses());

            state.resetScaffolding();
            state.setCurrentDifficulty(Math.max(1, state.getCurrentDifficulty() - 1));
            state.advanceToNextSkill();

            result.put("newTheta", newTheta);
            result.put("scaffolding", false);
            result.put("diagnosticMode", false);
            result.put("sessionComplete", false);
            result.put("diagnosedWeaknesses", finalWeaknesses);
            result.put("message", "Diagnostic breakdown complete. Moving ahead.");
        }

        return result;
    }

    private boolean evaluateAnswer(Question question, String studentAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return false;
        }

        String correctAnswer = question.getCorrectAnswer();

        if (correctAnswer == null) {
            return false;
        }

        return studentAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
    }

    @Transactional
    private void saveAnswer(AssessmentSession session,
                            Question question,
                            String studentAnswer,
                            boolean correct,
                            boolean scaffolded,
                            Integer timeTaken) {

        SessionAnswer answer = new SessionAnswer();

        answer.setSession(session);
        answer.setQuestion(question);
        answer.setTopic(question.getTopic());
        answer.setSkillTag(question.getSkillTag());
        answer.setDifficulty(question.getDifficulty());
        answer.setStudentAnswer(studentAnswer);
        answer.setIsCorrect(correct);
        answer.setScaffoldingTriggered(scaffolded);
        answer.setTimeTakenSeconds(timeTaken);

        answerRepository.save(answer);

        question.setTimesUsed(question.getTimesUsed() + 1);
        questionRepository.save(question);
    }

    private List<Question> tryAdjacentDifficulty(SessionState state, List<Long> usedIds) {
        for (int delta = 1; delta <= 2; delta++) {
            for (int sign : new int[]{-1, 1}) {
                int altDiff = state.getCurrentDifficulty() + (sign * delta);

                if (altDiff < 1 || altDiff > 5) continue;

                List<Question> result = questionRepository.findAvailableQuestions(
                        state.getCurrentTopic(),
                        state.getCurrentSkillTag(),
                        altDiff,
                        usedIds
                );

                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return Collections.emptyList();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return fallbackJson();
        }
    }

    private Map<String, Object> scaffoldFromState(SessionState state) {
        try {
            return objectMapper.readValue(
                    state.getCurrentScaffoldedQuestion(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            return fallbackScaffold();
        }
    }

    private String fallbackJson() {
        try {
            return objectMapper.writeValueAsString(fallbackScaffold());
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> fallbackScaffold() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("questionText", "A pizza is cut into 4 equal slices. What does 1 slice show?");
        fallback.put("optionA", "A whole pizza");
        fallback.put("optionB", "A part of a whole");
        fallback.put("optionC", "Four pizzas");
        fallback.put("optionD", "No pizza");
        fallback.put("correctAnswer", "B");
        fallback.put("correctValue", "A part of a whole");
        fallback.put("explanation", "A fraction shows equal parts of one whole.");
        fallback.put("diagnosedMisconception", "Student may not understand that fractions represent equal parts of a whole.");
        return fallback;
    }

    public SessionState getState(Long sessionId) {
        SessionState state = activeSessions.get(sessionId);

        if (state == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        return state;
    }

    public void removeSession(Long sessionId) {
        activeSessions.remove(sessionId);
    }

    public boolean sessionExists(Long sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}