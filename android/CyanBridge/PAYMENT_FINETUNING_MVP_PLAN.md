# Payment Finetuning MVP Plan

The production billing source is `/home/fertroll10/Documents/CyanBridgeLabs/Cyanbridge_website`. I excluded the Termux test server and made no code changes.

## Recommendation

Do not ship the current English direct-card beta unchanged.

Use this order of preference:

1. Evaluate the newer Asaas `/v3/checkouts` recurring hosted checkout with a pre-created `foreignCustomer`. It keeps card data away from CyanBridge and normally limits PCI scope to SAQ-A.
2. If it still cannot provide a suitable English experience, retain the English CyanBridge form with Asaas tokenization, but treat it as a PCI SAQ-D payment system.
3. Keep the Portuguese legacy invoice page only as a temporary fallback behind a feature flag.

HTTPS is mandatory, but Asaas explicitly says server-side tokenization remains SAQ-D because PAN and CVV pass through CyanBridge’s Vercel backend.

## Current Findings

- The direct English form already exists in `app/api/web-subscribe/route.ts:188` and tokenizes through `lib/asaas.ts:678`.
- The form correctly asks only for cardholder name, email, card number, expiry, and CVV.
- Asaas customers are created with `foreignCustomer: true` at `app/api/web-subscribe/route.ts:303`.
- The published tokenization schema still marks CPF/CNPJ, postal code, address number, and phone as required. This conflicts with what Asaas support told you about foreign customers and must be verified against live production before release.
- `lib/billing-catalog.ts` now owns separate provider prices: Asaas references `$1.13`, `$5.25`, and `$20.70` per month before BRL conversion; Paddle prices are `$1.55`, `$5.75`, and `$21.50`.
- The deployed `POST /api/billing/checkout-sessions` endpoint requires a bearer token and an explicit `asaas` or `paddle` provider. It returns a short-lived opaque `/web-subscribe?checkout_session=...` URL.
- Android presents an explicit provider choice for website checkout instead of letting the server select Paddle implicitly.
- The Paddle production page contains “beta,” environment details, price IDs, and test-card instructions at `PaddleCheckoutClient.tsx:159-205`.
- The browser never receives an Android bearer token. Android creates an authenticated checkout session and opens only its opaque checkout URL.
- `ensureRelayUser()` can restore an existing account solely by matching an unverified email at `lib/relay-kv.ts:174-205`. This is an account-takeover risk and must be fixed before improving checkout.
- The Android callback only consumes its one-time opaque result and verifies entitlement with the server; it does not trust entitlement data from URL parameters.
- The Asaas webhook acknowledges events even when durable event persistence fails at `app/api/webhooks/asaas/route.ts:334-389`. That conflicts with Asaas’s webhook guidance.
- Payment webhooks do not directly activate or renew the corresponding RelayUser. The current flow therefore polls Asaas repeatedly.
- Current checkout tests still target the old legacy HTML and do not cover foreign-customer tokenization, the direct form, Paddle pricing, duplicate submissions, or cross-provider plan changes.

## Implementation Plan

1. **Run a controlled production capability spike**

Validate these operations with a dedicated test customer:

- Create a customer using only `name`, `email`, `externalReference`, and `foreignCustomer: true`.
- Create a modern recurring `/v3/checkouts` session for that customer and inspect its required fields and language.
- Tokenize a foreign customer’s card using only name and email in `creditCardHolderInfo`.
- Create a monthly subscription with the resulting token and `nextDueDate` set to today.
- Capture the exact webhook sequence for authorization, confirmation, rejection, refund, and cancellation.
- Obtain written confirmation from Asaas that the omitted holder fields are supported for foreign customers in production.

2. **Keep checkout authentication provider-specific**

The deployed checkout-session API is the only Android checkout entry point:

```text
Android POST /api/billing/checkout-sessions
Authorization: Bearer <account token>
Body: { plan, provider, return_url, change_plan }
                ↓
Server returns an opaque, short-lived HTTPS checkout URL
                ↓
Browser sees only the random checkout-session ID
```

The session contains the user ID, plan, provider, return URL, expiry, and state. It must not expose the account API token. The callback contains only the one-time opaque result, and Android calls `/pro/verify` after returning.

Also:

- Stop restoring accounts through unverified email.
- Require the existing bearer token or verified email login/magic link.
- Allowlist callback destinations.
- Replace entitlement-bearing custom-scheme parameters with a one-time result ID.
- Have Android call `/pro/verify` after returning.
- Move the Android account credential to Keystore-backed storage and exclude it from backups.

3. **Create one canonical provider-aware catalog**

Represent each plan as a base product and separate provider offers:

| Plan | Base/Asaas price | Paddle checkout price | Paddle adjustment |
|---|---:|---:|---:|
| Cheap | $1.00 equivalent in BRL | $1.55 | $0.55 |
| Standard | $5.00 equivalent in BRL | $5.75 | $0.75 |
| Max | $20.00 equivalent in BRL | $21.50 | $1.50 |

Use wording such as:

> Paddle checkout price: $5.75/month
> Includes a $0.75 adjustment for Paddle’s payment, tax, and Merchant of Record service. Any applicable tax is shown before payment.

The current adjustments are `base + 5% of base + $0.50`; they do not completely gross up Paddle’s 5% fee because Paddle applies its percentage to the final charged amount. Keep configured Paddle product prices authoritative rather than recalculating them in clients.

For Asaas, display both the base USD reference and exact locked BRL renewal amount before payment.

4. **Rebuild the website checkout journey**

Use a clean three-stage layout:

- Plan and provider selection.
- Customer/payment details.
- Final recurring-payment review and consent.

For foreign Asaas customers, collect only:

- Full/cardholder name.
- Email.
- Card number, expiration, and CVV when direct tokenization is enabled.

Do not request CPF, CNPJ, phone, Brazilian postal code, or Brazilian address unless the live API test proves one is genuinely required.

For direct tokenization:

- Never persist or log PAN or CVV.
- Never repopulate card fields after an error.
- Set `Cache-Control: no-store`, strict CSP, `Referrer-Policy: no-referrer`, HSTS, and restrictive frame/form policies.
- Add rate limiting, CSRF protection, bot/card-testing controls, and an idempotency lock.
- Use at least a 60-second Asaas timeout as documented.
- Keep the token transient and immediately create the subscription.
- Display the exact recurring amount, currency, billing date, cancellation terms, processor, and privacy links before submission.

For Paddle:

- Replace the auto-opening beta screen with a polished provider review page and embedded/inline Paddle checkout.
- Show the provider adjustment before launching Paddle.
- Use Paddle checkout events to show actual subtotal, taxes, total today, and renewal total.
- Remove price IDs, environment information, and test cards from production.

5. **Make payment state webhook-driven**

Introduce a durable checkout-attempt state machine:

```text
created → awaiting_payment → confirmed
                         ↘ failed
                         ↘ canceled
                         ↘ expired
                         ↘ refunded/chargeback
```

- Store provider IDs and `externalReference` mappings with direct indexes instead of scanning every KV user.
- Persist webhook event IDs durably before returning HTTP 200.
- Process duplicate events idempotently.
- Grant entitlement only after `PAYMENT_CONFIRMED` or `PAYMENT_RECEIVED`, not because a subscription object exists or a browser reached `successUrl`.
- Poll only CyanBridge’s own checkout-session state while the browser waits; do not poll Asaas every four seconds.
- Handle renewal, overdue, refund, chargeback, cancellation, and expiration events.
- During provider changes, activate the new subscription first, then cancel the old provider subscription exactly once.

6. **Migrate the Android subscription UI to Material 3**

Add shared KMP billing models and reducer state under `shared/src/commonMain`:

- `BillingPlan`
- `BillingProvider`
- `ProviderOffer`
- `CheckoutState`
- `SubscriptionStatus`

Keep Play Billing, Custom Tabs, Keystore, and deep/app-link handling Android-owned.

The Compose screen should show:

- Clear plan cards.
- Asaas as the lower-cost BRL option.
- Paddle as the globally localized Merchant of Record option.
- Exact provider-specific price breakdown.
- Current provider, renewal date, and cancellation state.
- A secure-checkout explanation without claiming that CyanBridge “never handles card data” when direct Asaas tokenization is active.

Update `COMPOSE_MIGRATION_PLAN.md` from `legacy` to `hybrid` during the transition and `compose` after parity tests pass.

7. **Testing And Rollout**

Add coverage for:

- Foreign customer creation without Brazilian fields.
- Direct tokenization success, decline, timeout, and duplicate submission.
- No PAN/CVV/token exposure in logs, URLs, HTML responses, analytics, or exception telemetry.
- Checkout-session expiration and replay prevention.
- Forged callback rejection.
- Webhook duplicates and out-of-order events.
- Asaas-to-Paddle and Paddle-to-Asaas plan changes.
- Provider-specific pricing and exchange-rate snapshots.
- Paddle tax and renewal totals.
- Compose accessibility, small screens, process recreation, and callback recovery.

Use feature flags such as `ASAAS_CHECKOUT_MODE=modern_hosted|direct|legacy`. Keep `direct` disabled in production until the live foreign-customer test and PCI SAQ-D readiness are complete.

## Relevant Documentation

- [Asaas llms.txt](https://docs.asaas.com/llms.txt)
- [PCI-DSS responsibilities](https://docs.asaas.com/docs/pci-dss.md)
- [Tokenization](https://docs.asaas.com/docs/tokenization.md)
- [Credit-card subscriptions](https://docs.asaas.com/docs/subscriptions-via-credit-card.md)
- [Modern Asaas Checkout](https://docs.asaas.com/docs/asaas-checkout.md)
- [Creating foreign customers](https://docs.asaas.com/reference/criar-novo-cliente.md)
- [Paddle pricing](https://www.paddle.com/pricing)
