package com.example.lpc_origin_app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.lpc_origin_app.databinding.ActivityAddCarBinding
import com.example.lpc_origin_app.databinding.ItemBrandSelectionBinding
import com.example.lpc_origin_app.databinding.ItemFeatureSwitchBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import pub.devrel.easypermissions.EasyPermissions
import java.io.ByteArrayOutputStream

class AddCarActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: ActivityAddCarBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedBrand: String? = null
    private var selectedModel: String? = null
    private val selectedImageUris = mutableListOf<Uri>()
    private var existingImageUrls = mutableListOf<String>()
    private var selectedFuelType: String = "Diesel"
    
    private var isEditMode = false
    private var carId: String? = null
    private val RC_CAMERA = 123

    // 📸 CAMERA RESULT
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                binding.ivCarPreview.visibility = View.VISIBLE
                binding.ivCarPreview.setImageBitmap(bitmap)

                val uri = getImageUri(bitmap)
                selectedImageUris.add(uri)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    selectedImageUris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                selectedImageUris.add(data.data!!)
            }

            if (selectedImageUris.isNotEmpty()) {
                binding.ivCarPreview.visibility = View.VISIBLE
                binding.ivCarPreview.setImageURI(selectedImageUris[0])
                Toast.makeText(this, "${selectedImageUris.size} images selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        carId = intent.getStringExtra("CAR_ID")

        initCloudinary()
        loadUserProfileImage()
        setupTabSwitcher()
        setupImagePickers()
        setupFuelTypeSelection()
        setupDescriptionCounter()
        setupFeatureLabels()
        fetchBrands()

        if (isEditMode && carId != null) {
            binding.tvTitle.text = "Update Car"
            binding.btnAjouterVoiture.text = "Update Car"
            loadCarData(carId!!)
        }

        binding.btnAjouterVoiture.setOnClickListener {
            if (isEditMode) {
                updateCar()
            } else {
                uploadImagesAndSaveCar()
            }
        }
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

    private fun loadCarData(id: String) {
        db.collection("cars").document(id).get().addOnSuccessListener { doc ->
            val car = doc.toObject(Car::class.java)
            if (car != null) {
                selectedBrand = car.brand
                selectedModel = car.model
                binding.etRegistrationNumber.setText(car.registration)
                binding.etPricePerDay.setText(car.pricePerDay)
                binding.etDescription.setText(car.description)
                setFuelType(car.fuelType)
                setFeatures(car.features)
                existingImageUrls = car.imageUrls.toMutableList()
                
                if (existingImageUrls.isNotEmpty()) {
                    binding.ivCarPreview.visibility = View.VISIBLE
                    Glide.with(this).load(existingImageUrls[0]).into(binding.ivCarPreview)
                }
                
                fetchModels(car.brand)
            }
        }
    }

    private fun setFuelType(fuel: String) {
        selectedFuelType = fuel
        val fuelChips = mutableListOf<TextView>()
        for (i in 0 until binding.llFuelTypes.childCount) {
            val child = binding.llFuelTypes.getChildAt(i)
            if (child is TextView) fuelChips.add(child)
        }
        fuelChips.forEach { chip ->
            if (chip.text.toString() == fuel) {
                chip.setBackgroundResource(R.drawable.bg_button_primary)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_edittext)
                chip.setTextColor(Color.GRAY)
            }
        }
    }

    private fun setFeatures(features: Map<String, String>) {
        val bindings = listOf(
            ItemFeatureSwitchBinding.bind(binding.featureCapacity.root),
            ItemFeatureSwitchBinding.bind(binding.featureEngine.root),
            ItemFeatureSwitchBinding.bind(binding.featureMaxSpeed.root),
            ItemFeatureSwitchBinding.bind(binding.featureAdvance1.root),
            ItemFeatureSwitchBinding.bind(binding.featureSingleCharge.root),
            ItemFeatureSwitchBinding.bind(binding.featureAdvance2.root)
        )

        bindings.forEach { b ->
            val name = b.tvFeatureName.text.toString()
            if (features.containsKey(name)) {
                b.switchFeature.isChecked = true
                b.etFeatureValue.setText(features[name])
            }
        }
    }

    private fun initCloudinary() {
        val config = hashMapOf(
            "cloud_name" to "dclps7qrz",
            "api_key" to "461214831747485",
            "api_secret" to "joHU92o--5FsrsGQxFwagf0Ei7I",
            "secure" to true
        )
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Already initialized
        }
    }

    private fun setupTabSwitcher() {
        binding.tvTabBrand.setOnClickListener { showBrandTab() }
        binding.tvTabModel.setOnClickListener {
            if (selectedBrand != null) showModelTab()
            else Toast.makeText(this, "Select a brand first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImagePickers() {
        binding.ivGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            galleryLauncher.launch(intent)
        }
        binding.ivCamera.setOnClickListener {
            openCameraWithPermission()
        }
        binding.btnCamera.setOnClickListener {
            openCameraWithPermission()
        }
    }

    private fun openCameraWithPermission() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            openCamera()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Khasna permission dyal camera 📷",
                RC_CAMERA,
                Manifest.permission.CAMERA
            )
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == RC_CAMERA) openCamera()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "Permission مرفوضة ❌", Toast.LENGTH_SHORT).show()
    }

    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "CarImage_${System.currentTimeMillis()}", null)
        return Uri.parse(path)
    }

    private fun setupFuelTypeSelection() {
        val fuelChips = mutableListOf<TextView>()
        for (i in 0 until binding.llFuelTypes.childCount) {
            val child = binding.llFuelTypes.getChildAt(i)
            if (child is TextView) fuelChips.add(child)
        }

        fuelChips.forEach { chip ->
            chip.setOnClickListener {
                fuelChips.forEach {
                    it.setBackgroundResource(R.drawable.bg_edittext)
                    it.setTextColor(Color.GRAY)
                }
                chip.setBackgroundResource(R.drawable.bg_button_primary)
                chip.setTextColor(Color.WHITE)
                selectedFuelType = chip.text.toString()
            }
        }
    }

    private fun setupDescriptionCounter() {
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvCharCount.text = "${s?.length ?: 0}/1000"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFeatureLabels() {
        ItemFeatureSwitchBinding.bind(binding.featureCapacity.root).tvFeatureName.text = "Capacity"
        ItemFeatureSwitchBinding.bind(binding.featureEngine.root).tvFeatureName.text = "Engine"
        ItemFeatureSwitchBinding.bind(binding.featureMaxSpeed.root).tvFeatureName.text = "Max Speed"
        ItemFeatureSwitchBinding.bind(binding.featureAdvance1.root).tvFeatureName.text = "Advance 1"
        ItemFeatureSwitchBinding.bind(binding.featureSingleCharge.root).tvFeatureName.text = "Charge"
        ItemFeatureSwitchBinding.bind(binding.featureAdvance2.root).tvFeatureName.text = "Advance 2"
    }

    private fun getFeatures(): Map<String, String> {
        val features = mutableMapOf<String, String>()
        val bindings = listOf(
            ItemFeatureSwitchBinding.bind(binding.featureCapacity.root),
            ItemFeatureSwitchBinding.bind(binding.featureEngine.root),
            ItemFeatureSwitchBinding.bind(binding.featureMaxSpeed.root),
            ItemFeatureSwitchBinding.bind(binding.featureAdvance1.root),
            ItemFeatureSwitchBinding.bind(binding.featureSingleCharge.root),
            ItemFeatureSwitchBinding.bind(binding.featureAdvance2.root)
        )

        bindings.forEach { b ->
            if (b.switchFeature.isChecked) {
                features[b.tvFeatureName.text.toString()] = b.etFeatureValue.text.toString()
            }
        }
        return features
    }

    private fun updateCar() {
        val regNumber = binding.etRegistrationNumber.text.toString()
        val price = binding.etPricePerDay.text.toString()
        val desc = binding.etDescription.text.toString()

        if (selectedBrand == null || selectedModel == null || regNumber.isEmpty() || price.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnAjouterVoiture.isEnabled = false
        
        if (selectedImageUris.isNotEmpty()) {
            binding.btnAjouterVoiture.text = "Uploading new images..."
            uploadNewImagesAndSave(regNumber, price, desc)
        } else {
            saveCarToFirestore(existingImageUrls, regNumber, price, desc)
        }
    }

    private fun uploadNewImagesAndSave(reg: String, price: String, desc: String) {
        val newUrls = mutableListOf<String>()
        var uploadCount = 0

        selectedImageUris.forEach { uri ->
            MediaManager.get().upload(uri)
                .unsigned("kotlinproject")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val imageUrl = resultData?.get("secure_url") as String
                        newUrls.add(imageUrl)
                        uploadCount++
                        if (uploadCount == selectedImageUris.size) {
                            val allUrls = existingImageUrls + newUrls
                            saveCarToFirestore(allUrls, reg, price, desc)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        uploadCount++
                        if (uploadCount == selectedImageUris.size) {
                            val allUrls = existingImageUrls + newUrls
                            saveCarToFirestore(allUrls, reg, price, desc)
                        }
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun uploadImagesAndSaveCar() {
        val regNumber = binding.etRegistrationNumber.text.toString()
        val price = binding.etPricePerDay.text.toString()
        val desc = binding.etDescription.text.toString()

        if (selectedBrand == null || selectedModel == null || regNumber.isEmpty() || price.isEmpty() || selectedImageUris.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields and select at least one image", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnAjouterVoiture.isEnabled = false
        binding.btnAjouterVoiture.text = "Uploading images..."

        val uploadedUrls = mutableListOf<String>()
        var uploadCount = 0

        selectedImageUris.forEach { uri ->
            MediaManager.get().upload(uri)
                .unsigned("kotlinproject")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val imageUrl = resultData?.get("secure_url") as String
                        uploadedUrls.add(imageUrl)
                        uploadCount++
                        if (uploadCount == selectedImageUris.size) {
                            saveCarToFirestore(uploadedUrls, regNumber, price, desc)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        uploadCount++
                        if (uploadCount == selectedImageUris.size) {
                            if (uploadedUrls.isNotEmpty()) {
                                saveCarToFirestore(uploadedUrls, regNumber, price, desc)
                            } else {
                                binding.btnAjouterVoiture.isEnabled = true
                                binding.btnAjouterVoiture.text = "Ajouter Voiture"
                                Toast.makeText(this@AddCarActivity, "All uploads failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun saveCarToFirestore(imageUrls: List<String>, reg: String, price: String, desc: String) {
        val carRef = if (isEditMode && carId != null) db.collection("cars").document(carId!!) 
                     else db.collection("cars").document()
        
        val car = Car(
            id = carRef.id,
            brand = selectedBrand!!,
            model = selectedModel!!,
            registration = reg,
            pricePerDay = price,
            imageUrls = imageUrls,
            description = desc,
            fuelType = selectedFuelType,
            features = getFeatures(),
            status = "Available",
            createdAt = if (isEditMode) 0 else System.currentTimeMillis()
        )

        carRef.set(car).addOnSuccessListener {
            val msg = if (isEditMode) "Car Updated Successfully!" else "Car Added Successfully!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
        .addOnFailureListener {
            binding.btnAjouterVoiture.isEnabled = true
            binding.btnAjouterVoiture.text = if (isEditMode) "Update Car" else "Ajouter Voiture"
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchBrands() {
        db.collection("brands").get().addOnSuccessListener { documents ->
            val brands = documents.mapNotNull { it.getString("name") }
            setupBrandsRecyclerView(brands)
        }
    }

    private fun fetchModels(brandName: String) {
        db.collection("models")
            .whereEqualTo("brandId", brandName)
            .get()
            .addOnSuccessListener { documents ->
                val models = documents.mapNotNull { it.getString("name") }
                setupModelsRecyclerView(models)
            }
    }

    private fun setupBrandsRecyclerView(brands: List<String>) {
        binding.rvBrands.adapter = SelectionAdapter(brands) { brand ->
            selectedBrand = brand
            selectedModel = null
            fetchModels(brand)
            showModelTab()
        }
    }

    private fun setupModelsRecyclerView(models: List<String>) {
        binding.rvModels.adapter = SelectionAdapter(models) { model ->
            selectedModel = model
        }
    }

    private fun showBrandTab() {
        binding.rvBrands.visibility = View.VISIBLE
        binding.rvModels.visibility = View.GONE
        binding.tvTabBrand.setBackgroundResource(R.drawable.bg_button_primary)
        binding.tvTabBrand.setTextColor(Color.WHITE)
        binding.tvTabModel.background = null
        binding.tvTabModel.setTextColor(Color.GRAY)
    }

    private fun showModelTab() {
        binding.rvBrands.visibility = View.GONE
        binding.rvModels.visibility = View.VISIBLE
        binding.tvTabModel.setBackgroundResource(R.drawable.bg_button_primary)
        binding.tvTabModel.setTextColor(Color.WHITE)
        binding.tvTabBrand.background = null
        binding.tvTabBrand.setTextColor(Color.GRAY)
    }

    inner class SelectionAdapter(private val items: List<String>, private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<SelectionAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemBrandSelectionBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemBrandSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item
            
            val isSelected = item == selectedBrand || item == selectedModel
            if (isSelected) {
                holder.binding.tvName.setTextColor(Color.BLUE)
            } else {
                holder.binding.tvName.setTextColor(Color.BLACK)
            }

            holder.itemView.setOnClickListener { 
                onItemClick(item)
                notifyDataSetChanged()
            }
        }
        override fun getItemCount() = items.size
    }
}
