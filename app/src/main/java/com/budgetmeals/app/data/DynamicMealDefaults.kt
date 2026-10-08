package com.budgetmeals.app.data

internal object DynamicMealDefaults {
    val catalog: List<FoodCatalogItem> = listOf(
        FoodCatalogItem("catalog_oil", "Oil", ItemCategory.FOOD_STAPLE, "bottle (700ml)", "spoon", 50.0, 1.3),
        FoodCatalogItem("catalog_tea", "Tea", ItemCategory.FOOD_STAPLE, "pack (100g)", "spoon", 33.0, 1.2),
        FoodCatalogItem("catalog_sugar", "Sugar", ItemCategory.FOOD_STAPLE, "kg", "g", 1000.0, 0.035),
        FoodCatalogItem("catalog_fruit", "Fruit", ItemCategory.FOOD_FRESH, "kg", "piece", 5.0, 7.0),
        FoodCatalogItem("catalog_tomatoes", "Tomatoes", ItemCategory.FOOD_FRESH, "kg", "piece", 6.0, 3.0),
        FoodCatalogItem("catalog_cucumbers", "Cucumbers", ItemCategory.FOOD_FRESH, "kg", "piece", 8.0, 2.5),
        FoodCatalogItem("catalog_potatoes", "Potatoes", ItemCategory.FOOD_FRESH, "kg", "piece", 4.0, 5.0),
        FoodCatalogItem("catalog_onions_fresh", "Onions", ItemCategory.FOOD_FRESH, "kg", "piece", 6.0, 3.0),
        FoodCatalogItem("catalog_eggs", "Eggs", ItemCategory.FOOD_FRESH, "carton (30)", "egg", 30.0, 5.5),
        FoodCatalogItem(
            "catalog_cheese",
            "Cheese",
            ItemCategory.FOOD_FRESH,
            "tub (500g)",
            "spoon",
            25.0,
            3.0,
            FoodMeasurementCodec.encodeNotes(
                userNotes = "Feta / White cheese (1 spoon ≈ 20g)",
                measurement = FoodMeasurementType.SpoonsToGrams(20.0, "spoon"),
            ),
        ),
        FoodCatalogItem("catalog_yogurt", "Yogurt", ItemCategory.FOOD_FRESH, "pack (6)", "cup", 6.0, 7.0),
        FoodCatalogItem("catalog_bread", "Baladi bread", ItemCategory.FOOD_STAPLE, "bag (5)", "loaf", 5.0, 5.0),
        FoodCatalogItem("catalog_ful", "Ful medames", ItemCategory.FOOD_STAPLE, "bag", "portion", 1.0, 10.0),
        FoodCatalogItem("catalog_falafel", "Falafel", ItemCategory.FOOD_STAPLE, "bag (10)", "piece", 10.0, 3.5),
        FoodCatalogItem("catalog_rice", "Rice", ItemCategory.FOOD_STAPLE, "kg", "cup", 5.0, 7.0),
        FoodCatalogItem("catalog_koshari", "Koshari", ItemCategory.FOOD_STAPLE, "portion", "portion", 1.0, 30.0),
        FoodCatalogItem("catalog_fava", "Split fava", ItemCategory.FOOD_STAPLE, "pack (500g)", "portion", 5.0, 5.0),
        FoodCatalogItem("catalog_tomato_sauce", "Tomato sauce", ItemCategory.FOOD_STAPLE, "jar (320g)", "spoon", 10.0, 2.5),
        FoodCatalogItem("catalog_onions", "Fried onions", ItemCategory.FOOD_STAPLE, "pack (200g)", "spoon", 20.0, 2.5),
        FoodCatalogItem("catalog_pickles", "Pickles", ItemCategory.FOOD_FRESH, "jar", "portion", 10.0, 3.0),
        FoodCatalogItem("catalog_jam", "Jam", ItemCategory.FOOD_STAPLE, "jar (380g)", "spoon", 19.0, 3.2),
        FoodCatalogItem("catalog_milk", "Milk", ItemCategory.FOOD_FRESH, "bottle (1L)", "cup", 4.0, 10.0),
        FoodCatalogItem("catalog_tahina", "Tahina", ItemCategory.FOOD_STAPLE, "jar (300g)", "spoon", 20.0, 2.5),
        FoodCatalogItem("catalog_salad", "Salad", ItemCategory.FOOD_FRESH, "portion", "portion", 1.0, 5.0),
        FoodCatalogItem("catalog_honey", "Honey", ItemCategory.FOOD_STAPLE, "jar (500g)", "spoon", 25.0, 3.5),
        FoodCatalogItem(
            "catalog_chips",
            "Chips",
            ItemCategory.SNACK,
            "pack",
            "pack",
            1.0,
            15.0,
            FoodMeasurementCodec.encodeNotes(
                userNotes = "Snack bags measured by size (Small 10 EGP, Medium 15 EGP, Large 20 EGP)",
                measurement = FoodMeasurementType.TieredSizes(
                    listOf(
                        TieredSize("Small", 10.0),
                        TieredSize("Medium", 15.0),
                        TieredSize("Large", 20.0),
                    ),
                ),
            ),
        ),
        FoodCatalogItem("catalog_soda", "Soda", ItemCategory.SNACK, "can", "can", 1.0, 15.0),
    )

    fun componentsForTemplate(templateId: String, catalogIds: Map<String, String>): List<MealComponent> {
        fun component(id: String, catalogKey: String, name: String, quantity: Double, unit: String, costPerUnit: Double) =
            MealComponent(
                id = id,
                catalogId = catalogIds[catalogKey] ?: "catalog_$catalogKey",
                name = name,
                quantity = quantity,
                unit = unit,
                costPerUnit = costPerUnit,
            )

        return when (templateId) {
            "fuul_combo" -> listOf(
                component("component_fuul", "ful", "Ful medames", 150.0, "g", 0.1),
                component("component_falafel", "falafel", "Falafel", 2.0, "piece", 5.0),
                component("component_bread", "bread", "Baladi bread", 1.0, "piece", 12.0),
                component("component_pickles", "pickles", "Pickles", 1.0, "piece", 3.0),
            )
            "koshary_day" -> listOf(
                component("component_rice", "rice", "Rice", 150.0, "g", 0.07),
                component("component_koshari", "koshari", "Koshari", 100.0, "g", 0.25),
                component("component_fava", "fava", "Split fava", 100.0, "g", 0.15),
                component("component_sauce", "tomato_sauce", "Tomato sauce", 50.0, "g", 0.2),
                component("component_onions", "onions", "Fried onions", 20.0, "g", 0.4),
            )
            "egg_dinner" -> listOf(
                component("component_eggs", "eggs", "Eggs", 2.0, "piece", 8.0),
                component("component_yogurt", "yogurt", "Yogurt", 1.0, "cup", 4.0),
                component("component_bread_dinner", "bread", "Baladi bread", 1.0, "piece", 5.0),
                component("component_jam_dinner", "jam", "Jam", 10.0, "g", 0.3),
            )
            "cheese_plate" -> listOf(
                component("component_cheese", "cheese", "Cheese", 3.0, "spoon", 1.6),
                component("component_bread_plate", "bread", "Baladi bread", 1.0, "piece", 5.0),
                component("component_tomato_plate", "tomatoes", "Tomatoes", 1.0, "piece", 2.0),
                component("component_cucumber_plate", "cucumbers", "Cucumbers", 1.0, "piece", 2.5),
            )
            "jam_breakfast" -> listOf(
                component("component_jam_breakfast", "jam", "Jam", 20.0, "g", 0.25),
                component("component_bread_breakfast", "bread", "Baladi bread", 1.0, "piece", 5.0),
                component("component_milk", "milk", "Milk", 200.0, "ml", 0.04),
            )
            "falafel_night" -> listOf(
                component("component_falafel_night", "falafel", "Falafel", 3.0, "piece", 5.0),
                component("component_bread_night", "bread", "Baladi bread", 1.0, "piece", 5.0),
                component("component_tahina", "tahina", "Tahina", 30.0, "g", 0.17),
                component("component_salad_night", "salad", "Salad", 50.0, "g", 0.1),
            )
            "chips_treat" -> listOf(
                component("component_chips", "chips", "Chips (Medium)", 1.0, "pack", 15.0),
                component("component_soda", "soda", "Soda", 330.0, "ml", 0.021),
            )
            "ful_sandwich" -> listOf(
                component("component_ful_sandwich", "ful", "Ful medames", 100.0, "g", 0.1),
                component("component_bread_sandwich", "bread", "Baladi bread", 1.0, "piece", 15.0),
                component("component_salad_sandwich", "salad", "Salad", 50.0, "g", 0.1),
            )
            "yogurt_breakfast" -> listOf(
                component("component_yogurt_breakfast", "yogurt", "Yogurt", 1.0, "cup", 10.0),
                component("component_bread_yogurt", "bread", "Baladi bread", 1.0, "piece", 8.0),
                component("component_honey", "honey", "Honey", 10.0, "g", 0.5),
            )
            else -> emptyList()
        }
    }
}
