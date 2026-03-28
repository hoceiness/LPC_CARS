package com.example.lpc_origin_app

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lpc_origin_app.databinding.ActivityNotificationsBinding
import com.example.lpc_origin_app.databinding.DialogDeleteConfirmationBinding
import com.example.lpc_origin_app.databinding.ItemNotificationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val repository = AuthRepository()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        fetchNotifications()
        setupBottomNav()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupBottomNav() {
        binding.bottomNav.navNotifications.setImageResource(R.drawable.notification_icon)
        binding.bottomNav.navNotifications.setColorFilter(getColor(R.color.white))

        binding.bottomNav.navHome.setOnClickListener {
            goHome(AdminHomeActivity::class.java, MainActivity::class.java)
        }
        binding.bottomNav.navHistory.setOnClickListener {
            startActivity(Intent(this, LiveContractsActivity::class.java))
        }
        binding.bottomNav.navFavorites.setOnClickListener {
            startActivity(Intent(this, FavouriteActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
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
                finish()
            }
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerViews() {
        binding.rvToday.layoutManager = LinearLayoutManager(this)
        binding.rvPrevious.layoutManager = LinearLayoutManager(this)

        // Add swipe to delete functionality
        setupSwipeToDelete(binding.rvToday)
        setupSwipeToDelete(binding.rvPrevious)
    }

    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = recyclerView.adapter as? NotificationAdapter
                val notification = adapter?.getNotificationAt(position)
                
                if (notification != null) {
                    showDeleteConfirmationDialog(notification, adapter, position)
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun showDeleteConfirmationDialog(notification: Notification, adapter: NotificationAdapter?, position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position) // Reset swipe
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            adapter?.notifyItemChanged(position) // Reset swipe
        }

        dialogBinding.btnDelete.setOnClickListener {
            db.collection("notifications").document(notification.id).delete()
                .addOnSuccessListener {
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    dialog.dismiss()
                    adapter?.notifyItemChanged(position)
                }
        }

        dialog.setOnCancelListener {
            adapter?.notifyItemChanged(position)
        }

        dialog.show()
    }

    private fun fetchNotifications() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val allNotifications = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Notification::class.java)?.copy(id = doc.id)
                }

                if (allNotifications.isEmpty()) {
                    binding.llEmptyState.visibility = View.VISIBLE
                    binding.nsvNotifications.visibility = View.GONE
                } else {
                    binding.llEmptyState.visibility = View.GONE
                    binding.nsvNotifications.visibility = View.VISIBLE

                    val today = Calendar.getInstance()
                    val todayNotifs = mutableListOf<Notification>()
                    val previousNotifs = mutableListOf<Notification>()

                    for (notif in allNotifications) {
                        val notifDate = Calendar.getInstance().apply { timeInMillis = notif.timestamp }
                        if (isSameDay(today, notifDate)) {
                            todayNotifs.add(notif)
                        } else {
                            previousNotifs.add(notif)
                        }
                    }

                    binding.rvToday.adapter = NotificationAdapter(todayNotifs)
                    binding.rvPrevious.adapter = NotificationAdapter(previousNotifs)

                    val unreadCount = allNotifications.count { !it.isRead }
                    binding.tvUnreadCount.text = "$unreadCount Unread Notification"

                    binding.rlTodayHeader.visibility = if (todayNotifs.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvPreviousHeader.visibility = if (previousNotifs.isEmpty()) View.GONE else View.VISIBLE
                }
            }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    inner class NotificationAdapter(private val notifications: List<Notification>) :
        RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notif = notifications[position]
            holder.binding.tvNotificationTitle.text = notif.title
            holder.binding.tvNotificationMessage.text = notif.message
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.binding.tvNotificationTime.text = sdf.format(Date(notif.timestamp))

            holder.binding.vUnreadIndicator.visibility = if (notif.isRead) View.GONE else View.VISIBLE
            
            holder.itemView.setOnClickListener {
                if (!notif.isRead) {
                    db.collection("notifications").document(notif.id).update("isRead", true)
                }
            }
        }

        fun getNotificationAt(position: Int): Notification = notifications[position]

        override fun getItemCount() = notifications.size
    }
}
