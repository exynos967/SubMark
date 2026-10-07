# core:ui — component catalogue

Package root `io.github.submark.core.ui`. Shared strings: `ui_action_save/delete/edit/add/cancel/ok/confirm/done/retry`.

## theme
- `ThemeConfig(darkMode: DarkMode, dynamicColor, fontFamily: AppFontFamily, fontScale, lineSpacing, followSystemFontScale, colorfulCards)`; `SubMarkTheme(config) { }`; `LocalThemeConfig`.
- `SubMarkTheme.extendedColors` (success/warning/isDark), `SubMarkTheme.chartPalette` (8 colour-blind-safe colours), `ChartOtherColor`.

## format (pure)
- `UiText` (`raw`/`res`/`plural`/concat) → `.asString(context)` or composable `.asString()`.
- `MoneyFormatter.format(amount, currencyCode, symbol?, locale, hideDecimals, compact, showPlusSign)`; `MoneyInput.sanitize/parse/validate`.
- `DateLabels.relative/due/overdue/countdownLevel/duration/formatDate/formatMonthDay/formatYearMonth`.
- `CycleLabels.label(cycle, count, unit, showYmd)` / `label(sub, showYmd)` / `perSuffix`.
- Enum mappers: `.labelRes` on all model enums, `.descriptionRes` on SplitMode/WalletKind, `BadgeTone`, `PaymentStatus.tone`, `TagColor.color(dark)` / `themedColor()`, `SystemCategory.defaultIconName/defaultColorHex`, `Category.displayName(): UiText`.
- `DateQuickPick` with `.apply(today)`.

## icon
- `IconCatalog` (`all`, `[name]`, `vector(name)`, `group(g)`, `search(q)`), 173 Material Rounded icons in 11 `IconGroup`s. Names are persisted — never rename.
- `SubscriptionIcon(type, value, fallbackName, modifier, size, tint, background, shape)` — SYMBOL/EMOJI/URL(Coil)/FILE(`filesDir/icons/`), monogram fallback.
- `IconChoice(type, value).encode()` / `IconChoice.decode(s)` — the `"TYPE|value"` format of `NavResults.ICON`. `ICON_DIR`, `monogramOf(name)`.

## component
- Scaffolding: `SubMarkTopAppBar`, `EmptyState`, `LoadingState`, `ErrorState`, `SectionHeader`, `SectionCard`.
- Settings: `SettingsGroup`, `SettingsNavRow`, `SettingsSwitchRow`, `SettingsValueRow`.
- `ConfirmDialog(title, message?, onConfirm, onDismiss, confirmLabel, dismissLabel, destructive)`.
- Money: `LocalMoneyDisplayOptions`/`MoneyDisplayOptions(hideDecimals)` (provided once by app), `formatMoney(...)`, `MoneyText`, `MoneyInputField`.
- Badges/chips: `StatusBadge`, `CountdownBadge`, `CategoryChip`, `TagChip`.
- Inputs: `SegmentedTabs`, `SearchField`, `ColorPickerDialog` (null = default), `DatePickerField`, `SubMarkDatePickerDialog`, `TimePickerField`.
- `ReorderableItemsColumn(items, key, onMove) { item, isDragging, dragHandle -> }` + `DragHandleIcon` (non-lazy, short lists).
- `CurrencyPickerSheet(currencies, selectedCode, onSelect, onDismiss, defaultCode)`.
- `MarkPaidDialog(name, amountText, dueDate, today, onConfirm: (MarkTiming) -> Unit, onDismiss)`; `MarkPaidSituation.of(due, today)`.
- `SubscriptionCard(name, priceText, cycleText, dateText, secondaryText, iconType, iconValue, dueDate, today, badges, accentColor, variant LIST|GRID, dimmed, onClick, onLongClick)`; `SubscriptionBadge.of(sub, today, includeAuto)`.

## chart (Canvas)
- `ChartEntry(label, value: Double, color?)`; `LineBarChart(entries, style BAR|LINE, selectedIndex, onSelect, ...)`; `DonutChart(...)` (merge tail beyond 8 into "Other"); `HeatmapGrid(days: List<HeatmapDay>, ...)`; `RadarChart(axes, series: List<RadarSeries>, ...)`; `ChartLegend`; `ChartMath`.

## util
- `SnackbarMessage` + `SnackbarEffect(messages: Flow, hostState)`; `colorFromHex`, `Color.toHex()`, `Color.contentColorFor()`, `Modifier.thenIf`.
