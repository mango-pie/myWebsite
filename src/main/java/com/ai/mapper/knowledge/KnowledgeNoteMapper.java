package com.ai.mapper.knowledge;

import com.ai.model.entity.knowledge.KnowledgeNote;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface KnowledgeNoteMapper extends BaseMapper<KnowledgeNote> {

    /**
     * 列表场景列裁剪：只取 VO 组装所需列，不含 distilled_md（LONGTEXT）等大字段。
     * 显式别名落实体字段，不依赖 mapUnderscoreToCamelCase 配置。
     */
    String NOTE_LIST_COLUMNS =
            "n.id AS id, n.source_document_id AS sourceDocumentId, n.blog_post_id AS blogPostId, "
            + "n.knowledge_document_id AS knowledgeDocumentId, n.title AS title, n.tags AS tags, n.status AS status, "
            + "n.publish_status AS publishStatus, n.index_status AS indexStatus, "
            + "n.create_time AS createTime, n.update_time AS updateTime, n.last_edited_at AS lastEditedAt, "
            + "n.last_published_at AS lastPublishedAt, n.last_indexed_at AS lastIndexedAt";

    /**
     * 笔记列表/搜索共用 FROM+WHERE。自定义 SQL 不经过 MyBatis-Flex，逻辑删除需显式 is_delete = 0；
     * INNER JOIN 同时复刻原 Java 端「来源已删除/缺失即跳过」行为。
     * keywordLike 由调用方完成 LIKE 转义并包裹 %；utf8mb4 *_ci 排序规则下 LIKE / = 天然不区分大小写，
     * 与原 Java 端 toLowerCase().contains / equalsIgnoreCase 语义一致。
     */
    String SEARCH_FROM_WHERE =
            "FROM knowledge_note n "
            + "INNER JOIN source_document sd ON sd.id = n.source_document_id AND sd.is_delete = 0 "
            + "WHERE n.is_delete = 0 AND sd.user_id = #{userId} "
            + "<if test='publishStatus != null and publishStatus != \"\"'>AND n.publish_status = #{publishStatus} </if>"
            + "<if test='indexStatus != null and indexStatus != \"\"'>AND n.index_status = #{indexStatus} </if>"
            + "<if test='sourceType != null and sourceType != \"\"'>AND sd.source_type = #{sourceType} </if>"
            + "<if test='keywordLike != null and keywordLike != \"\"'>"
            + "AND (n.title LIKE #{keywordLike} OR n.tags LIKE #{keywordLike} OR sd.source_url LIKE #{keywordLike}) "
            + "</if>";

    @Select("<script>SELECT " + NOTE_LIST_COLUMNS + " " + SEARCH_FROM_WHERE
            + "ORDER BY n.update_time DESC LIMIT #{offset}, #{limit}</script>")
    List<KnowledgeNote> selectSearchPage(@Param("userId") Long userId,
                                         @Param("keywordLike") String keywordLike,
                                         @Param("sourceType") String sourceType,
                                         @Param("publishStatus") String publishStatus,
                                         @Param("indexStatus") String indexStatus,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) " + SEARCH_FROM_WHERE + "</script>")
    long countSearch(@Param("userId") Long userId,
                     @Param("keywordLike") String keywordLike,
                     @Param("sourceType") String sourceType,
                     @Param("publishStatus") String publishStatus,
                     @Param("indexStatus") String indexStatus);
}
