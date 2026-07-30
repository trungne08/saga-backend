-- Git commit messages are not limited to the legacy VARCHAR(255) capacity.
-- MEDIUMTEXT retains existing NULL values and data while allowing full messages.
ALTER TABLE commit_data
    MODIFY COLUMN message MEDIUMTEXT NULL;
