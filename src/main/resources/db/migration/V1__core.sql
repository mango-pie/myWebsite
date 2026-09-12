-- Core schema: user / app / chat_history / blog_*
-- Adopted into Flyway as an idempotent baseline (CREATE TABLE IF NOT EXISTS,
-- no DROP statements) so it is safe to run on an existing production database.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userAccount` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'account',
  `userPassword` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'password',
  `userName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'nickname',
  `userAvatar` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'avatar',
  `userProfile` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'profile',
  `userRole` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT 'role: user/admin',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'edit time',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete',
  `vipExpireTime` datetime DEFAULT NULL COMMENT 'vip expire time',
  `vipCode` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'vip code',
  `vipNumber` bigint DEFAULT NULL COMMENT 'vip number',
  `shareCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'share code',
  `inviteUser` bigint DEFAULT NULL COMMENT 'invite user id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_userAccount` (`userAccount`),
  KEY `idx_userName` (`userName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='user';

CREATE TABLE IF NOT EXISTS `app` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `appName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'app name',
  `cover` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'cover',
  `initPrompt` text COLLATE utf8mb4_unicode_ci COMMENT 'init prompt',
  `codeGenType` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'code gen type',
  `deployKey` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'deploy key',
  `deployedTime` datetime DEFAULT NULL COMMENT 'deployed time',
  `priority` int NOT NULL DEFAULT '0' COMMENT 'priority',
  `userId` bigint NOT NULL COMMENT 'creator user id',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'edit time',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deployKey` (`deployKey`),
  KEY `idx_appName` (`appName`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='app';

CREATE TABLE IF NOT EXISTS `chat_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `message` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'message',
  `messageType` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'user/ai',
  `appId` bigint NOT NULL COMMENT 'app id',
  `userId` bigint NOT NULL COMMENT 'creator user id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete',
  `parentId` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appId` (`appId`),
  KEY `idx_createTime` (`createTime`),
  KEY `idx_appId_createTime` (`appId`,`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='chat history';

CREATE TABLE IF NOT EXISTS `blog_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'category id',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'name',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'description',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'icon',
  `sort_order` int DEFAULT '0' COMMENT 'sort order',
  `status` tinyint DEFAULT '1' COMMENT 'status: 0 disabled / 1 enabled',
  `created_time` datetime NOT NULL COMMENT 'create time',
  `updated_time` datetime NOT NULL COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='blog category';

CREATE TABLE IF NOT EXISTS `blog_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'tag id',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'name',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'description',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '#667eea' COMMENT 'color',
  `count` int DEFAULT '0' COMMENT 'usage count',
  `status` tinyint DEFAULT '1' COMMENT 'status: 0 disabled / 1 enabled',
  `created_time` datetime NOT NULL COMMENT 'create time',
  `updated_time` datetime NOT NULL COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='blog tag';

CREATE TABLE IF NOT EXISTS `blog_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'post id',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'title',
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'summary',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'content',
  `cover_url` varchar(1200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'cover url',
  `category_id` bigint NOT NULL COMMENT 'category id',
  `user_id` bigint NOT NULL COMMENT 'author id',
  `view_count` int DEFAULT '0' COMMENT 'view count',
  `like_count` int DEFAULT '0' COMMENT 'like count',
  `status` tinyint DEFAULT '1' COMMENT 'status: 0 draft / 1 published / 2 offline',
  `is_top` tinyint DEFAULT '0' COMMENT 'is top: 0 no / 1 yes',
  `sort_order` int DEFAULT '0' COMMENT 'sort order',
  `extend_info` json DEFAULT NULL COMMENT 'extend info',
  `created_time` datetime NOT NULL COMMENT 'create time',
  `updated_time` datetime NOT NULL COMMENT 'update time',
  `deleted_time` datetime DEFAULT NULL COMMENT 'delete time',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_top` (`is_top`),
  KEY `idx_created_time` (`created_time`),
  FULLTEXT KEY `idx_search` (`title`,`summary`),
  CONSTRAINT `fk_post_category` FOREIGN KEY (`category_id`) REFERENCES `blog_category` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_post_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='blog post';

CREATE TABLE IF NOT EXISTS `blog_post_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'relation id',
  `post_id` bigint NOT NULL COMMENT 'post id',
  `tag_id` bigint NOT NULL COMMENT 'tag id',
  `created_time` datetime NOT NULL COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_tag` (`post_id`,`tag_id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_post_tag_post` FOREIGN KEY (`post_id`) REFERENCES `blog_post` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_post_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `blog_tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='blog post-tag relation';

CREATE TABLE IF NOT EXISTS `blog_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'image id',
  `filename` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'original filename',
  `storage_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'storage filename',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'access url',
  `size` bigint NOT NULL COMMENT 'file size',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME type',
  `width` int DEFAULT NULL COMMENT 'width',
  `height` int DEFAULT NULL COMMENT 'height',
  `post_id` bigint DEFAULT NULL COMMENT 'related post id',
  `user_id` bigint NOT NULL COMMENT 'uploader user id',
  `usage_type` tinyint DEFAULT '1' COMMENT 'usage: 1 cover / 2 content / 3 other',
  `status` tinyint DEFAULT '1' COMMENT 'status: 0 deleted / 1 normal',
  `created_time` datetime NOT NULL COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_storage_name` (`storage_name`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_usage_type` (`usage_type`),
  CONSTRAINT `fk_image_post` FOREIGN KEY (`post_id`) REFERENCES `blog_post` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_image_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='blog image';

SET FOREIGN_KEY_CHECKS = 1;
