package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityHistoryBinding
import com.example.lpc_origin_app.databinding.ItemAwaitingReservationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        checkUserRole()
        setupBottomNav()
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            isAdmin = doc.getString("type") == "Admin"
            fetchHistory()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.navHistory.setColorFilter(getColor(R.color.white))
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.text_gray))
        
        binding.bottomNav.navHome.setOnClickListener {
            val intent = if (isAdmin) Intent(this, AdminHomeActivity::class.java)
                         else Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
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

    private fun fetchHistory() {
        val userId = auth.currentUser?.uid ?: return
        
        val query = if (isAdmin) {
            db.collection("bookings")
        } else {
            db.collection("bookings").whereEqualTo("userId", userId)
        }

        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                val bookings = snapshots.toObjects(Booking::class.java)
                binding.rvHistory.adapter = HistoryAdapter(bookings)
            }
    }

    inner class HistoryAdapter(private val bookings: List<Booking>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemAwaitingReservationBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemAwaitingReservationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val booking = bookings[position]
            holder.binding.tvCarName.text = booking.carName
            holder.binding.tvAmount.text = "${booking.totalAmount} MAD"
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            holder.binding.tvDate.text = sdf.format(Date(booking.timestamp))

            if (booking.isPaid) {
                holder.binding.tvStatus.text = "Paid"
                holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_circle_badge)
                holder.binding.tvStatus.backgroundTintList = getColorStateList(R.color.bottom_nav_dark)
                holder.binding.tvStatus.setTextColor(getColor(R.color.white))
            } else {
                holder.binding.tvStatus.text = "Unpaid"
                // Default style is already Unpaid orange from item_awaiting_reservation
            }

            if (booking.carImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context).load(booking.carImageUrl).into(holder.binding.ivCarImage)
            }

            holder.itemView.setOnClickListener {
                if (!booking.isPaid && !isAdmin) {
                    val intent = Intent(this@HistoryActivity, PaymentMethodsActivity::class.java)
                    intent.putExtra("BOOKING_ID", booking.id)
                    intent.putExtra("CAR_ID", booking.carId)
                    startActivity(intent)
                }
            }
        }
        override fun getItemCount() = bookings.size
    }
}
