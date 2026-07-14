package com.corpusai.quiz;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.model.ModelFactory;
import com.corpusai.model.ModelProvider;
import com.corpusai.quiz.dto.QuizQuestionResponse;
import com.corpusai.quiz.dto.QuizResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

    public QuizService(EmbeddingModel embeddingModel,
                       PgVectorEmbeddingStore embeddingStore,
                       ModelFactory modelFactory,
                       SubjectService subjectService,
                       SubjectAccessService subjectAccessService,
                       QuizRepository quizRepository,
                       QuizQuestionRepository quizQuestionRepository) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.modelFactory = modelFactory;
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
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

        var generator = AiServices.builder(QuizGenerator.class)
                .chatModel(modelFactory.chatModel(provider, modelFor(provider)))
                .build();

        Result<GeneratedQuiz> result = generator.generate(content, count, lang);
        List<GeneratedQuestion> generated = result.content().questions();
        generated.forEach(this::requireWellFormed);

        TokenUsage usage = result.tokenUsage();
        log.info("Generated {} question(s) - tokens in/out: {}/{}",
                generated.size(),
                usage != null ? usage.inputTokenCount() : null,
                usage != null ? usage.outputTokenCount() : null);

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

    private String modelFor(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-4.1";
            case ANTHROPIC -> "claude-haiku-4-5";
        };
    }
}
