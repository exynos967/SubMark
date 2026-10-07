# core:data

Repositories (read side + simple CRUD) and services (multi-table business operations) shared by all features.
Package root: `io.github.submark.core.data`. Everything is a Hilt `@Singleton`; inject the class directly.

## Conventions

- **Results.** Operations that can fail for a user-facing reason return `DataResult<T>`
  (`Success(value)` / `Failure(error: DataError)`); exceptions mean bugs.
  `DataError` cases: `NotFound`, `Invalid(reason: InvalidReason, detail)`, `InUse(count)`,
  `DuplicateAppStoreId(existing)`, `InsufficientFunds(walletId, available, required)`, `RateUnavailable(code)`,
  `UnsupportedCurrency(code)`, `Stale`, `Network(message)`. Map each `InvalidReason` to a localized string.
  Helpers: `getOrNull()`, `errorOrNull()`, `isSuccess`, `map {}`, `onSuccess {}`, `onFailure {}`.
- **Atomicity.** Every multi-table write runs in one Room transaction; a failure (e.g. wallet charge) saves nothing.
- **Change bus.** After every committed mutation `ChangeNotifier` calls every `SubscriptionChangeListener`
  (`suspend fun onSubscriptionsChanged(ids: Set<String>?)`, `null` = anything changed). Contribute one with
  `@Binds @IntoSet` in your feature module (notifications, calendar sync, widgets).
- **App start.** Contribute `change.AppStartListener` (`suspend fun onAppStart()`) with `@Binds @IntoSet` for work needed at every launch; the app calls all of them after seeding and `processDue()`.
- **Time.** Inject `TimeProvider` (`today(): LocalDate`, `now(): Instant`, `zone(): ZoneId`); never call `LocalDate.now()`.
- **System notes.** Notes the data layer writes start with `@` (`SystemNotes.PAYMENT_DELETED`, `PAYMENT_EDITED`,
  `SUBSCRIPTION_DELETED`, `STORED_VALUE_RECORD_DELETED`, `INITIAL_BALANCE`); localize them, check with `SystemNotes.isSystem(note)`.
  Payment records carry no generated note text: derive labels from `kind`/`source`/`markTiming`/`extensionFrom..To`.

## App wiring (owned by `app`)

1. On start, in order: `DataSeeder.seed()` → `SubscriptionService.processDue()` (show its summary as alerts) →
   `RateRefreshWorker.schedule(context)` → optionally `CurrencyRepository.refreshRates()`.
2. `RateRefreshWorker` is a `@HiltWorker`: the Application must implement `Configuration.Provider` with
   `HiltWorkerFactory` and disable the default WorkManager initializer in the manifest.
3. `processDue()` is idempotent and safe to call from a daily worker too.

## Settings — `settings.SettingsRepository`

| Member | Description |
|---|---|
| `val settings: Flow<AppSettings>` | All preferences (typed DataStore, JSON file `app_settings.json`). |
| `suspend fun update(transform: (AppSettings) -> AppSettings)` | Atomic read-modify-write. |

`AppSettings` groups: `display`, `font`, `navigation`, `list`, `calendar`, `overview`, `analytics`, `poster`, `share`,
`search`, `addForm` (`AddFormSettings.FULL` / `MINIMAL` presets), `subscriptions`, `money`, `notifications`, `security`,
`integrations`, `onboardingCompleted`, `developerOptionsUnlocked`. All fields have defaults; UI enums live in
`settings/SettingsEnums.kt`. Component lists (`ComponentSetting<T>(id, visible)`) are ordered = display order.
`money.defaultCurrencyCode` defaults to the device-locale currency (fallback USD) on first launch.

## Secrets — `secret.SecretStore`

Android Keystore AES-256-GCM; file in `noBackupFilesDir` (never auto-backed-up, never exported). Keys: `model.SecretKeys`.

| Method | Description |
|---|---|
| `suspend fun get(key: String): String?` | Decrypted value, null if absent or undecryptable. |
| `suspend fun put(key: String, value: String)` | Store/replace. |
| `suspend fun remove(key: String)` | Delete one. |
| `suspend fun clear()` | Delete all. |

## Network — `di.NetworkModule`

Injectable `OkHttpClient` (15 s connect / 30 s read+write, `User-Agent: SubMark/<version>`) and
`Json { ignoreUnknownKeys = true; encodeDefaults = true }`. `network.AppInfo.versionName` gives the app version.

## Currencies — `currency.CurrencyRepository`

Rates are units per 1 USD (`Currency.usdRate`). Sources in order: Frankfurter (`api.frankfurter.dev/v1`),
fawazahmed0 via jsDelivr, fawazahmed0 via pages.dev. `CurrencyCatalog` lists ~44 built-in fiat currencies.

| Method | Description |
|---|---|
| `observeCurrencies(): Flow<List<Currency>>` | All currencies (enabled first). |
| `observeEnabled(): Flow<List<Currency>>` | Enabled only. |
| `observeEnabledSorted(): Flow<List<Currency>>` | Enabled, default currency first. |
| `suspend get(code): Currency?` | One currency. |
| `suspend converter(): CurrencyConverter` | Snapshot over current rates. |
| `observeConverter(): Flow<CurrencyConverter>` | Re-emits when rates change. |
| `suspend refreshRates(): DataResult<Int>` | Fetch latest for AUTO-mode currencies; count updated. |
| `suspend fetchSuggestedRate(code): DataResult<SuggestedRate>` | Provider rate for the custom-currency form (`UnsupportedCurrency` / `Network`). |
| `suspend enable(code)` / `disable(code)` | Toggle visibility; default currency cannot be disabled. |
| `suspend setDefault(code)` | Enable + make reporting currency (nothing is rebased). |
| `suspend addCustom(code, name, symbol, usdRate, rateMode = MANUAL): DataResult<Currency>` | Code 2–10 `[A-Z0-9]`, rate in (0, 1 000 000). Use `CurrencyConverter.usdRateFromDefault` to convert a "1 default = x" rate. |
| `suspend update(code, name?, symbol?, usdRate?, rateMode?): DataResult<Currency>` | Rate/mode for any currency; name/symbol custom only. |
| `suspend delete(code): DataResult<Unit>` | Custom only; `InUse(n)` while subscriptions/payments/wallets use it. |
| `observeHistoricalStats(): Flow<HistoricalRateStats>` | count / oldest / newest cached day. |
| `suspend historicalRate(code, date): BigDecimal?` | Cache → providers → current rate fallback (MANUAL = current). |
| `suspend historicalConverter(date): CurrencyConverter` | Converter as of a day (missing codes use current rates). |
| `suspend preloadRecent(days = 30, onProgress: (done, total) -> Unit): DataResult<Int>` | Warm the cache. |
| `suspend cleanupHistorical()` | Drop rows fetched > 6 months ago. |
| `suspend clearHistorical()` | Clear the cache. |

`RateRefreshWorker.schedule(context)` — daily, network-constrained refresh + cleanup.

## Seeding — `seed.DataSeeder`

`suspend fun seed()` inserts missing presets only (idempotent): 10 system categories (`SystemCategories`, ids
`cat_<key>`, `OTHER_ID`), 9 payment methods (`SystemPaymentMethods.*` ids `pm_*`), the currency catalogue with the
default currency + USD/EUR/CNY/GBP/JPY enabled on first run.

## Repositories

### `repository.SubscriptionRepository` (read only)
| Method | Description |
|---|---|
| `observeAll()` / `observe(id)` / `observeChildren(parentId)` | Raw rows. |
| `suspend get(id)` / `getAll()` | Snapshots. |
| `observeItems(): Flow<List<SubscriptionItem>>` | Subscription + category + tags + bundle children. |
| `observeItem(id): Flow<SubscriptionItem?>` | One item. |
| `suspend findByAppStoreId(appStoreId, excludeId = "")` | Duplicate lookup. |

### `repository.CategoryRepository`
`observeAll()`, `observeVisible()`, `observeHidden()`, `get(id)`,
`create(name, iconType, iconValue, colorHex): DataResult<Category>`, `update(category)` (preset: null name = localized preset name),
`resetPresetStyle(id)`, `hide(id)` (system only; subscriptions → Other), `restore(id)`,
`delete(id)` (custom only; subscriptions → Other), `reorder(orderedIds)`, `resetToDefault()`. "Other" can't be hidden/deleted.

### `repository.TagRepository`
`observeTags()`, `observeSubscriptionTags()`, `observeTagsWithUsage(): Flow<List<TagWithUsage>>`, `getTagIds(subscriptionId)`,
`create(name, color, iconType?, iconValue?)` (unique, case-insensitive), `findOrCreate(name)`, `update(tag)`,
`delete(id)` (links cascade), `setSubscriptionTags(subscriptionId, tagIds)`,
`observeFolders(): Flow<List<TagFolderWithTags>>` (`matches(subscriptionTagIds)`), `saveFolder(folder, tagIds)` (≥ 1 tag),
`deleteFolder(id)`, `reorderFolders(orderedIds)`. Pure helper: `TagFolderMatcher.matches(mode, folderTagIds, subscriptionTagIds)`.

### `repository.CustomFieldRepository`
`observeFields(): Flow<List<CustomFieldWithOptions>>`, `observeValues(subscriptionId)`, `observeStats(): Flow<List<CustomFieldStats>>`,
`fieldsFor(categoryId)` (active global + category fields), `saveField(definition, options)` (DROPDOWN ≥ 1 option),
`deleteField(id)` (values cascade), `setActive(id, active)`, `reorder(orderedIds)`,
`setValues(subscriptionId, categoryId, values: Map<fieldId, String?>)` (required + format checks; unlisted values kept).
`CustomFieldRepository.isValid(type, value, optionIds)` validates one encoded value.

### `repository.PaymentMethodRepository`
`observeAll()`, `get(id)`, `add(name, iconValue)`, `update(method)` (custom only), `delete(id)` (custom only; subscriptions unlinked),
`resetToDefault()`.

### `repository.SubscriptionExtrasRepository`
`observePhotos(subscriptionId)`, `addPhoto(subscriptionId, fileName)`, `deletePhoto(photo)`, `reorderPhotos(subscriptionId, orderedIds)`,
`observeReminders(subscriptionId)`, `observeAllReminders()`, `setReminders(subscriptionId, List<Pair<daysBefore, LocalTime>>)`.
Photo files are managed by the caller.

## Services

### `service.SubscriptionService`
| Method | Description |
|---|---|
| `suspend findDuplicateAppStoreId(appStoreId, excludeId = ""): Subscription?` | For the "Add anyway" dialog. |
| `suspend create(draft: SubscriptionDraft): DataResult<String>` | Validates, schedules (history, LIFETIME purchase + optional wallet charge, STORED_VALUE initial deposit), bundle MAIN + children, tags, custom fields. Returns main id. |
| `suspend update(draft): DataResult<Unit>` | Start date locked once payments exist; schedule recomputed when cycle/trial/start/single-cycle end change. Children are edited individually. |
| `suspend hasWalletCharges(id): Boolean` | Whether to show the keep/reverse wallet dialog. |
| `suspend delete(id, reverseWalletCharges = false): DataResult<DeleteOutcome>` | Deletes (MAIN: + children); returns photo file names to remove. |
| `suspend pause(id)` / `activate(id)` | Activate moves a past next date to the first occurrence from today (no backfill). |
| `suspend restoreFromArchive(id)` | ACTIVE, anchor = next = today, expired end date cleared. |
| `suspend markPaid(id, newCycle: Boolean, source = USER_MANUAL): DataResult<MarkPaidOutcome>` | Current due occurrence paid today (early always allowed). Shared → user's share; wallet-linked → charged (failure saves nothing); stored value → deduction; bundle MAIN → payment sync. |
| `suspend extend(id, by: ExtendBy, fee: BigDecimal?, expectedUpdatedAt: Instant): DataResult<LocalDate>` | Single-cycle only; EXTENSION record; `Stale` when changed. |
| `suspend resolveTrial(id, renewal: AUTO\|MANUAL): DataResult<MarkPaidOutcome>` | TRIAL_EXPIRED payment dated trial end. |
| `suspend activateWishlist(id, toLifetime: Boolean, walletId: String? = null): DataResult<Unit>` | Start = today. |
| `suspend processDue(): ProcessDueSummary` | AUTO backfill (SYSTEM_OVERDUE past / SYSTEM_AUTO today), stored-value auto deduction, auto-pause expired, report trial ends and wallet failures. |

`SubscriptionDraft(subscription, tagIds, customFieldValues, generateHistory, purchaseWalletId, initialDeposit,
initialDepositWalletId, children, allowDuplicateAppStoreId)` — `subscription` carries form fields; timestamps and
schedule fields are assigned by the service. `ExtendBy.Days(1..1000)`, `Months(1..120)`, `Until(date)`.

### `service.PaymentService`
| Method | Description |
|---|---|
| `observeAll()`, `observeForSubscription(id)`, `observeBetween(from, to)`, `observe(id)`, `suspend get(id)` | Reads. |
| `isReadOnly(record)` | STORED_VALUE_DEPOSIT records are edited via `StoredValueService`. |
| `suspend add(NewPayment): DataResult<AddPaymentOutcome>` | SUCCESS-only date effects: regular latest payment → next = date + cycle; END_DATE (target ≥ payment date and ≥ current end); NEXT_BILLING (clears end date); IAP never moves dates; sync price; wallet charge; reactivates a paused subscription whose new coverage is after today. Stores prev* dates. |
| `suspend edit(PaymentEdit): DataResult<PaymentRecord>` | Amount/currency/date/status/IAP/note; wallet re-charged with compensating transactions; optional stale check. |
| `suspend delete(id): DataResult<Unit>` | Wallet reversal in the same transaction; restores prev* dates if it is the latest date-moving record and nothing changed since; deposits delegate to stored value rules. |

### `service.WalletService`
| Method | Description |
|---|---|
| `observeWallets()` (non-deleted), `observeAllWallets()`, `observe(id)`, `observeTransactions(walletId)`, `observeAllTransactions()`, `get(id)`, `getTransaction(id)` | Reads. |
| `suspend create(name, kind, currencyCode, initialBalance = 0, creditLimit?, colorHex?, iconValue?)` | Opening balance as ADJUSTMENT. |
| `suspend update(wallet)` | Currency locked after the first transaction (`isCurrencyLocked(id)`). |
| `suspend deactivate(id)` / `reactivate(id)` / `delete(id)` | Deactivate/delete unlink subscriptions; delete is soft and irreversible. |
| `suspend topUp(id, amount, note?)` / `deduct(id, amount, note?)` | Manual transactions; kind rules apply. |
| `suspend reverseTransaction(txnId, note?)` | Compensating transaction. |
| `suspend chargeProblem(walletId, amount, currencyCode): DataError?` | For wallet pickers (insufficient / rate unavailable / inactive). |
| `observeTrackedAssets(currencyCode): Flow<TrackedAssets>` | Active BALANCE_TRACKED wallets only. |
| `WalletService.coverage(subscription, wallet, converter, linkedCount): WalletCoverage` | Payable cycles / covers-until (null = unlimited). |

Kind rules: BALANCE_TRACKED never negative; CREDIT down to `-creditLimit` (null = unlimited); SETTLEMENT_ONLY unlimited.

### `service.StoredValueService`
`observeRecords(subscriptionId)`, `observeAllRecords()`,
`topUp(subscriptionId, amount, currencyCode, description?, walletId?): DataResult<StoredValueRecord>` (DEPOSIT + STORED_VALUE_DEPOSIT payment),
`deleteRecord(recordId)` (deposit: refused if balance would go negative; deletes its payment, reverses its wallet charge),
`recomputeBalance(subscriptionId): DataResult<BigDecimal>`.

### `service.SharedService`
`observeConfig(id)`, `observeMembers(id)`, `observeAllConfigs()`, `observeAllMembers()`,
`enable(subscriptionId, creatorName)`, `disable(subscriptionId)` (deletes members), `updateDescription(id, text?)`,
`setSplitMode(id, mode)`, `addMember(member): DataResult<SharedMember>`, `updateMember(member)`, `deleteMember(subscriptionId, memberId)`
(creator protected), `userShare(subscriptionId): BigDecimal?`. EQUAL mode re-balances ratios on every member change.

## Backup

### `backup.ExportService`
| Method | Description |
|---|---|
| `suspend export(): ExportBundle` | Consistent snapshot (no secrets, settings, rate cache or backup profiles). |
| `encode(bundle): String` / `decode(text): DataResult<ExportBundle>` | JSON; newer `exportVersion` → `UNSUPPORTED_EXPORT_VERSION`. |
| `suspend import(bundle, mode: RestoreMode): DataResult<ImportResult>` | MERGE inserts missing ids, REPLACE_MATCHING upserts, EXACT clears covered tables first; one transaction; re-seeds and notifies. Count keys = `ExportBundle` property names. |

### `backup.DataResetService`
`suspend clearAll()` — wipes all tables, secrets and settings (keeps onboarding flag and default currency), re-seeds, notifies.
