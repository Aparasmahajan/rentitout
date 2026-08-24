-- Twelve members across one neighbourhood (central Berlin), so the feed, the
-- map and the distance ordering all have something to show on a fresh database.
-- Loaded only when the `dev` profile is active - see spring.flyway.locations.
--
-- Repeatable (R__) on purpose. A versioned seed pinned at a high number blocks
-- every later migration: Flyway sees the new lower version as out of order and
-- refuses to start. Repeatable migrations always run last and never collide.

WITH seeded AS (
    SELECT * FROM (VALUES
        ('+491700000001', 'Amara Okafor',    'Kreuzberg',      52.4996, 13.4180, 'Fixes anything with a plug. Ask about the drill.'),
        ('+491700000002', 'Jonas Meyer',     'Neukölln',       52.4810, 13.4350, 'Weekend carpenter, weekday accountant.'),
        ('+491700000003', 'Priya Raman',     'Kreuzberg',      52.5010, 13.4260, 'Video editor. Happy to teach the basics.'),
        ('+491700000004', 'Tomas Novak',     'Friedrichshain', 52.5150, 13.4540, 'Has a van and too many tools.'),
        ('+491700000005', 'Sofia Almeida',   'Neukölln',       52.4790, 13.4290, 'Plants, bikes, sourdough.'),
        ('+491700000006', 'Erik Lindqvist',  'Kreuzberg',      52.4960, 13.4210, 'Photographer. Lends lenses to careful people.'),
        ('+491700000007', 'Nadia Haddad',    'Friedrichshain', 52.5120, 13.4480, 'Maths tutor, evenings only.'),
        ('+491700000008', 'Ben Carter',      'Kreuzberg',      52.4980, 13.4300, 'Projector, party tent, questionable playlists.'),
        ('+491700000009', 'Yuki Tanaka',     'Neukölln',       52.4760, 13.4400, 'Sewing machine and patience for hire.'),
        ('+491700000010', 'Marta Kowalska',  'Friedrichshain', 52.5180, 13.4600, 'Climbing gear, camping gear, cold hands.'),
        ('+491700000011', 'Idris Bello',     'Kreuzberg',      52.5020, 13.4150, 'Electrician. Will not touch your fuse box for free.'),
        ('+491700000012', 'Lena Fischer',    'Neukölln',       52.4830, 13.4230, 'Guitar teacher. Two amps, one cat.')
    ) AS t(phone, display_name, area, lat, lon, bio)
), inserted AS (
    INSERT INTO app_user (id, phone, display_name, status)
    SELECT gen_random_uuid(), phone, display_name, 'ACTIVE' FROM seeded
    ON CONFLICT (phone) DO NOTHING
    RETURNING id, phone
)
INSERT INTO profile (user_id, area_label, lat, lon, search_radius_km, open_to_requests, bio, rating_avg, completed_count, verified)
SELECT i.id, s.area, s.lat, s.lon, 5, true, s.bio,
       4.2 + (random() * 0.8), (random() * 20)::int, (random() > 0.4)
FROM inserted i JOIN seeded s ON s.phone = i.phone
ON CONFLICT (user_id) DO NOTHING;

-- Give every seeded member a few tags so search has something to match on.
INSERT INTO profile_tag (profile_id, tag_id, relation)
SELECT p.user_id, t.id, r.relation
FROM profile p
CROSS JOIN LATERAL (
    SELECT id FROM tag ORDER BY md5(id::text || p.user_id::text) LIMIT 4
) t
CROSS JOIN LATERAL (
    SELECT (ARRAY['has', 'teaches', 'needs', 'enjoys'])[1 + (abs(hashtext(t.id::text)) % 4)] AS relation
) r
ON CONFLICT DO NOTHING;
