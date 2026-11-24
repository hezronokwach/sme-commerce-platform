# Deploying (Neon + Render + Vercel)

The steps that need your accounts. The repo already holds the configuration (ADR 0018).

## 1. Database on Neon

1. In Neon, create a project with Postgres 17 in **AWS Europe Central (Frankfurt)**, the same region as
   the Render service, so every query stays close.
2. On the project dashboard, choose **Connect** and turn **Connection pooling off**. The pooled address
   (its host has `-pooler`) shares connections between clients per transaction, which breaks Flyway's
   migration lock. The direct address looks like:

   ```
   postgresql://USER:PASSWORD@ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require
   ```

3. Keep these four parts for Render: the host (`ep-xxxx.eu-central-1.aws.neon.tech`), the database
   (`neondb`), the user and the password. Nothing needs to be run by hand: the first migration creates the
   `vector` extension, which Neon supports.

## 2. Backend on Render

1. In Render, choose **New > Blueprint** and pick this repository.
2. Set the Blueprint path to `infra/render.yaml`. Render creates `sme-commerce-api`.
3. When it asks for the `sync: false` values:
   - `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`: the four parts from Neon (required);
   - `GEMINI_API_KEY`, `GROQ_API_KEY`: the assistant;
   - `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`: card payments in test mode;
   - `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_PASSKEY`: the Daraja sandbox app;
   - `MPESA_CALLBACK_BASE_URL`: this service's address, `https://sme-commerce-api.onrender.com`;
   - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`: product photos and shop logos
     (cloudinary.com > Settings > API Keys; the free plan is enough);
   - `CORS_ALLOWED_ORIGINS`: the two Vercel addresses, comma separated (leave it empty until section 4).

   The key values are the same ones as in your local `backend/.env`. Empty is fine for any of the
   optional ones: the related feature reports "not set up".
4. Wait for the deploy. The logs should show Flyway applying migrations, then `Started SmeCommerceApplication`.
   Open `https://sme-commerce-api.onrender.com/actuator/health`: it should say `UP`.
5. If Render gave the service a different address, put that address in both `vercel.json` files and in
   `MPESA_CALLBACK_BASE_URL`.

## 3. Frontends on Vercel (two projects)

1. **New Project**, import this repo, set the root directory to `admin-angular`, then deploy.
2. **New Project** again, with the root directory `storefront-react`, then deploy.
3. Neither needs environment variables: `/api` is passed on to Render by `vercel.json`.

## 4. Finish

1. Put both Vercel addresses into `CORS_ALLOWED_ORIGINS` on Render, then redeploy the backend.
2. For card payments, add a Stripe webhook to `https://sme-commerce-api.onrender.com/api/webhooks/card`
   and copy its signing secret into `STRIPE_WEBHOOK_SECRET`.
3. Sign up a shop in the admin, add a product, and buy it on the storefront with a phone ending in 0
   (the fake provider succeeds). The order should turn Paid on its own.
4. Switch the shop to M-Pesa and buy again with your own phone. The prompt arrives on the phone, and
   the order turns Paid when Safaricom calls back (or, if no callback comes, when the status check runs
   about two minutes later).

Free plans sleep when idle. A sleeping Render instance takes about a minute to answer its first request,
and a sleeping Neon database a few seconds. Open the health address shortly before a demo, or use a paid
Render instance.
