package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityResetPasswordBinding

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                sendOtp(email)
            } else {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvReturn.setOnClickListener { finish() }
    }

    private fun sendOtp(email: String) {
        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Sending OTP..."

        // Using your existing AuthRepository SendGrid logic
        val tempUser = User(email = email)
        authRepository.sendEmailOtp(tempUser, "") { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "OTP sent! Redirecting in 5 seconds...", Toast.LENGTH_LONG).show()
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this, VerifyResetPasswordActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    }, 5000)
                } else {
                    binding.btnContinue.isEnabled = true
                    binding.btnContinue.text = "Continue"
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
