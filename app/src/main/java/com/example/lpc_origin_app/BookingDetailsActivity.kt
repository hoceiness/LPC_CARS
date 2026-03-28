package com.example.lpc_origin_app

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lpc_origin_app.databinding.ActivityBookingDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class BookingDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var rentalType = "Day"
    private var pickupDate: Calendar = Calendar.getInstance()
    private var returnDate: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/ MMMM /yyyy", Locale.ENGLISH)
    
    private var carPrice = 0.0
    private var carName = ""
    private var carImageUrl = ""

    private val startHours = (8..23).map { String.format("%02d:00", it) }
    private val endHours = (9..24).map { if (it == 24) "00:00 (Next Day)" else String.format("%02d:00", it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val carId = intent.getStringExtra("CAR_ID") ?: return

        setupUI()
        setupSpinners()
        loadUserData()
        fetchCarData(carId)

        binding.btnPayNow.setOnClickListener {
            processBooking(carId)
        }

        binding.btnBack.setOnClickListener { finish() }
        
        binding.swDriver.setOnCheckedChangeListener { _, _ -> calculateTotal() }
    }

    private fun fetchCarData(carId: String) {
        db.collection("cars").document(carId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val brand = doc.getString("brand") ?: ""
                val model = doc.getString("model") ?: ""
                carName = "$brand $model"
                carPrice = doc.getString("pricePerDay")?.toDoubleOrNull() ?: 0.0
                val imageUrls = doc.get("imageUrls") as? List<String>
                carImageUrl = imageUrls?.firstOrNull() ?: ""
                calculateTotal()
            }
        }
    }

    private fun setupUI() {
        // Set return date to tomorrow initially
        returnDate.add(Calendar.DAY_OF_YEAR, 1)
        updateDateDisplays()

        // Rental Type selection
        val typeButtons = listOf(binding.btnHour, binding.btnDay, binding.btnWeekly, binding.btnMonthly)
        typeButtons.forEach { button ->
            button.setOnClickListener {
                rentalType = button.text.toString()
                updateRentalTypeUI()
                adjustDatesForType()
                calculateTotal()
            }
        }

        // Date Pickers
        binding.llPickupDate.setOnClickListener {
            showDatePicker(true) { calendar ->
                pickupDate = calendar
                adjustDatesForType()
                updateDateDisplays()
                calculateTotal()
            }
        }

        binding.llReturnDate.setOnClickListener {
            showDatePicker(false) { calendar ->
                if (validateReturnDate(calendar)) {
                    returnDate = calendar
                    updateDateDisplays()
                    calculateTotal()
                }
            }
        }
    }

    private fun setupSpinners() {
        val startAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, startHours)
        binding.spStartTime.adapter = startAdapter

        val endAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, endHours)
        binding.spEndTime.adapter = endAdapter

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateTotal()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spStartTime.onItemSelectedListener = itemSelectedListener
        binding.spEndTime.onItemSelectedListener = itemSelectedListener
    }

    private fun updateRentalTypeUI() {
        val typeButtons = listOf(binding.btnHour, binding.btnDay, binding.btnWeekly, binding.btnMonthly)
        typeButtons.forEach { button ->
            if (button.text.toString() == rentalType) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
                button.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
                button.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            }
        }

        when (rentalType) {
            "Hour" -> {
                binding.llReturnDate.visibility = View.GONE
                binding.viewDivider.visibility = View.GONE
                binding.llHourSelection.visibility = View.VISIBLE
                binding.tvPickupLabel.text = "Rental Date"
            }
            else -> {
                binding.llReturnDate.visibility = View.VISIBLE
                binding.viewDivider.visibility = View.VISIBLE
                binding.llHourSelection.visibility = View.GONE
                binding.tvPickupLabel.text = "Pick up Date"
            }
        }
    }

    private fun adjustDatesForType() {
        val minReturn = pickupDate.clone() as Calendar
        when (rentalType) {
            "Weekly" -> {
                minReturn.add(Calendar.DAY_OF_YEAR, 7)
                if (returnDate.before(minReturn)) returnDate = minReturn
            }
            "Monthly" -> {
                minReturn.add(Calendar.DAY_OF_YEAR, 30)
                if (returnDate.before(minReturn)) returnDate = minReturn
            }
            "Day" -> {
                minReturn.add(Calendar.DAY_OF_YEAR, 1)
                if (returnDate.before(minReturn)) returnDate = minReturn
            }
        }
        updateDateDisplays()
    }

    private fun validateReturnDate(selected: Calendar): Boolean {
        val minReturn = pickupDate.clone() as Calendar
        when (rentalType) {
            "Weekly" -> {
                val diff = selected.timeInMillis - pickupDate.timeInMillis
                val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                if (days < 7 || days % 7 != 0) {
                    Toast.makeText(this, "Weekly rental must be in increments of 7 days", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            "Monthly" -> {
                val diff = selected.timeInMillis - pickupDate.timeInMillis
                val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                if (days < 30 || days % 30 != 0) {
                    Toast.makeText(this, "Monthly rental must be in increments of 30 days", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            "Day" -> {
                if (selected.before(pickupDate) || selected.equals(pickupDate)) {
                    Toast.makeText(this, "Return date must be after pickup date", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }
        return true
    }

    private fun calculateTotal() {
        var durationText = ""
        var totalAmount = 0.0

        if (rentalType == "Hour") {
            val startIdx = binding.spStartTime.selectedItemPosition
            val endIdx = binding.spEndTime.selectedItemPosition
            
            val startHour = 8 + startIdx
            val endHour = 9 + endIdx
            
            var hours = endHour - startHour
            if (hours <= 0) hours += 24 // Handle next day crossing
            
            if (hours > 24) {
                Toast.makeText(this, "Hourly rental cannot exceed 24 hours", Toast.LENGTH_SHORT).show()
                hours = 24
            }
            
            durationText = "$hours hours"
            totalAmount = (carPrice / 24.0) * hours
        } else {
            val diff = returnDate.timeInMillis - pickupDate.timeInMillis
            val days = (diff / (1000 * 60 * 60 * 24)).toDouble()
            val totalDays = if (days < 1) 1.0 else days
            durationText = "${totalDays.toInt()} days"
            totalAmount = carPrice * totalDays
        }

        binding.tvDurationLabel.text = "Duration ($durationText)"
        binding.tvBasePrice.text = String.format("%.2f MAD", totalAmount)
        binding.tvTotalAmount.text = String.format("%.2f MAD", totalAmount)
    }

    private fun updateDateDisplays() {
        binding.tvPickupDate.text = dateFormat.format(pickupDate.time)
        binding.tvReturnDate.text = dateFormat.format(returnDate.time)
    }

    private fun showDatePicker(isPickup: Boolean, onDateSelected: (Calendar) -> Unit) {
        val calendar = if (isPickup) pickupDate else returnDate
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth, 0, 0, 0)
                selectedDate.set(Calendar.MILLISECOND, 0)
                onDateSelected(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Disable past dates
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        
        if (isPickup) {
            dialog.datePicker.minDate = today.timeInMillis
        } else {
            dialog.datePicker.minDate = pickupDate.timeInMillis + (1000 * 60 * 60 * 24)
        }
        
        dialog.show()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                binding.etFullName.setText(doc.getString("full_name"))
                binding.etEmail.setText(doc.getString("email"))
                binding.etContact.setText(doc.getString("phone_number"))
                binding.etCIN.setText(doc.getString("cin"))
            }
        }
    }

    private fun processBooking(carId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val contact = binding.etContact.text.toString().trim()
        val cin = binding.etCIN.text.toString().trim()

        if (fullName.isEmpty() || email.isEmpty() || contact.isEmpty()) {
            Toast.makeText(this, "Please fill required fields*", Toast.LENGTH_SHORT).show()
            return
        }

        // Final total amount
        val totalAmountStr = binding.tvTotalAmount.text.toString().replace(" MAD", "").replace(",", ".")
        val totalAmount = totalAmountStr.toDoubleOrNull() ?: 0.0

        val booking = Booking(
            userId = userId,
            carId = carId,
            fullName = fullName,
            email = email,
            contact = contact,
            cin = cin,
            rentalType = rentalType,
            pickupDate = pickupDate.timeInMillis,
            returnDate = if (rentalType == "Hour") pickupDate.timeInMillis else returnDate.timeInMillis,
            status = "Pending",
            timestamp = System.currentTimeMillis(),
            withDriver = binding.swDriver.isChecked,
            isPaid = false,
            carName = carName,
            carImageUrl = carImageUrl,
            totalAmount = totalAmount
        )

        db.collection("bookings").add(booking)
            .addOnSuccessListener { docRef ->
                val intent = Intent(this, PaymentMethodsActivity::class.java)
                intent.putExtra("BOOKING_ID", docRef.id)
                intent.putExtra("CAR_ID", carId)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Booking creation failed", Toast.LENGTH_SHORT).show()
            }
    }
}
