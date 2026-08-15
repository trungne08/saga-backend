ALTER TABLE task
    MODIFY COLUMN type ENUM(
        'BUG',
        'EPIC',
        'FEATURE',
        'REQUEST',
        'STORY',
        'SUBTASK',
        'TASK'
    ) NULL DEFAULT NULL;
