-- Update reference_data to use local logo paths instead of external URLs
-- Logos are cached in web/public/logos/

-- Banks: GitHub indian-banks SVGs → /logos/banks/{name}.svg
UPDATE reference_data SET metadata = jsonb_set(metadata, '{logo}',
  to_jsonb('/logos/banks/' || REPLACE(REPLACE(value, ' ', '_'), '&', '_') || '.svg'))
WHERE category = 'BANK' AND metadata->>'logo' LIKE '%github%indian-banks%';

-- Banks: gstatic favicons → /logos/banks/{name}.png
UPDATE reference_data SET metadata = jsonb_set(metadata, '{logo}',
  to_jsonb('/logos/banks/' || REPLACE(REPLACE(value, ' ', '_'), '&', '_') || '.png'))
WHERE category = 'BANK' AND metadata->>'logo' LIKE '%gstatic%';

-- Brokers → /logos/brokers/{name}.png
UPDATE reference_data SET metadata = jsonb_set(metadata, '{logo}',
  to_jsonb('/logos/brokers/' || REPLACE(REPLACE(value, ' ', '_'), '&', '_') || '.png'))
WHERE category = 'BROKER' AND metadata->>'logo' IS NOT NULL;

-- Card Issuers → /logos/cards/{name}.png
UPDATE reference_data SET metadata = jsonb_set(metadata, '{logo}',
  to_jsonb('/logos/cards/' || REPLACE(REPLACE(value, ' ', '_'), '&', '_') || '.png'))
WHERE category = 'CARD_ISSUER' AND metadata->>'logo' IS NOT NULL;

-- Card Networks → /logos/cards/{name}.png
UPDATE reference_data SET metadata = jsonb_set(metadata, '{logo}',
  to_jsonb('/logos/cards/' || REPLACE(REPLACE(value, ' ', '_'), '&', '_') || '.png'))
WHERE category = 'CARD_NETWORK' AND metadata->>'logo' IS NOT NULL;
