ALTER TABLE ai_conversation_content
    ADD COLUMN IF NOT EXISTS attachments MEDIUMTEXT NULL COMMENT '图片附件 JSON' AFTER content;
