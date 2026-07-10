
package com.corpusai.chat;

import com.corpusai.auth.Role;
import com.corpusai.auth.User;
import com.corpusai.auth.UserRepository;
import com.corpusai.model.ModelProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChatMemoryStoreImplTest {

    @Autowired
    private ChatMemoryStoreImpl chatMemoryStore;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private UserRepository userRepository;

    private ChatSession createSession() {
        User user = userRepository.save(new User("student@example.com", "hash", "Student", Role.USER));
        return chatSessionRepository.save(
                new ChatSession(user.getId(), "softverski-proces", "sr", ModelProvider.OPENAI));
    }

    @Test
    void newSessionHasNoMessages() {
        ChatSession session = createSession();

        assertThat(chatMemoryStore.getMessages(session.getId())).isEmpty();
    }

    @Test
    void updateMessagesAppendsOnlyTheNewlyAddedMessage() {
        ChatSession session = createSession();

        chatMemoryStore.updateMessages(session.getId(), List.of(UserMessage.from("What is a design pattern?")));
        chatMemoryStore.updateMessages(session.getId(), List.of(
                UserMessage.from("What is a design pattern?"),
                AiMessage.from("A design pattern is a reusable solution to a recurring problem.")));

        List<ChatMessage> messages = chatMemoryStore.getMessages(session.getId());

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("What is a design pattern?");
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(1)).text())
                .isEqualTo("A design pattern is a reusable solution to a recurring problem.");
    }

    @Test
    void updateMessagesIgnoresSystemMessages() {
        ChatSession session = createSession();

        chatMemoryStore.updateMessages(session.getId(), List.of(SystemMessage.from("You are a tutor.")));

        assertThat(chatMemoryStore.getMessages(session.getId())).isEmpty();
    }

    @Test
    void deleteMessagesRemovesAllRowsForSession() {
        ChatSession session = createSession();
        chatMemoryStore.updateMessages(session.getId(), List.of(UserMessage.from("hi")));

        chatMemoryStore.deleteMessages(session.getId());

        assertThat(chatMemoryStore.getMessages(session.getId())).isEmpty();
    }
}
