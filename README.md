# BudgetMeals

BudgetMeals is a native Android app built with Kotlin and Jetpack Compose. It is designed around short, repeatable actions instead of a long data-entry form.

## What is included

- Home dashboard that starts with today's saved meal plan and the individual foods in each meal
- End-of-day meal review for food eaten, leftovers, and skipped meals
- Automatic catch-up that closes an untouched previous day as skipped when the app opens again
- Purchase calculator: price, quantity, unit, rough daily use, cost per unit, cost per day, and estimated days left
- Every purchase can be remembered for the next shop by default
- Shopping list with one-tap buy. Buying updates stock and food spending together
- Stock batches with weekly and monthly filters, low-stock warnings, and usage logging
- Food catalog for reusable ingredients, with search, type-to-add, stock links, and portion conversion
- Meal shortcuts built from individual food components, quantities, units, and optional per-food stock tracking
- Linked stock is reduced when a meal is marked eaten or saved in the day review, using FIFO stock batches
- Purchase price and category are locked after saving so the cash ledger stays accurate; corrections happen through Recalculate
- Meal plan window starts on the current day and runs through Friday
- Cash budget based on food purchases once, without charging meal check-ins again
- Budget correction tool for forgotten spending or matching the app to the month's real total
- Expense tracker for food, internet, calling, household, and custom categories
- Spares account with save and spend transactions, treat affordability, and streaks
- Local SQLite storage with no account or network required
- Android notification scheduling and Android share-sheet CSV export
- Editable seeded menu, batch items, and expense categories

## Build

Open the project in Android Studio, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

The project uses the Android SDK at the path in `local.properties` for this machine. Android Studio can recreate that file if the SDK path is different.

## First-use flow

1. Tap **I bought something**.
2. Enter the name, price, and quantity.
3. Leave **Remember for the next shop** on.
4. Save. The purchase is counted as food spending, added to stock, and added to the shopping list.

For a meal already in stock, use the meal plan and tap the check mark. That records consumption only. The purchase remains the money event, so the meal is not charged twice. For a one-off street meal, add it as a purchase with `piece` or `plate` as the unit.

At 10 PM, the reminder opens the day review. Meals left unmarked default to skipped. The app also closes the day automatically at 11:59 PM, and catches up any older open plan the next time it loads. Linked food components reduce their stock quantity when a meal is marked eaten or when the review is saved. A component without a stock link stays visible in the meal and records a shortage instead of silently changing inventory.

If spending was missed or entered twice, use **Match real spending** on Home or in More. The correction stays in the expense ledger and can be removed later.

## Food catalog and stock links

Open **Plan**, add or edit a meal, then add each food as its own row. Search the catalog or type a name and choose **Add to food catalog**. In a stock item, choose the same catalog food and set how many portions are in one stock unit. For example, a tomato catalog item can use `1 g = 0.01 piece`; a meal that uses two pieces removes `200 g` from a kilogram stock batch. If the stock unit is kilograms, enter `10` instead.

The catalog is stored locally. Purchases remain the cash-budget event. Meal check-ins and day reviews only update consumption and linked stock quantities.

## Food catalog JSON and AI population

Open **More → Food items** to import or export the catalog as JSON. Export creates an AI-editable file with package prices, portion conversions, tiered prices, notes, and meal usage. Import validates the file, keeps existing IDs, and merges items into the local catalog. Use **Copy AI catalog instructions** to copy a prompt that tells an LLM the accepted JSON structure. `food-catalog.example.json` contains a populated starter file based on the current kitchen list.

## Notes

The app is local-first. It does not send ledger data anywhere. The export action uses Android's normal share sheet so the user chooses the destination.
