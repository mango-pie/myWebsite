package com.ai.service.knowledge.impl;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.knowledge.KnowledgeDocumentReaderService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class KnowledgeDocumentReaderServiceImpl implements KnowledgeDocumentReaderService {

    @Override
    public String read(byte[] content, String fileType) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档内容为空");
        }
        String type = fileType == null ? "" : fileType.toLowerCase();
        try {
            return switch (type) {
                case "txt", "md", "markdown" -> new String(content, StandardCharsets.UTF_8);
                case "pdf" -> readPdf(content);
                case "docx" -> readDocx(content);
                default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文档类型");
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析文档失败：" + e.getMessage());
        }
    }

    private String readPdf(byte[] content) throws Exception {
        try (var document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String readDocx(byte[] content) throws Exception {
        try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
        }
    }
}
