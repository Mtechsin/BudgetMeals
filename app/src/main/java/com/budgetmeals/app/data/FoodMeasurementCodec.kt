package com.budgetmeals.app.data

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class TieredSize(
    val name: String,
    val price: Double,
    val gramsEquivalent: Double? = null,
)

sealed interface FoodMeasurementType {
    object Standard : FoodMeasurementType

    data class SpoonsToGrams(
        val gramsPerSpoon: Double = 10.0,
        val spoonUnitName: String = "spoon",
    ) : FoodMeasurementType

    data class TieredSizes(
        val sizes: List<TieredSize> = listOf(
            TieredSize("Small", 10.0),
            TieredSize("Medium", 15.0),
            TieredSize("Large", 20.0),
        ),
    ) : FoodMeasurementType
}

data class FoodPreset(
    val title: String,
    val emoji: String,
    val name: String,
    val category: ItemCategory,
    val measurement: FoodMeasurementType,
    val stockUnit: String,
    val portionUnit: String,
    val portionsPerStockUnit: Double,
    val defaultCostPerPortion: Double,
    val description: String,
    val mealUsage: String = "Any",
    val purchasePrice: Double? = null,
    val purchaseQuantity: Double = 1.0,
    val menuCategory: String = "Staples",
)

object FoodMeasurementCodec {
    val menuCategories: List<String> = listOf(
        "All",
        "Bakery",
        "Dairy & Eggs",
        "Staples",
        "Produce",
        "Spreads & Oils",
        "Drinks & Snacks",
    )

    @Volatile
    private var cachedPresets: List<FoodPreset>? = null

    fun init(context: Context) {
        if (cachedPresets == null) {
            loadPresets(context)
        }
    }

    val presets: List<FoodPreset>
        get() = cachedPresets ?: emptyList()

    fun loadPresets(context: Context): List<FoodPreset> {
        val cached = cachedPresets
        if (cached != null) return cached
        return runCatching {
            val jsonString = context.assets.open("food_presets.json").bufferedReader().use { it.readText() }
            parsePresetsJson(jsonString).also { cachedPresets = it }
        }.getOrElse {
            emptyList()
        }
    }

    fun parsePresetsJson(jsonString: String): List<FoodPreset> {
        val array = JSONArray(jsonString)
        val list = mutableListOf<FoodPreset>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val measurementObj = obj.getJSONObject("measurement")
            val measurementType = when (measurementObj.getString("type")) {
                "spoons" -> FoodMeasurementType.SpoonsToGrams(
                    gramsPerSpoon = measurementObj.optDouble("gramsPerSpoon", 10.0),
                    spoonUnitName = measurementObj.optString("spoonUnitName", "spoon"),
                )
                "tiered" -> {
                    val sizesArray = measurementObj.getJSONArray("sizes")
                    val sizes = mutableListOf<TieredSize>()
                    for (j in 0 until sizesArray.length()) {
                        val s = sizesArray.getJSONObject(j)
                        sizes.add(
                            TieredSize(
                                name = s.getString("name"),
                                price = s.getDouble("price"),
                                gramsEquivalent = if (s.has("grams")) s.getDouble("grams") else null,
                            )
                        )
                    }
                    FoodMeasurementType.TieredSizes(sizes)
                }
                else -> FoodMeasurementType.Standard
            }
            val catName = obj.getString("category")
            val category = runCatching { ItemCategory.valueOf(catName) }.getOrDefault(ItemCategory.FOOD_STAPLE)
            list.add(
                FoodPreset(
                    title = obj.getString("title"),
                    emoji = obj.getString("emoji"),
                    name = obj.getString("name"),
                    category = category,
                    measurement = measurementType,
                    stockUnit = obj.getString("stockUnit"),
                    portionUnit = obj.getString("portionUnit"),
                    portionsPerStockUnit = obj.getDouble("portionsPerStockUnit"),
                    defaultCostPerPortion = obj.getDouble("defaultCostPerPortion"),
                    description = obj.optString("description", ""),
                    mealUsage = obj.optString("mealUsage", "Any"),
                    purchasePrice = if (obj.has("purchasePrice") && !obj.isNull("purchasePrice")) obj.getDouble("purchasePrice") else null,
                    purchaseQuantity = obj.optDouble("purchaseQuantity", 1.0),
                    menuCategory = obj.optString("menuCategory", "Staples"),
                )
            )
        }
        return list
    }

    fun encodeNotes(
        userNotes: String,
        measurement: FoodMeasurementType,
    ): String {
        if (measurement is FoodMeasurementType.Standard) {
            return userNotes.trim()
        }
        val root = JSONObject()
        val trimmed = userNotes.trim()
        if (trimmed.isNotBlank()) {
            root.put("userNotes", trimmed)
        }
        when (measurement) {
            is FoodMeasurementType.SpoonsToGrams -> {
                val obj = JSONObject().apply {
                    put("type", "spoons")
                    put("gramsPerSpoon", measurement.gramsPerSpoon)
                    put("spoonUnitName", measurement.spoonUnitName)
                }
                root.put("measurement", obj)
            }
            is FoodMeasurementType.TieredSizes -> {
                val array = JSONArray()
                measurement.sizes.forEach { size ->
                    val sObj = JSONObject().apply {
                        put("name", size.name)
                        put("price", size.price)
                        if (size.gramsEquivalent != null) {
                            put("grams", size.gramsEquivalent)
                        }
                    }
                    array.put(sObj)
                }
                val obj = JSONObject().apply {
                    put("type", "tiered")
                    put("sizes", array)
                }
                root.put("measurement", obj)
            }
            FoodMeasurementType.Standard -> Unit
        }
        return root.toString()
    }

    fun decodeMeasurement(
        rawNotes: String,
        name: String = "",
        portionUnit: String = "",
        stockUnit: String = "",
    ): FoodMeasurementType {
        if (rawNotes.isNotBlank() && rawNotes.trim().startsWith("{")) {
            val result = runCatching {
                val root = JSONObject(rawNotes)
                val mObj = root.optJSONObject("measurement")
                if (mObj != null) {
                    when (mObj.optString("type")) {
                        "spoons" -> {
                            val grams = mObj.optDouble("gramsPerSpoon", 10.0)
                            val unitName = mObj.optString("spoonUnitName", "spoon")
                            FoodMeasurementType.SpoonsToGrams(grams, unitName)
                        }
                        "tiered" -> {
                            val arr = mObj.optJSONArray("sizes")
                            if (arr != null && arr.length() > 0) {
                                val list = buildList {
                                    for (i in 0 until arr.length()) {
                                        val s = arr.optJSONObject(i) ?: continue
                                        val sName = s.optString("name", "Size")
                                        val price = s.optDouble("price", 0.0)
                                        val grams = if (s.has("grams")) s.optDouble("grams") else null
                                        add(TieredSize(sName, price, grams))
                                    }
                                }
                                if (list.isNotEmpty()) FoodMeasurementType.TieredSizes(list) else null
                            } else null
                        }
                        else -> null
                    }
                } else null
            }.getOrNull()
            if (result != null) return result
        }

        // Automatic smart inference from food name or units if not in JSON:
        val lowerName = name.lowercase().trim()
        val lowerPortion = portionUnit.lowercase().trim()
        if (lowerName.contains("cheese") || lowerPortion.contains("spoon")) {
            return FoodMeasurementType.SpoonsToGrams(gramsPerSpoon = 10.0, spoonUnitName = "spoon")
        }
        if (lowerName.contains("chips") || lowerName.contains("crisps") || lowerPortion.contains("size")) {
            return FoodMeasurementType.TieredSizes(
                listOf(
                    TieredSize("Small", 10.0),
                    TieredSize("Medium", 15.0),
                    TieredSize("Large", 20.0),
                ),
            )
        }

        return FoodMeasurementType.Standard
    }

    fun extractUserNotes(rawNotes: String): String {
        if (rawNotes.isNotBlank() && rawNotes.trim().startsWith("{")) {
            val extracted = runCatching {
                val root = JSONObject(rawNotes)
                root.optString("userNotes", "")
            }.getOrNull()
            if (extracted != null) return extracted
        }
        return rawNotes
    }
}
