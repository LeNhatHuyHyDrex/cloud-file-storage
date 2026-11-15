CREATE TABLE folder_invitations (
    id UUID PRIMARY KEY,
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    owner VARCHAR(50) NOT NULL,
    recipient VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (folder_id, recipient)
);
CREATE INDEX idx_folder_invitation_recipient ON folder_invitations(recipient);

CREATE TABLE folder_links (
    id UUID PRIMARY KEY,
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    owner VARCHAR(50) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_folder_links_folder ON folder_links(folder_id);
