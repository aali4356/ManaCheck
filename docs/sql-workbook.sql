-- Run against the local demo database with psql, e.g.:
-- PGPASSWORD=local-demo-only psql -h 127.0.0.1 -p 55432 -U manacheck -d manacheck -f docs/sql-workbook.sql
-- Reads only. Select the most recently edited deck and its current snapshot.
SELECT id AS deck_id,current_revision AS current_revision FROM deck ORDER BY updated_at DESC LIMIT 1 \gset
SELECT id AS revision_id FROM deck_revision WHERE deck_id=:'deck_id' AND revision_number=:'current_revision' \gset

-- Role coverage: the same card can contribute to multiple role totals.
SELECT t.tag,SUM(e.quantity) AS cards
FROM deck_entry e JOIN card_tag t USING(oracle_id)
WHERE e.revision_id=:'revision_id' AND t.tag IN ('land','ramp','draw','interaction')
GROUP BY t.tag ORDER BY t.tag;

-- Mana curve: weighted by quantity, with lands excluded.
SELECT CASE WHEN c.mana_value>=7 THEN '7+' ELSE floor(c.mana_value)::text END AS bucket,SUM(e.quantity) AS cards
FROM deck_entry e JOIN card c USING(oracle_id)
WHERE e.revision_id=:'revision_id'
  AND NOT EXISTS(SELECT 1 FROM card_tag t WHERE t.oracle_id=c.oracle_id AND t.tag='land')
GROUP BY bucket ORDER BY bucket;

-- Color identity: multicolor cards count once in each applicable color.
SELECT color,SUM(e.quantity) AS cards FROM deck_entry e JOIN card c USING(oracle_id)
CROSS JOIN LATERAL unnest(CASE WHEN cardinality(c.color_identity)=0 THEN ARRAY['C'] ELSE c.color_identity END) AS colors(color)
WHERE e.revision_id=:'revision_id' GROUP BY color ORDER BY color;

-- Revision difference: FULL OUTER JOIN includes cards present on either side.
WITH snapshots AS (
 SELECT r.revision_number,e.oracle_id,e.quantity FROM deck_revision r JOIN deck_entry e ON e.revision_id=r.id
 WHERE r.deck_id=:'deck_id' AND r.revision_number IN (1,:'current_revision')
), before_cards AS (SELECT * FROM snapshots WHERE revision_number=1),
after_cards AS (SELECT * FROM snapshots WHERE revision_number=:'current_revision')
SELECT c.name,COALESCE(b.quantity,0) AS before,COALESCE(a.quantity,0) AS after,
 COALESCE(a.quantity,0)-COALESCE(b.quantity,0) AS delta
FROM before_cards b FULL OUTER JOIN after_cards a USING(oracle_id)
JOIN card c ON c.oracle_id=COALESCE(b.oracle_id,a.oracle_id)
WHERE COALESCE(a.quantity,0)<>COALESCE(b.quantity,0) ORDER BY c.name;

-- The application also compares commander changes; see DeckService.diff().
-- The executed recommendation CTE + ROW_NUMBER query is in:
-- src/main/resources/sql/recommendations.sql
