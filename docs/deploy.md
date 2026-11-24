# Deploying (Render + Vercel)

The steps that need your accounts. The repo already holds the configuration (ADR 0018).

## 1. Backend and database on Render

1. In Render, choose **New > Blueprint** and pick this repository.
2. Set the Blueprint path to `infra/render.yaml`. Render creates `sme-commerce-db` and `sme-commerce-api`.
3. When it asks for the `sync: false` values, fill in what you have. Empty is fine for now, and the related
   feature reports "not set up":
   - `GEMINI_API_KEY`, `GROQ_API_KEY`: the assistant;
   - `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`: card payments in test mode;
   - `CORS_ALLOWED_ORIGINS`: the two Vercel addresses, comma separated (step 3).
4. Wait for the deploy. The logs should show Flyway applying migrations, then `Started SmeCommerceApplication`.
   Open `https://sme-commerce-api.onrender.com/actuator/health`: it should say `UP`.
5. If Render gave the service a different address, put that address in both `vercel.json` files.

## 2. Frontends on Vercel (two projects)

1. **New Project**, import this repo, set the root directory to `admin-angular`, then deploy.
2. **New Project** again, with the root directory `storefront-react`, then deploy.
3. Neither needs environment variables: `/api` is passed on to Render by `vercel.json`.

## 3. Finish

1. Put both Vercel addresses into `CORS_ALLOWED_ORIGINS` on Render, then redeploy the backend.
2. For card payments, add a Stripe webhook to `https://sme-commerce-api.onrender.com/api/webhooks/card`
   and copy its signing secret into `STRIPE_WEBHOOK_SECRET`.
3. Sign up a shop in the admin, add a product, and buy it on the storefront with a phone ending in 0
   (the fake provider succeeds). The order should turn Paid on its own.

A free Render instance sleeps when idle, and its first request takes a while. Use a paid instance for a demo.
