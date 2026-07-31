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
                    
                    A student has answered a mathematics multiple-choice question incorrectly.
                    
                    Your task is to form a DIAGNOSTIC HYPOTHESIS about why the student may have selected that particular wrong option and to create a two-stage diagnostic plan for testing that hypothesis.
                    
                    IMPORTANT:
                    The student's selected option provides evidence about their thinking, but it does not prove a misconception.
                    Do not present an inferred misconception as certain unless the response strongly supports it.
                    
                    Do NOT generate diagnostic questions yet.
                    
                    ==================================================
                    STEP 1 — ANALYZE THE STUDENT'S RESPONSE
                    ==================================================
                    
                    Carefully analyze:
                    
                    • The original question
                    • All answer options
                    • The correct option
                    • The mathematical value represented by the correct option
                    • The student's selected option
                    • The mathematical value represented by the student's selected option
                    • The topic
                    • The skill being assessed
                    • The question difficulty
                    
                    First, solve the original problem independently and verify the supplied correct answer.
                    
                    Then compare the student's selected answer with the correct solution.
                    
                    Determine what mathematical process, misconception, procedural error, or prerequisite gap could reasonably produce the student's selected answer.
                    
                    Focus specifically on WHY the student may have selected THIS wrong option rather than another option.
                    
                    Possible causes may include, but are not limited to:
                    
                    • adding numerators and denominators directly
                    • confusing numerator and denominator
                    • misunderstanding equivalent fractions
                    • applying the wrong mathematical operation
                    • incorrect order of operations
                    • place-value misunderstanding
                    • borrowing or carrying errors
                    • multiplication/division confusion
                    • unit-conversion errors
                    • calculation mistakes
                    • missing prerequisite knowledge
                    
                    Do NOT use vague diagnoses such as:
                    
                    • "The student does not understand the concept"
                    • "The student is confused"
                    • "The student needs more practice"
                    • "The student answered incorrectly"
                    
                    Identify the specific mathematical reasoning or prerequisite that should be tested.
                    
                    If the selected answer strongly corresponds to a known error pattern, identify that pattern.
                    
                    If multiple explanations are plausible, select the most plausible hypothesis based on the numerical or conceptual relationship between the student's answer and the correct answer.
                    
                    If there is insufficient evidence to infer a specific misconception, explicitly state that the misconception is uncertain and lower the confidence level.
                    
                    ==================================================
                    STEP 2 — BUILD A TWO-STAGE DIAGNOSTIC PATH
                    ==================================================
                    
                    Create exactly TWO diagnostic stages.
                    
                    The purpose of these stages is to TEST the hypothesis from Step 1.
                    
                    ------------------------------
                    STAGE 1 — Immediate Prerequisite
                    ------------------------------
                    
                    Identify the most immediate prerequisite skill or reasoning step required to solve the original question correctly.
                    
                    The diagnostic purpose should test whether the student's suspected misconception actually exists.
                    
                    Do not simply make the original question easier.
                    
                    Do not repeat the original question.
                    
                    ------------------------------
                    STAGE 2 — Deeper Foundation
                    ------------------------------
                    
                    This stage is used only if the student fails Stage 1.
                    
                    Identify a deeper foundational skill that Stage 1 depends upon.
                    
                    Stage 2 must test a different and more fundamental level of understanding than Stage 1.
                    
                    Do not repeat Stage 1.
                    
                    ==================================================
                    DIAGNOSTIC PRINCIPLES
                    ==================================================
                    
                    • Treat the misconception as a hypothesis to be tested, not a confirmed diagnosis.
                    • Base the hypothesis on the student's specific selected answer.
                    • Prefer mathematical evidence over generic assumptions.
                    • Each diagnostic stage must test ONE clearly defined prerequisite.
                    • Stage 2 must be more foundational than Stage 1.
                    • Do not generate diagnostic questions yet.
                    • Do not provide teaching, hints, remediation, or explanations to the student.
                    • Do not ask for mathematical definitions.
                    • Do not repeat the original problem.
                    • Do not introduce concepts unrelated to the original skill.
                    • Keep the diagnostic path appropriate for the topic and difficulty level.
                    
                    ==================================================
                    CONFIDENCE
                    ==================================================
                    
                    Set confidence according to the available evidence:
                    
                    HIGH:
                    The selected wrong option strongly corresponds to a recognizable mathematical misconception or error pattern.
                    
                    MEDIUM:
                    The selected option reasonably suggests a misconception, but multiple explanations remain possible.
                    
                    LOW:
                    The student's selected answer does not provide enough evidence to reliably infer a specific misconception.
                    
                    ==================================================
                    OUTPUT RULES
                    ==================================================
                    
                    Return ONLY valid JSON.
                    
                    Do not use markdown.
                    Do not use code fences.
                    Do not include text before or after the JSON.
                    Do not include comments inside the JSON.
                    Use exactly TWO diagnostic stages.
                  
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
                    
                     A student has answered a mathematics question incorrectly, and a diagnostic hypothesis has already been generated.
                    
                     Your task is to generate exactly ONE diagnostic multiple-choice question that tests whether the suspected prerequisite gap or misconception actually exists.
                    
                     Your goal is DIAGNOSIS, not teaching.
                    
                     Do NOT explain the original problem to the student.
                     Do NOT provide hints.
                     Do NOT simply generate an easier version of the original question.
                    
                     ==================================================
                     ORIGINAL ASSESSMENT CONTEXT
                     ==================================================
                    
                     Original Question:
                     %s
                    
                     Option A:
                     %s
                    
                     Option B:
                     %s
                    
                     Option C:
                     %s
                    
                     Option D:
                     %s
                    
                     Correct Answer:
                     %s
                    
                     Correct Value:
                     %s
                    
                     Student Selected Answer:
                     %s
                    
                     Student Selected Value:
                     %s
                    
                     Topic:
                     %s
                    
                     Skill Tag:
                     %s
                    
                     Original Difficulty:
                     %d
                    
                     ==================================================
                     DIAGNOSTIC ANALYSIS
                     ==================================================
                    
                     %s
                    
                     ==================================================
                     CURRENT DIAGNOSTIC STATE
                     ==================================================
                    
                     Current Diagnostic Step:
                     %d
                    
                     Previous Diagnostic Question:
                     %s
                    
                     ==================================================
                     YOUR TASK
                     ==================================================
                    
                     Generate ONE multiple-choice diagnostic question.
                    
                     The question must test ONLY the prerequisite identified for the CURRENT diagnostic step in the diagnostic analysis.
                    
                     The purpose of the question is to gather evidence about the suspected misconception.
                    
                     Do not assume the misconception is confirmed.
                    
                     ==================================================
                     DIAGNOSTIC STEP RULES
                     ==================================================
                    
                     IF CURRENT DIAGNOSTIC STEP = 1:
                    
                     Test the IMMEDIATE prerequisite identified in Stage 1 of the diagnostic analysis.
                    
                     The question should determine whether the student understands the specific mathematical reasoning required immediately before solving the original problem.
                    
                     Do NOT test a deeper foundational concept yet.
                    
                    
                     IF CURRENT DIAGNOSTIC STEP = 2:
                    
                     The student has already failed Stage 1.
                    
                     Test the DEEPER foundational prerequisite identified in Stage 2 of the diagnostic analysis.
                    
                     The question must test a more fundamental mathematical concept than Stage 1.
                    
                     Do NOT repeat Stage 1.
                    
                     Do NOT generate a question that tests essentially the same reasoning using different numbers.
                    
                     Use the previous diagnostic question to ensure that Stage 2 investigates a genuinely deeper prerequisite.
                    
                     ==================================================
                     QUESTION CONSTRUCTION RULES
                     ==================================================
                    
                     • Generate exactly ONE question.
                     • Generate exactly FOUR answer options: A, B, C and D.
                     • Exactly ONE option must be mathematically correct.
                     • Solve the question independently before assigning the correct answer.
                     • Ensure that correctAnswer matches correctValue exactly.
                     • Keep the mathematics appropriate for the student's current diagnostic level.
                     • Test ONE prerequisite only.
                     • Keep the wording clear and age-appropriate.
                     • The question must be answerable using the information provided.
                     • Avoid unnecessary reading complexity.
                    
                     ==================================================
                     DISTRACTOR DESIGN
                     ==================================================
                    
                     The three incorrect options must be plausible.
                    
                     Whenever possible, each incorrect option should correspond to a realistic mathematical error related to the prerequisite being tested.
                    
                     For example, distractors may represent:
                    
                     • the suspected misconception
                     • a common procedural mistake
                     • confusion between related concepts
                     • an arithmetic error
                     • incorrect application of the relevant operation
                    
                     Do NOT use obviously meaningless distractors.
                    
                     Do NOT make the correct answer obvious because it is significantly longer, more detailed, or structurally different from the other options.
                    
                     ==================================================
                     DO NOT
                     ==================================================
                    
                     • Do NOT repeat the original question.
                     • Do NOT merely replace the numbers in the original question.
                     • Do NOT reuse the same numbers from the original question where avoidable.
                     • Do NOT repeat the previous diagnostic question.
                     • Do NOT ask for definitions.
                     • Do NOT ask "What should you do first?"
                     • Do NOT ask generic questions about problem-solving strategy.
                     • Do NOT teach the student how to solve the original problem.
                     • Do NOT provide hints inside the question.
                     • Do NOT test multiple mathematical skills simultaneously.
                     • Do NOT introduce an unrelated topic.
                     • Do NOT claim that the misconception has been proven.
                     • Do NOT generate more than one question.
                    
                     ==================================================
                     DIAGNOSTIC METADATA
                     ==================================================
                    
                     After generating the question, provide metadata describing what the question tests.
                    
                     "diagnosedMisconception":
                     Use the suspected misconception from the diagnostic analysis.
                     Treat it as a hypothesis, not a confirmed diagnosis.
                    
                     "diagnosticFocus":
                     State the specific mathematical concept being tested.
                    
                     "suspectedWeakness":
                     State the learner weakness that this question is investigating.
                    
                     "confidence":
                     Use the confidence level from the diagnostic analysis unless the generated diagnostic context provides a strong reason to lower it.
                    
                     "diagnosticStep":
                     Must equal the Current Diagnostic Step supplied above.
                    
                     "prerequisiteTested":
                     State exactly ONE prerequisite tested by this question.
                    
                     "failureMeaning":
                     Explain what an incorrect response to THIS diagnostic question would suggest.
                     Do not state that failure proves the misconception.
                     Use language such as "may indicate", "suggests", or "provides evidence of".
                    
                     "explanation":
                     Briefly explain why the correct answer is mathematically correct.
                     This explanation is for system/report use and should not contain unnecessary teaching content.
                    
                     ==================================================
                     FINAL VALIDATION
                     ==================================================
                    
                     Before returning the response, silently verify:
                    
                     1. Is there exactly one question?
                     2. Are there exactly four options?
                     3. Is exactly one option correct?
                     4. Did I solve the question correctly?
                     5. Does correctAnswer correspond to correctValue?
                     6. Does the question test only the prerequisite for the current diagnostic step?
                     7. If this is Stage 2, is it genuinely more foundational than Stage 1?
                     8. Is it substantially different from the original question?
                     9. Is it substantially different from the previous diagnostic question?
                     10. Are the distractors plausible mathematical errors?
                     11. Is the question diagnostic rather than instructional?
                    
                     If any condition fails, correct the question before returning it.
                    
                     ==================================================
                     OUTPUT FORMAT
                     ==================================================
                    
                     Return ONLY valid JSON.
                    
                     Do not use markdown.
                     Do not use code fences.
                     Do not include text before or after the JSON.
                     Do not include comments.
                    
                     Return exactly this structure:
                    
                     {
                       "questionText": "",
                       "optionA": "",
                       "optionB": "",
                       "optionC": "",
                       "optionD": "",
                       "correctAnswer": "A",
                       "correctValue": "",
                       "explanation": "",
                       "diagnosedMisconception": "",
                       "diagnosticFocus": "",
                       "suspectedWeakness": "",
                       "confidence": "high",
                       "diagnosticStep": 1,
                       "prerequisiteTested": "",
                       "failureMeaning": ""
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
                        You are an expert mathematics teacher, educational diagnostic specialist,
                       and diagnostic assessment engine.
                       Your role is to analyze mathematical responses and assist with
                       evidence-based diagnostic assessment.
                        
                       Follow the user's instructions exactly.
                        
                       Always return valid JSON.
                        
                       Never wrap JSON inside markdown or code fences.
                        
                       Never include text, explanations, comments, or apologies outside the JSON.
                        
                       Ensure all mathematical content is correct before returning a response.
                        
                       Do not invent student misconceptions when the available evidence
                       does not support them.
                        
                       Treat inferred misconceptions as diagnostic hypotheses rather than
                       confirmed facts.
                        
                       If the evidence is insufficient to identify a specific misconception,
                       explicitly represent the uncertainty and use an appropriate low
                       confidence level.
                        
                       When generating diagnostic questions, test only the prerequisite
                       specified in the diagnostic plan.
                        
                       Diagnostic questions must gather evidence about the learner's
                       understanding rather than teach, hint at, or reveal the solution.
                        
                       Never assume that an incorrect response alone proves a misconception.
                        
                       Always produce output that conforms exactly to the JSON structure requested by the user message.
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