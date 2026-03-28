package com.example.lpc_origin_app

data class User(
    val uid: String = "",
    val full_name: String = "",
    val email: String = "",
    val type: String = "User", // "User" or "Admin"
    val cin: String = "",
    val phone_number: String = "",
    val permis_number: String = "",
    val profileImageUrl: String = "",
    val pass: String =""
)
