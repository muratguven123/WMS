-- CHAR(3) -> VARCHAR(3) for Hibernate schema validation compatibility
ALTER TABLE invoices
    ALTER COLUMN invoice_currency TYPE VARCHAR(3),
    ALTER COLUMN accounting_currency TYPE VARCHAR(3);
