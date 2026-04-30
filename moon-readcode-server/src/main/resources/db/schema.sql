-- ============================================================
-- moon-readcode (project-pilot) 数据库初始化脚本
-- 数据库：moon_readcode
-- 在 MySQL 中执行：
--   CREATE DATABASE IF NOT EXISTS moon_readcode DEFAULT CHARACTER SET utf8mb4;
--   USE moon_readcode;
--   source /path/to/schema.sql;
-- ============================================================

-- ------------------------------------------------------------
-- 工程文件表（Phase 1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_file (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    VARCHAR(128)  NOT NULL COMMENT '工程标识',
    file_name     VARCHAR(256)  NOT NULL COMMENT '文件名（不含路径）',
    file_path     VARCHAR(1024) NOT NULL COMMENT '相对工程根目录的相对路径',
    file_type     VARCHAR(32)   NOT NULL COMMENT '文件后缀类型：java/xml/properties...',
    package_name  VARCHAR(512)  DEFAULT NULL COMMENT 'Java 包名',
    content_hash  VARCHAR(64)   DEFAULT NULL COMMENT '文件内容 SHA-256，用于增量识别',
    content       LONGTEXT      COMMENT '文件原始内容',
    create_time   DATETIME      NOT NULL,
    update_time   DATETIME      NOT NULL,
    KEY idx_project_id (project_id),
    KEY idx_project_path (project_id, file_path(255))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工程源文件元数据';

-- ------------------------------------------------------------
-- 类元数据表（Phase 2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_class (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL,
    file_id         BIGINT        NOT NULL COMMENT '所属 code_file.id',
    class_name      VARCHAR(256)  NOT NULL COMMENT '简单类名',
    qualified_name  VARCHAR(512)  NOT NULL COMMENT '全限定类名',
    package_name    VARCHAR(512),
    class_type      VARCHAR(32)   COMMENT 'CLASS/INTERFACE/ENUM/RECORD/ANNOTATION',
    stereotype      VARCHAR(32)   COMMENT '业务原型：CONTROLLER/SERVICE/MAPPER/ENTITY/CONFIG/OTHER',
    super_class     VARCHAR(512),
    interfaces      TEXT          COMMENT '实现的接口，逗号分隔',
    annotations     TEXT          COMMENT '类注解，逗号分隔',
    start_line      INT,
    end_line        INT,
    create_time     DATETIME      NOT NULL,
    update_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_file (file_id),
    KEY idx_qualified (qualified_name(255))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '类级元数据';

-- ------------------------------------------------------------
-- 方法元数据表（Phase 2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_method (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL,
    class_id        BIGINT        NOT NULL,
    method_name     VARCHAR(256)  NOT NULL,
    signature       VARCHAR(1024) NOT NULL COMMENT '方法签名 className#methodName(paramTypes)',
    return_type     VARCHAR(256),
    parameters      TEXT          COMMENT '参数列表 JSON',
    annotations     TEXT,
    modifiers       VARCHAR(128)  COMMENT 'public/private/static 等，逗号分隔',
    body_snippet    LONGTEXT      COMMENT '方法体源码片段',
    start_line      INT,
    end_line        INT,
    create_time     DATETIME      NOT NULL,
    update_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_class (class_id),
    KEY idx_signature (signature(255))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '方法级元数据';

-- ------------------------------------------------------------
-- 调用 / 依赖关系表（Phase 2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_relation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL,
    relation_type   VARCHAR(32)   NOT NULL COMMENT 'METHOD_CALL/CLASS_DEPEND/FIELD_INJECT/IMPLEMENT/EXTEND',
    from_id         BIGINT        NOT NULL COMMENT '源 method_id 或 class_id',
    from_type       VARCHAR(16)   NOT NULL COMMENT 'CLASS/METHOD',
    to_ref          VARCHAR(1024) NOT NULL COMMENT '目标的全限定名或签名（可能尚未解析到具体 id）',
    to_id           BIGINT        DEFAULT NULL COMMENT '若已解析到本工程内目标，记录 id',
    create_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_from (from_id, from_type),
    KEY idx_to_ref (to_ref(255))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '代码间关系';

-- ------------------------------------------------------------
-- 语义摘要表（Phase 3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL,
    target_type     VARCHAR(16)   NOT NULL COMMENT 'FILE/CLASS/METHOD/MODULE/PROJECT',
    target_id       BIGINT        DEFAULT NULL COMMENT '对应表的主键 id（PROJECT 维度时为 NULL）',
    target_ref      VARCHAR(512)  COMMENT '冗余的可读标识，如全限定名/签名',
    summary         LONGTEXT      COMMENT 'LLM 生成的自然语言摘要',
    vector_id       VARCHAR(128)  COMMENT '在向量库中的 id（与 Chroma 对齐）',
    model_name      VARCHAR(64)   COMMENT '生成时所用模型',
    create_time     DATETIME      NOT NULL,
    update_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '语义摘要 / 向量映射';

-- ------------------------------------------------------------
-- 问答历史表（热点分析数据源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS qa_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL COMMENT '工程标识',
    question        TEXT          NOT NULL COMMENT '用户问题原文',
    question_vector TEXT          COMMENT '问题的 embedding 向量（JSON 数组，用于聚类）',
    answer          LONGTEXT      COMMENT 'LLM 回答',
    hit_refs        TEXT          COMMENT '命中的代码引用（JSON 数组）',
    feedback        VARCHAR(16)   DEFAULT NULL COMMENT '用户反馈：null/GOOD/BAD',
    create_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '问答历史记录';

-- ------------------------------------------------------------
-- 热点主题表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hotspot_topic (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id                VARCHAR(128)  NOT NULL COMMENT '工程标识',
    topic_name                VARCHAR(256)  NOT NULL COMMENT '系统生成的主题名称',
    question_count            INT           NOT NULL DEFAULT 0 COMMENT '被问次数',
    representative_questions  TEXT          COMMENT '代表性问题（JSON 数组）',
    involved_modules          TEXT          COMMENT '涉及的代码模块（JSON 数组）',
    analysis                  LONGTEXT      COMMENT '系统分析结论',
    status                    VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/REVISED',
    reject_reason             TEXT          COMMENT '被驳回的原因（人工填写）',
    create_time               DATETIME      NOT NULL,
    update_time               DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '热点主题';

-- ------------------------------------------------------------
-- 热点沉淀文档表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hotspot_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      VARCHAR(128)  NOT NULL COMMENT '工程标识',
    topic_id        BIGINT        NOT NULL COMMENT '关联的热点主题 id',
    title           VARCHAR(512)  NOT NULL COMMENT '文档标题',
    content         LONGTEXT      COMMENT '文档正文（Markdown）',
    status          VARCHAR(16)   NOT NULL DEFAULT 'GENERATED' COMMENT 'GENERATED/PUBLISHED',
    create_time     DATETIME      NOT NULL,
    update_time     DATETIME      NOT NULL,
    KEY idx_project (project_id),
    KEY idx_topic (topic_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '热点沉淀文档';
