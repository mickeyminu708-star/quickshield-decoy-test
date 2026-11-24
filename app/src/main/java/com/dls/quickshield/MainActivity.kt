package com.dls.quickshield

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var radioGroupMode: RadioGroup
    private lateinit var editBoringText: EditText
    private lateinit var textSelectedImage: TextView
    private lateinit var textStatus: TextView
    private var selectedImageUri: String? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                selectedImageUri = uri.toString()
                textSelectedImage.text = "Image Selected: $selectedImageUri"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radioGroupMode = findViewById(R.id.radioGroupMode)
        editBoringText = findViewById(R.id.editBoringText)
        textSelectedImage = findViewById(R.id.textSelectedImage)
        textStatus = findViewById(R.id.textStatus)

        val btnPickImage: Button = findViewById(R.id.btnPickImage)
        val btnSaveSettings: Button = findViewById(R.id.btnSaveSettings)
        val btnOverlayPermission: Button = findViewById(R.id.btnOverlayPermission)
        val btnStartGuard: Button = findViewById(R.id.btnStartGuard)
        val btnStopGuard: Button = findViewById(R.id.btnStopGuard)

        loadSavedSettings()

        btnPickImage.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        btnOverlayPermission.setOnClickListener {
            openOverlayPermissionSettings()
        }

        btnStartGuard.setOnClickListener {
            if (!canDrawOverlays()) {
                textStatus.text = "Pehle 'Display over other apps' permission ON karein."
                openOverlayPermissionSettings()
                return@setOnClickListener
            }
            val serviceIntent = Intent(this, DecoyOverlayService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            textStatus.text = "Guard Service STARTED. Edge handle par double-tap karein."
        }

        btnStopGuard.setOnClickListener {
            val serviceIntent = Intent(this, DecoyOverlayService::class.java)
            stopService(serviceIntent)
            textStatus.text = "Guard Service STOPPED."
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusText()
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("quickshield_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("decoy_mode", "text") ?: "text"
        val text = prefs.getString(
            "decoy_text",
            "Notes: Quarterly Tax & Inventory Compliance Guidelines..."
        ) ?: ""
        val uriStr = prefs.getString("decoy_image_uri", null)

        if (mode == "image") {
            radioGroupMode.check(R.id.radioImage)
        } else {
            radioGroupMode.check(R.id.radioText)
        }

        editBoringText.setText(text)
        selectedImageUri = uriStr
        textSelectedImage.text = if (!uriStr.isNullOrEmpty()) {
            "Saved Image: $uriStr"
        } else {
            "No image selected"
        }
    }

    private fun saveSettings() {
        val mode = if (radioGroupMode.checkedRadioButtonId == R.id.radioImage) {
            "image"
        } else {
            "text"
        }
        val textValue = editBoringText.text.toString().trim().ifEmpty {
            "Notes: General Reference Document."
        }

        if (mode == "image" && selectedImageUri.isNullOrEmpty()) {
            textStatus.text = "Image mode ke liye pehle ek picture chunein."
            return
        }

        val prefs = getSharedPreferences("quickshield_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("decoy_mode", mode)
            .putString("decoy_text", textValue)
            .putString("decoy_image_uri", selectedImageUri)
            .apply()

        textStatus.text = "Settings saved ($mode mode)."
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun updatePermissionStatusText() {
        if (canDrawOverlays()) {
            textStatus.text = "Overlay permission: ALLOWED"
        } else {
            textStatus.text = "Overlay permission: REQUIRED"
        }
    }
}
