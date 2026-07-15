package com.corpusai.quiz;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.metrics.LlmFeature;
import com.corpusai.metrics.UsageRecorder;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.quiz.dto.QuizDetailResponse;
import com.corpusai.quiz.dto.QuizQuestionResponse;
import com.corpusai.quiz.dto.QuizResponse;
import com.corpusai.quiz.dto.QuizSubmissionRequest;
import com.corpusai.quiz.dto.QuizSubmissionResponse;
import com.corpusai.quiz.dto.QuizSummaryResponse;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuizService {

    private static final int CHUNKS_PER_QUESTION = 2;
    private static final int MAX_RETRIEVAL_CHUNKS = 20;
    private static final String BROAD_QUERY = "main topics key concepts overview";
    private static final int OPTIONS_PER_QUESTION = 4;

    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final ModelFactory modelFactory;
    private final SubjectService subjectService;
    private final SubjectAccessService subjectAccessService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UsageRecorder usageRecorder;

    public QuizService(EmbeddingModel embeddingModel,
                       PgVectorEmbeddingStore embeddingStore,
                       ModelFactory modelFactory,
                       SubjectService subjectService,
                       SubjectAccessService subjectAccessService,
                       QuizRepository quizRepository,
                       QuizQuestionRepository quizQuestionRepository,
                       UsageRecorder usageRecorder) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.usageRecorder = usageRecorder;
    }

    @Transactional
    public QuizResponse generate(AuthenticatedUser principal, String subjectId, String topic,
                                 int count, String lang, ModelProvider provider) {
        subjectService.findById(subjectId);
        subjectAccessService.checkAccess(principal, subjectId);

        log.info("Quiz request - subject: '{}', topic: '{}', count: {}, lang: {}, provider: {}",
                subjectId, topic, count, lang, provider);

        int chunkCount = Math.min(count * CHUNKS_PER_QUESTION, MAX_RETRIEVAL_CHUNKS);

        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(chunkCount)
                .filter(new IsEqualTo("subject_id", subjectId))
                .build();

        String query = (topic != null && !topic.isBlank()) ? topic : BROAD_QUERY;

        List<Content> chunks = retriever.retrieve(Query.from(query));
        String content = chunks.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        log.info("Generating {} question(s) from {} chunk(s)", count, chunks.size());

        String model = modelFor(provider);
        var generator = AiServices.builder(QuizGenerator.class)
                .chatModel(modelFactory.chatModel(provider, model))
                .build();

        Instant startedAt = Instant.now();
        Result<GeneratedQuiz> result = generator.generate(content, count, lang);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        List<GeneratedQuestion> generated = result.content().questions();

        TokenUsage usage = result.tokenUsage();
        log.info("Generated {} question(s) - tokens in/out: {}/{}",
                generated.size(),
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null);

        // Recorded before validation: the LLM call already succeeded and cost real tokens by this
        // point, regardless of whether the response turns out well-formed below.
        usageRecorder.record(LlmFeature.QUIZ, provider, model, usage, latencyMs,
                principal.id(), subjectId, null);

        generated.forEach(this::requireWellFormed);

        Quiz quiz = new Quiz(principal.id(), subjectId, topic, lang, provider, generated.size());
        quizRepository.save(quiz);

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            GeneratedQuestion g = generated.get(i);
            questions.add(new QuizQuestion(quiz.getId(), g.question(), g.options(), g.correctIndex(), g.explanation(), i));
        }
        quizQuestionRepository.saveAll(questions);

        return toQuizResponse(quiz, questions);
    }

    @Transactional
    public QuizSubmissionResponse submit(AuthenticatedUser principal, UUID quizId, QuizSubmissionRequest request) {
        Quiz quiz = resolveOwnedQuiz(principal, quizId);
        if (quiz.isCompleted()) {
            throw new QuizAlreadyCompletedException("Quiz already completed: " + quizId);
        }

        List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizIdOrderByPositionAsc(quiz.getId());
        Map<UUID, Integer> answers = toAnswerMap(request, questions);

        int score = 0;
        List<QuizSubmissionResponse.AnswerResult> results = new ArrayList<>();
        for (QuizQuestion question : questions) {
            Integer selected = answers.get(question.getId());
            if (selected != null) {
                question.recordAnswer(selected);
            }
            boolean correct = isCorrect(question);
            if (correct) {
                score++;
            }
            results.add(new QuizSubmissionResponse.AnswerResult(
                    question.getId(), correct, question.getCorrectIndex(), question.getExplanation()));
        }
        quiz.complete(score);

        log.info("Quiz {} submitted - score {}/{}", quizId, score, questions.size());
        return new QuizSubmissionResponse(score, questions.size(), results);
    }

    public List<QuizSummaryResponse> listQuizzes(AuthenticatedUser principal, String subjectId) {
        subjectService.findById(subjectId);
        return quizRepository.findAllByUserIdAndSubjectIdOrderByCreatedAtDesc(principal.id(), subjectId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public QuizDetailResponse getQuiz(AuthenticatedUser principal, UUID quizId) {
        Quiz quiz = resolveOwnedQuiz(principal, quizId);
        List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizIdOrderByPositionAsc(quiz.getId());
        return toDetailResponse(quiz, questions);
    }

    public void deleteQuiz(AuthenticatedUser principal, UUID quizId) {
        Quiz quiz = resolveOwnedQuiz(principal, quizId);
        quizRepository.delete(quiz);
    }

    private Quiz resolveOwnedQuiz(AuthenticatedUser principal, UUID quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizNotFoundException("Unknown quiz: " + quizId));
        if (!quiz.getUserId().equals(principal.id())) {
            throw new AccessDeniedException("You do not have access to this quiz: " + quizId);
        }
        return quiz;
    }

    // Partial submits are allowed (an unanswered question just counts as wrong), but duplicate
    // answers and answers for another quiz's questions signal a broken client and are rejected.
    private Map<UUID, Integer> toAnswerMap(QuizSubmissionRequest request, List<QuizQuestion> questions) {
        Set<UUID> questionIds = questions.stream().map(QuizQuestion::getId).collect(Collectors.toSet());
        Map<UUID, Integer> answers = new HashMap<>();
        for (QuizSubmissionRequest.AnswerSubmission answer : request.answers()) {
            if (!questionIds.contains(answer.questionId())) {
                throw new IllegalArgumentException("Question does not belong to this quiz: " + answer.questionId());
            }
            if (answers.putIfAbsent(answer.questionId(), answer.selectedIndex()) != null) {
                throw new IllegalArgumentException("Duplicate answer for question: " + answer.questionId());
            }
        }
        return answers;
    }

    private boolean isCorrect(QuizQuestion question) {
        return question.getSelectedIndex() != null && question.getSelectedIndex() == question.getCorrectIndex();
    }

    // The DB CHECK can bound correct_index, but it cannot see inside the JSONB options array —
    // a malformed model response must fail loudly here, before grading arithmetic depends on it.
    private void requireWellFormed(GeneratedQuestion question) {
        if (question.options() == null || question.options().size() != OPTIONS_PER_QUESTION) {
            throw new IllegalStateException(
                    "Model returned a question without exactly " + OPTIONS_PER_QUESTION + " options: " + question.question());
        }
        if (question.correctIndex() < 0 || question.correctIndex() >= OPTIONS_PER_QUESTION) {
            throw new IllegalStateException(
                    "Model returned an out-of-range correctIndex (" + question.correctIndex() + "): " + question.question());
        }
    }

    private QuizResponse toQuizResponse(Quiz quiz, List<QuizQuestion> questions) {
        List<QuizQuestionResponse> questionResponses = questions.stream()
                .map(q -> new QuizQuestionResponse(q.getId(), q.getQuestion(), q.getOptions()))
                .toList();
        return new QuizResponse(quiz.getId(), quiz.getSubjectId(), quiz.getTopic(),
                quiz.getLang(), quiz.getProvider(), quiz.getCreatedAt(), questionResponses);
    }

    private QuizSummaryResponse toSummaryResponse(Quiz quiz) {
        return new QuizSummaryResponse(quiz.getId(), quiz.getSubjectId(), quiz.getTopic(), quiz.getLang(),
                quiz.getProvider(), quiz.getQuestionCount(), quiz.getScore(), quiz.getCompletedAt(),
                quiz.getCreatedAt());
    }

    // The single gate for the grading fields: they leave the service only for a completed quiz.
    private QuizDetailResponse toDetailResponse(Quiz quiz, List<QuizQuestion> questions) {
        boolean completed = quiz.isCompleted();
        List<QuizDetailResponse.QuestionDetail> questionDetails = questions.stream()
                .map(q -> new QuizDetailResponse.QuestionDetail(
                        q.getId(), q.getQuestion(), q.getOptions(),
                        completed ? q.getSelectedIndex() : null,
                        completed ? isCorrect(q) : null,
                        completed ? q.getCorrectIndex() : null,
                        completed ? q.getExplanation() : null))
                .toList();
        return new QuizDetailResponse(quiz.getId(), quiz.getSubjectId(), quiz.getTopic(), quiz.getLang(),
                quiz.getProvider(), quiz.getQuestionCount(), quiz.getScore(), quiz.getCompletedAt(),
                quiz.getCreatedAt(), questionDetails);
    }

    private String modelFor(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-4.1";
            case ANTHROPIC -> "claude-haiku-4-5";
        };
    }
}
