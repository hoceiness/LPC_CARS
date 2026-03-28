package com.example.lpc_origin_app

data class Booking(
    val id: String = "",
    val userId: String = "",
    val carId: String = "",
    val fullName: String = "",
    val email: String = "",
    val contact: String = "",
    val cin: String = "",
    val rentalType: String = "",
    val pickupDate: Long = 0,
    val returnDate: Long = 0,
    val status: String = "Pending", // "Pending", "Live", "Completed", "Cancelled"
    val timestamp: Long = 0,
    val withDriver: Boolean = false,
    val isPaid: Boolean = false,
    val carName: String = "",
    val carImageUrl: String = "",
    val totalAmount: Double = 0.0
)
