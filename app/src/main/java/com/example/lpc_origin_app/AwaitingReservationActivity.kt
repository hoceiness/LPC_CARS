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
import com.example.lpc_origin_app.databinding.ActivityAwaitingReservationBinding
import com.example.lpc_origin_app.databinding.DialogDeleteConfirmationBinding
import com.example.lpc_origin_app.databinding.ItemAwaitingReservationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class AwaitingReservationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAwaitingReservationBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAwaitingReservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvAwaiting.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        checkUserRole()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.navHome.setColorFilter(getColor(R.color.text_gray))
        binding.bottomNav.navHome.setOnClickListener {
            val intent = if (isAdmin) Intent(this, AdminHomeActivity::class.java) 
                         else Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        binding.bottomNav.navHistory.setOnClickListener {
            startActivity(Intent(this, LiveContractsActivity::class.java))
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

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            isAdmin = doc.getString("type") == "Admin"
            setupSwipeToDelete()
            fetchAwaitingReservations()
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = binding.rvAwaiting.adapter as? AwaitingAdapter
                val booking = adapter?.getBookingAt(position)
                
                if (booking != null) {
                    showDeleteConfirmationDialog(booking.id, adapter, position)
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.rvAwaiting)
    }

    private fun showDeleteConfirmationDialog(bookingId: String, adapter: AwaitingAdapter?, position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position)
        }
        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position)
        }

        dialogBinding.btnDelete.setOnClickListener {
            db.collection("bookings").document(bookingId).delete()
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Reservation deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    dialog.dismiss()
                    adapter?.notifyItemChanged(position)
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
        }
        dialog.show()
    }

    private fun fetchAwaitingReservations() {
        val userId = auth.currentUser?.uid ?: return
        
        val query = if (isAdmin) {
            db.collection("bookings").whereEqualTo("isPaid", false)
        } else {
            db.collection("bookings")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isPaid", false)
        }

        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                val bookings = snapshots.toObjects(Booking::class.java).mapIndexed { index, booking ->
                    booking.copy(id = snapshots.documents[index].id)
                }
                binding.rvAwaiting.adapter = AwaitingAdapter(bookings)
            }
    }

    inner class AwaitingAdapter(private val bookings: List<Booking>) : RecyclerView.Adapter<AwaitingAdapter.ViewHolder>() {
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

            if (booking.carImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context).load(booking.carImageUrl).into(holder.binding.ivCarImage)
            }

            holder.itemView.setOnClickListener {
                if (!isAdmin) {
                    val intent = Intent(this@AwaitingReservationActivity, BookingDetailsActivity::class.java)
                    intent.putExtra("CAR_ID", booking.carId)
                    // You could also pass booking data to pre-fill
                    startActivity(intent)
                }
            }
        }
        fun getBookingAt(pos: Int) = bookings[pos]
        override fun getItemCount() = bookings.size
    }
}
