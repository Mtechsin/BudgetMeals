package com.budgetmeals.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodCatalogJsonCodecTest {
    @Test
    fun foodCatalogJsonRoundTripsAiEditableFields() {
        val original = FoodCatalogItem(
            id = "catalog_bread",
            name = "Bread",
            category = ItemCategory.FOOD_STAPLE,
            stockUnit = "loaf",
            portionUnit = "loaf",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = 2.0,
            notes = "Any meal",
            purchasePrice = 2.0,
            purchaseQuantity = 1.0,
            purchaseUnit = "loaf",
            mealUsage = "any",
            priceKnown = true,
        )

        val restored = FoodCatalogJsonCodec.decode(FoodCatalogJsonCodec.encode(listOf(original))).getOrThrow().single()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(requireNotNull(original.purchasePrice), requireNotNull(restored.purchasePrice), 0.001)
        assertEquals(original.mealUsage, restored.mealUsage)
        assertEquals(original.cleanUserNotes, restored.cleanUserNotes)
    }

    @Test
    fun foodCatalogJsonDerivesPerPortionCostFromPackagePrice() {
        val raw = """
            {
              "format": "budgetmeals.food-catalog",
              "version": 1,
              "items": [
                {
                  "id": "catalog_jam_user",
                  "name": "Jam",
                  "category": "FOOD_STAPLE",
                  "purchaseUnit": "jar",
                  "purchaseQuantity": 1,
                  "purchasePrice": 53,
                  "portionUnit": "g",
                  "portionsPerPurchaseUnit": 680,
                  "priceKnown": true,
                  "conversionKnown": true,
                  "mealUsage": "dinner or breakfast",
                  "notes": "680 g jar"
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()

        assertEquals(53.0 / 680.0, item.defaultCostPerPortion, 0.0001)
        assertEquals("dinner or breakfast", item.mealUsage)
    }

    @Test
    fun foodCatalogJsonAcceptsTieredPricesFromAi() {
        val raw = """
            {
              "format": "budgetmeals.food-catalog",
              "version": 1,
              "items": [
                {
                  "name": "Chips",
                  "category": "SNACK",
                  "purchaseUnit": "pack",
                  "portionUnit": "pack",
                  "measurement": {
                    "type": "tiered",
                    "sizes": [
                      { "name": "Small", "price": 10 },
                      { "name": "Medium", "price": 15 },
                      { "name": "Large", "price": 20 }
                    ]
                  },
                  "mealUsage": "any",
                  "priceKnown": true
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()
        val sizes = (item.measurement as FoodMeasurementType.TieredSizes).sizes

        assertEquals(3, sizes.size)
        assertEquals(10.0, sizes[0].price, 0.001)
        assertEquals(20.0, sizes[2].price, 0.001)
    }

    @Test
    fun foodCatalogJsonCodecAcceptsSnakeCaseAliases() {
        val raw = """
            {
              "items": [
                {
                  "name": "Tomatoes",
                  "purchase_unit": "kg",
                  "portion_unit": "piece",
                  "portions_per_purchase_unit": 6,
                  "purchase_price": 18,
                  "category": "VEGETABLE",
                  "meal_usage": "any"
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()
        assertEquals("Tomatoes", item.name)
        assertEquals("kg", item.purchaseUnit)
        assertEquals("piece", item.portionUnit)
        assertEquals(6.0, item.portionsPerStockUnit, 0.001)
        assertEquals(18.0, requireNotNull(item.purchasePrice), 0.001)
        assertEquals(3.0, item.defaultCostPerPortion, 0.001)
        assertEquals("any", item.mealUsage)
    }

    @Test
    fun codecDerivesPortionPriceFromPackagePriceAndPortionCount() {
        val item = FoodCatalogJsonCodec.decode(
            """
            {
              "items": [{
                "name": "Tahini",
                "purchaseUnit": "jar",
                "purchaseQuantity": 2,
                "purchasePrice": 100,
                "portionUnit": "spoon",
                "portionsPerPurchaseUnit": 10
              }]
            }
            """.trimIndent(),
        ).getOrThrow().single()

        assertEquals(2.0, requireNotNull(item.purchaseQuantity), 0.0001)
        assertEquals(5.0, item.defaultCostPerPortion, 0.0001)
    }

    @Test
    fun scalarPriceOptionsSkipNegativeAndOverflowingNumbersAndRemainExportable() {
        val options = FoodCatalogJsonCodec.decodePriceOptions("[5, 1e999, -1, 0]")

        assertEquals(listOf(5.0, 0.0), options.map { it.price })
        assertEquals(options, FoodCatalogJsonCodec.decodePriceOptions(FoodCatalogJsonCodec.encodePriceOptions(options)))
    }
}
