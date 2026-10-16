# Logic review

Reviewed the app with three subagents covering persistence, calculations, and UI/state/notifications. Existing shopping UI edits were preserved.

## Fixed behavior

| Area | Corrections |
| --- | --- |
| Savings | Optional categories no longer cause failed inserts. Withdrawals check the balance atomically. Daily savings cannot be replaced when their preference marker is missing. Settings forms preserve savings bookkeeping and observe background settings changes. |
| Cash ledger | Failed writes abort multi-record changes. Purchase metadata stays synchronized with its expense. Linked purchase and new spares expenses cannot be edited independently of their source. |
| Recurring expenses | Month-end schedules retain their original day through short months. Editing an occurrence preserves its schedule. Deleting an occurrence stops repeats while retaining earlier payments. |
| Shopping | Stale Buy again actions cannot resurrect deleted rows or overwrite newer details. Purchase undo refuses to remove stock still allocated to meals. Confirmed zero prices remain known. Household purchases do not reduce the food-budget preview. |
| Meals | Closing a day preserves previously recorded stock allocations and spending. Day closures and summaries include meals outside the saved plan. Reviews, usage logging, and closure invalidation are atomic. Weekly affordability consumes one shared stock balance. |
| Units and availability | Corrected volume/spoon, kilogram/spoon, and unsized-container conversions. Generic pieces cannot invent a mass/volume conversion. Package sizes cannot disappear during conversion. Overflow returns an unavailable conversion. Future purchases are excluded from usable stock. |
| Catalog and imports | Explicit new catalog items persist with purchases. Imports reject invalid prices and quantities and recognize option/tier pricing. Meal component IDs remain distinct. Nonfinite serialized values are handled safely. Catalog edits preserve purchase units, price options, custom tier metadata, and fractional spoon sizes. |
| Forms and lifecycle | Invalid prices cannot become free purchases. Repeated submissions are guarded; failed saves retain input. Old save callbacks cannot dismiss newer sheets. Notification review requests survive recreation. |
| Reminders | Local-calendar scheduling handles DST and time-zone changes. Day closure is scheduled for midnight. Receivers restore alarms asynchronously, respect disabled reminders, and reschedule after failures. |
| Upgrades | Version 8 stock conversion preserves dated/source-tagged usage and converts linked allocations. Version 14 repairs only exact unchanged shipped recipe ingredients and eligible current/future plans; recorded meal history, edited ingredients, and saved prices are preserved. |

## Validation

The verification command builds both APKs, runs JVM regression tests, and runs Android lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

- 120 unit tests passed, with no failures or skipped tests.
- Debug app APK and instrumentation APK compiled successfully.
- 26 isolated SQLite device tests were added and compiled.
- Android lint passed with zero errors; 44 SDK/dependency/vector-resource warnings remain.

SQLite regression tests use isolated databases and preferences:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

No Android device or emulator was connected during this review. Device tests are compiled but their SQLite migration/transaction assertions and device alarm/lifecycle behavior still require execution on Android. Historical spares expenses with random IDs are not automatically relinked because their source cannot be identified reliably.
