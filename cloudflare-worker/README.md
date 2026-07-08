# LiveCaptionN Premium Worker

Cloudflare Worker backing the `github` flavor's Stripe subscriptions (Ad-Free
/ Pro). Runs entirely on Cloudflare's free plan — one KV namespace, no
Durable Objects, no paid add-ons.

## Endpoints

- `POST /checkout` `{ email, product: "AD_FREE"|"PRO" }` -> `{ checkoutUrl }`
- `POST /webhook` — Stripe webhook receiver (checkout completed / subscription updated / deleted)
- `POST /entitlement` `{ sessionId }` (strong, post-purchase) or `{ email, ownerKey? }` (weak, rate-limited restore) -> `{ entitlements, licenseToken, issuedAt, revalidateAfter }`
- `GET /portal` -> `{ portalUrl }` — Stripe's own hosted Customer Portal login page (Stripe verifies the email itself)

### Security model

`/entitlement` only trusts a bare email for the "restore on a new device" case, and even then
it's rate-limited (5 requests/email/day) — a bare email is not proof of ownership. The primary
purchase flow instead sends the Stripe Checkout `session_id` right after payment completes,
which this Worker verifies against Stripe's own record before minting a token. `/portal` never
accepts an email at all; it hands back Stripe's hosted portal login link, where Stripe itself
does email verification. The `OWNER_ALLOWLIST` bypass additionally requires a matching
`OWNER_ACCESS_KEY` so knowing the owner's email alone isn't enough to get free entitlement.

## One-time setup

1. `npm install`
2. Create the two Stripe subscription Products/Prices (Ad-Free monthly, Pro monthly) and note their price IDs.
3. Fill `STRIPE_PRICE_AD_FREE` / `STRIPE_PRICE_PRO` in `wrangler.jsonc`.
4. In the Stripe Dashboard, go to Settings -> Billing -> Customer portal, enable the shareable login link, and copy its URL.
5. Set secrets (never committed):
   ```
   wrangler secret put STRIPE_SECRET_KEY
   wrangler secret put STRIPE_WEBHOOK_SECRET
   wrangler secret put LICENSE_SIGNING_SECRET   # any long random string
   wrangler secret put OWNER_ALLOWLIST          # comma-separated emails, e.g. you@example.com
   wrangler secret put OWNER_ACCESS_KEY         # any long random string, kept only in your own local.properties
   wrangler secret put STRIPE_PORTAL_LOGIN_URL  # the URL from step 4
   ```
6. `npm run deploy`
7. In the Stripe Dashboard, add a webhook endpoint pointing at `https://<worker>.workers.dev/webhook` subscribed to `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`, and copy its signing secret into `STRIPE_WEBHOOK_SECRET` (step 5).

## Local dev

`npm run dev` — put secrets in `.dev.vars` (gitignored) for local testing.
