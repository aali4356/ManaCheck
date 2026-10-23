CREATE TABLE catalog_import (
    id UUID PRIMARY KEY,
    source TEXT NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    content_hash TEXT,
    status TEXT NOT NULL CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','UNCHANGED')),
    record_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE TABLE catalog_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton),
    active_import_id UUID REFERENCES catalog_import(id)
);
INSERT INTO catalog_state(singleton) VALUES(TRUE);
CREATE TABLE card (
    oracle_id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    mana_value NUMERIC NOT NULL CHECK(mana_value >= 0),
    type_line TEXT NOT NULL,
    oracle_text TEXT NOT NULL,
    color_identity TEXT[] NOT NULL,
    basic_land_types TEXT[] NOT NULL,
    keywords TEXT[] NOT NULL,
    commander_legality TEXT NOT NULL,
    paper BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    commander_eligible BOOLEAN NOT NULL,
    unsupported_reason TEXT,
    copy_limit INTEGER CHECK(copy_limit IS NULL OR copy_limit > 0),
    scryfall_uri TEXT,
    import_id UUID NOT NULL REFERENCES catalog_import(id)
);
CREATE INDEX card_name_lower_idx ON card(lower(name));
CREATE INDEX card_identity_idx ON card USING GIN(color_identity);
CREATE INDEX card_active_legality_idx ON card(commander_legality, active, paper);
CREATE TABLE card_face (
    oracle_id UUID NOT NULL REFERENCES card(oracle_id),
    face_number INTEGER NOT NULL,
    name TEXT NOT NULL,
    type_line TEXT NOT NULL,
    oracle_text TEXT NOT NULL,
    image_uri TEXT,
    PRIMARY KEY(oracle_id, face_number)
);
CREATE TABLE card_tag (
    oracle_id UUID NOT NULL REFERENCES card(oracle_id),
    tag TEXT NOT NULL,
    rules_version TEXT NOT NULL,
    PRIMARY KEY(oracle_id, tag)
);
CREATE INDEX card_tag_tag_idx ON card_tag(tag, oracle_id);
CREATE TABLE catalog_stage (
    import_id UUID NOT NULL REFERENCES catalog_import(id),
    oracle_id UUID NOT NULL,
    payload JSONB NOT NULL,
    PRIMARY KEY(import_id, oracle_id)
);
CREATE TABLE deck (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 120),
    current_revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE deck_revision (
    id UUID PRIMARY KEY,
    deck_id UUID NOT NULL REFERENCES deck(id),
    revision_number INTEGER NOT NULL CHECK(revision_number > 0),
    commander_id UUID NOT NULL REFERENCES card(oracle_id),
    catalog_import_id UUID REFERENCES catalog_import(id),
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(deck_id, revision_number)
);
CREATE TABLE deck_entry (
    revision_id UUID NOT NULL REFERENCES deck_revision(id),
    oracle_id UUID NOT NULL REFERENCES card(oracle_id),
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000),
    PRIMARY KEY(revision_id, oracle_id)
);
CREATE FUNCTION protect_deck_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Deck revisions are immutable'; END $$;
CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON deck_revision FOR EACH ROW EXECUTE FUNCTION protect_deck_snapshot();
CREATE TRIGGER immutable_entry BEFORE UPDATE OR DELETE ON deck_entry FOR EACH ROW EXECUTE FUNCTION protect_deck_snapshot();
