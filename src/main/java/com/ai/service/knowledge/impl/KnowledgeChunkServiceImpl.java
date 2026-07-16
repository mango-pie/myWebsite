package com.ai.service.knowledge.impl;

import com.ai.model.vo.knowledge.KnowledgeTextChunk;
import com.ai.service.knowledge.KnowledgeChunkService;
import com.ai.setting.runtime.KnowledgeRuntimeSettings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private final KnowledgeRuntimeSettings knowledgeRuntimeSettings;

    public KnowledgeChunkServiceImpl(KnowledgeRuntimeSettings knowledgeRuntimeSettings) {
        this.knowledgeRuntimeSettings = knowledgeRuntimeSettings;
    }

    @Override
    public List<KnowledgeTextChunk> split(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int chunkSize = Math.max(200, knowledgeRuntimeSettings.ragChunkSize());
        int overlap = Math.max(0, Math.min(knowledgeRuntimeSettings.ragChunkOverlap(), chunkSize - 1));

        List<String> blocks = splitByStructure(normalized);
        List<KnowledgeTextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String heading = "";
        for (String block : blocks) {
            if (block.startsWith("#")) {
                heading = firstHeadingLine(block);
            }
            if (current.length() + block.length() > chunkSize && current.length() > 0) {
                addChunk(chunks, current.toString(), heading);
                String carried = overlapPrefix(current.toString(), overlap);
                current.setLength(0);
                if (!carried.isEmpty()) {
                    current.append(carried);
                }
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(block);
        }
        if (current.length() > 0) {
            addChunk(chunks, current.toString(), heading);
        }
        return chunks;
    }

    /**
     * 按空行分块，但不会拆开 fenced code block（``` ... ```），避免入库后 Markdown 结构错乱。
     */
    private List<String> splitByStructure(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
                continue;
            }
            if (inFence) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
                continue;
            }
            if (trimmed.isEmpty()) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        if (blocks.isEmpty()) {
            blocks.add(text);
        }
        return blocks;
    }

    private String overlapPrefix(String content, int overlap) {
        if (overlap <= 0 || content == null || content.isEmpty()) {
            return "";
        }
        String stripped = content.strip();
        if (stripped.length() <= overlap) {
            return stripped;
        }
        return stripped.substring(stripped.length() - overlap);
    }

    private String firstHeadingLine(String block) {
        String firstLine = block.split("\n", 2)[0];
        return firstLine.replaceFirst("^#+\\s*", "").trim();
    }

    private void addChunk(List<KnowledgeTextChunk> chunks, String content, String heading) {
        int index = chunks.size();
        String trimmed = content.strip();
        chunks.add(new KnowledgeTextChunk(index, heading, trimmed, estimateTokens(trimmed), "{}"));
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 2);
    }
}
