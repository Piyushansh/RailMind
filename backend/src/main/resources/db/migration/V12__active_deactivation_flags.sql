-- Admin activation/deactivation support (spec: admin platform must support
-- "Activation/deactivation"). Deliberately additive/backward compatible:
-- default TRUE means every existing seeded row (and any row already
-- created by the running app) stays exactly as visible/bookable as before.
-- Deactivating hides a record from PUBLIC search/booking flows without
-- deleting it or touching any booking that already references it - "do not
-- leave train records unusable" means existing bookings against a
-- deactivated train must keep working, which is why this is a soft flag
-- and not a delete.

ALTER TABLE stations ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE trains ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE coaches ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_stations_active ON stations(active);
CREATE INDEX idx_trains_active ON trains(active);
