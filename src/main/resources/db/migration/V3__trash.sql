ALTER TABLE files ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE files ADD COLUMN trash_batch UUID;
ALTER TABLE folders ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE folders ADD COLUMN trash_batch UUID;
CREATE INDEX idx_files_trash ON files(owner, deleted_at);
CREATE INDEX idx_folders_trash ON folders(owner, deleted_at);
CREATE TABLE pending_object_deletions (
    object_key VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
