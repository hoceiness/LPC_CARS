package com.example.lpc_origin_app

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository class that handles all authentication-related operations.
 * It manages interaction with Firebase Auth, Firestore, and SendGrid for OTP delivery.
 */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Retrofit setup for SendGrid API calls
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.sendgrid.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val emailApi = retrofit.create(EmailApiService::class.java)

    companion object {
        // NOTE: In a production app, these should be stored securely (e.g., BuildConfig or Secret Manager)
        private const val SENDGRID_API_KEY = "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"
        private const val FROM_EMAIL = "hoceinesspro1@gmail.com"
        
        // Temporary storage for OTP and user details during the verification flow
        private var generatedOtp: String? = null
        private var pendingUser: User? = null
        private var pendingPassword: String? = null
        
        fun setPending(user: User, pass: String, otp: String) {
            pendingUser = user
            pendingPassword = pass
            generatedOtp = otp
        }
        
        fun getPendingOtp() = generatedOtp
        fun getPendingUser() = pendingUser
        fun getPendingPass() = pendingPassword
    }

    /**
     * Generates a 4-digit OTP and sends it to the user's email via SendGrid.
     * 
     * @param user The user object containing the target email.
     * @param password The password the user intends to register with.
     * @param onResult Callback returning success status and an optional error message.
     */
    fun sendEmailOtp(user: User, password: String, onResult: (Boolean, String?) -> Unit) {
        val otp = (1000..9999).random().toString()
        setPending(user, password, otp)

        val request = SendGridRequest(
            personalizations = listOf(Personalization(to = listOf(EmailUser(user.email)))),
            from = EmailUser(FROM_EMAIL, "LPC Cars OTP"),
            subject = "Your Verification Code",
            content = listOf(Content("text/plain", "Your verification code is: $otp"))
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = emailApi.sendEmail("Bearer $SENDGRID_API_KEY", request)
                if (response.isSuccessful) {
                    onResult(true, null)
                } else {
                    Log.e("SendGrid", "Error: ${response.errorBody()?.string()}")
                    onResult(false, "Failed to send email. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("SendGrid", "Exception: ${e.message}")
                onResult(false, e.message)
            }
        }
    }

    /**
     * Verifies if the entered OTP matches the generated one.
     * If correct, it proceeds to register the user in Firebase.
     */
    fun verifyOtpAndRegister(enteredOtp: String, onResult: (Boolean, String?) -> Unit) {
        val correctOtp = getPendingOtp()
        val user = getPendingUser()
        val pass = getPendingPass()

        if (enteredOtp == correctOtp && user != null && pass != null) {
            registerUserInFirebase(user, pass, onResult)
        } else {
            onResult(false, "Invalid verification code")
        }
    }

    /**
     * Internal helper to create the user account in Firebase Auth and store details in Firestore.
     * 
     * Note: If you encounter CONFIGURATION_NOT_FOUND (RecaptchaAction), ensure that 
     * reCAPTCHA Enterprise or App Check is correctly configured in your Firebase Console
     * for the Email/Password provider.
     */
    private fun registerUserInFirebase(user: User, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val userWithId = user.copy(uid = uid)
                // Save user profile data to Firestore
                db.collection("users").document(uid).set(userWithId)
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { onResult(false, it.message) }
            }
            .addOnFailureListener { 
                // Error handling (e.g., CONFIGURATION_NOT_FOUND if Recaptcha is misconfigured)
                onResult(false, it.message) 
            }
    }

    /**
     * Authenticates a user with email and password.
     */
    fun loginUser(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.message) }
    }

    /**
     * Retrieves the role (type) of the user from Firestore.
     */
    fun getUserRole(uid: String, onResult: (String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.getString("type")) }
    }

    /**
     * Returns the currently authenticated Firebase user.
     */
    fun getCurrentUser() = auth.currentUser
}
