package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityEnterVerificationCodeBinding

/**
 * Activity responsible for handling the OTP (One-Time Password) verification step.
 * This screen is reached after a user attempts to sign up and a verification code
 * is sent to their email via SendGrid.
 */
class EnterVerificationCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnterVerificationCodeBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnterVerificationCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCodeInput()

        binding.btnContinue.setOnClickListener {
            val code = binding.etCode.text.toString()
            if (code.length == 4) {
                // Initiates the OTP verification and subsequent Firebase registration
                viewModel.verifyOtp(code)
            } else {
                Toast.makeText(this, "Please enter the 4-digit code", Toast.LENGTH_SHORT).show()
            }
        }

        observeAuthState()
    }

    /**
     * Observes the authentication state from the ViewModel to handle navigation
     * and UI updates based on registration progress.
     */
    private fun observeAuthState() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    // Disable button to prevent multiple submissions during processing
                    binding.btnContinue.isEnabled = false
                }
                is AuthState.Authenticated -> {
                    Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                    // Redirect user based on their role (Admin or Regular User)
                    if (state.role == "Admin") {
                        startActivity(Intent(this, AdminHomeActivity::class.java))
                    } else {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                    // Clear activity stack to prevent user from going back to registration
                    finishAffinity()
                }
                is AuthState.Error -> {
                    binding.btnContinue.isEnabled = true
                    // Display error message (e.g., CONFIGURATION_NOT_FOUND or Invalid Code)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    /**
     * Sets up the 4-digit code input field and synchronization with the visual digit indicators.
     */
    private fun setupCodeInput() {
        binding.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val code = s.toString()
                // Update the visual representation for each digit
                binding.tvDigit1.text = if (code.length >= 1) code[0].toString() else ""
                binding.tvDigit2.text = if (code.length >= 2) code[1].toString() else ""
                binding.tvDigit3.text = if (code.length >= 3) code[2].toString() else ""
                binding.tvDigit4.text = if (code.length >= 4) code[3].toString() else ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Request focus and show keyboard automatically for better UX
        binding.etCode.requestFocus()
    }
}
