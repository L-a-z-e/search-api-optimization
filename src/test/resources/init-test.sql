-- FULLTEXT index needs explicit creation in test (ddl-auto=create handles table but not FULLTEXT)
-- This runs after Testcontainers MySQL starts but before Hibernate DDL.
-- We'll add FULLTEXT index via a test setup method instead.
SELECT 1;
