package com.slncloth.shop.core.api

import kotlinx.serialization.json.Json

object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
        prettyPrint = false
    }
}
