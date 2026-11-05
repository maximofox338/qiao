-- Reviews (one per side of a COMPLETED contract). The table exists since V1 (no rows: it had no endpoints).
ALTER TABLE review
    ADD COLUMN from_side VARCHAR(16) NOT NULL AFTER to_user_id,
    ADD UNIQUE KEY uk_review_contract_side (contract_id, from_side),
    ADD KEY idx_review_side (from_side, id);
