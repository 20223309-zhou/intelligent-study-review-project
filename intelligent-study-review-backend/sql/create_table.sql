-- 创建库
create database if not exists intelligent_study_review;
use intelligent_study_review;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 问题表
CREATE TABLE IF NOT EXISTS `question`  (
                             `id` bigint NOT NULL COMMENT '主键ID',
                             `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题干内容',
                             `questionType` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题型：SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/SHORT_ANSWER/BLANK_FILLING/PROOF_QUESTION/MATERIAL/ESSAY',
                             `options` json NULL COMMENT '选项内容（仅选择题）',
                             `materialText` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '材料题公共阅读材料',
                             `paperId` bigint NOT NULL COMMENT '所属试卷ID',
                             `sortOrder` int NOT NULL DEFAULT 0 COMMENT '题号',
                             `score` int NOT NULL DEFAULT 0 COMMENT '分值',
                             `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '正确答案或参考答案',
                             `analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '答案解析',
                             `difficulty` int NULL DEFAULT NULL COMMENT '难度：1-简单 2-中等 3-困难',
                             `knowledgePoints` json NULL COMMENT '知识点标签',
                             `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `isDelete` int NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
                             PRIMARY KEY (`id`) USING BTREE,
                             INDEX `idx_paperId`(`paperId` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '题库表' ROW_FORMAT = Dynamic;

-- 试卷表
CREATE TABLE IF NOT EXISTS `exam_paper`  (
                               `id` bigint NOT NULL COMMENT '试卷唯一ID',
                               `userId` bigint NOT NULL COMMENT '创建人ID',
                               `comment` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '试卷描述备注',
                               `paperName` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '试卷名称',
                               `questionConfig` json NULL COMMENT '题型要求 {题型:数量}',
                               `subject` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科/专业',
                               `gradeOrLevel` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '年级或专业层次',
                               `textbookVersion` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '教材版本',
                               `chapterRange` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '考察章节范围',
                               `generationStatus` int NOT NULL DEFAULT 0 COMMENT '生成状态：0-生成中 1-已生成 2-审查驳回 3-生成失败',
                               `aiReviewComment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI审查意见',
                               `promptContext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户输入表单与特殊要求',
                               `totalScore` decimal(10, 2) NULL DEFAULT NULL COMMENT '总分',
                               `durationMinutes` int NULL DEFAULT NULL COMMENT '考试时长（分钟）',
                               `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               `isDelete` int NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
                               PRIMARY KEY (`id`) USING BTREE,
                               INDEX `idx_userId`(`userId` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '试卷主表' ROW_FORMAT = Dynamic;