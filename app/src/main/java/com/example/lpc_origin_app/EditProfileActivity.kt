package com.example.lpc_origin_app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.lpc_origin_app.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private var selectedImageUri: Uri? = null
    private var currentImageUrl: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            binding.ivProfileLarge.setImageURI(selectedImageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initCloudinary()
        loadCurrentData()
        setupClickListeners()
    }

    private fun initCloudinary() {
        val config = hashMapOf(
            "cloud_name" to "dclps7qrz",
            "api_key" to "461214831747485",
            "api_secret" to "joHU92o--5FsrsGQxFwagf0Ei7I",
            "secure" to true
        )
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Already initialized is fine
        }
    }

    private fun loadCurrentData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val fullName = document.getString("full_name") ?: ""
                    binding.etFullName.setText(fullName)
                    binding.tvProfileName.text = fullName
                    binding.etEmail.setText(document.getString("email"))
                    binding.etPhone.setText(document.getString("phone_number"))
                    binding.etCin.setText(document.getString("cin"))
                    binding.etLicense.setText(document.getString("permis_number"))
                    
                    currentImageUrl = document.getString("profileImageUrl")
                    if (!currentImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(currentImageUrl).into(binding.ivProfileLarge)
                    }
                }
            }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.flProfileImage.setOnClickListener { openGallery() }
        binding.ivCrayon.setOnClickListener { openGallery() }

        binding.btnSave.setOnClickListener {
            if (selectedImageUri != null) {
                uploadImageAndSave()
            } else {
                saveChanges(currentImageUrl)
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun uploadImageAndSave() {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Uploading Image..."

        selectedImageUri?.let { uri ->
            MediaManager.get().upload(uri)
                .unsigned("kotlinproject")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val imageUrl = resultData?.get("secure_url") as String
                        saveChanges(imageUrl)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = "Save Change"
                        Toast.makeText(this@EditProfileActivity, "Upload failed: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun saveChanges(imageUrl: String?) {
        val uid = auth.currentUser?.uid ?: return
        
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "full_name" to binding.etFullName.text.toString().trim(),
            "email" to binding.etEmail.text.toString().trim(),
            "phone_number" to binding.etPhone.text.toString().trim(),
            "cin" to binding.etCin.text.toString().trim(),
            "permis_number" to binding.etLicense.text.toString().trim()
        )
        
        if (!imageUrl.isNullOrEmpty()) {
            updates["profileImageUrl"] = imageUrl
        }

        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                // Trigger notification
                NotificationHelper.sendNotification(
                    uid,
                    "Profile Updated",
                    "Your profile data has been changed successfully.",
                    imageUrl ?: currentImageUrl ?: ""
                )

                Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Save Change"
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }
}
