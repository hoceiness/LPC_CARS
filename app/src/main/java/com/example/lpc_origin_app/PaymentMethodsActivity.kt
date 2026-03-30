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
            if (validateFields()) {
                processPayment(carId, bookingId)
            }
        }
    }

    private fun setupCountrySpinner() {
        val countries = Locale.getISOCountries().map {
            Locale("", it).displayCountry
        }.sorted()

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countries)
        (binding.actvCountry as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun validateFields(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val cardNumber = binding.etCardNumber.text.toString().trim()
        val expiryDate = binding.etExpiryDate.text.toString().trim()
        val cvc = binding.etCVC.text.toString().trim()
        val zip = binding.etZipCode.text.toString().trim()
        val country = binding.actvCountry.text.toString().trim()

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter full name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter email", Toast.LENGTH_SHORT).show()
            return false
        }
        if (cardNumber.length < 16) {
            Toast.makeText(this, "Invalid card number", Toast.LENGTH_SHORT).show()
            return false
        }
        if (expiryDate.isEmpty()) {
            Toast.makeText(this, "Please enter expiry date", Toast.LENGTH_SHORT).show()
            return false
        }
        if (cvc.length < 3) {
            Toast.makeText(this, "Invalid CVC", Toast.LENGTH_SHORT).show()
            return false
        }
        if (country.isEmpty()) {
            Toast.makeText(this, "Please select a country", Toast.LENGTH_SHORT).show()
            return false
        }
        if (zip.isEmpty()) {
            Toast.makeText(this, "Please enter ZIP code", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!binding.cbTerms.isChecked) {
            Toast.makeText(this, "Please accept terms and conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
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
                intent.putExtra("BOOKING_ID", bookingId)
                // Passing a mock transaction ID and last 4 digits of card for the receipt
                val cardNumber = binding.etCardNumber.text.toString()
                val last4 = if (cardNumber.length >= 4) cardNumber.substring(cardNumber.length - 4) else "0000"
                intent.putExtra("CARD_LAST_4", last4)
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
