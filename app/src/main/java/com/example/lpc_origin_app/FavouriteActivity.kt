package com.example.lpc_origin_app

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lpc_origin_app.databinding.ActivityFavouriteBinding
import com.example.lpc_origin_app.databinding.DialogDeleteConfirmationBinding
import com.example.lpc_origin_app.databinding.ItemFavouriteCarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FavouriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavouriteBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvFavourites.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        setupSwipeToDelete()
        fetchFavouriteCars()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        // Highlight Favorites icon
        binding.bottomNav.navFavorites.setImageResource(R.drawable.favourite_heart_icon)
        binding.bottomNav.navFavorites.setColorFilter(getColor(R.color.white))
        
        // Ensure Home icon is gray
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.text_gray))

        binding.bottomNav.navHome.setOnClickListener {
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                val type = doc.getString("type")
                val intent = if (type == "Admin") {
                    Intent(this, AdminHomeActivity::class.java)
                } else {
                    Intent(this, MainActivity::class.java)
                }
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(intent)
            }
        }
        binding.bottomNav.navHistory.setOnClickListener {
            startActivity(Intent(this, LiveContractsActivity::class.java))
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = binding.rvFavourites.adapter as? FavouriteAdapter
                val item = adapter?.getItemAt(position)
                
                if (item != null) {
                    showDeleteConfirmationDialog(item.second, adapter, position)
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.rvFavourites)
    }

    private fun showDeleteConfirmationDialog(favouriteId: String, adapter: FavouriteAdapter?, position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position)
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position)
        }

        dialogBinding.btnDelete.setOnClickListener {
            db.collection("favourites").document(favouriteId).delete()
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    dialog.dismiss()
                    adapter?.notifyItemChanged(position)
                    Toast.makeText(this, "Error removing favourite", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.setOnCancelListener {
            adapter?.notifyItemChanged(position)
        }

        dialog.show()
    }

    private fun fetchFavouriteCars() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("favourites")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                val favItems = snapshots.documents.map { doc ->
                    doc.getString("carId") to doc.id
                }

                if (favItems.isEmpty()) {
                    binding.rvFavourites.adapter = FavouriteAdapter(emptyList())
                    return@addSnapshotListener
                }

                val carIds = favItems.mapNotNull { it.first }
                
                db.collection("cars").get().addOnSuccessListener { carDocs ->
                    val allCars = carDocs.toObjects(Car::class.java)
                    val favoriteCarsWithId = allCars.filter { carIds.contains(it.id) }.map { car ->
                        car to (favItems.find { it.first == car.id }?.second ?: "")
                    }
                    binding.rvFavourites.adapter = FavouriteAdapter(favoriteCarsWithId)
                }
            }
    }

    inner class FavouriteAdapter(private val cars: List<Pair<Car, String>>) :
        RecyclerView.Adapter<FavouriteAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemFavouriteCarBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemFavouriteCarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val car = cars[position].first
            holder.binding.tvCarName.text = "${car.brand}-${car.model}"
            holder.binding.tvPrice.text = "Price : ${car.pricePerDay} MAD"
            holder.binding.tvStatus.text = car.status
            holder.binding.tvMatricule.text = "car matriculle : ${car.registration}"
            
            holder.binding.tvFavCount.text = "Saved"
            holder.binding.tvOwnerName.text = "Owner: Admin"

            if (car.imageUrls.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(car.imageUrls[0])
                    .placeholder(R.drawable.logo)
                    .into(holder.binding.ivCarImage)
            }
            
            Glide.with(holder.itemView.context)
                .load(android.R.drawable.ic_menu_myplaces)
                .circleCrop()
                .into(holder.binding.ivOwnerProfile)
        }

        fun getItemAt(position: Int) = cars[position]

        override fun getItemCount() = cars.size
    }
}
