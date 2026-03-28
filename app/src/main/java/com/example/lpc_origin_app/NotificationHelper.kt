package com.example.lpc_origin_app

import com.google.firebase.firestore.FirebaseFirestore

object NotificationHelper {
    private val db = FirebaseFirestore.getInstance()

    fun sendNotification(userId: String, title: String, message: String, iconUrl: String = "") {
        val notification = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "iconUrl" to iconUrl
        )
        db.collection("notifications").add(notification)
    }

    fun notifyAdmin(title: String, message: String, iconUrl: String = "") {
        db.collection("users")
            .whereEqualTo("type", "Admin")
            .get()
            .addOnSuccessListener { admins ->
                for (admin in admins) {
                    sendNotification(admin.id, title, message, iconUrl)
                }
            }
    }
    
    fun sendDualBookingNotification(userId: String, userName: String, userImageUrl: String, carName: String, carImageUrl: String) {
        // 1. To User
        sendNotification(
            userId = userId,
            title = "Car Reservation",
            message = carName,
            iconUrl = carImageUrl
        )
        
        // 2. To Admins
        notifyAdmin(
            title = "Car Reservation",
            message = "$userName reserved $carName",
            iconUrl = userImageUrl
        )
    }
}
