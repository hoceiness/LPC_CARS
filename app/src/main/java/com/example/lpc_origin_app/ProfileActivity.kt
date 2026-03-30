package com.example.lpc_origin_app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val repository = AuthRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        // Highlight Profile icon
        binding.bottomNav.navProfile.setImageResource(R.drawable.profile_settings)
        binding.bottomNav.navProfile.setColorFilter(getColor(R.color.white))
        
        // Ensure Home icon is gray
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.text_gray))

        binding.bottomNav.navHome.setOnClickListener {
            goHome(AdminHomeActivity::class.java, MainActivity::class.java)
        }
        binding.bottomNav.navHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.bottomNav.navFavorites.setOnClickListener {
            startActivity(Intent(this, FavouriteActivity::class.java))
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fullName = document.getString("full_name") ?: "N/A"
                        val email = document.getString("email") ?: user.email
                        val phone = document.getString("phone_number") ?: "N/A"
                        val cin = document.getString("cin") ?: "N/A"
                        val license = document.getString("permis_number") ?: "N/A"
                        val profileImageUrl = document.getString("profileImageUrl")
                        
                        binding.tvFullName.text = fullName
                        binding.tvEmail.text = email
                        binding.tvPhone.text = phone
                        binding.tvCin.text = cin
                        binding.tvLicense.text = license

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(android.R.drawable.ic_menu_myplaces)
                                .into(binding.ivProfile)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.llEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.llHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.llLanguages.setOnClickListener {
            showLanguageDialog()
        }

        binding.llLiveContracts.setOnClickListener {
            startActivity(Intent(this, LiveContractsActivity::class.java))
        }

        binding.llAwaitingReservations.setOnClickListener {
            startActivity(Intent(this, AwaitingReservationActivity::class.java))
        }

        binding.llLogout.setOnClickListener {
            logout()
        }
    }
    private fun goHome(adminActivity: Class<out Activity>, userActivity: Class<out Activity>) {
        val currentUser = repository.getCurrentUser() // Firebase
        if (currentUser != null) {
            repository.getUserRole(currentUser.uid) { role ->
                when (role) {
                    "Admin" -> startActivity(Intent(this, adminActivity))
                    "User" -> startActivity(Intent(this, userActivity))
                    else -> Toast.makeText(this, "Role not found", Toast.LENGTH_SHORT).show()
                }
                finish() // تسالي Activity الحالية
            }
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "French", "Arabic")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Language")
        builder.setItems(languages) { _, which ->
            val selectedLanguage = languages[which]
            Toast.makeText(this, "Language set to $selectedLanguage", Toast.LENGTH_SHORT).show()
            // Here you would implement the actual language switching logic (locale change)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun logout() {
        auth.signOut()
        
        // Clear Remember Me preference on logout
        val prefs = getSharedPreferences("LPC_PREFS", MODE_PRIVATE)
        prefs.edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
