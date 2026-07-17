package com.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ScaffoldingService {

    @Value("${openai.api.key}")
    private String apiKey;


    @Value("${openai.api.url}")
    private String apiUrl;


    @Value("${openai.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateDiagnosticPlan(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctAnswer,
            String correctValue,
            String studentAnswer,
            String studentValue,
            String topic,
            String skillTag,
            int difficulty
    ) {

        try {

            String prompt = """
                    You are an expert mathematics teacher, educational psychologist, and diagnostic assessment specialist.
                    
                    A student answered a mathematics multiple-choice question incorrectly.
                    
                    Your responsibility is to diagnose WHY the student selected the wrong option.
                    
                    Do NOT generate a diagnostic question yet.
                    
                    ---------------------------------------------------
                    STEP 1 — Analyze the student's thinking
                    ---------------------------------------------------
                    
                    Carefully examine:
                    
                    • Original question
                    • Every option
                    • Correct answer
                    • Student's chosen answer
                    
                    Infer the mathematical reasoning that most likely caused the student to choose that option.
                    
                    Do NOT simply say the answer is wrong.
                    
                    Instead determine the misconception behind the student's thinking.
                    
                    For example:
                    
                    • adding numerators and denominators directly
                    • confusing numerator with denominator
                    • incorrect order of operations
                    • misunderstanding place value
                    • incorrect borrowing in subtraction
                    • misunderstanding equivalent fractions
                    • multiplying instead of adding
                    • incorrect unit conversion
                    
                    If several misconceptions are possible,
                    choose the MOST LIKELY one.
                    
                    ---------------------------------------------------
                    STEP 2 — Build the diagnostic path
                    ---------------------------------------------------
                    
                    Generate ONLY TWO diagnostic stages.
                    
                    Stage 1
                    
                    Test the immediate prerequisite skill responsible for the misconception.
                    
                    Stage 2
                    
                    If the student fails Stage 1,
                    test the deeper mathematical foundation.
                    
                    Each stage should isolate a different level of understanding.
                    
                    Do NOT repeat the original question.
                    
                    Do NOT ask definitions.
                    
                    ---------------------------------------------------
                    RULES
                    ---------------------------------------------------
                    
                    • Return ONLY JSON.
                    • No markdown.
                    • No explanations.
                    • Two stages only.
                    • Be specific.
                    • Keep prerequisite descriptions short.
                    • Keep failureMeaning educational.
                    
                    ---------------------------------------------------
                    Return exactly:
                    
                    {
                      "misconception":"...",
                      "confidence":"high",
                      "reasoning":"...",
                      "studentError":"...",
                      "recommendedDifficulty":"easy",
                      "steps":[
                        {
                          "stepNumber":1,
                          "focus":"concept",
                          "prerequisite":"...",
                          "diagnosticPurpose":"...",
                          "failureMeaning":"..."
                        },
                        {
                          "stepNumber":2,
                          "focus":"foundation",
                          "prerequisite":"...",
                          "diagnosticPurpose":"...",
                          "failureMeaning":"..."
                        }
                      ]
                    }
                    
                    ---------------------------------------------------
                    
                    Original Question
                    
                    %s
                    
                    Option A
                    
                    %s
                    
                    Option B
                    
                    %s
                    
                    Option C
                    
                    %s
                    
                    Option D
                    
                    %s
                    
                    Correct Answer
                    
                    %s
                    
                    Correct Value
                    
                    %s
                    
                    Student Selected Answer
                    
                    %s
                    
                    Student Selected Value
                    
                    %s
                    
                    Topic
                    
                    %s
                    
                    Skill Tag
                    
                    %s
                    
                    Difficulty
                    
                    %d
                    
                    """.formatted(
                    questionText,
                    optionA,
                    optionB,
                    optionC,
                    optionD,
                    correctAnswer,
                    correctValue,
                    studentAnswer,
                    studentValue,
                    topic,
                    skillTag,
                    difficulty
            );

            String response = callOpenAI(prompt);

            return cleanJson(response);

        } catch (Exception e) {

            e.printStackTrace();

            return fallbackDiagnosticPlan();

        }
    }

    public Map<String, Object> generateDiagnosticQuestion(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctAnswer,
            String correctValue,
            String studentAnswer,
            String studentValue,
            String topic,
            String skillTag,
            int difficulty,
            String diagnosticPlanJson,
            int diagnosticStep,
            String previousDiagnosticQuestion
    ) {

        try {

            String prompt = """
                You are an expert mathematics teacher and diagnostic assessment specialist.

                A student's misconception has already been analyzed.

                Your task is to generate ONE diagnostic multiple-choice question.

                Your goal is NOT to teach.

                Your goal is to VERIFY whether the diagnosed misconception actually exists.

                ==================================================

                Original Question

                %s

                Option A

                %s

                Option B

                %s

                Option C

                %s

                Option D

                %s

                Correct Answer

                %s

                Correct Value

                %s

                Student Selected Answer

                %s

                Student Selected Value

                %s

                Topic

                %s

                Skill Tag

                %s

                Difficulty

                %d

                ==================================================

                Diagnostic Analysis

                %s

                Current Diagnostic Step

                %d

                Previous Diagnostic Question

                %s

                ==================================================

                Generate ONE easier diagnostic multiple-choice question.

                Rules:

                • Test ONLY the prerequisite of the current diagnostic step.
                • Do NOT repeat the original wording.
                • Do NOT reuse the same numbers.
                • Do NOT ask definitions.
                • Generate exactly FOUR options.
                • Exactly ONE option must be correct.
                • The incorrect options should represent realistic misconceptions.
                • Solve the question yourself before returning it.

                Return ONLY JSON.

                {
                  "questionText":"",
                  "optionA":"",
                  "optionB":"",
                  "optionC":"",
                  "optionD":"",
                  "correctAnswer":"A",
                  "correctValue":"",
                  "explanation":"",
                  "diagnosedMisconception":"",
                  "diagnosticFocus":"",
                  "suspectedWeakness":"",
                  "confidence":"high",
                  "diagnosticStep":1,
                  "prerequisiteTested":"",
                  "failureMeaning":""
                }
                """
                    .formatted(
                            questionText,
                            optionA,
                            optionB,
                            optionC,
                            optionD,
                            correctAnswer,
                            correctValue,
                            studentAnswer,
                            studentValue,
                            topic,
                            skillTag,
                            difficulty,
                            diagnosticPlanJson,
                            diagnosticStep,
                            previousDiagnosticQuestion == null ? "None" : previousDiagnosticQuestion
                    );

            String content = callOpenAI(prompt);

            content = cleanJson(content);

            JsonNode json = objectMapper.readTree(content);

            Map<String, Object> result = new HashMap<>();

            result.put("questionText", json.path("questionText").asText());
            result.put("optionA", json.path("optionA").asText());
            result.put("optionB", json.path("optionB").asText());
            result.put("optionC", json.path("optionC").asText());
            result.put("optionD", json.path("optionD").asText());

            result.put("correctAnswer", json.path("correctAnswer").asText());
            result.put("correctValue", json.path("correctValue").asText());

            result.put("explanation", json.path("explanation").asText());

            result.put("diagnosedMisconception",
                    json.path("diagnosedMisconception").asText());

            result.put("diagnosticFocus",
                    json.path("diagnosticFocus").asText());

            result.put("suspectedWeakness",
                    json.path("suspectedWeakness").asText());

            result.put("confidence",
                    json.path("confidence").asText("medium"));

            result.put("diagnosticStep",
                    json.path("diagnosticStep").asInt(diagnosticStep));

            result.put("prerequisiteTested",
                    json.path("prerequisiteTested").asText());

            result.put("failureMeaning",
                    json.path("failureMeaning").asText());

            return result;

        } catch (Exception e) {

            e.printStackTrace();

            return fallbackDiagnosticQuestion(diagnosticStep);
        }
    }

    private String callOpenAI(String prompt) throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put(
                "content",
                """
                        You are an expert mathematics tutor and diagnostic assessment engine.
                        
                        Always follow the user's instructions exactly.
                        
                        Always return valid JSON.
                        
                        Never wrap JSON inside markdown.
                        
                        Never explain outside JSON.
                        
                        Never apologize.
                        
                        Never produce invalid JSON.
                        
                        If information is missing, make the best educational assumption.
                        """
        );

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> body = new HashMap<>();

        body.put("model", model);

        body.put(
                "messages",
                List.of(
                        systemMessage,
                        userMessage
                )
        );

        body.put("temperature", 0.15);
        body.put("top_p", 0.9);
        body.put("frequency_penalty", 0);
        body.put("presence_penalty", 0);
        body.put("max_tokens", 900);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        apiUrl,
                        HttpMethod.POST,
                        request,
                        String.class
                );

        JsonNode root = objectMapper.readTree(response.getBody());

        return root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

    }

    private String cleanJson(String response) {

        if (response == null)
            return "";

        response = response.trim();

        response = response
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int firstBrace = response.indexOf("{");
        int lastBrace = response.lastIndexOf("}");

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            response = response.substring(firstBrace, lastBrace + 1);
        }

        return response.trim();
    }

    private String fallbackDiagnosticPlan() {

        return """
                {
                  "misconception":"Unknown misconception",
                  "confidence":"low",
                  "reasoning":"AI could not determine the student's reasoning.",
                  "studentError":"Unknown",
                  "recommendedDifficulty":"easy",
                  "steps":[
                    {
                      "stepNumber":1,
                      "focus":"concept",
                      "prerequisite":"Basic understanding of the concept",
                      "diagnosticPurpose":"Check the immediate prerequisite skill.",
                      "failureMeaning":"The student has a conceptual misunderstanding."
                    },
                    {
                      "stepNumber":2,
                      "focus":"foundation",
                      "prerequisite":"Foundational mathematical understanding",
                      "diagnosticPurpose":"Check the deeper prerequisite.",
                      "failureMeaning":"The student lacks the underlying mathematical foundation."
                    }
                  ]
                }
                """;

    }

    private Map<String, Object> fallbackDiagnosticQuestion(int diagnosticStep) {

        Map<String, Object> question = new HashMap<>();

        question.put(
                "questionText",
                "Which fraction is greater than 1?"
        );

        question.put("optionA", "5/4");
        question.put("optionB", "2/5");
        question.put("optionC", "3/8");
        question.put("optionD", "4/9");

        question.put("correctAnswer", "A");
        question.put("correctValue", "5/4");

        question.put(
                "explanation",
                "Fractions with numerator greater than denominator are greater than 1."
        );

        question.put(
                "diagnosedMisconception",
                "Unable to determine automatically."
        );

        question.put(
                "diagnosticFocus",
                "Concept"
        );

        question.put(
                "suspectedWeakness",
                "Foundational mathematical understanding"
        );

        question.put(
                "confidence",
                "low"
        );

        question.put(
                "diagnosticStep",
                diagnosticStep
        );

        question.put(
                "prerequisiteTested",
                "Basic prerequisite"
        );

        question.put(
                "failureMeaning",
                "Student may have a conceptual gap."
        );

        return question;

    }
}