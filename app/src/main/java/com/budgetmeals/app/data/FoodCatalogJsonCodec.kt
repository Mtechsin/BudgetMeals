package com.budgetmeals.app.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object FoodCatalogJsonCodec {
    const val FORMAT = "budgetmeals.food-catalog"
    const val VERSION = 1

    fun encode(items: List<FoodCatalogItem>): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("items", JSONArray().apply {
                items.sortedBy { it.name.lowercase() }.forEach { put(encodeItem(it)) }
            })
        return root.toString(2)
    }

    fun prompt(context: android.content.Context? = null): String {
        val template = (if (context != null) {
            runCatching {
                context.resources.openRawResource(com.budgetmeals.app.R.raw.food_catalog_prompt)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrNull()
        } else null) ?: DEFAULT_PROMPT
        return template
            .replace("%FORMAT%", FORMAT)
            .replace("%VERSION%", VERSION.toString())
            .replace("%CATEGORIES%", ItemCategory.entries.joinToString(", ") { it.name })
    }

    private const val DEFAULT_PROMPT = """You edit BudgetMeals food catalog JSON with authentic Egyptian market and pantry conventions.
Return only valid JSON with format "%FORMAT%" and version %VERSION%.
Each item must have: name, category, purchaseUnit, purchaseQuantity, purchasePrice, portionUnit, portionsPerPurchaseUnit, defaultCostPerPortion, priceKnown, conversionKnown, notes.
category must be one of: %CATEGORIES%.
Use 0 and priceKnown=false when a price is unknown. Use conversionKnown=false when the portion conversion is unknown.
mealUsage is free text such as "any", "breakfast, dinner", "lunch", "tea, coffee, baking", or "with ful".
Use measurement.type="tiered" with measurement.sizes for multiple sizes/prices such as chips 10/15/20 EGP.

EGYPTIAN MARKET & USAGE CONVERSIONS:
- Fresh Produce (bought in kg or head, consumed in piece or clove):
  * Tomatoes (طماطم): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=6 (~3 EGP/piece, 18-20 EGP/kg)
  * Cucumbers (خيار): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=8 (~2.5 EGP/piece, 20 EGP/kg)
  * Potatoes (بطاطس): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=4 (~5 EGP/piece, 20 EGP/kg)
  * Onions (بصل): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=6 (~3 EGP/piece, 18 EGP/kg)
  * Lemons (ليمون): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=16 (~1.5 EGP/piece, 24 EGP/kg)
  * Bell pepper (فلفل رومي): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=12 (~2 EGP/piece, 24 EGP/kg)
  * Garlic (توم): purchaseUnit="head", portionUnit="clove", portionsPerPurchaseUnit=10 (~0.5 EGP/clove, 5 EGP/head)
  * Fruit (تفاح/موز/برتقال): purchaseUnit="kg", portionUnit="piece", portionsPerPurchaseUnit=5 (~7 EGP/piece, 35 EGP/kg)
- Granular Dry Staples (bought in kg, consumed in g, cup, or spoon):
  * Sugar (سكر): purchaseUnit="kg", portionUnit="g", portionsPerPurchaseUnit=1000 (~0.035 EGP/g, 35 EGP/kg) OR portionUnit="spoon", portionsPerPurchaseUnit=100 (~0.35 EGP/spoon)
  * Flour (دقيق): purchaseUnit="kg", portionUnit="g", portionsPerPurchaseUnit=1000 (~0.025 EGP/g, 25 EGP/kg) OR portionUnit="cup", portionsPerPurchaseUnit=8 (~3.1 EGP/cup)
  * Egyptian Rice (أرز مصري): purchaseUnit="kg", portionUnit="cup", portionsPerPurchaseUnit=5 (~7 EGP/cup, 200g/cup, 35 EGP/kg)
  * Egyptian Pasta (مكرونة): purchaseUnit="pack (400g)", portionUnit="portion", portionsPerPurchaseUnit=3 (~5 EGP/portion, 15 EGP/pack)
  * Lentils (عدس): purchaseUnit="pack (500g)", portionUnit="cup", portionsPerPurchaseUnit=2.5 (~12 EGP/cup, 30 EGP/pack)
- Bakery:
  * Baladi bread (عيش بلدي): purchaseUnit="bag (5)", portionUnit="loaf", portionsPerPurchaseUnit=5 (~5 EGP/loaf, 25 EGP/bag)
  * Fino bread (عيش فينو): purchaseUnit="bag (5)", portionUnit="piece", portionsPerPurchaseUnit=5 (~2.5 EGP/piece, 12.5 EGP/bag)
- Dairy & Eggs:
  * Eggs (بيض): purchaseUnit="carton (30)", portionUnit="egg", portionsPerPurchaseUnit=30 (~5.5 EGP/egg, 165 EGP/carton)
  * Milk (لبن): purchaseUnit="bottle (1L)", portionUnit="cup", portionsPerPurchaseUnit=4 (~10 EGP/cup, 40 EGP/bottle)
  * Feta Cheese (جبنة بيضا): purchaseUnit="tub (500g)", portionUnit="spoon", portionsPerPurchaseUnit=25 (~3 EGP/spoon, 75 EGP/tub)
  * Rumi Cheese (جبنة رومي): purchaseUnit="pack (250g)", portionUnit="slice", portionsPerPurchaseUnit=10 (~12 EGP/slice, 120 EGP/pack)
  * Yogurt (زبادي): purchaseUnit="pack (6)", portionUnit="cup", portionsPerPurchaseUnit=6 (~7 EGP/cup, 42 EGP/pack)
- Oils, Spreads & Drinks:
  * Cooking oil (زيت): purchaseUnit="bottle (700ml)", portionUnit="spoon", portionsPerPurchaseUnit=50 (~1.3 EGP/spoon, 65 EGP/bottle)
  * Ghee (سمنة): purchaseUnit="tub (700g)", portionUnit="spoon", portionsPerPurchaseUnit=45 (~2.8 EGP/spoon, 125 EGP/tub)
  * Black Tea (شاي): purchaseUnit="pack (100g)", portionUnit="spoon", portionsPerPurchaseUnit=33 (~1.2 EGP/spoon, 40 EGP/pack)
  * Coffee (قهوة): purchaseUnit="jar (100g)", portionUnit="spoon", portionsPerPurchaseUnit=20 (~4 EGP/spoon, 80 EGP/jar)

purchasePrice is the total price paid for purchaseQuantity purchase units. portionsPerPurchaseUnit counts portions in ONE purchase unit. Calculate defaultCostPerPortion as purchasePrice / (purchaseQuantity * portionsPerPurchaseUnit), and keep an explicitly supplied defaultCostPerPortion authoritative.
Do not change ids for items that already exist. Do not wrap JSON in markdown."""

    fun decode(raw: String): Result<List<FoodCatalogItem>> {
        return runCatching {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removeSuffix("```")
                .trim()
            val root = if (trimmed.startsWith("[")) {
                JSONObject().put("items", JSONArray(trimmed))
            } else {
                JSONObject(trimmed)
            }
            val format = root.optString("format")
            require(format.isBlank() || format == FORMAT) {
                "Unsupported JSON format. Expected format '$FORMAT'."
            }
            val version = root.optInt("version", VERSION)
            require(version in 1..VERSION) { "Unsupported JSON version $version." }
            val array = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(decodeItem(item, index))
                }
            }.also { items ->
                require(items.isNotEmpty()) { "The file does not contain any food items." }
                require(items.map { it.id }.distinct().size == items.size) {
                    "The file contains duplicate item IDs."
                }
                val duplicateNames = items.groupBy { canonicalName(it.name) }.filterValues { it.size > 1 }
                require(duplicateNames.isEmpty()) {
                    "The file contains duplicate food names: ${duplicateNames.keys.joinToString()}."
                }
            }
        }
    }

    private fun encodeItem(item: FoodCatalogItem): JSONObject {
        val inferredPurchasePrice = item.purchasePrice
            ?: item.defaultCostPerPortion.takeIf { it > 0.0 }?.let {
                it * item.portionsPerStockUnit * (item.purchaseQuantity ?: 1.0)
            }
        val json = JSONObject()
            .put("id", item.id)
            .put("name", item.name.trim())
            .put("category", item.category.name)
            .put("purchaseUnit", item.purchaseUnit ?: item.stockUnit)
            .put("purchaseQuantity", item.purchaseQuantity ?: 1.0)
            .put("purchasePrice", inferredPurchasePrice)
            .put("portionUnit", item.portionUnit)
            .put("portionsPerPurchaseUnit", item.portionsPerStockUnit)
            .put("defaultCostPerPortion", item.defaultCostPerPortion)
            .put("mealUsage", item.mealUsage)
            .put("priceKnown", item.priceKnown)
            .put("conversionKnown", item.conversionKnown)
            .put("notes", item.cleanUserNotes)
        val measurement = encodeMeasurement(item.measurement)
        if (measurement != null) json.put("measurement", measurement)
        if (item.priceOptions.isNotEmpty()) {
            json.put("priceOptions", JSONArray().apply {
                item.priceOptions.forEach { option ->
                    put(JSONObject()
                        .put("name", option.name)
                        .put("price", option.price)
                        .put("quantity", option.quantity)
                        .put("unit", option.unit))
                }
            })
        }
        return json
    }

    private fun decodeItem(json: JSONObject, index: Int): FoodCatalogItem {
        val name = json.optString("name").trim()
        require(name.isNotBlank()) { "Item ${index + 1} is missing a name." }
        val category = json.optString("category")
            .takeIf { it.isNotBlank() }
            ?.let { value ->
                val upper = value.trim().uppercase()
                when (upper) {
                    "FOOD_FRESH", "FRESH", "VEGETABLE", "VEGETABLES", "FRUIT", "FRUITS", "PRODUCE", "DAIRY", "MEAT", "POULTRY" -> ItemCategory.FOOD_FRESH
                    "FOOD_STAPLE", "STAPLE", "STAPLES", "PANTRY", "GRAINS", "GRAIN", "SPICE", "SPICES" -> ItemCategory.FOOD_STAPLE
                    "FOOD_STREET", "STREET", "STREET_FOOD", "TAKEAWAY", "RESTAURANT" -> ItemCategory.FOOD_STREET
                    "SNACK", "SNACKS" -> ItemCategory.SNACK
                    "HOUSEHOLD", "CLEANING" -> ItemCategory.HOUSEHOLD
                    "OTHER" -> ItemCategory.OTHER
                    else -> runCatching { ItemCategory.valueOf(upper) }.getOrElse { ItemCategory.FOOD_FRESH }
                }
            }
            ?: ItemCategory.FOOD_FRESH
        val purchaseUnit = json.optString("purchaseUnit")
            .ifBlank { json.optString("purchase_unit") }
            .ifBlank { json.optString("stockUnit") }
            .ifBlank { json.optString("stock_unit") }
            .trim()
            .ifBlank { "piece" }
        val portionUnit = json.optString("portionUnit")
            .ifBlank { json.optString("portion_unit") }
            .trim()
            .ifBlank { "piece" }
        val purchaseQuantity = json.optionalPositiveDouble("purchaseQuantity")
            ?: json.optionalPositiveDouble("purchase_quantity")
            ?: json.optionalPositiveDouble("quantity")
            ?: 1.0
        val portionsPerPurchaseUnit = json.optionalPositiveDouble("portionsPerPurchaseUnit")
            ?: json.optionalPositiveDouble("portions_per_purchase_unit")
            ?: json.optionalPositiveDouble("portionsPerStockUnit")
            ?: json.optionalPositiveDouble("portions_per_stock_unit")
            ?: 1.0
        val explicitDefaultCost = json.optionalNonNegativeDouble("defaultCostPerPortion")
            ?: json.optionalNonNegativeDouble("default_cost_per_portion")
            ?: json.optionalNonNegativeDouble("cost_per_portion")
        val defaultCost = explicitDefaultCost ?: 0.0
        val explicitPurchasePrice = json.optionalNonNegativeDouble("purchasePrice")
            ?: json.optionalNonNegativeDouble("purchase_price")
        val portionsForCost = (purchaseQuantity * portionsPerPurchaseUnit).coerceAtLeast(0.0001)
        val derivedDefaultCost = explicitPurchasePrice?.div(portionsForCost) ?: 0.0
        val priceKnown = json.optBoolean("priceKnown", json.optBoolean("price_known", defaultCost > 0.0 || explicitPurchasePrice != null))
        val conversionKnown = json.optBoolean("conversionKnown", json.optBoolean("conversion_known", true))
        val priceOptions = decodePriceOptions(json.optJSONArray("priceOptions") ?: json.optJSONArray("prices"), name)
        val effectiveDefaultCost = when {
            explicitDefaultCost != null -> explicitDefaultCost
            priceOptions.isNotEmpty() -> priceOptions.map { it.price }.average()
            else -> derivedDefaultCost
        }
        val measurement = decodeMeasurement(json.optJSONObject("measurement"), name)
        val userNotes = json.optString("notes").trim()
        return FoodCatalogItem(
            id = json.optString("id").trim().ifBlank { UUID.randomUUID().toString() },
            name = name,
            category = category,
            stockUnit = purchaseUnit,
            portionUnit = portionUnit,
            portionsPerStockUnit = portionsPerPurchaseUnit,
            defaultCostPerPortion = effectiveDefaultCost,
            notes = FoodMeasurementCodec.encodeNotes(userNotes, measurement),
            purchasePrice = explicitPurchasePrice,
            purchaseQuantity = purchaseQuantity,
            purchaseUnit = purchaseUnit,
            mealUsage = json.optString("mealUsage", json.optString("meal_usage", json.optString("usage"))).trim(),
            priceOptions = priceOptions,
            priceKnown = priceKnown,
            conversionKnown = conversionKnown,
        )
    }

    fun encodePriceOptions(options: List<FoodPriceOption>): String {
        if (options.isEmpty()) return ""
        return JSONArray().apply {
            options.forEach { option ->
                put(JSONObject()
                    .put("name", option.name)
                    .put("price", option.price)
                    .put("quantity", option.quantity)
                    .put("unit", option.unit))
            }
        }.toString()
    }

    fun decodePriceOptions(raw: String): List<FoodPriceOption> {
        if (raw.isBlank()) return emptyList()
        return decodePriceOptions(runCatching { JSONArray(raw) }.getOrNull(), "")
    }

    private fun encodeMeasurement(measurement: FoodMeasurementType): JSONObject? = when (measurement) {
        FoodMeasurementType.Standard -> null
        is FoodMeasurementType.SpoonsToGrams -> JSONObject()
            .put("type", "spoons")
            .put("gramsPerSpoon", measurement.gramsPerSpoon)
            .put("portionUnitName", measurement.spoonUnitName)
        is FoodMeasurementType.TieredSizes -> JSONObject()
            .put("type", "tiered")
            .put("sizes", JSONArray().apply {
                measurement.sizes.forEach { size ->
                    put(JSONObject()
                        .put("name", size.name)
                        .put("price", size.price)
                        .put("grams", size.gramsEquivalent))
                }
            })
    }

    private fun decodeMeasurement(json: JSONObject?, name: String): FoodMeasurementType {
        if (json == null) return FoodMeasurementType.Standard
        return when (json.optString("type").lowercase()) {
            "spoons", "spoon", "grams" -> FoodMeasurementType.SpoonsToGrams(
                gramsPerSpoon = json.optionalPositiveDouble("gramsPerSpoon") ?: 10.0,
                spoonUnitName = json.optString("portionUnitName", json.optString("spoonUnitName", "spoon")).trim()
                    .ifBlank { "spoon" },
            )
            "tiered", "tiered_sizes", "sizes" -> {
                val sizes = decodeTieredSizes(json.optJSONArray("sizes"), name)
                if (sizes.isEmpty()) FoodMeasurementType.Standard else FoodMeasurementType.TieredSizes(sizes)
            }
            else -> FoodMeasurementType.Standard
        }
    }

    private fun decodeTieredSizes(array: JSONArray?, name: String): List<TieredSize> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val sizeName = json.optString("name").trim().ifBlank { "Size ${index + 1}" }
                val price = json.optDouble("price", Double.NaN)
                if (price.isFinite() && price >= 0.0) {
                    val grams = if (json.isNull("grams")) null else json.optDouble("grams", Double.NaN).takeIf { it.isFinite() }
                    add(TieredSize(sizeName, price, grams))
                }
            }
        }.ifEmpty {
            when (name.lowercase()) {
                "chips", "crisps" -> listOf(TieredSize("Small", 10.0), TieredSize("Medium", 15.0), TieredSize("Large", 20.0))
                else -> emptyList()
            }
        }
    }

    private fun decodePriceOptions(array: JSONArray?, name: String): List<FoodPriceOption> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                if (json != null) {
                    val price = json.optDouble("price", Double.NaN)
                    if (price.isFinite() && price >= 0.0) {
                        add(
                            FoodPriceOption(
                                name = json.optString("name").trim().ifBlank { "Option ${index + 1}" },
                                price = price,
                                quantity = json.optionalPositiveDouble("quantity"),
                                unit = json.optString("unit").trim().ifBlank { null },
                            ),
                        )
                    }
                } else {
                    val price = (array.opt(index) as? Number)?.toDouble()
                    if (price != null && price.isFinite() && price >= 0.0) {
                        add(FoodPriceOption("Option ${index + 1}", price))
                    }
                }
            }
        }
    }

    private fun JSONObject.optionalPositiveDouble(key: String): Double? {
        if (isNull(key) || !has(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun JSONObject.optionalNonNegativeDouble(key: String): Double? {
        if (isNull(key) || !has(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun canonicalName(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
}
