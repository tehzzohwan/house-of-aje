-- Service categories group bookable services (e.g. "Lashes" contains
-- "Classic Lashes", "Volume Lashes"). Categories are labels only: they are
-- not bookable themselves and carry no price or duration.
CREATE TABLE service_categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_service_categories_updated
    BEFORE UPDATE ON service_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE services ADD COLUMN category_id UUID REFERENCES service_categories(id);
CREATE INDEX idx_services_category ON services (category_id);
