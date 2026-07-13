-- WMS PostgreSQL init script
-- Creates empty databases only. Tables are created when microservices start (Flyway).
-- Re-run safely: existing databases are skipped.

SELECT 'CREATE DATABASE wms_core_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_core_db')\gexec
SELECT 'CREATE DATABASE wms_localization_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_localization_db')\gexec
SELECT 'CREATE DATABASE wms_finance_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_finance_db')\gexec
SELECT 'CREATE DATABASE wms_billing_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_billing_db')\gexec
SELECT 'CREATE DATABASE wms_integration_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_integration_db')\gexec
SELECT 'CREATE DATABASE wms_inbound_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_inbound_db')\gexec
SELECT 'CREATE DATABASE wms_inventory_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_inventory_db')\gexec
SELECT 'CREATE DATABASE wms_outbound_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'wms_outbound_db')\gexec
SELECT 'CREATE DATABASE keycloak_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec
