package com.example.meritzshortcut.data

import org.json.JSONObject

data class Receipt(
    val id: String,
    val filePath: String,
    val mediaUri: String? = null,
    val createdAt: Long,
    val isCompleted: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("filePath", filePath)
            put("mediaUri", mediaUri)
            put("createdAt", createdAt)
            put("isCompleted", isCompleted)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Receipt {
            return Receipt(
                id = json.getString("id"),
                filePath = json.getString("filePath"),
                mediaUri = if (json.has("mediaUri") && !json.isNull("mediaUri")) json.getString("mediaUri") else null,
                createdAt = json.getLong("createdAt"),
                isCompleted = json.optBoolean("isCompleted", false)
            )
        }
    }
}
