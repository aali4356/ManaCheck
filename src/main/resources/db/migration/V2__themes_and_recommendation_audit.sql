-- Additive upgrade: existing revisions retain their entries and receive a general theme.
ALTER TABLE deck_revision ADD COLUMN theme TEXT NOT NULL DEFAULT 'general'
    CHECK(theme IN ('general','tokens','counters','graveyard'));
CREATE TABLE recommendation_run (
    id UUID PRIMARY KEY,
    deck_id UUID NOT NULL REFERENCES deck(id),
    revision_number INTEGER NOT NULL,
    catalog_import_id UUID NOT NULL REFERENCES catalog_import(id),
    suggestions JSONB NOT NULL,
    explanation_status TEXT NOT NULL DEFAULT 'rules'
        CHECK(explanation_status IN ('rules','pending','haiku','fallback')),
    explanation_detail TEXT,
    model TEXT,
    usage JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(deck_id, revision_number) REFERENCES deck_revision(deck_id, revision_number)
);
CREATE INDEX recommendation_deck_idx ON recommendation_run(deck_id, created_at DESC);
