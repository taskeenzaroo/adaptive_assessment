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

    @Value("${groq.api.key}")
    private String apiKey;

    //    @Value("${groq.api.url}")
//    private String apiUrl;
    private String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateDiagnosticPlan(
            String questionText,
            String topic,
            String skillTag,
            int difficulty
    ) {
        try {
            String prompt = """
                    You are an expert diagnostic tutor.

                    A student answered this question incorrectly.

                    Your job is to break the question into 4 generic prerequisite diagnostic steps.

                    The steps should identify where the student's understanding breaks down.

                    Use this generic structure:
                    1. Concept understanding
                    2. Procedure or method understanding
                    3. Calculation or execution
                    4. Application or representation

                    But customize each step to the given question.

                    Return ONLY valid JSON.
                    Do not include markdown.
                    Do not explain outside JSON.

                    Return exactly this format:

                    {
                      "steps": [
                        {
                          "stepNumber": 1,
                          "focus": "concept",
                          "prerequisite": "...",
                          "diagnosticPurpose": "...",
                          "failureMeaning": "..."
                        },
                        {
                          "stepNumber": 2,
                          "focus": "procedure",
                          "prerequisite": "...",
                          "diagnosticPurpose": "...",
                          "failureMeaning": "..."
                        },
                        {
                          "stepNumber": 3,
                          "focus": "calculation",
                          "prerequisite": "...",
                          "diagnosticPurpose": "...",
                          "failureMeaning": "..."
                        },
                        {
                          "stepNumber": 4,
                          "focus": "application",
                          "prerequisite": "...",
                          "diagnosticPurpose": "...",
                          "failureMeaning": "..."
                        }
                      ]
                    }

                    Original Question:
                    %s

                    Topic:
                    %s

                    Skill Tag:
                    %s

                    Difficulty:
                    %d
                    """.formatted(questionText, topic, skillTag, difficulty);

            String content = callGroq(prompt);
            return cleanJson(content);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Diagnostic plan generation failed: " + e.getMessage());
            return fallbackDiagnosticPlan();
        }
    }

    public Map<String, Object> generateDiagnosticQuestion(
            String questionText,
            String topic,
            String skillTag,
            int difficulty,
            String diagnosticPlanJson,
            int diagnosticStep,
            String previousDiagnosticQuestion
    ) {
        try {
            String prompt = """
                            You are an expert adaptive math tutor for children.
                            
                                      A student answered the original math question incorrectly.
                                      A diagnostic plan has already been created.
                            
                                      Your job:
                                      Generate ONE diagnostic MCQ for the current diagnostic step.
                            
                                      MAIN GOAL:
                                      Find the exact mathematical gap by asking a smaller, easier math question.
                            
                                      VERY IMPORTANT:
                                      Generate an actual math problem.
                                      Do NOT generate theory-only or definition-only questions.
                            
                                      The student should solve, compare, convert, calculate, identify a value, or choose the correct next step.
                            
                                      DO NOT ask questions like:
                                      - What is a numerator?
                                      - What is a denominator?
                                      - What is an improper fraction?
                                      - What does mixed fraction mean?
                            
                                      GOOD question types:
                                      - Convert 7/4 into a mixed fraction.
                                      - Which fraction is greater than 1?
                                      - Which fraction is equivalent to 1/2?
                                      - What is the LCM of 4 and 6?
                                      - Add 2/5 + 1/5.
                                      - What should be the next step to solve this fraction problem?
                            
                                      RULES:
                                      1. Test ONLY the current diagnostic step.
                                      2. Make it easier than the original question.
                                      3. Stay connected to the original question topic.
                                      4. Do NOT repeat the original question.
                                      5. Do NOT reuse the exact same numbers from the original question.
                                      6. Do NOT repeat any previous scaffold question.
                                      7. Options must be short math values, fractions, numbers, expressions, or short steps.
                                      8. Do NOT make all options wrong.
                                      9. Exactly ONE option must be correct.
                                      10. The other three options must be plausible but clearly incorrect.
                                      11. correctAnswer must match the correct option letter.
                                      12. correctValue must match the correct option text exactly.
                                      13. Before returning, silently solve the question and verify the answer.
                                      14. Return ONLY valid JSON.
                                      15. Do not include markdown.
                                      16. Do not explain outside JSON.
                            
                                      If diagnostic focus is concept:
                                      Ask a math-based concept check, not a definition.
                                      Example:
                                      "Which fraction is greater than 1?"
                                      Options: "3/2", "1/3", "2/5", "4/7"
                            
                                      If diagnostic focus is procedure:
                                      Ask for the correct next step.
                            
                                      If diagnostic focus is calculation:
                                      Ask for a smaller calculation.
                            
                                      If diagnostic focus is application:
                                      Ask a simpler version of the original problem.
                            
                                      Return exactly this JSON:
                            
                                      {
                                        "questionText": "...",
                                        "optionA": "...",
                                        "optionB": "...",
                                        "optionC": "...",
                                        "optionD": "...",
                                        "correctAnswer": "A",
                                        "correctValue": "...",
                                        "explanation": "...",
                                        "diagnosedMisconception": "...",
                                        "diagnosticFocus": "...",
                                        "suspectedWeakness": "...",
                                        "confidence": "medium",
                                        "diagnosticStep": 1,
                                        "prerequisiteTested": "...",
                                        "failureMeaning": "..."
                                      }
                            
                                      Original Question:
                                      %s
                            
                                      Topic:
                                      %s
                            
                                      Skill Tag:
                                      %s
                            
                                      Difficulty:
                                      %d
                            
                                      Diagnostic Plan:
                                      %s
                            
                                      Current Diagnostic Step:
                                      %d
                            
                                      Previous Diagnostic Question:
                                      %s
                """.formatted(
                    questionText,
                    topic,
                    skillTag,
                    difficulty,
                    diagnosticPlanJson,
                    diagnosticStep,
                    previousDiagnosticQuestion
            );

            String content = callGroq(prompt);
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
            result.put("diagnosedMisconception", json.path("diagnosedMisconception").asText());
            result.put("diagnosticFocus", json.path("diagnosticFocus").asText());
            result.put("suspectedWeakness", json.path("suspectedWeakness").asText());
            result.put("confidence", json.path("confidence").asText());
            result.put("diagnosticStep", json.path("diagnosticStep").asInt(diagnosticStep));
            result.put("prerequisiteTested", json.path("prerequisiteTested").asText());
            result.put("failureMeaning", json.path("failureMeaning").asText());

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Diagnostic question failed: " + e.getMessage());
            return fallbackDiagnosticQuestion(diagnosticStep);
        }
    }

    private String callGroq(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", 0.2);
        body.put("max_tokens", 700);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        System.out.println("ACTUAL GROQ URL = [" + apiUrl + "]");
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        JsonNode root = objectMapper.readTree(response.getBody());

        return root.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }

    private String cleanJson(String content) {
        return content
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private String fallbackDiagnosticPlan() {
        return """
                {
                  "steps": [
                    {
                      "stepNumber": 1,
                      "focus": "concept",
                      "prerequisite": "Understand the basic concept needed for the question",
                      "diagnosticPurpose": "Check whether the student understands the meaning of the concept",
                      "failureMeaning": "The student may have a concept gap"
                    },
                    {
                      "stepNumber": 2,
                      "focus": "procedure",
                      "prerequisite": "Know the correct method or steps",
                      "diagnosticPurpose": "Check whether the student knows the procedure",
                      "failureMeaning": "The student may have procedural confusion"
                    },
                    {
                      "stepNumber": 3,
                      "focus": "calculation",
                      "prerequisite": "Carry out the calculation correctly",
                      "diagnosticPurpose": "Check calculation accuracy",
                      "failureMeaning": "The student may have a calculation gap"
                    },
                    {
                      "stepNumber": 4,
                      "focus": "application",
                      "prerequisite": "Apply the concept to the full problem",
                      "diagnosticPurpose": "Check whether the student can apply the idea",
                      "failureMeaning": "The student may struggle with application"
                    }
                  ]
                }
                """;
    }

    private Map<String, Object> fallbackDiagnosticQuestion(int diagnosticStep) {
        Map<String, Object> fallback = new HashMap<>();

        fallback.put("questionText", "What should we do first when solving a new question?");
        fallback.put("optionA", "Read carefully and understand what is asked");
        fallback.put("optionB", "Guess quickly");
        fallback.put("optionC", "Skip the question");
        fallback.put("optionD", "Choose the longest option");
        fallback.put("correctAnswer", "A");
        fallback.put("correctValue", "Read carefully and understand what is asked");
        fallback.put("explanation", "Understanding the question is the first step to solving it.");
        fallback.put("diagnosedMisconception", "Student may be rushing without understanding the problem.");
        fallback.put("diagnosticFocus", "foundational understanding");
        fallback.put("suspectedWeakness", "problem understanding");
        fallback.put("confidence", "low");
        fallback.put("diagnosticStep", diagnosticStep);
        fallback.put("prerequisiteTested", "Understanding the question");
        fallback.put("failureMeaning", "The student may struggle to understand what the question is asking.");

        return fallback;
    }
}