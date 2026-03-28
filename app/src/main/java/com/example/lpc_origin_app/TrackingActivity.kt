package com.example.lpc_origin_app

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import com.example.lpc_origin_app.databinding.ActivityTrackingBinding
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding
    private val db = FirebaseFirestore.getInstance()
    private var carMarker: Marker? = null
    private var carId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSMdroid Configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        carId = intent.getStringExtra("CAR_ID")
        val carName = intent.getStringExtra("CAR_NAME") ?: "Car"
        binding.tvCarName.text = "Tracking: $carName"

        setupMap()
        
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        val mapController = binding.map.controller
        mapController.setZoom(15.0)

        carId?.let { startTracking(it) }
    }

    private fun startTracking(id: String) {
        db.collection("locations").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    binding.tvStatus.text = "Waiting for GPS signal..."
                    return@addSnapshotListener
                }

                val lat = snapshot.getDouble("latitude") ?: return@addSnapshotListener
                val lng = snapshot.getDouble("longitude") ?: return@addSnapshotListener
                val geoPoint = GeoPoint(lat, lng)

                binding.tvStatus.text = "Live: $lat, $lng"

                if (carMarker == null) {
                    carMarker = Marker(binding.map)
                    carMarker?.title = "Car Location"
                    carMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    binding.map.overlays.add(carMarker)
                }
                
                carMarker?.position = geoPoint
                binding.map.controller.animateTo(geoPoint)
                binding.map.invalidate() // Refresh map
            }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}
