-- =============================================================================
-- V3 — Create user_roles junction table
-- DB      : MariaDB 10.6+
-- Module  : raservice
-- Purpose : Many-to-many mapping between users and roles.
--           One user can hold multiple roles; one role can be held by many users.
--           UNIQUE(user_id, role_id) prevents duplicate assignments.
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_roles
(
    id          BIGINT          NOT NULL AUTO_INCREMENT,

    user_id     BIGINT          NOT NULL,
    role_id     BIGINT          NOT NULL,

    -- Who assigned this role and when
    assigned_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by VARCHAR(100)    NOT NULL DEFAULT 'system',

    PRIMARY KEY (id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
        ON DELETE CASCADE,

    -- Prevent assigning the same role twice to the same user
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id)

) ENGINE = InnoDB
  DEFAULT CHARSET  = utf8mb4
  COLLATE          = utf8mb4_unicode_ci
  COMMENT          = 'Many-to-many: users ↔ roles. UNIQUE(user_id, role_id).';

-- Indexes for FK lookups
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- =============================================================================
-- Seed — assign ROLE_ADMIN to admin user
-- Subquery used to avoid hardcoded IDs (seed order may vary)
-- =============================================================================
INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT u.id, r.id, 'system'
FROM   users u
JOIN   roles r ON r.role_name = 'ROLE_ADMIN'
WHERE  u.username = 'admin';
