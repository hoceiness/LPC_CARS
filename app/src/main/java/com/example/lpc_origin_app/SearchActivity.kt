package com.example.lpc_origin_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.lpc_origin_app.databinding.ActivitySearchBinding
import com.example.lpc_origin_app.databinding.ItemCarSearchBinding
import com.google.firebase.firestore.FirebaseFirestore

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // load default cars
        fetchRecommendedCars()

        // search live
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchCars(query)
                } else {
                    fetchRecommendedCars()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun fetchRecommendedCars() {
        db.collection("cars")
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val cars = documents.toObjects(Car::class.java)
                binding.rvRecommend.adapter = SearchCarAdapter(cars)
            }
    }

    private fun searchCars(query: String) {
        db.collection("cars")
            .get()
            .addOnSuccessListener { documents ->
                val cars = documents.toObjects(Car::class.java)
                    .filter {
                        it.brand.contains(query, true) ||
                                it.model.contains(query, true)
                    }

                binding.rvRecommend.adapter = SearchCarAdapter(cars)
            }
    }

    inner class SearchCarAdapter(private val cars: List<Car>) :
        RecyclerView.Adapter<SearchCarAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCarSearchBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCarSearchBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val car = cars[position]

            holder.binding.tvCarName.text = "${car.brand} ${car.model}"
            holder.binding.tvPrice.text = "${car.pricePerDay} MAD/Day"

            holder.itemView.setOnClickListener {
                val intent = Intent(this@SearchActivity, CarDetailsActivity::class.java)
                intent.putExtra("CAR_ID", car.id)
                startActivity(intent)
            }
        }

        override fun getItemCount() = cars.size
    }
}