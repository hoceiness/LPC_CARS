package com.example.lpc_origin_app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityCarDetailsBinding
import com.example.lpc_origin_app.databinding.ItemCarImageBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CarDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentCar: Car? = null
    private var isFavourite = false
    private var favouriteId: String? = null
    private var adminPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val carId = intent.getStringExtra("CAR_ID") ?: return

        fetchCarDetails(carId)
        checkUserRole()
        checkIfFavourite(carId)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnBookNowBottom.setOnClickListener {
            val intent = Intent(this, BookingDetailsActivity::class.java)
            intent.putExtra("CAR_ID", carId)
            startActivity(intent)
        }

        binding.btnModifyCar.setOnClickListener {
            val intent = Intent(this, AddCarActivity::class.java)
            intent.putExtra("EDIT_MODE", true)
            intent.putExtra("CAR_ID", carId)
            startActivity(intent)
        }

        binding.btnDeleteCar.setOnClickListener {
            showDeleteConfirmation(carId)
        }

        binding.btnTrackLocation.setOnClickListener {
            if (currentCar?.status == "Available") {
                Toast.makeText(this, "Tracking only for rented cars", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, TrackingActivity::class.java)
                intent.putExtra("CAR_ID", carId)
                startActivity(intent)
            }
        }

        binding.btnFavourite.setOnClickListener {
            toggleFavourite(carId)
        }

        binding.ivCallAdmin.setOnClickListener {
            adminPhone?.let { phone ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$phone")
                startActivity(intent)
            } ?: Toast.makeText(this, "Admin phone not available", Toast.LENGTH_SHORT).show()
        }

        binding.ivWhatsappAdmin.setOnClickListener {
            adminPhone?.let { phone ->
                val url = "https://api.whatsapp.com/send?phone=$phone"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
            } ?: Toast.makeText(this, "Admin phone not available", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= FETCH DATA =================

    private fun fetchCarDetails(carId: String) {
        db.collection("cars").document(carId).get().addOnSuccessListener { document ->
            val car = document.toObject(Car::class.java)
            if (car != null) {
                currentCar = car

                // BASIC INFO
                binding.tvCarName.text = "${car.brand} ${car.model}"
                binding.tvPricePerDay.text = "${car.pricePerDay} MAD/Day"
                binding.tvDescription.text = car.description

                // IMAGES
                setupImageSlider(car.imageUrls)

                // FEATURES 🔥🔥🔥
                val f = car.features

                binding.tvCapacity.text = f["Capacity"] ?: "-"
                binding.tvEngine.text = f["Engine"] ?: "-"
                binding.tvMaxSpeed.text = f["Max Speed"] ?: "-"
                binding.tvAdvance.text = f["Advance 1"] ?: "-"
                binding.tvCharge.text = f["Charge"] ?: "-"
                binding.tvInit.text = f["Advance 2"] ?: "-"

                // STATUS
                if (car.status == "Available") {
                    binding.btnTrackLocation.alpha = 0.5f
                    binding.btnTrackLocation.text = "Tracking Disabled"
                } else {
                    binding.btnTrackLocation.alpha = 1f
                    binding.btnTrackLocation.text = "Track Location"
                }
            }
        }
    }

    // ================= IMAGES =================

    private fun setupImageSlider(images: List<String>) {
        binding.vpCarImages.adapter = ImageSliderAdapter(images)
    }

    inner class ImageSliderAdapter(private val images: List<String>) :
        RecyclerView.Adapter<ImageSliderAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCarImageBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCarImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            Glide.with(holder.itemView.context)
                .load(images[position])
                .into(holder.binding.ivCarImage)
        }

        override fun getItemCount() = images.size
    }

    // ================= ADMIN =================

    private fun loadAdminInfo() {
        db.collection("users")
            .whereEqualTo("type", "Admin")
            .limit(1)
            .get()
            .addOnSuccessListener {
                if (!it.isEmpty) {
                    val doc = it.documents[0]
                    adminPhone = doc.getString("phone_number")
                    updateAdminUI(doc.getString("full_name"), doc.getString("profileImageUrl"))
                }
            }
    }

    private fun updateAdminUI(name: String?, imageUrl: String?) {
        binding.tvAdminName.text = name ?: "Admin"
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.ste_logo)
                .into(binding.ivAdmin)
        }
    }

    // ================= USER ROLE =================

    private fun checkUserRole() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val type = doc.getString("type")
            if (type == "Admin") {
                binding.llAdminActions.visibility = View.VISIBLE
                binding.btnBookNowBottom.visibility = View.GONE
                adminPhone = doc.getString("phone_number")
                updateAdminUI(doc.getString("full_name"), doc.getString("profileImageUrl"))
            } else {
                binding.llAdminActions.visibility = View.GONE
                binding.btnBookNowBottom.visibility = View.VISIBLE
                loadAdminInfo()
            }
        }
    }

    // ================= FAVOURITE =================

    private fun checkIfFavourite(carId: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("favourites")
            .whereEqualTo("userId", userId)
            .whereEqualTo("carId", carId)
            .get()
            .addOnSuccessListener {
                isFavourite = !it.isEmpty

                if (isFavourite) {
                    favouriteId = it.documents[0].id
                    binding.btnFavourite.imageTintList = ColorStateList.valueOf(Color.RED)
                } else {
                    binding.btnFavourite.imageTintList = ColorStateList.valueOf(Color.GRAY)
                }
            }
    }

    private fun toggleFavourite(carId: String) {
        val userId = auth.currentUser?.uid ?: return

        if (isFavourite) {
            favouriteId?.let {
                db.collection("favourites").document(it).delete().addOnSuccessListener {
                    isFavourite = false
                    binding.btnFavourite.imageTintList = ColorStateList.valueOf(Color.GRAY)
                }
            }
        } else {
            val data = hashMapOf(
                "userId" to userId,
                "carId" to carId
            )

            db.collection("favourites").add(data).addOnSuccessListener {
                isFavourite = true
                binding.btnFavourite.imageTintList = ColorStateList.valueOf(Color.RED)
            }
        }
    }

    // ================= DELETE =================

    private fun showDeleteConfirmation(carId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Car")
            .setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("cars").document(carId).delete().addOnSuccessListener {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
