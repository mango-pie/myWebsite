-- =============================================================================
-- 模块下线 site_setting 数据清理（chat 对话页 / app-lab 实验室 / tts 语音合成）
-- 交接文档：AI-frontend/docs/MODULE_REMOVAL_HANDOFF.md §4
--
-- 说明：本仓库 Flyway 迁移约定「不动存量数据」（见 application.yml flyway 注释），
--       故本清理不做成 V 迁移，由运维备份后手工执行。可在管理后台任意时刻重跑。
-- =============================================================================

-- 0) 备份（必须先执行）
CREATE TABLE IF NOT EXISTS site_setting_bak_20261009 AS
SELECT * FROM site_setting;

-- 1) 模块开关行：GET /app/modules 已不再返回 chat / tts / app-lab，残留行不再被读取
DELETE FROM site_setting
WHERE `module` = 'modules'
  AND setting_key IN ('chat', 'tts', 'app-lab');

-- 2) TTS 模块设置（TtsModule 已删，setting schema 不再消费）
DELETE FROM site_setting WHERE `module` = 'tts';

-- 3) 实验室模块设置（AppModule 已删：codegen.* / deploy.enabled / deploy.public_host_display）
DELETE FROM site_setting WHERE `module` = 'app';

-- 4) 集成组里的代码生成 / TTS / 部署条目（IntegrationModule schema 已删对应字段）
DELETE FROM site_setting
WHERE `module` = 'integration'
  AND (setting_key LIKE 'codegen.%'
       OR setting_key LIKE 'tts.%'
       OR setting_key LIKE 'app.deploy.%');

-- 5) 复核（应返回 0 行）
SELECT * FROM site_setting
WHERE `module` IN ('tts', 'app')
   OR (`module` = 'modules' AND setting_key IN ('chat', 'tts', 'app-lab'))
   OR (`module` = 'integration'
       AND (setting_key LIKE 'codegen.%' OR setting_key LIKE 'tts.%' OR setting_key LIKE 'app.deploy.%'));

-- =============================================================================
-- 与交接文档的偏差说明：
-- 文档 §4 建议同时清理 chat.% / agent.% / image_caption.% / segmentation.% /
-- attachment_cache_ttl_minutes，但对话模块本身保留（桌宠 AI 直连 /chat/chat 全家桶），
-- 这些设置仍被运行时读取：
--   agent.*        → ChatAgentFacade / ChatOrchestrator（桌宠 Agent 模式）
--   segmentation.* → 聊天分段（桌宠逐段显示）
--   image_caption.*→ 图片转述（资料库 LibraryAiOrchestrationService 共用）
-- 全部保留，不做清理。
-- =============================================================================
