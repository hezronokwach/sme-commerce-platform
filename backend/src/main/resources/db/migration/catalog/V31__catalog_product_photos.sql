-- Issue "Shop settings and product photos": a product can have up to five photos, in the order the
-- owner chose. The first is the main photo, shown in the product list.
-- The photos live on Cloudinary; only their https addresses are stored here (ADR 0022).

ALTER TABLE catalog.products ADD COLUMN image_urls text[] NOT NULL DEFAULT '{}';

-- Keep the one photo a product may already have, as its first.
UPDATE catalog.products SET image_urls = ARRAY[image_url] WHERE image_url IS NOT NULL;

ALTER TABLE catalog.products DROP COLUMN image_url;

ALTER TABLE catalog.products
    ADD CONSTRAINT ck_products_at_most_five_photos CHECK (cardinality(image_urls) <= 5);
