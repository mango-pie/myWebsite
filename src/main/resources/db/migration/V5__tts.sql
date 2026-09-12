-- TTS 音色档案表

CREATE TABLE IF NOT EXISTS tts_voice_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '音色ID',
    name VARCHAR(100) NOT NULL COMMENT '展示名称',
    ref_audio_path VARCHAR(500) NOT NULL COMMENT '参考音频服务端路径(正斜杠)',
    prompt_text VARCHAR(500) NULL COMMENT '参考文本',
    prompt_lang VARCHAR(20) DEFAULT 'zh' COMMENT '参考语种',
    text_lang VARCHAR(20) DEFAULT 'zh' COMMENT '合成语种',
    is_default TINYINT DEFAULT 0 COMMENT '0否 1默认音色',
    status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    KEY idx_status_sort (status, sort_order),
    KEY idx_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS音色档案';

-- 默认达妮娅音色（路径请按本机 GPT-SoVITS 实际文件调整）
INSERT INTO tts_voice_profile (name, ref_audio_path, prompt_text, prompt_lang, text_lang, is_default, status, sort_order, created_time, updated_time)
SELECT '达妮娅', 'E:/Quark/gpt-vot/output.wav_0009342720.wav',
       '怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽', 'zh', 'zh', 1, 1, 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM tts_voice_profile WHERE is_default = 1 LIMIT 1);
