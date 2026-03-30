package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityPaymentSuccessBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class PaymentSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSuccessBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        val last4 = intent.getStringExtra("CARD_LAST_4") ?: "0000"

        if (bookingId.isNotEmpty()) {
            fetchBookingDetails(bookingId, last4)
        } else {
            Toast.makeText(this, "Error: Booking info missing", Toast.LENGTH_SHORT).show()
        }

        binding.btnBackHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun fetchBookingDetails(bookingId: String, last4: String) {
        db.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { document ->
                val booking = document.toObject(Booking::class.java)
                if (booking != null) {
                    displayReceipt(booking, bookingId, last4)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load receipt details", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayReceipt(booking: Booking, bookingId: String, last4: String) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateTimeSdf = SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.getDefault())

        // Booking Info
        binding.tvCarModel.text = booking.carName
        val pickup = sdf.format(Date(booking.pickupDate))
        val returned = sdf.format(Date(booking.returnDate))
        binding.tvRentalDate.text = "$pickup - $returned"
        binding.tvUserName.text = booking.fullName

        // Transaction Detail
        binding.tvTransactionId.text = "#${bookingId.take(10).uppercase()}"
        binding.tvTransactionDate.text = dateTimeSdf.format(Date(booking.timestamp))
        binding.tvCardNumberMasked.text = "**** **** **** $last4"

        // Amounts
        val serviceFee = 15.0
        val tax = 0.0
        val baseAmount = booking.totalAmount - serviceFee - tax
        
        binding.tvBaseAmount.text = String.format("%.2f MAD", if (baseAmount > 0) baseAmount else booking.totalAmount)
        binding.tvServiceFee.text = String.format("%.2f MAD", serviceFee)
        binding.tvTax.text = String.format("%.2f MAD", tax)
        binding.tvTotalAmount.text = String.format("%.2f MAD", booking.totalAmount)
    }
}
