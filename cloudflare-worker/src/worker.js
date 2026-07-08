/**
 * LiveCaptionN premium entitlement Worker.
 *
 * Serves the github-flavor (Stripe) side of the app's billing system. Must
 * stay entirely on Cloudflare's free plan: one KV namespace (ENTITLEMENTS),
 * no Durable Objects, no Queues, no cron triggers.
 *
 * Endpoints:
 *   POST /checkout    { email, product } -> { checkoutUrl }
 *   POST /webhook      Stripe webhook receiver
 *   POST /entitlement  { sessionId } | { email } -> { entitlements, licenseToken, issuedAt, revalidateAfter }
 *   GET  /portal -> { portalUrl }  (static Stripe-hosted portal login page; Stripe itself verifies the email)
 *
 * Required secrets (set via `wrangler secret put <name>`):
 *   STRIPE_SECRET_KEY       - Stripe secret key (sk_...)
 *   STRIPE_WEBHOOK_SECRET   - Stripe webhook signing secret (whsec_...)
 *   LICENSE_SIGNING_SECRET  - random string used to HMAC-sign license tokens
 *   OWNER_ALLOWLIST         - comma-separated emails that always get full entitlement
 *   OWNER_ACCESS_KEY        - required alongside an allowlisted email to actually get the bypass
 *   STRIPE_PORTAL_LOGIN_URL - Stripe's shareable Customer Portal login page URL
 *                             (Stripe Dashboard -> Settings -> Billing -> Customer portal -> "Enable link").
 *                             Stripe hosts its own email verification there, so this Worker never
 *                             has to authenticate the requester itself.
 *
 * Non-secret vars (wrangler.jsonc): STRIPE_PRICE_AD_FREE, STRIPE_PRICE_PRO
 *
 * Trust model for /entitlement:
 *   - `sessionId` (returned by Stripe right after a successful Checkout) is verified against
 *     Stripe's own record of that session (payment_status must be "paid") before a token is
 *     minted — this is the strong, forgery-proof path and is what `purchase()` uses.
 *   - A bare `email` (used only by the "restore purchase on a new device" flow) is NOT proof of
 *     ownership by itself — anyone who knows a subscriber's email could otherwise mint a token
 *     for it. It is rate-limited hard (see RATE_LIMIT_*) to bound abuse. This mirrors the trust
 *     model of the app's local-only entitlement cache: low value target, deliberately simple.
 */

const LICENSE_TTL_SECONDS = 35 * 24 * 60 * 60; // ~35 days
const REVALIDATE_AFTER_SECONDS = 24 * 60 * 60; // client revalidates ~daily
const WEBHOOK_TOLERANCE_SECONDS = 5 * 60;
const RATE_LIMIT_MAX_PER_WINDOW = 5;
const RATE_LIMIT_WINDOW_SECONDS = 24 * 60 * 60;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/checkout") {
        return await handleCheckout(request, env);
      }
      if (request.method === "POST" && url.pathname === "/webhook") {
        return await handleWebhook(request, env);
      }
      if (request.method === "POST" && url.pathname === "/entitlement") {
        return await handleEntitlement(request, env);
      }
      if (url.pathname === "/portal") {
        return await handlePortal(request, env);
      }
      if (url.pathname === "/checkout/success" || url.pathname === "/checkout/cancel") {
        const success = url.pathname === "/checkout/success";
        const sessionId = success ? url.searchParams.get("session_id") : null;
        return htmlResponse(checkoutResultPage(success, sessionId));
      }
      return jsonResponse({ error: "Not found" }, 404);
    } catch (err) {
      console.error(err);
      return jsonResponse({ error: err.message || "Internal error" }, 500);
    }
  }
};

// ── /checkout ──

async function handleCheckout(request, env) {
  const body = await request.json();
  const email = normalizeEmail(body.email);
  const product = body.product;
  if (!email || (product !== "AD_FREE" && product !== "PRO")) {
    return jsonResponse({ error: "email and product (AD_FREE|PRO) are required" }, 400);
  }
  const priceId = product === "PRO" ? env.STRIPE_PRICE_PRO : env.STRIPE_PRICE_AD_FREE;
  if (!priceId) {
    return jsonResponse({ error: `No Stripe price configured for ${product}` }, 500);
  }
  const baseUrl = `${new URL(request.url).origin}`;
  const session = await stripeFetch(env, "POST", "/checkout/sessions", {
    mode: "subscription",
    customer_email: email,
    client_reference_id: email,
    line_items: [{ price: priceId, quantity: 1 }],
    success_url: `${baseUrl}/checkout/success?session_id={CHECKOUT_SESSION_ID}`,
    cancel_url: `${baseUrl}/checkout/cancel`
  });
  return jsonResponse({ checkoutUrl: session.url });
}

// ── /webhook ──

async function handleWebhook(request, env) {
  const signatureHeader = request.headers.get("Stripe-Signature");
  const payload = await request.text();
  if (!signatureHeader || !(await verifyStripeSignature(payload, signatureHeader, env.STRIPE_WEBHOOK_SECRET))) {
    return jsonResponse({ error: "Invalid signature" }, 400);
  }
  const event = JSON.parse(payload);

  const relevantTypes = [
    "checkout.session.completed",
    "customer.subscription.updated",
    "customer.subscription.deleted"
  ];
  if (!relevantTypes.includes(event.type)) {
    return jsonResponse({ received: true });
  }

  const customerId = event.data.object.customer;
  if (!customerId) {
    return jsonResponse({ received: true });
  }

  const email = await resolveCustomerEmail(env, event.data.object, customerId);
  if (!email) {
    return jsonResponse({ received: true });
  }

  await recomputeAndStoreEntitlement(env, email, customerId);
  return jsonResponse({ received: true });
}

async function resolveCustomerEmail(env, eventObject, customerId) {
  if (eventObject.customer_details?.email) return normalizeEmail(eventObject.customer_details.email);
  if (eventObject.customer_email) return normalizeEmail(eventObject.customer_email);
  const customer = await stripeFetch(env, "GET", `/customers/${customerId}`);
  return customer.email ? normalizeEmail(customer.email) : null;
}

async function recomputeAndStoreEntitlement(env, email, stripeCustomerId) {
  const subs = await stripeFetch(env, "GET", "/subscriptions", null, {
    customer: stripeCustomerId,
    status: "all",
    limit: "10"
  });

  const subscriptions = {};
  const entitlements = new Set();
  for (const sub of subs.data || []) {
    const product = sub.items?.data?.[0]?.price?.id === env.STRIPE_PRICE_PRO ? "PRO" : "AD_FREE";
    const key = product === "PRO" ? "pro" : "ad_free";
    subscriptions[key] = {
      status: sub.status,
      currentPeriodEnd: sub.current_period_end,
      stripeSubscriptionId: sub.id
    };
    if (sub.status === "active" || sub.status === "trialing") {
      entitlements.add(product === "PRO" ? "PRO" : "AD_FREE");
    }
  }

  const record = {
    email,
    stripeCustomerId,
    entitlements: Array.from(entitlements),
    subscriptions,
    updatedAt: Math.floor(Date.now() / 1000)
  };
  await env.ENTITLEMENTS.put(email, JSON.stringify(record));
  return record;
}

// ── /entitlement ──

async function handleEntitlement(request, env) {
  const body = await request.json();
  const sessionId = typeof body.sessionId === "string" ? body.sessionId : null;
  const ownerKey = typeof body.ownerKey === "string" ? body.ownerKey : null;

  let email;
  if (sessionId) {
    // Strong path: prove ownership via Stripe's own record of a completed Checkout Session.
    const session = await stripeFetch(env, "GET", `/checkout/sessions/${encodeURIComponent(sessionId)}`);
    if (session.payment_status !== "paid" && session.status !== "complete") {
      return jsonResponse({ error: "Checkout session is not completed" }, 403);
    }
    email = normalizeEmail(session.customer_details?.email || session.customer_email);
    if (!email) {
      return jsonResponse({ error: "Checkout session has no email" }, 400);
    }
    if (session.customer) {
      await recomputeAndStoreEntitlement(env, email, session.customer);
    }
  } else {
    // Weak path: bare email, used only by "restore purchase on a new device".
    // Not proof of ownership — rate-limited to bound abuse (see file header).
    email = normalizeEmail(body.email);
    if (!email) {
      return jsonResponse({ error: "sessionId or email is required" }, 400);
    }
    const withinLimit = await checkAndConsumeRateLimit(env, `entitlement:${email}`);
    if (!withinLimit) {
      return jsonResponse({ error: "Too many requests for this email. Try again later." }, 429);
    }
  }

  let entitlements;
  if (isAllowlisted(env, email, ownerKey)) {
    entitlements = ["AD_FREE", "PRO"];
  } else {
    const record = await getEntitlementRecord(env, email);
    entitlements = record?.entitlements ?? [];
  }

  const issuedAt = Math.floor(Date.now() / 1000);
  const licenseToken = await signLicenseToken(env, {
    email,
    entitlements,
    iat: issuedAt,
    exp: issuedAt + LICENSE_TTL_SECONDS
  });

  return jsonResponse({
    email,
    entitlements,
    licenseToken,
    issuedAt,
    revalidateAfter: issuedAt + REVALIDATE_AFTER_SECONDS
  });
}

function isAllowlisted(env, email, ownerKey) {
  if (!env.OWNER_ACCESS_KEY || ownerKey !== env.OWNER_ACCESS_KEY) return false;
  const list = (env.OWNER_ALLOWLIST || "")
    .split(",")
    .map((e) => normalizeEmail(e))
    .filter(Boolean);
  return list.includes(email);
}

/** Simple KV-backed fixed-window rate limiter. Returns false once the window's cap is hit. */
async function checkAndConsumeRateLimit(env, key) {
  const rateLimitKey = `ratelimit:${key}`;
  const current = Number((await env.ENTITLEMENTS.get(rateLimitKey)) || "0");
  if (current >= RATE_LIMIT_MAX_PER_WINDOW) return false;
  await env.ENTITLEMENTS.put(rateLimitKey, String(current + 1), { expirationTtl: RATE_LIMIT_WINDOW_SECONDS });
  return true;
}

async function getEntitlementRecord(env, email) {
  const json = await env.ENTITLEMENTS.get(email);
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}

// ── /portal ──

async function handlePortal(request, env) {
  // Deliberately does not accept an email and does not create a per-customer
  // portal session itself — that would mean trusting a bare email as proof of
  // ownership (see file header). Instead this returns Stripe's own hosted
  // Customer Portal login page, where Stripe verifies the user's email with
  // its own magic-link flow before showing anything billing-related.
  if (!env.STRIPE_PORTAL_LOGIN_URL) {
    return jsonResponse({ error: "Customer portal is not configured" }, 500);
  }
  return jsonResponse({ portalUrl: env.STRIPE_PORTAL_LOGIN_URL });
}

// ── Stripe API helper (dependency-free, form-encoded requests) ──

async function stripeFetch(env, method, path, body, query) {
  let url = `https://api.stripe.com/v1${path}`;
  if (query) {
    const qs = new URLSearchParams(query).toString();
    url += `?${qs}`;
  }
  const init = {
    method,
    headers: {
      Authorization: `Basic ${btoa(`${env.STRIPE_SECRET_KEY}:`)}`,
      "Stripe-Version": "2024-06-20"
    }
  };
  if (body) {
    init.headers["Content-Type"] = "application/x-www-form-urlencoded";
    init.body = toFormBody(body).join("&");
  }
  const res = await fetch(url, init);
  const json = await res.json();
  if (!res.ok) {
    throw new Error(`Stripe API error (${res.status}): ${json.error?.message || "unknown"}`);
  }
  return json;
}

function encodeKey(parts) {
  return parts[0] + parts.slice(1).map((p) => `[${p}]`).join("");
}

function toFormBody(obj, prefix = [], out = []) {
  for (const [k, v] of Object.entries(obj)) {
    if (v === undefined || v === null) continue;
    const keyParts = [...prefix, k];
    if (Array.isArray(v)) {
      v.forEach((item, i) => {
        if (item !== null && typeof item === "object") {
          toFormBody(item, [...keyParts, String(i)], out);
        } else {
          out.push(`${encodeURIComponent(encodeKey(keyParts))}=${encodeURIComponent(String(item))}`);
        }
      });
    } else if (typeof v === "object") {
      toFormBody(v, keyParts, out);
    } else {
      out.push(`${encodeURIComponent(encodeKey(keyParts))}=${encodeURIComponent(String(v))}`);
    }
  }
  return out;
}

// ── Stripe webhook signature verification (Web Crypto HMAC-SHA256) ──

async function verifyStripeSignature(payload, signatureHeader, secret) {
  const parts = Object.fromEntries(
    signatureHeader.split(",").map((kv) => {
      const [k, v] = kv.split("=");
      return [k, v];
    })
  );
  const timestamp = parts.t;
  const signature = parts.v1;
  if (!timestamp || !signature) return false;

  const nowSeconds = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSeconds - Number(timestamp)) > WEBHOOK_TOLERANCE_SECONDS) return false;

  const expected = await hmacSha256Hex(secret, `${timestamp}.${payload}`);
  return timingSafeEqual(expected, signature);
}

// ── License token: compact HMAC-signed payload, decoded (not re-verified) client-side ──

async function signLicenseToken(env, payload) {
  const payloadB64 = base64UrlEncode(JSON.stringify(payload));
  const signature = await hmacSha256Hex(env.LICENSE_SIGNING_SECRET, payloadB64);
  return `${payloadB64}.${signature}`;
}

async function hmacSha256Hex(secret, message) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signatureBuffer = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return [...new Uint8Array(signatureBuffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}

function base64UrlEncode(str) {
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ── Small helpers ──

function normalizeEmail(email) {
  return (email || "").trim().toLowerCase() || null;
}

function jsonResponse(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function htmlResponse(html) {
  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
}

function checkoutResultPage(success, sessionId) {
  const title = success ? "Thanks for subscribing!" : "Checkout canceled";
  const message = success
    ? "You're all set. Redirecting you back to LiveCaptionN..."
    : "No charge was made. You can close this tab and return to LiveCaptionN.";
  // On success, bounce straight back into the app via its custom URI scheme,
  // carrying the Stripe Checkout session ID so the app can prove ownership
  // to /entitlement without ever trusting a bare email (see file header).
  const redirect =
    success && sessionId
      ? `<meta http-equiv="refresh" content="0; url=livecaptionn://premium-return?session_id=${encodeURIComponent(sessionId)}">`
      : "";
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
${redirect}<title>${title}</title></head><body style="font-family: sans-serif; text-align: center; padding: 48px 16px;">
<h1>${title}</h1><p>${message}</p></body></html>`;
}
