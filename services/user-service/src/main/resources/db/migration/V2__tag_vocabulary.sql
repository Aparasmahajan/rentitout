-- The starting tag vocabulary. Members can only pick from it in Phase 00, which
-- keeps the search synonym dictionary in Phase 02 tractable.

INSERT INTO tag (id, slug, label, kind) VALUES
  (gen_random_uuid(), 'video-editing',    'Video editing',     'skill'),
  (gen_random_uuid(), 'photography',      'Photography',       'skill'),
  (gen_random_uuid(), 'electrical',       'Electrical work',   'skill'),
  (gen_random_uuid(), 'plumbing',         'Plumbing',          'skill'),
  (gen_random_uuid(), 'carpentry',        'Carpentry',         'skill'),
  (gen_random_uuid(), 'gardening',        'Gardening',         'skill'),
  (gen_random_uuid(), 'tutoring-maths',   'Maths tutoring',    'teach'),
  (gen_random_uuid(), 'guitar',           'Guitar',            'teach'),
  (gen_random_uuid(), 'yoga',             'Yoga',              'teach'),
  (gen_random_uuid(), 'cooking',          'Cooking',           'teach'),
  (gen_random_uuid(), 'coding',           'Coding',            'teach'),
  (gen_random_uuid(), 'dog-walking',      'Dog walking',       'need'),
  (gen_random_uuid(), 'childcare',        'Childcare',         'need'),
  (gen_random_uuid(), 'moving-help',      'Moving help',       'need'),
  (gen_random_uuid(), 'cycling',          'Cycling',           'hobby'),
  (gen_random_uuid(), 'running',          'Running',           'hobby'),
  (gen_random_uuid(), 'board-games',      'Board games',       'hobby'),
  (gen_random_uuid(), 'ladder',           'Ladder',            'item'),
  (gen_random_uuid(), 'drill',            'Drill',             'item'),
  (gen_random_uuid(), 'pressure-washer',  'Pressure washer',   'item'),
  (gen_random_uuid(), 'camping-gear',     'Camping gear',      'item'),
  (gen_random_uuid(), 'projector',        'Projector',         'item'),
  (gen_random_uuid(), 'printer',          'Printer',           'item'),
  (gen_random_uuid(), 'car-roof-box',     'Car roof box',      'item'),
  (gen_random_uuid(), 'party-tent',       'Party tent',        'item'),
  (gen_random_uuid(), 'sewing-machine',   'Sewing machine',    'item')
ON CONFLICT (slug) DO NOTHING;
