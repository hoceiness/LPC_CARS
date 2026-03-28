package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityLiveContractsBinding
import com.example.lpc_origin_app.databinding.ItemLiveContractBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class LiveContractsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveContractsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveContractsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLiveContracts.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }
        
        checkUserRole()
        setupBottomNav()
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            isAdmin = doc.getString("type") == "Admin"
            fetchLiveContracts()
        }
    }

    private fun setupBottomNav() {
        // Highlight History/LiveContracts icon
        binding.bottomNav.navHistory.setImageResource(R.drawable.history_icon)
        binding.bottomNav.navHistory.setColorFilter(getColor(R.color.white))
        
        // Reset Home icon to gray
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

    private fun fetchLiveContracts() {
        val currentUser = auth.currentUser ?: return
        
        val query = if (isAdmin) {
            db.collection("bookings")
                .whereEqualTo("status", "Live")
                .whereEqualTo("isPaid", true)
        } else {
            db.collection("bookings")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("status", "Live")
                .whereEqualTo("isPaid", true)
        }
        
        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                val contracts = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Booking::class.java)?.copy(id = doc.id)
                }
                binding.rvLiveContracts.adapter = LiveContractAdapter(contracts)
            }
    }

    inner class LiveContractAdapter(private val contracts: List<Booking>) : RecyclerView.Adapter<LiveContractAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemLiveContractBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemLiveContractBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contract = contracts[position]
            holder.binding.tvCarName.text = contract.carName
            holder.binding.tvStatus.text = contract.status
            holder.binding.tvAmount.text = "${contract.totalAmount} MAD"
            
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            holder.binding.tvPickupDate.text = "Pickup: ${sdf.format(Date(contract.pickupDate))}"
            holder.binding.tvReturnDate.text = "Return: ${sdf.format(Date(contract.returnDate))}"

            if (contract.carImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context).load(contract.carImageUrl).into(holder.binding.ivCarImage)
            }
            
            if (isAdmin) {
                holder.binding.tvUserEmail.text = "User: ${contract.email}"
                holder.binding.tvUserEmail.visibility = View.VISIBLE
                holder.binding.btnEndContract.visibility = View.VISIBLE
                holder.binding.btnEndContract.setOnClickListener {
                    endContract(contract)
                }
            } else {
                holder.binding.tvUserEmail.visibility = View.GONE
                holder.binding.btnEndContract.visibility = View.GONE
            }
        }

        private fun endContract(booking: Booking) {
            // Update booking status to Completed
            db.collection("bookings").document(booking.id).update("status", "Completed")
                .addOnSuccessListener {
                    // Update car status back to Available
                    db.collection("cars").document(booking.carId).update("status", "Available")
                        .addOnSuccessListener {
                            Toast.makeText(this@LiveContractsActivity, "Contract ended and car is now available", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this@LiveContractsActivity, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        override fun getItemCount() = contracts.size
    }
}
