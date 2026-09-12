-- 冗余索引清理（第二批）：删除三个「被同表复合索引完全前缀覆盖」的单列索引。
-- 查询可无损改走复合索引，删除零回归风险：
--   chat_history.idx_appId       ⊂ idx_appId_createTime (appId, createTime)
--   blog_post_tag.idx_post_id    ⊂ uk_post_tag          (post_id, tag_id)
--   site_setting.idx_module      ⊂ uk_module_key        (module, setting_key)
-- 每个索引少一份写放大与统计维护成本。盘点依据见 docs/db-index-and-table-audit.md。
-- Idempotent: guarded by information_schema（MySQL 无 DROP INDEX IF EXISTS）。

DELIMITER $$

DROP PROCEDURE IF EXISTS _redundant_idx_cleanup $$
CREATE PROCEDURE _redundant_idx_cleanup()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'chat_history'
                 AND index_name = 'idx_appId') THEN
        ALTER TABLE chat_history DROP INDEX idx_appId;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'blog_post_tag'
                 AND index_name = 'idx_post_id') THEN
        ALTER TABLE blog_post_tag DROP INDEX idx_post_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'site_setting'
                 AND index_name = 'idx_module') THEN
        ALTER TABLE site_setting DROP INDEX idx_module;
    END IF;
END $$

DELIMITER ;

CALL _redundant_idx_cleanup();
DROP PROCEDURE IF EXISTS _redundant_idx_cleanup;
