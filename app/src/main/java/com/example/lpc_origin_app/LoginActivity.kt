package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is already logged in and "Remember Me" was checked
        val prefs = getSharedPreferences("LPC_PREFS", MODE_PRIVATE)
        val rememberMe = prefs.getBoolean("remember_me", false)
        
        if (rememberMe && auth.currentUser != null) {
            val role = prefs.getString("user_role", "User") ?: "User"
            navigateToHome(role)
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPasswordToggle()

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.btnLogin.isEnabled = false
                }
                is AuthState.Authenticated -> {
                    binding.btnLogin.isEnabled = true
                    
                    // Save Remember Me preference
                    if (binding.cbRemember.isChecked) {
                        prefs.edit().apply {
                            putBoolean("remember_me", true)
                            putString("user_role", state.role)
                            apply()
                        }
                    } else {
                        prefs.edit().clear().apply()
                    }
                    
                    navigateToHome(state.role)
                }
                is AuthState.Error -> {
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun setupPasswordToggle() {
        binding.ivShowPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.ivShowPassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Example "hide" icon
            } else {
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.ivShowPassword.setImageResource(android.R.drawable.ic_menu_view) // Example "show" icon
            }
            // Move cursor to end
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
    }

    private fun navigateToHome(role: String) {
        if (role == "Admin") {
            startActivity(Intent(this, AdminHomeActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
