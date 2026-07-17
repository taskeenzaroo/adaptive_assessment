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

    private static final int MAX_QUESTIONS = 7;

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

        state.setCurrentDifficulty(5);

        activeSessions.put(sessionId, state);
        return state;
    }

    public Map<String, Object> getNextQuestion(Long sessionId) {
        SessionState state = getState(sessionId);

        if (state.isScaffoldingActive()) {
            Map<String, Object> r = new HashMap<>();

            r.put("isScaffolding", true);
            r.put("scaffolding", true);
            r.put("diagnosticMode", true);
            r.put("diagnosticStep", state.getCurrentDiagnosticStep());
            r.put("scaffoldedQuestion", scaffoldFromState(state));
            r.put("originalQuestionId", state.getScaffoldingOriginalQuestionId());
            r.put("totalQuestionsAnswered", state.getTotalQuestionsAnswered());

            return r;
        }

        List<Long> usedIds = new ArrayList<>(state.getUsedQuestionIds());
        if (usedIds.isEmpty()) {
            usedIds.add(-1L);
        }

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
        r.put("totalQuestionsAnswered", state.getTotalQuestionsAnswered());

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
        result.put("totalQuestionsAnswered", state.getTotalQuestionsAnswered());

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

                    result.put("message",
                            "Difficulty increased to "
                                    + state.getCurrentDifficulty());

                } else if (state.hasMoreTopics()) {

                    state.advanceToNextTopic();

                    result.put(
                            "message",
                            "Moving to topic: " + state.getCurrentTopic()
                    );

                } else {

                    if (state.getTotalQuestionsAnswered() >= MAX_QUESTIONS) {

                        result.put("sessionComplete", true);
                        result.put("message", "Assessment complete");

                        return result;
                    }

                    state.getSkillsDoneThisCycle().clear();
                    state.getUsedQuestionIds().clear();

                    state.setCurrentSkillTag(Question.SkillTag.concept);

                    state.advanceToNextSkill();

                    result.put(
                            "message",
                            "Continuing assessment cycle"
                    );
                }

            } else {

                result.put(
                        "message",
                        "Moving to "
                                + state.getCurrentSkillTag().name()
                );
            }

        } else {

            state.setConsecutiveWrong(
                    state.getConsecutiveWrong() + 1
            );

            state.setConsecutiveCorrect(0);

            String diagnosticPlanJson =
                    scaffoldingService.generateDiagnosticPlan(

                            question.getQuestionText(),

                            question.getOptionA(),
                            question.getOptionB(),
                            question.getOptionC(),
                            question.getOptionD(),

                            question.getCorrectAnswer(),

                            getOptionValue(
                                    question,
                                    question.getCorrectAnswer()
                            ),

                            studentAnswer,

                            getOptionValue(
                                    question,
                                    studentAnswer
                            ),

                            question.getTopic(),

                            question.getSkillTag().name(),

                            question.getDifficulty()
                    );

            Map<String, Object> scaffoldQuestion =
                    scaffoldingService.generateDiagnosticQuestion(

                            question.getQuestionText(),

                            question.getOptionA(),
                            question.getOptionB(),
                            question.getOptionC(),
                            question.getOptionD(),

                            question.getCorrectAnswer(),

                            getOptionValue(
                                    question,
                                    question.getCorrectAnswer()
                            ),

                            studentAnswer,

                            getOptionValue(
                                    question,
                                    studentAnswer
                            ),

                            question.getTopic(),

                            question.getSkillTag().name(),

                            question.getDifficulty(),

                            diagnosticPlanJson,

                            1,

                            ""
                    );

            state.setDiagnosticPlanJson(diagnosticPlanJson);

            state.setCurrentDiagnosticStep(1);

            state.setScaffoldingActive(true);

            state.setScaffoldingAttempts(1);

            state.setScaffoldingOriginalQuestionId(question.getId());

            state.setCurrentScaffoldedQuestion(
                    toJson(scaffoldQuestion)
            );

            result.put("scaffolding", true);

            result.put("diagnosticMode", true);

            result.put("diagnosticStep", 1);

            result.put("sessionComplete", false);

            result.put(
                    "scaffoldedQuestion",
                    scaffoldQuestion
            );

            result.put(
                    "message",
                    "Let's identify exactly where the misunderstanding occurred."
            );
        }

        if (state.getTotalQuestionsAnswered() >= MAX_QUESTIONS) {

            result.put("sessionComplete", true);
            result.put("scaffolding", false);

            result.put(
                    "message",
                    "Assessment complete."
            );

            return result;
        }

        result.put("sessionComplete", false);

        return result;
    }

    @Transactional
    private Map<String, Object> processScaffoldAnswer(SessionState state,
                                                      String studentAnswer,
                                                      Integer timeTaken,
                                                      AssessmentSession session) {

        Map<String, Object> currentScaffold = scaffoldFromState(state);

        String correctAnswer = currentScaffold.get("correctAnswer") != null
                ? currentScaffold.get("correctAnswer").toString().trim()
                : "";

        boolean correct = studentAnswer != null
                && studentAnswer.trim().equalsIgnoreCase(correctAnswer);

        state.recordAnswer(
                state.getCurrentTopic(),
                state.getCurrentSkillTag(),
                correct,
                true
        );

        ScaffoldingLog log = new ScaffoldingLog();

        log.setSession(session);

        Question originalQuestion = questionRepository
                .findById(state.getScaffoldingOriginalQuestionId())
                .orElse(null);

        log.setOriginalQuestion(originalQuestion);

        log.setGeneratedQuestion(state.getCurrentScaffoldedQuestion());

        log.setStudentAnswer(studentAnswer);

        log.setIsCorrect(correct);

        log.setAttemptNumber(state.getCurrentDiagnosticStep());

        scaffoldingLogRepository.save(log);

        Map<String, Object> result = new HashMap<>();

        result.put("wasCorrect", correct);

        result.put(
                "totalQuestionsAnswered",
                state.getTotalQuestionsAnswered()
        );

        if (!correct) {

            Object weakness = currentScaffold.get("failureMeaning");

            if (weakness != null) {
                state.addDiagnosedWeakness(weakness.toString());
            }
        }

        /*
         * Since we now use ONLY TWO diagnostic stages
         */

        if (!correct && state.getCurrentDiagnosticStep() < 2) {

            int nextStep = 2;

            state.setCurrentDiagnosticStep(nextStep);

            state.setScaffoldingAttempts(nextStep);

            String previousQuestion =
                    currentScaffold.get("questionText") == null
                            ? ""
                            : currentScaffold.get("questionText").toString();

            Map<String, Object> nextScaffold =
                    scaffoldingService.generateDiagnosticQuestion(

                            originalQuestion.getQuestionText(),

                            originalQuestion.getOptionA(),
                            originalQuestion.getOptionB(),
                            originalQuestion.getOptionC(),
                            originalQuestion.getOptionD(),

                            originalQuestion.getCorrectAnswer(),

                            getOptionValue(
                                    originalQuestion,
                                    originalQuestion.getCorrectAnswer()
                            ),

                            studentAnswer,

                            getOptionValue(
                                    originalQuestion,
                                    studentAnswer
                            ),

                            originalQuestion.getTopic(),

                            originalQuestion.getSkillTag().name(),

                            Math.max(
                                    1,
                                    originalQuestion.getDifficulty() - 1
                            ),

                            state.getDiagnosticPlanJson(),

                            nextStep,

                            previousQuestion
                    );

            state.setCurrentScaffoldedQuestion(
                    toJson(nextScaffold)
            );

            result.put("scaffolding", true);

            result.put("diagnosticMode", true);

            result.put("diagnosticStep", nextStep);

            result.put("sessionComplete", false);

            result.put("scaffoldedQuestion", nextScaffold);

            result.put(
                    "message",
                    "Let's verify one deeper prerequisite."
            );

            return result;
        }

        /*
         * Student either:
         * 1. Solved the scaffold correctly
         * OR
         * 2. Failed both diagnostic stages
         */

        double newTheta = state.updateTheta(correct);

        List<String> weaknesses =
                new ArrayList<>(state.getDiagnosedWeaknesses());

        state.resetScaffolding();

        if (!correct) {
            state.setCurrentDifficulty(
                    Math.max(
                            1,
                            state.getCurrentDifficulty() - 1
                    )
            );
        }

        state.advanceToNextSkill();

        result.put("newTheta", newTheta);

        result.put("scaffolding", false);

        result.put("diagnosticMode", false);

        result.put("sessionComplete", false);

        result.put("diagnosedWeaknesses", weaknesses);

        result.put(
                "message",
                correct
                        ? "Diagnostic complete. Misconception resolved."
                        : "Diagnostic complete. Weakness identified."
        );

        return result;
    }

    private String getOptionValue(Question question, String answerLetter) {

        if (answerLetter == null) {
            return "";
        }

        switch (answerLetter.trim().toUpperCase()) {

            case "A":
                return question.getOptionA();

            case "B":
                return question.getOptionB();

            case "C":
                return question.getOptionC();

            case "D":
                return question.getOptionD();

            default:
                return "";
        }
    }

    private boolean evaluateAnswer(Question question, String studentAnswer) {

        if (studentAnswer == null || studentAnswer.isBlank()) {
            return false;
        }

        return studentAnswer.trim()
                .equalsIgnoreCase(question.getCorrectAnswer().trim());
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

    private List<Question> tryAdjacentDifficulty(SessionState state,
                                                 List<Long> usedIds) {

        for (int delta = 1; delta <= 2; delta++) {

            for (int sign : new int[]{-1, 1}) {

                int difficulty =
                        state.getCurrentDifficulty() + (delta * sign);

                if (difficulty < 1 || difficulty > 5) {
                    continue;
                }

                List<Question> questions =
                        questionRepository.findAvailableQuestions(
                                state.getCurrentTopic(),
                                state.getCurrentSkillTag(),
                                difficulty,
                                usedIds
                        );

                if (!questions.isEmpty()) {
                    return questions;
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
                    new TypeReference<Map<String, Object>>() {
                    }
            );

        } catch (Exception e) {

            return fallbackScaffold();

        }
    }

    private String fallbackJson() {

        try {

            return objectMapper.writeValueAsString(
                    fallbackScaffold()
            );

        } catch (Exception e) {

            return "{}";

        }
    }

    private Map<String, Object> fallbackScaffold() {

        Map<String, Object> scaffold = new HashMap<>();

        scaffold.put(
                "questionText",
                "Which fraction is greater than 1?"
        );

        scaffold.put("optionA", "5/4");
        scaffold.put("optionB", "1/4");
        scaffold.put("optionC", "2/5");
        scaffold.put("optionD", "3/8");

        scaffold.put("correctAnswer", "A");
        scaffold.put("correctValue", "5/4");

        scaffold.put(
                "explanation",
                "A fraction is greater than one when its numerator is larger than its denominator."
        );

        scaffold.put(
                "diagnosedMisconception",
                "Unable to determine."
        );

        scaffold.put(
                "diagnosticFocus",
                "Concept"
        );

        scaffold.put(
                "suspectedWeakness",
                "Foundational mathematical understanding"
        );

        scaffold.put(
                "confidence",
                "low"
        );

        scaffold.put(
                "diagnosticStep",
                1
        );

        scaffold.put(
                "prerequisiteTested",
                "Understanding fractions greater than one"
        );

        scaffold.put(
                "failureMeaning",
                "Student may have a conceptual misunderstanding."
        );

        return scaffold;
    }

    public SessionState getState(Long sessionId) {

        SessionState state = activeSessions.get(sessionId);

        if (state == null) {
            throw new RuntimeException(
                    "Session not found : " + sessionId
            );
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