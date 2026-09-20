package com.example.facecamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var statusText: TextView
    private lateinit var timeText: TextView
    private lateinit var batteryText: TextView
    private lateinit var cameraExecutor: ExecutorService

    private val handler = Handler(Looper.getMainLooper())
    private val clockUpdater = object : Runnable {
        override fun run() {
            val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            timeText.text = now
            batteryText.text = "BAT ${getBatteryPercent()}%"
            handler.postDelayed(this, 1000)
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else statusText.text = "Разрешение на камеру не выдано"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        faceOverlay = findViewById(R.id.faceOverlay)
        statusText = findViewById(R.id.statusText)
        timeText = findViewById(R.id.timeText)
        batteryText = findViewById(R.id.batteryText)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.camera1Button).setOnClickListener {
            statusText.text = "КАМЕРА 1 — собственная камера телефона"
            startCamera()
        }
        findViewById<Button>(R.id.camera2Button).setOnClickListener {
            statusText.text = "КАМЕРА 2 — подключение будет добавлено позже"
        }
        findViewById<Button>(R.id.camera3Button).setOnClickListener {
            statusText.text = "КАМЕРА 3 — подключение будет добавлено позже"
        }
        findViewById<Button>(R.id.camera4Button).setOnClickListener {
            statusText.text = "КАМЕРА 4 — подключение будет добавлено позже"
        }

        handler.post(clockUpdater)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun getBatteryPercent(): Int {
        val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.10f)
                .build()

            val detector = FaceDetection.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }

                val input = InputImage.fromMediaImage(
                    mediaImage,
                    proxy.imageInfo.rotationDegrees
                )

                detector.process(input)
                    .addOnSuccessListener { faces ->
                        val boxes = faces.map { it.boundingBox }
                        runOnUiThread {
                            faceOverlay.setFaces(boxes, proxy.width, proxy.height)
                            if (statusText.text.toString().startsWith("КАМЕРА 1") ||
                                statusText.text.toString() == "Камера запущена") {
                                statusText.text = if (faces.isEmpty()) {
                                    "КАМЕРА 1 • лиц не обнаружено"
                                } else {
                                    "КАМЕРА 1 • обнаружено лиц: ${faces.size}"
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        runOnUiThread { statusText.text = "Ошибка анализа камеры" }
                    }
                    .addOnCompleteListener {
                        proxy.close()
                    }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                statusText.text = "КАМЕРА 1 • камера запущена"
            } catch (e: Exception) {
                statusText.text = "Не удалось запустить камеру"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockUpdater)
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
