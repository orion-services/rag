-- Remove is_shared column from conversations table
-- MySQL doesn't support DROP COLUMN IF EXISTS, so we use a workaround
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'conversations' 
    AND COLUMN_NAME = 'is_shared');
SET @sqlstmt := IF(@exist > 0, 'ALTER TABLE conversations DROP COLUMN is_shared', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

