-- V3 领域知识树（见 docs/ai-reading-workbench/05-v3-agent-path.md）

CREATE TABLE IF NOT EXISTS learning_domain (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户',
    name VARCHAR(128) NOT NULL COMMENT '领域名，如 Java',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '多领域列表排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_learning_domain_user (user_id, is_delete),
    UNIQUE KEY uk_learning_domain_user_name (user_id, name, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 领域复习树-根';

CREATE TABLE IF NOT EXISTS learning_branch (
    id BIGINT NOT NULL PRIMARY KEY,
    domain_id BIGINT NOT NULL COMMENT '所属领域',
    user_id BIGINT NOT NULL COMMENT '冗余，权限与快照',
    parent_branch_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=L1 挂在 domain 下；非0=L2 父枝 id',
    depth TINYINT NOT NULL COMMENT '1 或 2',
    title VARCHAR(128) NOT NULL COMMENT '主题名',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同父下排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_learning_branch_domain (domain_id, parent_branch_id, is_delete),
    UNIQUE KEY uk_learning_branch_sibling (domain_id, parent_branch_id, title, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 主题枝';

CREATE TABLE IF NOT EXISTS learning_leaf (
    id BIGINT NOT NULL PRIMARY KEY,
    domain_id BIGINT NOT NULL COMMENT '冗余，按领域查挂载',
    branch_id BIGINT NOT NULL COMMENT '挂载枝',
    user_id BIGINT NOT NULL COMMENT '冗余，权限',
    knowledge_note_id BIGINT NOT NULL COMMENT '精读 note，全局唯一一行',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同枝内排序',
    attached_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认挂叶时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_learning_leaf_note (knowledge_note_id),
    KEY idx_learning_leaf_branch (branch_id, is_delete),
    KEY idx_learning_leaf_domain (domain_id, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 note 挂载叶';
