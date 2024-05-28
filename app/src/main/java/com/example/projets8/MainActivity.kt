package com.example.projets8

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {

    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var cameraExecutor: ExecutorService

    //Déclaration d'éléments statiques à l'aide d'un companion :
    companion object {
        private const val TAG = "Camera"
        private const val CODE_PERMISSIONS = 10
        private val PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vérfification de l'accès à la caméra
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, CODE_PERMISSIONS)
        }

    }

    // Gestion de la caméra :
    private fun startCamera() {
        val cameraProviderProcess = ProcessCameraProvider.getInstance(this) // Returns a ListenableFuture<ProcessCameraProvider>
        cameraProviderProcess.addListener({
            // The Process is ready
            cameraProvider  = cameraProviderProcess.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this)) // le listener est executé sur le thread principal pour que ce soit thread safe
    }


    // Fonctions utilitaires (appelées par d'autres fonctions)
    private fun allPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }




}