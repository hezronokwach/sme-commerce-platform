# 0022: Product photos and logos on Cloudinary, uploaded straight from the browser

Status: Accepted (2026-11-24)

## Context

Owners need to add photos to their products and a logo to their shop. Until now a product had one
`image_url` column that nothing in the admin could fill.

Photos can't be kept by the backend itself:
- Render's free disk is wiped on every deploy.
- Neon's free database has 0.5 GB, too little for photos, and serving them through our app would be slow.

## Decision

1. **Photos live on Cloudinary**, which stores them and serves resized copies from a CDN. The free plan
   is enough for this project. The keys come from the environment (`CLOUDINARY_CLOUD_NAME`,
   `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`); without them the admin says uploads aren't set up.
2. **The browser uploads straight to Cloudinary, with a signature from our backend.**
   - `POST /api/admin/photo-uploads/signature` returns a SHA-1 signature over the folder and a timestamp,
     made with the API secret, which never leaves the server.
   - The folder is `sme-commerce/<shop id>`, so each shop's photos stay apart, and the signature can't be
     reused for another folder. Cloudinary refuses signatures older than an hour.
   - The file never passes through our server: no upload size limits on Render, no extra bandwidth.
   - The admin sends the file with the browser's own `fetch`, not Angular's HttpClient, so our access
     token is never sent to Cloudinary.
3. **A product has up to five photos, stored as an ordered `text[]`** (`image_urls`, V31, replacing
   `image_url`). The first is the main photo. A separate table would add a join and an entity for a
   short list that always belongs to one product and is always read with it. Responses still carry
   `imageUrl` (the main photo) next to `imageUrls`, so lists don't need the whole array.
4. **Only https addresses are accepted** for photos and logos, so the storefront never loads anything over
   plain http.
5. **The frontends ask Cloudinary for the size they need** (`c_limit,w_800,f_auto,q_auto` in the address),
   so phones download small, modern-format copies.
6. The signing endpoint lives in catalog, since product photos are its job. The shop's logo uses the same
   endpoint; tenant only stores the logo's address.

## Consequences

- One more account and three more secrets on Render.
- Removing a photo in the admin removes it from the product, not from Cloudinary. A clean-up job (delete
  photos no product or shop uses) is left for later; on the free plan the leftovers cost nothing.
- The Database Design document still describes the single `image_url` column; this record supersedes it.
