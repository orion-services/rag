-- Add orion_user_hash column to users table
-- MySQL doesn't support IF NOT EXISTS in ALTER TABLE, so we check first
SET @dbname = DATABASE();
SET @tablename = 'users';
SET @columnname = 'orion_user_hash';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(255) UNIQUE')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Create index
CREATE INDEX idx_users_orion_hash ON users(orion_user_hash);

