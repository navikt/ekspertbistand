-- Kostnadsstedet saksbehandler velger ved innvilgelse. Null ved avslag og for foreløpige vedtak fra
-- før kolonnen fantes (bare i dev). Se specifications/kostnadssted_forelopig_vedtak.md.
SET lock_timeout = '3s';

ALTER TABLE forelopig_vedtak ADD COLUMN IF NOT EXISTS kostnadssted TEXT NULL;
