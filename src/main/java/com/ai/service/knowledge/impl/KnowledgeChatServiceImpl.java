package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeConversationMapper;
import com.ai.ops.AiUsageCallContext;
import com.ai.mapper.knowledge.KnowledgeMessageMapper;
import com.ai.mapper.knowledge.KnowledgeMessageReferenceMapper;
import com.ai.model.dto.knowledge.KnowledgeChatRequest;
import com.ai.model.entity.knowledge.KnowledgeConversation;
import com.ai.model.entity.knowledge.KnowledgeMessage;
import com.ai.model.entity.knowledge.KnowledgeMessageReference;
import com.ai.model.vo.knowledge.KnowledgeChatResponse;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeConversationVO;
import com.ai.model.vo.knowledge.KnowledgeMessageVO;
import com.ai.model.vo.knowledge.KnowledgeReferenceVO;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeBaseService;
import com.ai.service.knowledge.KnowledgeChatService;
import com.ai.service.knowledge.KnowledgeRagService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ConditionalOnModule("knowledge")
@Service
public class KnowledgeChatServiceImpl implements KnowledgeChatService {

    private static final String EMPTY_ANSWER = "当前知识库中没有找到相关信息";
    private static final int HISTORY_LIMIT = 10;

    @Resource
    private KnowledgeConversationMapper conversationMapper;

    @Resource
    private KnowledgeMessageMapper messageMapper;

    @Resource
    private KnowledgeMessageReferenceMapper referenceMapper;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeRagService ragService;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource(name = "aiTaskExecutor")
    private Executor aiTaskExecutor;

    @Override
    public KnowledgeChatResponse chat(KnowledgeChatRequest request, Long userId) {
        KnowledgeConversation conversation = prepareConversation(request, userId);
        List<KnowledgeMessage> history = recentHistory(conversation.getId());
        KnowledgeMessage userMessage = saveMessage(conversation.getId(), userId, "USER", request.getQuestion(), null);
        touchConversation(conversation, request.getQuestion(), true);

        // RAG 检索（pgvector）与 AI 调用（HTTP）不得持有 MySQL 连接，
        // 仅助手消息+引用落库使用短事务保证原子性
        List<KnowledgeChunkVO> chunks = ragService.searchChunks(conversation.getKnowledgeBaseId(),
                request.getSourceDocumentId(), request.getQuestion(), request.getTopK());
        List<KnowledgeReferenceVO> references = toReferences(chunks);
        String answer;
        if (chunks.isEmpty()) {
            answer = EMPTY_ANSWER;
        } else {
            AiUsageCallContext.set(userId, AiUsageSceneConstant.KNOWLEDGE_CHAT, conversation.getId(),
                    summarize(request.getQuestion(), 64));
            try {
                answer = aiModelService.chat(buildPrompt(chunks, history, request.getQuestion()));
            } finally {
                AiUsageCallContext.clear();
            }
        }
        String finalAnswer = answer;
        KnowledgeMessage assistantMessage = transactionTemplate.execute(status -> {
            KnowledgeMessage message = saveMessage(conversation.getId(), userId, "ASSISTANT", finalAnswer,
                    aiModelService.getChatModelName());
            saveReferences(message.getId(), references);
            touchConversation(conversation, finalAnswer, false);
            return message;
        });

        KnowledgeChatResponse response = new KnowledgeChatResponse();
        response.setConversationId(conversation.getId());
        response.setUserMessageId(userMessage.getId());
        response.setAssistantMessageId(assistantMessage.getId());
        response.setAnswer(answer);
        response.setReferences(references);
        return response;
    }

    @Override
    public SseEmitter streamChat(KnowledgeChatRequest request, Long userId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        // 客户端超时后主动完成，避免推送线程持续向死连接 write
        emitter.onTimeout(emitter::complete);
        CompletableFuture.runAsync(() -> {
            try {
                KnowledgeConversation conversation = prepareConversation(request, userId);
                List<KnowledgeMessage> history = recentHistory(conversation.getId());
                saveMessage(conversation.getId(), userId, "USER", request.getQuestion(), null);
                touchConversation(conversation, request.getQuestion(), true);

                List<KnowledgeChunkVO> chunks = ragService.searchChunks(conversation.getKnowledgeBaseId(),
                        request.getSourceDocumentId(), request.getQuestion(), request.getTopK());
                List<KnowledgeReferenceVO> references = toReferences(chunks);
                StringBuilder answer = new StringBuilder();
                if (chunks.isEmpty()) {
                    answer.append(EMPTY_ANSWER);
                    send(emitter, "message", EMPTY_ANSWER);
                } else {
                    AiUsageCallContext.set(userId, AiUsageSceneConstant.KNOWLEDGE_CHAT, conversation.getId(),
                            summarize(request.getQuestion(), 64));
                    try {
                        aiModelService.streamChat(buildPrompt(chunks, history, request.getQuestion()), delta -> {
                            answer.append(delta);
                            send(emitter, "message", delta);
                        });
                    } finally {
                        AiUsageCallContext.clear();
                    }
                }
                KnowledgeMessage assistantMessage = saveMessage(conversation.getId(), userId, "ASSISTANT",
                        answer.toString(), aiModelService.getChatModelName());
                saveReferences(assistantMessage.getId(), references);
                touchConversation(conversation, answer.toString(), false);
                send(emitter, "done", "");
                emitter.complete();
            } catch (Exception e) {
                send(emitter, "error", e.getMessage());
                emitter.completeWithError(e);
            }
        }, aiTaskExecutor);
        return emitter;
    }

    @Override
    public List<KnowledgeConversationVO> listConversations(Long knowledgeBaseId, Long userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("knowledge_base_id", knowledgeBaseId, knowledgeBaseId != null)
                .orderBy("update_time", false);
        return conversationMapper.selectListByQuery(wrapper).stream().map(this::toConversationVO).toList();
    }

    @Override
    public Page<KnowledgeMessageVO> listMessages(Long conversationId, Long userId, int pageNum, int pageSize) {
        KnowledgeConversation conversation = requireOwnedConversation(conversationId, userId);
        Page<KnowledgeMessage> page = messageMapper.paginate(Page.of(pageNum, pageSize),
                QueryWrapper.create()
                        .eq("conversation_id", conversation.getId())
                        .orderBy("create_time", true)
                        .orderBy("id", true));
        List<KnowledgeMessageVO> records = page.getRecords().stream().map(this::toMessageVO).toList();
        Page<KnowledgeMessageVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public boolean deleteConversation(Long conversationId, Long userId) {
        requireOwnedConversation(conversationId, userId);
        return conversationMapper.deleteById(conversationId) > 0;
    }

    private KnowledgeConversation prepareConversation(KnowledgeChatRequest request, Long userId) {
        if (request == null || request.getKnowledgeBaseId() == null || StrUtil.isBlank(request.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "knowledgeBaseId 和 question 不能为空");
        }
        knowledgeBaseService.requireOwned(request.getKnowledgeBaseId(), userId);
        if (request.getConversationId() == null) {
            KnowledgeConversation conversation = new KnowledgeConversation();
            conversation.setUserId(userId);
            conversation.setKnowledgeBaseId(request.getKnowledgeBaseId());
            conversation.setTitle(summarize(request.getQuestion(), 20));
            conversation.setLastMessage(request.getQuestion());
            conversation.setCreateTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            conversation.setIsDelete(0);
            conversationMapper.insert(conversation);
            return conversation;
        }
        KnowledgeConversation conversation = requireOwnedConversation(request.getConversationId(), userId);
        if (!Objects.equals(conversation.getKnowledgeBaseId(), request.getKnowledgeBaseId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话与知识库不匹配");
        }
        return conversation;
    }

    private KnowledgeConversation requireOwnedConversation(Long conversationId, Long userId) {
        KnowledgeConversation conversation = conversationMapper.selectOneByQuery(QueryWrapper.create().eq("id", conversationId));
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库会话不存在");
        }
        return conversation;
    }

    private List<KnowledgeMessage> recentHistory(Long conversationId) {
        return messageMapper.selectListByQuery(QueryWrapper.create()
                .eq("conversation_id", conversationId)
                .orderBy("create_time", false)
                .limit(HISTORY_LIMIT))
                .stream()
                .sorted(java.util.Comparator.comparing(KnowledgeMessage::getCreateTime))
                .toList();
    }

    private KnowledgeMessage saveMessage(Long conversationId, Long userId, String role, String content, String modelName) {
        KnowledgeMessage message = new KnowledgeMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModelName(modelName);
        message.setCreateTime(LocalDateTime.now());
        message.setIsDelete(0);
        messageMapper.insert(message);
        return message;
    }

    private void saveReferences(Long messageId, List<KnowledgeReferenceVO> references) {
        for (KnowledgeReferenceVO reference : references) {
            KnowledgeMessageReference entity = new KnowledgeMessageReference();
            entity.setMessageId(messageId);
            entity.setKnowledgeDocumentId(reference.getKnowledgeDocumentId());
            entity.setSourceDocumentId(reference.getSourceDocumentId());
            entity.setChunkId(reference.getChunkId());
            entity.setChunkIndex(reference.getChunkIndex());
            entity.setDocumentName(reference.getDocumentName());
            entity.setContent(reference.getContent());
            entity.setSimilarity(reference.getSimilarity());
            entity.setCreateTime(LocalDateTime.now());
            entity.setIsDelete(0);
            referenceMapper.insert(entity);
        }
    }

    private List<KnowledgeReferenceVO> toReferences(List<KnowledgeChunkVO> chunks) {
        return chunks.stream().map(chunk -> {
            KnowledgeReferenceVO vo = new KnowledgeReferenceVO();
            vo.setChunkId(chunk.getId());
            vo.setKnowledgeDocumentId(chunk.getKnowledgeDocumentId());
            vo.setSourceDocumentId(chunk.getSourceDocumentId());
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setDocumentName(chunk.getHeading());
            vo.setContent(summarize(chunk.getContent(), 500));
            vo.setSimilarity(BigDecimal.valueOf(chunk.getScore() == null ? 0 : chunk.getScore())
                    .setScale(6, RoundingMode.HALF_UP));
            return vo;
        }).toList();
    }

    private void touchConversation(KnowledgeConversation conversation, String message, boolean maybeTitle) {
        KnowledgeConversation update = new KnowledgeConversation();
        update.setId(conversation.getId());
        if (maybeTitle && StrUtil.isBlank(conversation.getTitle())) {
            update.setTitle(summarize(message, 20));
        }
        update.setLastMessage(summarize(message, 512));
        update.setUpdateTime(LocalDateTime.now());
        conversationMapper.update(update);
    }

    private String buildPrompt(List<KnowledgeChunkVO> chunks, List<KnowledgeMessage> history, String question) {
        return """
                你是个人知识库问答助手。
                请只根据提供的知识库上下文回答用户问题。
                如果上下文没有答案，请说明“当前知识库中没有找到相关信息”，不要编造。

                知识库上下文：
                %s

                历史对话：
                %s

                用户问题：
                %s
                """.formatted(buildContext(chunks), buildHistory(history), question);
    }

    private String buildContext(List<KnowledgeChunkVO> chunks) {
        StringBuilder builder = new StringBuilder();
        for (KnowledgeChunkVO chunk : chunks) {
            builder.append("[来源：")
                    .append(StrUtil.blankToDefault(chunk.getHeading(), "文档片段"))
                    .append("，片段 ")
                    .append(chunk.getChunkIndex())
                    .append("]\n")
                    .append(chunk.getContent())
                    .append("\n\n");
        }
        return builder.isEmpty() ? "无" : builder.toString();
    }

    private String buildHistory(List<KnowledgeMessage> history) {
        if (history.isEmpty()) {
            return "无";
        }
        return history.stream()
                .map(message -> message.getRole() + "：" + summarize(message.getContent(), 300))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("无");
    }

    private KnowledgeConversationVO toConversationVO(KnowledgeConversation conversation) {
        KnowledgeConversationVO vo = new KnowledgeConversationVO();
        BeanUtil.copyProperties(conversation, vo);
        return vo;
    }

    private KnowledgeMessageVO toMessageVO(KnowledgeMessage message) {
        KnowledgeMessageVO vo = new KnowledgeMessageVO();
        BeanUtil.copyProperties(message, vo);
        List<KnowledgeReferenceVO> references = referenceMapper.selectListByQuery(QueryWrapper.create()
                .eq("message_id", message.getId()))
                .stream()
                .map(entity -> {
                    KnowledgeReferenceVO ref = new KnowledgeReferenceVO();
                    ref.setChunkId(entity.getChunkId());
                    ref.setKnowledgeDocumentId(entity.getKnowledgeDocumentId());
                    ref.setSourceDocumentId(entity.getSourceDocumentId());
                    ref.setChunkIndex(entity.getChunkIndex());
                    ref.setDocumentName(entity.getDocumentName());
                    ref.setContent(entity.getContent());
                    ref.setSimilarity(entity.getSimilarity());
                    return ref;
                }).toList();
        vo.setReferences(references);
        return vo;
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String summarize(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
