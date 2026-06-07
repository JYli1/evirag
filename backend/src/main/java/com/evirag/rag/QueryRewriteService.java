package com.evirag.rag;

import com.evirag.llm.LlmClient;
import com.evirag.llm.LlmException;
import com.evirag.llm.LlmMessage;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Query rewrite 服务。
 *
 * <p>只有短问题、指代明显或依赖上文的问题才调用 LLM 改写；改写失败时回退原问题，避免用户提问链路被重写能力阻断。</p>
 */
@Service
public class QueryRewriteService {

    private final LlmClient llmClient;

    public QueryRewriteService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public RewriteResult rewrite(String question, List<RagHistoryMessage> historyMessages) {
        String normalizedQuestion = normalize(question);
        if (!shouldRewrite(normalizedQuestion, historyMessages)) {
            return new RewriteResult(question, normalizedQuestion, false);
        }

        try {
            String rewritten = normalize(llmClient.complete(List.of(
                    LlmMessage.system("你是检索问题改写器。请结合历史消息，把用户当前问题改写成一个独立、完整、适合知识库检索的问题。只输出改写后的问题，不要解释。"),
                    LlmMessage.user(prompt(normalizedQuestion, historyMessages))
            )));
            if (rewritten.isBlank()) {
                return new RewriteResult(question, normalizedQuestion, false);
            }
            return new RewriteResult(question, rewritten, !rewritten.equals(normalizedQuestion));
        } catch (LlmException ex) {
            return new RewriteResult(question, normalizedQuestion, false);
        }
    }

    private boolean shouldRewrite(String question, List<RagHistoryMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty() || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        return question.length() <= 20
                || lower.contains("它")
                || lower.contains("这个")
                || lower.contains("那个")
                || lower.contains("上述")
                || lower.contains("上面")
                || lower.contains("前面")
                || lower.contains("缺点")
                || lower.contains("优点");
    }

    private String prompt(String question, List<RagHistoryMessage> historyMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("历史消息：\n");
        for (RagHistoryMessage message : historyMessages) {
            builder.append(message.role()).append(": ").append(message.content()).append('\n');
        }
        builder.append("\n当前问题：").append(question);
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
