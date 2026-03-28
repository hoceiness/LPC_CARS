package com.example.lpc_origin_app

data class Car(
    val id: String = "",
    val brand: String = "",
    val model: String = "",
    val registration: String = "",
    val pricePerDay: String = "",
    val imageUrls: List<String> = emptyList(),
    val description: String = "",
    val fuelType: String = "",
    val features: Map<String, String> = emptyMap(),
    val status: String = "Available", // "Available" or "Not Available"
    val createdAt: Long = 0
)
