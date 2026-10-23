-- Parameters are bound by JdbcClient. Role totals intentionally overlap.
WITH role_counts AS MATERIALIZED (
    SELECT t.tag, SUM(e.quantity) AS quantity
    FROM deck_entry e JOIN card_tag t ON t.oracle_id=e.oracle_id
    WHERE e.revision_id=:revision_id GROUP BY t.tag
), curve AS (
    SELECT COALESCE(SUM(c.mana_value*e.quantity)/NULLIF(SUM(e.quantity),0),0) AS average_mana
    FROM deck_entry e JOIN card c USING(oracle_id)
    WHERE e.revision_id=:revision_id
      AND NOT EXISTS(SELECT 1 FROM card_tag t WHERE t.oracle_id=c.oracle_id AND t.tag='land')
), pool AS MATERIALIZED (
    SELECT c.oracle_id,c.name,c.mana_value,
      COALESCE((SELECT SUM(CASE
        WHEN t.tag='land' AND COALESCE(r.quantity,0)<:land_target THEN 6
        WHEN t.tag='ramp' AND COALESCE(r.quantity,0)<:ramp_target THEN 6
        WHEN t.tag='draw' AND COALESCE(r.quantity,0)<:draw_target THEN 6
        WHEN t.tag='interaction' AND COALESCE(r.quantity,0)<:interaction_target THEN 6
        ELSE 0 END)
        FROM card_tag t LEFT JOIN role_counts r ON r.tag=t.tag WHERE t.oracle_id=c.oracle_id),0) AS need_score,
      CASE WHEN :theme<>'general' AND EXISTS(SELECT 1 FROM card_tag t WHERE t.oracle_id=c.oracle_id AND t.tag=:theme) THEN 4 ELSE 0 END AS theme_score,
      CASE WHEN (SELECT average_mana FROM curve)>3.5 AND c.mana_value<=3
        AND NOT EXISTS(SELECT 1 FROM card_tag t WHERE t.oracle_id=c.oracle_id AND t.tag='land') THEN 3 ELSE 0 END AS curve_score
    FROM card c
    LEFT JOIN deck_entry e ON e.oracle_id=c.oracle_id AND e.revision_id=:revision_id
    WHERE c.active AND c.paper AND c.commander_legality='legal' AND c.unsupported_reason IS NULL
      AND c.oracle_id<>:commander_id
      AND c.color_identity <@ CAST(:identity AS text[])
      AND (c.copy_limit IS NULL OR COALESCE(e.quantity,0)<c.copy_limit)
      AND NOT EXISTS(SELECT 1 FROM unnest(c.basic_land_types) l(land_type)
        WHERE NOT ((CASE l.land_type WHEN 'Plains' THEN 'W' WHEN 'Island' THEN 'U' WHEN 'Swamp' THEN 'B' WHEN 'Mountain' THEN 'R' WHEN 'Forest' THEN 'G' END)=ANY(CAST(:identity AS text[]))))
), scored AS (
    SELECT *, (need_score+theme_score+curve_score+CASE WHEN mana_value<=3 THEN 2 ELSE 0 END)::integer AS score
    FROM pool WHERE need_score+theme_score+curve_score>0
), ranked AS (
    SELECT *, ROW_NUMBER() OVER(ORDER BY score DESC,mana_value,lower(name),oracle_id) AS ranking
    FROM scored
)
SELECT oracle_id,score FROM ranked WHERE ranking<=50 ORDER BY ranking;
