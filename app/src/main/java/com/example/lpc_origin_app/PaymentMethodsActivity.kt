package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityPaymentMethodsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class PaymentMethodsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentMethodsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentMethodsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val carId = intent.getStringExtra("CAR_ID") ?: ""
        val bookingId = intent.getStringExtra("BOOKING_ID") ?: ""

        setupCountrySpinner()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnContinueBottom.setOnClickListener {
            processPayment(carId, bookingId)
        }
    }

    private fun setupCountrySpinner() {
        val countries = Locale.getISOCountries().map {
            Locale("", it).displayCountry
        }.sorted()

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countries)
        (binding.actvCountry as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun processPayment(carId: String, bookingId: String) {
        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Error: No reservation found", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnContinueBottom.isEnabled = false
        binding.btnContinueBottom.text = "Processing..."

        // Mock Payment Success logic
        db.collection("bookings").document(bookingId)
            .update(
                "isPaid", true,
                "status", "Live"
            )
            .addOnSuccessListener {
                // Update car status too
                if (carId.isNotEmpty()) {
                    db.collection("cars").document(carId).update("status", "Not Available")
                }

                Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, PaymentSuccessActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                binding.btnContinueBottom.isEnabled = true
                binding.btnContinueBottom.text = "Continue"
                Toast.makeText(this, "Payment failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
