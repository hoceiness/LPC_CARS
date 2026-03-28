package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityAdminHomeBinding
import com.example.lpc_origin_app.databinding.ItemAdminCarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserProfileImage()
        setupClickListeners()
        fetchCars()
        observeNotifications()
        setupBottomNav()
    }

    private fun loadUserProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val imageUrl = document.getString("profileImageUrl")
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(imageUrl)
                                .circleCrop()
                                .placeholder(android.R.drawable.ic_menu_myplaces)
                                .into(binding.ivProfile)
                        }
                    }
                }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.navHome.setImageResource(R.drawable.homepage_icon)
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.white))

        binding.bottomNav.navSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupClickListeners() {
        binding.btnAddCar.setOnClickListener {
            startActivity(Intent(this, AddCarActivity::class.java))
        }
        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.flNotification.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
    }

    private fun observeNotifications() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                val count = snapshots.size()
                if (count > 0) {
                    binding.tvNotificationBadge.visibility = View.VISIBLE
                    binding.tvNotificationBadge.text = count.toString()
                } else {
                    binding.tvNotificationBadge.visibility = View.GONE
                }
            }
    }

    private fun fetchCars() {
        db.collection("cars").addSnapshotListener { snapshots, e ->
            if (e != null) return@addSnapshotListener
            val cars = snapshots?.toObjects(Car::class.java) ?: emptyList()
            binding.rvAdminCars.adapter = AdminCarAdapter(cars)
        }
    }

    inner class AdminCarAdapter(private val cars: List<Car>) : RecyclerView.Adapter<AdminCarAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemAdminCarBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemAdminCarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val car = cars[position]
            holder.binding.tvCarName.text = "${car.brand} ${car.model}"
            holder.binding.tvPrice.text = "${car.pricePerDay} MAD/Day"
            holder.binding.tvStatus.text = car.status

            if (car.imageUrls.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(car.imageUrls[0])
                    .into(holder.binding.ivCar)
            }
            
            holder.itemView.setOnClickListener {
                val intent = Intent(this@AdminHomeActivity, CarDetailsActivity::class.java)
                intent.putExtra("CAR_ID", car.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = cars.size
    }
}
