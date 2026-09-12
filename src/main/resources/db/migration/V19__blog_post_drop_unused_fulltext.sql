-- 冗余索引清理：删除 blog_post.idx_search（FULLTEXT title,summary）。
-- 该索引自 V1 建表以来从未被任何查询使用（博客搜索为 LIKE '%..%'，无法走 FULLTEXT），
-- 且未带 ngram 分词器，对中文内容基本无法命中；每次写帖都在白付 FTS 维护成本。
-- 如后续要启用中文全文检索，需重建为 WITH PARSER ngram 并改写查询为 MATCH...AGAINST，
-- 语义为分词短语匹配（非子串包含），另立迁移。
-- Idempotent: guarded by information_schema.

DELIMITER $$

DROP PROCEDURE IF EXISTS _blog_idx_cleanup $$
CREATE PROCEDURE _blog_idx_cleanup()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'blog_post'
                 AND index_name = 'idx_search') THEN
        ALTER TABLE blog_post DROP INDEX idx_search;
    END IF;
END $$

DELIMITER ;

CALL _blog_idx_cleanup();
DROP PROCEDURE IF EXISTS _blog_idx_cleanup;
