# SubMark Architecture

SubMark is an open-source Android subscription manager (Kotlin, Jetpack Compose, Material 3).
minSdk 29, targetSdk 35. Languages: English (default `values/`) and Simplified Chinese (`values-zh-rCN/`).
Every feature is free; there is no paywall, no "premium" gate and no cloud account.

## Modules and dependency rules

```
app ──► feature:* ──► core:ui ─┐
  │          │                 ├─► core:domain ─► core:model
  │          └──► core:data ───┤
  └────────────► core:database ┘
```

| Module | Contents | May depend on |
|---|---|---|
| `core:model` | Domain models (also Room entities via `room-common`), enums, serializers, `ExportBundle`, `SubscriptionPrefill` | nothing Android |
| `core:domain` | Pure calculation: `BillingCalculator`, `CostCalculator`, `CurrencyConverter`, `SplitCalculator`, `StoredValueCalculator` | model |
| `core:database` | Room DB, converters, DAOs (one file per domain) | model |
| `core:data` | Repositories/services (transactions that span tables), settings (DataStore), `SecretStore`, HTTP client, rate providers, seeding, change-event bus | model, domain, database |
| `core:ui` | Theme, formatting, shared composables, `navigation/Routes.kt` (the navigation contract) | model, domain |
| `feature:*` | Screens + ViewModels + feature-specific workers/clients for one domain | core:* only — **never another feature** |
| `app` | `MainActivity`, app shell (tabs), NavHost wiring, app lock gate, Application | everything |

Features talk to each other only through:
1. **Routes** in `core/ui/navigation/Routes.kt` (any feature may navigate to any route; the owner registers the composable).
2. **Services in `core:data`** (shared business operations).
3. **`SubscriptionChangeListener`** multibinding in `core:data`: features that react to data changes (notifications, calendar sync, widgets) contribute `@IntoSet` listeners instead of being called directly.

## Feature module conventions

- Each feature exposes one public entry point file `XxxNavigation.kt` with
  `fun NavGraphBuilder.xxxGraph(navController: NavController)` registering every route it owns.
  Tab features also expose their tab screen through the same function. `app` calls these.
- Screen = stateless `XxxScreen(state, onAction…)` + `XxxRoute()` wrapper that gets a `hiltViewModel()`.
- ViewModels expose `StateFlow<XxxUiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
  One-off events (snackbar, navigate) via `Channel`/`SharedFlow` or callback lambdas.
- No hard-coded user-visible text: every string in `res/values/strings.xml` **and** `res/values-zh-rCN/strings.xml`.
  Prefix string names with the feature name (`subscriptions_…`) to avoid merge clashes. Write your own text; never copy MarkBuy wording.
- Use Material 3 components and `Icons.*` from `material-icons-extended`. Shared building blocks live in `core:ui` — reuse before writing new ones.
- Money is `BigDecimal`, never `Double`. Format through `core:ui` formatters.
- Dates: `LocalDate` for billing, `Instant` for timestamps, `java.time` everywhere. "Today" comes from an injected `Clock` (`core:data`), never `LocalDate.now()` directly, so logic is testable.
- Unit tests for every non-trivial ViewModel/business rule in `src/test`.

## Key design decisions

| Topic | Decision |
|---|---|
| Entities | Domain models are Room entities (annotations from `room-common`); no parallel entity/DTO layer. |
| IDs | UUID strings. |
| Money storage | `BigDecimal` as TEXT. Aggregate in Kotlin, not SQL. |
| Exchange rates | Stored as units per 1 USD (pivot). Conversion A→B = amount / r(A) × r(B). Changing default currency rebases nothing. Providers: Frankfurter → fawazahmed0 (jsDelivr) → fawazahmed0 (pages.dev). |
| Billing schedule | Occurrence k = anchor + k·cycle, computed from `cycleAnchorDate`; month steps clamp to month end. Custom cycles store count + unit (no day folding). |
| Fixed payment day | Month/year cycles only; applies to occurrences after the anchor. |
| Mark paid | ORIGINAL_CYCLE keeps the anchor; NEW_CYCLE re-bases the anchor to the payment date. Early marking always allowed with that choice. |
| Archive | Not stored. PAUSED + archive-mode pref = archived. Restore: ACTIVE, anchor = next = today, expired end date cleared. |
| Stored value | Top-ups create a `STORED_VALUE_DEPOSIT` payment (counted as spending). Auto deductions only change the balance + create a `StoredValueRecord`, so money is never counted twice. |
| Wallets | One DB transaction for payment + wallet transaction + balance. Deletes/edits create compensating transactions. Soft-deleted wallets are never touched. |
| Price monitoring | App price only (iTunes lookup API). In-app purchase prices have no public API → out of scope. |
| Calendar sync | Events in an app-owned local calendar via `CalendarContract`. No "Reminders" equivalent on Android. |
| Secrets | API keys/passwords in `SecretStore` (Android Keystore AES-GCM), never in Room, never in exports. |
| Popular catalogue | MarkBuyRepo-compatible JSON from user-added URLs; no default repository is bundled (upstream has no license). |
| iCloud | Not implemented. WebDAV backup + JSON export cover data portability. |

## Feature specs

Detailed behaviour specs (inferred from the original app, paraphrased) live outside the repo in
`../artifacts/specs/` during development. They contain open questions; the table above records the resolutions.
