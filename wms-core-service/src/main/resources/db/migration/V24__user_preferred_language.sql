-- =============================================================================
-- V24: Add preferred_language column to users table
-- =============================================================================
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5);
