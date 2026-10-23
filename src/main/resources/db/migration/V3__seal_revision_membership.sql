-- Entries can be inserted only while the next snapshot is being assembled.
-- Advancing deck.current_revision seals the snapshot, including its membership.
CREATE FUNCTION protect_revision_membership() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE published_revision INTEGER; candidate_revision INTEGER;
BEGIN
    SELECT d.current_revision,r.revision_number INTO published_revision,candidate_revision
      FROM deck_revision r JOIN deck d ON d.id=r.deck_id WHERE r.id=NEW.revision_id;
    IF candidate_revision IS NOT NULL AND candidate_revision <= published_revision THEN
        RAISE EXCEPTION 'Published deck revision membership is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_membership BEFORE INSERT ON deck_entry
    FOR EACH ROW EXECUTE FUNCTION protect_revision_membership();
