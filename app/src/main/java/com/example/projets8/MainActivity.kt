package com.example.projets8

import android.Manifest

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle

import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts

import com.example.projets8.databinding.ActivityMainBinding

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), DetectorListener {


    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null // ? utilisé pour faire un type nullable (et initié à null)
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private var modelName: String = "flowers_model.tflite"
    private var labelName: String = "flowers_labels.txt"
    private lateinit var cameraExecutor: ExecutorService


    //Déclaration d'éléments statiques à l'aide d'un companion :
    companion object {
        private const val TAG = "Camera"
        private const val CODE_PERMISSIONS = 10
        private val PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
        const val MODEL_PATH = "flowers_model.tflite"
        const val LABELS_PATH = "new_labels.txt"
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val menuButton: ImageButton = findViewById(R.id.menu_button)
        menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }

        // Vérfification de l'accès à la caméra
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, CODE_PERMISSIONS)
        }
        detector = Detector(baseContext, modelName, labelName,this)
        detector.setup()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.popup_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            onMenuItemClick(menuItem)
        }
        popup.show()
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_one -> {
                if (modelName != "flowers_model.tflite") {
                    modelName = "flowers_model.tflite"
                    labelName = "flowers_labels.txt"
                    detector = Detector(baseContext, modelName, labelName, this)
                    detector.setup()
                }
                true
            }
            R.id.action_two -> {
                if (modelName != "crockery_model.tflite") {
                    modelName = "crockery_model.tflite"
                    labelName = "crockery_labels.txt"
                    detector = Detector(baseContext, modelName, labelName, this)
                    detector.setup()
                }
                true
            }
            else -> false
        }
    }

    // Gestion de la caméra :
    private fun startCamera() {
        val cameraProviderProcess = ProcessCameraProvider.getInstance(this) // Returns a ListenableFuture<ProcessCameraProvider>
        cameraProviderProcess.addListener({
            // The Process is ready
            cameraProvider  = cameraProviderProcess.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this)) // le listener est executé sur le thread principal pour que ce soit thread safe
    }

    private fun bindCamera() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val buffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )

            imageProxy.use { buffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val rotate = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                buffer, 0, 0, buffer.width, buffer.height,
                rotate, true
            )

            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll() //reset de tous les binds

        try {
            camera = cameraProvider.bindToLifecycle( // on affecte
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, CODE_PERMISSIONS)
        }
    }

    override fun onDetect(boundingBoxes: List<Box>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }
    }
    override fun onEmptyDetect() {
        binding.overlay.invalidate()
    }


    // Fonctions utilitaires (appelées par d'autres fonctions)
    private fun allPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }




}