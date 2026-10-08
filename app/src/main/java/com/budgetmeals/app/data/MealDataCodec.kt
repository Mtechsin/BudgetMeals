package com.budgetmeals.app.data

import org.json.JSONArray
import org.json.JSONObject

object MealDataCodec {
    fun encodeComponents(components: List<MealComponent>): String {
        if (components.isEmpty()) return ""
        return JSONArray().apply {
            components.forEach { component ->
                put(
                    JSONObject().apply {
                        put("id", component.id)
                        put("catalogId", component.catalogId ?: JSONObject.NULL)
                        put("name", component.name)
                        put("quantity", component.quantity)
                        put("unit", component.unit)
                        put("costPerUnit", component.costPerUnit)
                        put("useStock", component.useStock)
                    },
                )
            }
        }.toString()
    }

    fun legacyComponents(raw: String): List<MealComponent> {
        return raw.split(',', '+', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .mapIndexed { index, name ->
                MealComponent(
                    id = "legacy-$index-${name.hashCode()}",
                    name = name,
                    quantity = 1.0,
                    unit = "serving",
                    costPerUnit = 0.0,
                    useStock = false,
                )
            }
    }

    fun decodeComponents(raw: String): List<MealComponent> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        MealComponent(
                            id = item.optString("id").ifBlank { "component-$index-${name.hashCode()}" },
                            catalogId = item.optString("catalogId").takeUnless { it.isBlank() || it == "null" },
                            name = name,
                            quantity = item.optDouble("quantity", 1.0).coerceAtLeast(0.0),
                            unit = item.optString("unit", "piece").ifBlank { "piece" },
                            costPerUnit = item.optDouble("costPerUnit", 0.0).coerceAtLeast(0.0),
                            useStock = item.optBoolean("useStock", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
