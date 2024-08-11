package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var arFragment: ArFragment
    private lateinit var cubeRenderable: ModelRenderable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment

        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.BLUE))
            .thenAccept { material ->
                cubeRenderable = ShapeFactory.makeCube(
                    Vector3(0.1f, 0.1f, 0.1f),  // Tamanho do cubo
                    Vector3(0f, 0.1f, 0f),      // Posição
                    material
                )
                cubeRenderable.isShadowCaster = true
                cubeRenderable.isShadowReceiver = true
            }

        // Adiciona o cubo à cena
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            val cube = TransformableNode(arFragment.transformationSystem)
            cube.renderable = cubeRenderable
            cube.setParent(anchorNode)
            cube.select()
        }
    }

    // Outros métodos para detectar e reagir aos gestos...

    override fun onDetect(labels: List<String>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            labels.forEach { label ->
                Log.d("Gesture Detection", "Detected: $label")
                when (label) {
                    "parar" -> rotateCube(-10f, 0f) // Rotaciona para a esquerda

                }
            }
        }
    }

    private fun rotateCube(deltaX: Float, deltaY: Float) {
        val cubeNode = arFragment.arSceneView.scene.children.firstOrNull { it is TransformableNode } as? TransformableNode
        cubeNode?.apply {
            localRotation = localRotation.multiply(Quaternion(Vector3(1f, 0f, 0f), deltaX))
            localRotation = localRotation.multiply(Quaternion(Vector3(0f, 1f, 0f), deltaY))
        }
    }
}
    

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()

            // Iniciar a primeira detecção
            handler.post(detectRunnable)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
        handler.removeCallbacks(detectRunnable)  // Remover o Runnable quando a Activity for destruída
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        // Reagendar a próxima detecção para 2 segundos depois
        handler.postDelayed(detectRunnable, 3000)
    }

    override fun onDetect(labels: List<String>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            // Exibe os labels no console
            labels.forEach { label ->
                Log.d("Gesture Detection", "Detected: $label")
            }

            // Reagendar a próxima detecção para 2 segundos depois
            handler.postDelayed(detectRunnable, 3000)
        }
    }

