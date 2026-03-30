package com.example.lpc_origin_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityMainBinding
import com.example.lpc_origin_app.databinding.ItemBrandBinding
import com.example.lpc_origin_app.databinding.ItemCarAvailableBinding
import com.example.lpc_origin_app.databinding.ItemCarUnavailableBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTrackingIfBookingActive()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        fetchBrands()
        fetchCars()
        observeNotifications()
        setupBottomNav()
        checkLocationPermissions()
        binding.btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
    }

    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            startTrackingIfBookingActive()
        }
    }

    private fun startTrackingIfBookingActive() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("bookings")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "Live")
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val carId = documents.documents[0].getString("carId")
                    if (carId != null) {
                        val serviceIntent = Intent(this, LocationService::class.java)
                        serviceIntent.putExtra("CAR_ID", carId)
                        ContextCompat.startForegroundService(this, serviceIntent)
                    }
                }
            }
    }

    private fun setupBottomNav() {
        binding.bottomNav.navHome.setImageResource(R.drawable.homepage_icon)
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.white))

        binding.bottomNav.navHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.bottomNav.navFavorites.setOnClickListener {
            startActivity(Intent(this, FavouriteActivity::class.java))
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces)
                            .circleCrop()
                            .into(binding.ivProfile)
                    }
                }
            }
    }

    private fun setupClickListeners() {
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

    private fun fetchBrands() {
        db.collection("brands").get().addOnSuccessListener { documents ->
            val brandList = documents.mapNotNull { doc ->
                val name = doc.getString("name")
                val image = doc.getString("image")

                if (name != null && image != null) {
                    brands(name, image)
                } else null
            }

            binding.rvBrands.adapter = BrandAdapter(brandList)
        }
    }

    private fun fetchCars() {
        db.collection("cars").addSnapshotListener { snapshots, e ->
            if (e != null) return@addSnapshotListener
            val allCars = snapshots?.toObjects(Car::class.java) ?: emptyList()
            
            val availableCars = allCars.filter { it.status == "Available" }
            val unavailableCars = allCars.filter { it.status != "Available" }

            binding.rvAvailableCars.adapter = AvailableCarAdapter(availableCars)
            binding.rvNotAvailableCars.adapter = UnavailableCarAdapter(unavailableCars)
        }
    }

    inner class BrandAdapter(private val brands: List<brands>) : RecyclerView.Adapter<BrandAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemBrandBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemBrandBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.tvBrandName.text = brands[position].name
            Glide.with(holder.itemView.context)
                .load(brands[position].image)
                .into(holder.binding.ivBrandLogo)
        }
        override fun getItemCount() = brands.size
    }

    inner class AvailableCarAdapter(private val cars: List<Car>) : RecyclerView.Adapter<AvailableCarAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemCarAvailableBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCarAvailableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val car = cars[position]
            holder.binding.tvCarName.text = "${car.brand} ${car.model}"
            holder.binding.tvPrice.text = "${car.pricePerDay} MAD/Day"
            
            if (car.imageUrls.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(car.imageUrls[0])
                    .into(holder.binding.ivCar)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, CarDetailsActivity::class.java)
                intent.putExtra("CAR_ID", car.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = cars.size
    }

    inner class UnavailableCarAdapter(private val cars: List<Car>) : RecyclerView.Adapter<UnavailableCarAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemCarUnavailableBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCarUnavailableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val car = cars[position]
            holder.binding.tvCarNameLarge.text = "${car.brand} ${car.model}"
            holder.binding.tvPriceLarge.text = "${car.pricePerDay} MAD/Day"
            
            if (car.imageUrls.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(car.imageUrls[0])
                    .into(holder.binding.ivCarLarge)
            }
        }
        override fun getItemCount() = cars.size
    }
}
