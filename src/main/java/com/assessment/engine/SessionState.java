package com.assessment.engine;

import com.assessment.entity.Question;
import java.util.*;

public class SessionState {

    private Long sessionId;
    private Long studentId;
    private List<String> topicQueue = new ArrayList<>();
    private int currentTopicIndex = 0;
    private String currentTopic;
    private Question.SkillTag currentSkillTag = Question.SkillTag.concept;
    private int currentDifficulty = 3;
    private Map<String, Double> thetaPerTopic = new HashMap<>();
    private Set<Question.SkillTag> skillsDoneThisCycle = new HashSet<>();
    private Set<Long> usedQuestionIds = new HashSet<>();
    private int consecutiveCorrect = 0;
    private int consecutiveWrong = 0;
    private boolean scaffoldingActive = false;
    private int scaffoldingAttempts = 0;
    private Long scaffoldingOriginalQuestionId = null;
    private String currentScaffoldedQuestion = null;
    private Long currentQuestionId = null;
    private Map<String, Integer> correctCount = new HashMap<>();
    private Map<String, Integer> totalCount = new HashMap<>();
    private Map<String, Integer> scaffoldCount = new HashMap<>();
    private int totalQuestionsAnswered=0;

    //diagnosed gap
    private boolean diagnosticMode = false;
    private Long originalQuestionId;
    private String diagnosticPlanJson;
    private int currentDiagnosticStep = 0;
    private String lastDiagnosticQuestionJson;
    private List<String> diagnosedWeaknesses = new ArrayList<>();
    private String lastDiagnosticQuestion;

    public boolean isDiagnosticMode() {
        return diagnosticMode;
    }

    public void setDiagnosticMode(boolean diagnosticMode) {
        this.diagnosticMode = diagnosticMode;
    }

    public Long getOriginalQuestionId() {
        return originalQuestionId;
    }

    public void setOriginalQuestionId(Long originalQuestionId) {
        this.originalQuestionId = originalQuestionId;
    }

    public String getDiagnosticPlanJson() {
        return diagnosticPlanJson;
    }

    public void setDiagnosticPlanJson(String diagnosticPlanJson) {
        this.diagnosticPlanJson = diagnosticPlanJson;
    }

    public int getCurrentDiagnosticStep() {
        return currentDiagnosticStep;
    }

    public void setCurrentDiagnosticStep(int currentDiagnosticStep) {
        this.currentDiagnosticStep = currentDiagnosticStep;
    }

    public String getLastDiagnosticQuestionJson() {
        return lastDiagnosticQuestionJson;
    }

    public void setLastDiagnosticQuestionJson(String lastDiagnosticQuestionJson) {
        this.lastDiagnosticQuestionJson = lastDiagnosticQuestionJson;
    }

    public List<String> getDiagnosedWeaknesses() {
        return diagnosedWeaknesses;
    }

    public void addDiagnosedWeakness(String weakness) {
        this.diagnosedWeaknesses.add(weakness);
    }
    public String getLastDiagnosticQuestion() {
        return lastDiagnosticQuestion;
    }

    public void setLastDiagnosticQuestion(String lastDiagnosticQuestion) {
        this.lastDiagnosticQuestion = lastDiagnosticQuestion;
    }


    public static final double LEARNING_RATE = 0.3;
    public static final double THETA_MIN = 1.0;
    public static final double THETA_MAX = 5.0;
//    public static final int MAX_SCAFFOLDING_ATTEMPTS = 2;
    public static final double ADVANCE_ACCURACY_THRESHOLD = 0.7;

    public static final Map<Integer, Double> DIFFICULTY_WEIGHT = Map.of(
        1, 0.5, 2, 0.7, 3, 1.0, 4, 1.3, 5, 1.6
    );

    public String topicSkillKey(String topic, Question.SkillTag skill) {
        return topic + "_" + skill.name();
    }

    public void recordAnswer(String topic, Question.SkillTag skill, boolean correct, boolean scaffolded) {
        String key = topicSkillKey(topic, skill);
        totalCount.merge(key, 1, Integer::sum);
        if (correct) correctCount.merge(key, 1, Integer::sum);
        if (scaffolded) scaffoldCount.merge(key, 1, Integer::sum);
    }

    public double getAccuracyForCycle() {
        int total = 0, correct = 0;
        for (Question.SkillTag skill : Question.SkillTag.values()) {
            String key = topicSkillKey(currentTopic, skill);
            total += totalCount.getOrDefault(key, 0);
            correct += correctCount.getOrDefault(key, 0);
        }
        return total == 0 ? 0 : (double) correct / total;
    }

    public double updateTheta(boolean correct) {
        double oldTheta = thetaPerTopic.getOrDefault(currentTopic, 3.0);
        double weight = DIFFICULTY_WEIGHT.getOrDefault(currentDifficulty, 1.0);
        double delta = LEARNING_RATE * weight * (correct ? 1 : -1);
        double newTheta = Math.min(THETA_MAX, Math.max(THETA_MIN, oldTheta + delta));
        thetaPerTopic.put(currentTopic, newTheta);
        return newTheta;
    }

    public boolean allSkillsDone() {
        return skillsDoneThisCycle.containsAll(Arrays.asList(Question.SkillTag.values()));
    }

    public boolean hasMoreTopics() {
        return currentTopicIndex + 1 < topicQueue.size();
    }

    public void advanceToNextTopic() {
        currentTopicIndex++;
        if (currentTopicIndex < topicQueue.size()) {
            currentTopic = topicQueue.get(currentTopicIndex);
            double theta = thetaPerTopic.getOrDefault(currentTopic, 3.0);
            currentDifficulty = (int) Math.round(Math.min(5, Math.max(1, theta)));
            skillsDoneThisCycle.clear();
            currentSkillTag = Question.SkillTag.concept;
            consecutiveCorrect = 0;
            consecutiveWrong = 0;
        }
    }

    public void advanceToNextSkill() {
        skillsDoneThisCycle.add(currentSkillTag);
        for (Question.SkillTag skill : Question.SkillTag.values()) {
            if (!skillsDoneThisCycle.contains(skill)) {
                currentSkillTag = skill;
                return;
            }
        }
    }

    public void resetScaffolding() {
        scaffoldingActive = false;
        scaffoldingAttempts = 0;
        scaffoldingOriginalQuestionId = null;
        currentScaffoldedQuestion = null;

        diagnosticMode = false;
        originalQuestionId = null;
        diagnosticPlanJson = null;
        currentDiagnosticStep = 0;
        lastDiagnosticQuestionJson = null;
        lastDiagnosticQuestion = null;
    }

    // ── Getters and Setters ────────────────────────────────────────────────
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public List<String> getTopicQueue() { return topicQueue; }
    public void setTopicQueue(List<String> topicQueue) { this.topicQueue = topicQueue; }
    public int getCurrentTopicIndex() { return currentTopicIndex; }
    public void setCurrentTopicIndex(int currentTopicIndex) { this.currentTopicIndex = currentTopicIndex; }
    public String getCurrentTopic() { return currentTopic; }
    public void setCurrentTopic(String currentTopic) { this.currentTopic = currentTopic; }
    public Question.SkillTag getCurrentSkillTag() { return currentSkillTag; }
    public void setCurrentSkillTag(Question.SkillTag currentSkillTag) { this.currentSkillTag = currentSkillTag; }
    public int getCurrentDifficulty() { return currentDifficulty; }
    public void setCurrentDifficulty(int currentDifficulty) { this.currentDifficulty = currentDifficulty; }
    public Map<String, Double> getThetaPerTopic() { return thetaPerTopic; }
    public void setThetaPerTopic(Map<String, Double> thetaPerTopic) { this.thetaPerTopic = thetaPerTopic; }
    public Set<Question.SkillTag> getSkillsDoneThisCycle() { return skillsDoneThisCycle; }
    public void setSkillsDoneThisCycle(Set<Question.SkillTag> skillsDoneThisCycle) { this.skillsDoneThisCycle = skillsDoneThisCycle; }
    public Set<Long> getUsedQuestionIds() { return usedQuestionIds; }
    public void setUsedQuestionIds(Set<Long> usedQuestionIds) { this.usedQuestionIds = usedQuestionIds; }
    public int getConsecutiveCorrect() { return consecutiveCorrect; }
    public void setConsecutiveCorrect(int consecutiveCorrect) { this.consecutiveCorrect = consecutiveCorrect; }
    public int getConsecutiveWrong() { return consecutiveWrong; }
    public void setConsecutiveWrong(int consecutiveWrong) { this.consecutiveWrong = consecutiveWrong; }
    public boolean isScaffoldingActive() { return scaffoldingActive; }
    public void setScaffoldingActive(boolean scaffoldingActive) { this.scaffoldingActive = scaffoldingActive; }
    public int getScaffoldingAttempts() { return scaffoldingAttempts; }
    public void setScaffoldingAttempts(int scaffoldingAttempts) { this.scaffoldingAttempts = scaffoldingAttempts; }
    public Long getScaffoldingOriginalQuestionId() { return scaffoldingOriginalQuestionId; }
    public void setScaffoldingOriginalQuestionId(Long scaffoldingOriginalQuestionId) { this.scaffoldingOriginalQuestionId = scaffoldingOriginalQuestionId; }
    public String getCurrentScaffoldedQuestion() { return currentScaffoldedQuestion; }
    public void setCurrentScaffoldedQuestion(String currentScaffoldedQuestion) { this.currentScaffoldedQuestion = currentScaffoldedQuestion; }
    public Long getCurrentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(Long currentQuestionId) { this.currentQuestionId = currentQuestionId; }
    public Map<String, Integer> getCorrectCount() { return correctCount; }
    public Map<String, Integer> getTotalCount() { return totalCount; }
    public Map<String, Integer> getScaffoldCount() { return scaffoldCount; }
    public int getTotalQuestionsAnswered() {
        return totalQuestionsAnswered;
    }

    public void setTotalQuestionsAnswered(int totalQuestionsAnswered) {
        this.totalQuestionsAnswered = totalQuestionsAnswered;
    }

    public void incrementTotalQuestionsAnswered() {
        this.totalQuestionsAnswered++;
    }
}
