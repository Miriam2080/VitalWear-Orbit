package com.janneth.vitalwear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val dataClient by lazy { Wearable.getDataClient(this) }

    private var currentSteps by mutableStateOf(0)
    private var stepGoal by mutableStateOf(10000)

    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var battery by mutableStateOf<Int?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)

    private var acceleration by mutableStateOf<Float?>(null)
    private var motionStatus by mutableStateOf("Sin lectura")
    private var status by mutableStateOf("Sin sincronizar")
    private var riskScore by mutableStateOf(0)
    private var lastSyncText by mutableStateOf("Sin sincronizar")

    private var sosAlert by mutableStateOf<String?>(null)
    private val eventLog = mutableStateListOf<String>()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var photoFile: File? = null
    private var showCamera by mutableStateOf(false)

    private val CAMERA_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainScreen(
                                currentSteps = currentSteps,
                                stepGoal = stepGoal,
                                onGoalChange = { newGoal -> stepGoal = newGoal },
                                pressure = pressure,
                                temperature = temperature,
                                heartRate = heartRate,
                                heartBeat = heartBeat,
                                battery = battery,
                                humidity = humidity,
                                proximity = proximity,
                                light = light,
                                magneticField = magneticField,
                                acceleration = acceleration,
                                motionStatus = motionStatus,
                                status = status,
                                riskScore = riskScore,
                                lastSyncText = lastSyncText,
                                sosAlert = sosAlert,
                                events = eventLog,
                                showCamera = showCamera,
                                activity = this@MainActivity,
                                onOpenGallery = { navController.navigate("gallery") },
                                onDismissSos = { sosAlert = null }
                            )
                        }

                        composable("gallery") {
                            PhotoGalleryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        dataClient.addListener(this)
        Wearable.getMessageClient(this).addListener(this)

        addEvent("App móvil iniciada")
    }

    override fun onDestroy() {
        super.onDestroy()

        dataClient.removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)

        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Permiso de cámara concedido",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Permiso de cámara denegado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem

                if (dataItem.uri.path?.compareTo("/steps") == 0) {
                    DataMapItem.fromDataItem(dataItem).dataMap.apply {
                        val stepsVal = getInt("steps")

                        val pressureVal =
                            if (containsKey("pressure")) getFloat("pressure") else null
                        val temperatureVal =
                            if (containsKey("temperature")) getFloat("temperature") else null
                        val heartRateVal =
                            if (containsKey("heartRate")) getFloat("heartRate") else null
                        val heartBeatVal =
                            if (containsKey("heartBeat")) getFloat("heartBeat") else null
                        val batteryVal =
                            if (containsKey("battery")) getInt("battery") else null
                        val humidityVal =
                            if (containsKey("humidity")) getFloat("humidity") else null
                        val proximityVal =
                            if (containsKey("proximity")) getFloat("proximity") else null
                        val lightVal =
                            if (containsKey("light")) getFloat("light") else null
                        val magneticFieldVal =
                            if (containsKey("magneticField")) getFloat("magneticField") else null
                        val accelerationVal =
                            if (containsKey("acceleration")) getFloat("acceleration") else null

                        val statusVal =
                            if (containsKey("status")) getString("status") else "Normal"
                        val motionStatusVal =
                            if (containsKey("motionStatus")) getString("motionStatus") else "Sin lectura"
                        val riskScoreVal =
                            if (containsKey("riskScore")) getInt("riskScore") else 0
                        val timestampVal =
                            if (containsKey("timestamp")) getLong("timestamp") else System.currentTimeMillis()

                        runOnUiThread {
                            currentSteps = stepsVal
                            pressure = pressureVal
                            temperature = temperatureVal
                            heartRate = heartRateVal
                            heartBeat = heartBeatVal
                            battery = batteryVal
                            humidity = humidityVal
                            proximity = proximityVal
                            light = lightVal
                            magneticField = magneticFieldVal
                            acceleration = accelerationVal

                            status = statusVal ?: "Normal"
                            motionStatus = motionStatusVal ?: "Sin lectura"
                            riskScore = riskScoreVal.coerceIn(0, 100)
                            lastSyncText = formatHour(timestampVal)

                            addEvent("Datos recibidos del reloj")
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/take_photo" -> {
                runOnUiThread {
                    showCamera = true
                    sendPhotoTakenConfirmation()

                    Toast.makeText(
                        this,
                        "Cámara lista para el reloj",
                        Toast.LENGTH_SHORT
                    ).show()

                    addEvent("Cámara abierta desde reloj")
                }
            }

            "/capture_photo" -> {
                runOnUiThread {
                    takePhoto()
                    showCamera = false
                    addEvent("Captura solicitada desde reloj")
                }
            }

            "/sos_alert" -> {
                val message = event.data?.toString(Charsets.UTF_8)
                    ?: "Alerta SOS recibida"

                runOnUiThread {
                    sosAlert = message
                    addEvent("SOS recibido desde reloj")

                    Toast.makeText(
                        this,
                        "SOS recibido desde el ⌚",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun startCamera(previewView: PreviewView) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()

                    imageCapture = ImageCapture.Builder().build()

                    cameraProvider.unbindAll()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.bindToLifecycle(
                        this as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    preview.setSurfaceProvider(previewView.surfaceProvider)
                } catch (e: Exception) {
                    Log.e("VitalWearMobile", "Error al configurar cámara", e)

                    Toast.makeText(
                        this,
                        "Error al abrir cámara: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("VitalWearMobile", "Error general en startCamera", e)

            Toast.makeText(
                this,
                "Error general al abrir cámara",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        photoFile = File(
            getExternalFilesDir(null),
            "photo_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile!!)
            .build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    sendPhotoToWatch()
                    sendPhotoCapturedConfirmation()
                    addEvent("Foto guardada y enviada al reloj")
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al tomar foto: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    addEvent("Error al tomar foto")
                }
            }
        )
    }

    private fun sendPhotoToWatch() {
        photoFile?.let { file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            if (bitmap == null) {
                Toast.makeText(
                    this,
                    "No se pudo leer la imagen",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val stream = ByteArrayOutputStream()

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                80,
                stream
            )

            val bytes = stream.toByteArray()
            val asset = Asset.createFromBytes(bytes)

            val dataMap = PutDataMapRequest
                .create("/photo_image")
                .apply {
                    dataMap.putAsset("photo", asset)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                .asPutDataRequest()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dataClient.putDataItem(dataMap).await()
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error al enviar foto: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun sendPhotoTakenConfirmation() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/photo_taken",
                        null
                    ).await()
                }
            } catch (e: Exception) {
                Log.e(
                    "VitalWearMobile",
                    "Error enviando confirmación de cámara",
                    e
                )
            }
        }
    }

    private fun sendPhotoCapturedConfirmation() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/photo_captured",
                        null
                    ).await()
                }
            } catch (e: Exception) {
                Log.e(
                    "VitalWearMobile",
                    "Error enviando confirmación de captura",
                    e
                )
            }
        }
    }

    private fun addEvent(text: String) {
        val event = "${formatHour(System.currentTimeMillis())}  $text"

        eventLog.add(0, event)

        if (eventLog.size > 8) {
            eventLog.removeAt(eventLog.lastIndex)
        }
    }
}

@Composable
fun MainScreen(
    currentSteps: Int,
    stepGoal: Int,
    onGoalChange: (Int) -> Unit,
    pressure: Float?,
    temperature: Float?,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?,
    acceleration: Float?,
    motionStatus: String,
    status: String,
    riskScore: Int,
    lastSyncText: String,
    sosAlert: String?,
    events: List<String>,
    showCamera: Boolean,
    activity: MainActivity,
    onOpenGallery: () -> Unit,
    onDismissSos: () -> Unit
) {
    if (showCamera) {
        CameraPreviewScreen(activity = activity)
    } else {
        MobileDashboardScreen(
            currentSteps = currentSteps,
            stepGoal = stepGoal,
            onGoalChange = onGoalChange,
            pressure = pressure,
            temperature = temperature,
            heartRate = heartRate,
            heartBeat = heartBeat,
            battery = battery,
            humidity = humidity,
            proximity = proximity,
            light = light,
            magneticField = magneticField,
            acceleration = acceleration,
            motionStatus = motionStatus,
            status = status,
            riskScore = riskScore,
            lastSyncText = lastSyncText,
            sosAlert = sosAlert,
            events = events,
            onOpenGallery = onOpenGallery,
            onDismissSos = onDismissSos
        )
    }
}

@Composable
fun CameraPreviewScreen(activity: MainActivity) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    scaleType = PreviewView.ScaleType.FILL_CENTER

                    if (
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        activity.startCamera(this)
                    } else {
                        Toast.makeText(
                            context,
                            "Permiso de cámara no concedido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Cámara controlada desde el reloj",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MobileDashboardScreen(
    currentSteps: Int,
    stepGoal: Int,
    onGoalChange: (Int) -> Unit,
    pressure: Float?,
    temperature: Float?,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?,
    acceleration: Float?,
    motionStatus: String,
    status: String,
    riskScore: Int,
    lastSyncText: String,
    sosAlert: String?,
    events: List<String>,
    onOpenGallery: () -> Unit,
    onDismissSos: () -> Unit
) {
    var selectedSection by remember { mutableStateOf("Resumen") }

    val sections = listOf(
        "Resumen",
        "Salud",
        "Ambiente",
        "Movimiento",
        "Eventos"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B5ED7),
                        Color(0xFF102A43),
                        Color(0xFF061826)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeaderMobileCard(
                    status = status,
                    riskScore = riskScore,
                    lastSyncText = lastSyncText
                )
            }

            item {
                SectionSelector(
                    sections = sections,
                    selected = selectedSection,
                    onSelected = { selectedSection = it }
                )
            }

            item {
                when (selectedSection) {
                    "Resumen" -> SummarySection(
                        currentSteps = currentSteps,
                        stepGoal = stepGoal,
                        riskScore = riskScore,
                        motionStatus = motionStatus,
                        battery = battery,
                        onOpenGallery = onOpenGallery
                    )

                    "Salud" -> HealthMobileSection(
                        currentSteps = currentSteps,
                        stepGoal = stepGoal,
                        onGoalChange = onGoalChange,
                        heartRate = heartRate,
                        heartBeat = heartBeat,
                        battery = battery
                    )

                    "Ambiente" -> EnvironmentMobileSection(
                        pressure = pressure,
                        temperature = temperature,
                        humidity = humidity,
                        proximity = proximity,
                        light = light,
                        magneticField = magneticField
                    )

                    "Movimiento" -> MotionMobileSection(
                        acceleration = acceleration,
                        motionStatus = motionStatus,
                        riskScore = riskScore
                    )

                    "Eventos" -> EventsMobileSection(events = events)
                }
            }
        }

        sosAlert?.let { alert ->
            SosAlertDialog(
                message = alert,
                onDismiss = onDismissSos
            )
        }
    }
}

@Composable
fun HeaderMobileCard(
    status: String,
    riskScore: Int,
    lastSyncText: String
) {
    val riskColor = when {
        riskScore >= 70 -> Color(0xFFFF5252)
        riskScore >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFF00E676)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(riskColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = CenterHorizontally) {
                    Text(
                        text = "$riskScore%",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "riesgo",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VitalWear Orbit pruebas",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Estado: $status",
                    color = riskColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Última sincronización: $lastSyncText",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SectionSelector(
    sections: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.take(3).forEach { section ->
                SectionChip(
                    text = section,
                    selected = selected == section,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(section) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.drop(3).forEach { section ->
                SectionChip(
                    text = section,
                    selected = selected == section,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(section) }
                )
            }
        }
    }
}

@Composable
fun SectionChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF80DEEA) else Color.White.copy(alpha = 0.12f)
    val fg = if (selected) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SummarySection(
    currentSteps: Int,
    stepGoal: Int,
    riskScore: Int,
    motionStatus: String,
    battery: Int?,
    onOpenGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val progress = if (stepGoal > 0) {
            (currentSteps.toFloat() / stepGoal).coerceIn(0f, 1f)
        } else {
            0f
        }

        GlassCard {
            Text(
                text = "Resumen general",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "$currentSteps pasos",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Meta diaria: $stepGoal pasos",
                color = Color.White.copy(alpha = 0.72f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).toInt()}% completado",
                color = Color(0xFF80DEEA),
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetricCard(
                title = "Riesgo",
                value = "$riskScore%",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFC107)
            )

            MiniMetricCard(
                title = "Movimiento",
                value = motionStatus,
                modifier = Modifier.weight(1f),
                color = Color(0xFF80DEEA)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetricCard(
                title = "Batería",
                value = "${battery ?: "-"}%",
                modifier = Modifier.weight(1f),
                color = Color(0xFF00E676)
            )

            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenGallery() }
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Galería",
                    tint = Color(0xFF80DEEA),
                    modifier = Modifier.size(30.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Galería",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Fotos tomadas",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun HealthMobileSection(
    currentSteps: Int,
    stepGoal: Int,
    onGoalChange: (Int) -> Unit,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?
) {
    var goalInput by remember { mutableStateOf(TextFieldValue(stepGoal.toString())) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SensorMobileCard(
            icon = Icons.Rounded.Favorite,
            title = "Ritmo cardiaco",
            value = "${heartRate?.toInt() ?: "-"} bpm",
            color = Color(0xFFFF5252)
        )

        SensorMobileCard(
            icon = Icons.Rounded.Favorite,
            title = "Latido",
            value = "${heartBeat?.toInt() ?: "-"}",
            color = Color(0xFFE91E63)
        )

        SensorMobileCard(
            icon = Icons.Rounded.BatteryFull,
            title = "Batería",
            value = "${battery ?: "-"}%",
            color = Color(0xFF00E676)
        )

        SensorMobileCard(
            icon = Icons.Rounded.Sensors,
            title = "Pasos recibidos",
            value = currentSteps.toString(),
            color = Color(0xFF80DEEA)
        )

        GlassCard {
            Text(
                text = "Configurar meta diaria",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = it },
                label = { Text("Número de pasos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val newGoal = goalInput.text.toIntOrNull()

                    if (newGoal != null && newGoal > 0) {
                        onGoalChange(newGoal)

                        Toast.makeText(
                            context,
                            "Meta actualizada",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Ingrese un número válido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar meta")
            }
        }
    }
}

@Composable
fun EnvironmentMobileSection(
    pressure: Float?,
    temperature: Float?,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SensorMobileCard(
            icon = Icons.Rounded.Thermostat,
            title = "Temperatura",
            value = "${temperature?.toInt() ?: "-"} °C",
            color = Color(0xFFFF7043)
        )

        SensorMobileCard(
            icon = Icons.Rounded.Air,
            title = "Presión",
            value = "${pressure?.toInt() ?: "-"} hPa",
            color = Color(0xFF18FFFF)
        )

        SensorMobileCard(
            icon = Icons.Rounded.WaterDrop,
            title = "Humedad",
            value = "${humidity?.toInt() ?: "-"}%",
            color = Color(0xFF40C4FF)
        )

        SensorMobileCard(
            icon = Icons.Rounded.LightMode,
            title = "Luz ambiental",
            value = "${light?.toInt() ?: "-"} lx",
            color = Color(0xFFFFEA00)
        )

        SensorMobileCard(
            icon = Icons.Rounded.Sensors,
            title = "Proximidad",
            value = "${proximity ?: "-"} cm",
            color = Color(0xFFFF9800)
        )

        SensorMobileCard(
            icon = Icons.Rounded.Explore,
            title = "Campo magnético",
            value = "${magneticField?.toInt() ?: "-"} μT",
            color = Color(0xFFE040FB)
        )
    }
}

@Composable
fun MotionMobileSection(
    acceleration: Float?,
    motionStatus: String,
    riskScore: Int
) {
    val acc = acceleration ?: 0f

    val movementColor = when {
        acc >= 25f -> Color(0xFFFF5252)
        acc >= 15f -> Color(0xFFFFC107)
        else -> Color(0xFF00E676)
    }

    val riskColor = when {
        riskScore >= 70 -> Color(0xFFFF5252)
        riskScore >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFF00E676)
    }

    val riskText = when {
        riskScore >= 70 -> "Riesgo alto"
        riskScore >= 40 -> "Riesgo moderado"
        else -> "Riesgo bajo"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Tarjeta principal del análisis
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(movementColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = "Análisis de movimiento",
                        tint = movementColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Análisis de movimiento",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Monitoreo del acelerómetro",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(movementColor.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "Estado actual",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = motionStatus,
                        color = movementColor,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Métricas de aceleración y riesgo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aceleración",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${acc.toInt()} m/s²",
                    color = Color(0xFF80DEEA),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            GlassCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nivel de riesgo",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$riskScore%",
                    color = riskColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Indicador visual del riesgo
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Evaluación de riesgo",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(riskColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = riskText,
                        color = riskColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = riskScore.coerceIn(0, 100) / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "El nivel se calcula con los datos recibidos desde el reloj.",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Información de la función
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF80DEEA).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = "Información",
                        tint = Color(0xFF80DEEA),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Detección inteligente",
                    color = Color(0xFF80DEEA),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "El reloj utiliza el acelerómetro para identificar movimiento activo o movimientos bruscos. Cuando detecta un nivel de riesgo, puede enviar una alerta SOS al teléfono.",
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EventsMobileSection(events: List<String>) {
    GlassCard {
        Text(
            text = "Historial de operación",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (events.isEmpty()) {
            Text(
                text = "Aún no hay eventos registrados.",
                color = Color.White.copy(alpha = 0.70f)
            )
        } else {
            events.forEach { event ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = event,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SensorMobileCard(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = value,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MiniMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.70f),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SosAlertDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Alerta SOS recibida",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

@Composable
fun PhotoGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(listPhotoFiles(context)) }
    var selectedPhoto by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B5ED7),
                        Color(0xFF102A43),
                        Color(0xFF061826)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) {
                    Text("Volver")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Galería",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (photos.isEmpty()) {
                Text(
                    text = "No hay fotos disponibles.",
                    color = Color.White,
                    modifier = Modifier.align(CenterHorizontally)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos) { file ->
                        PhotoItem(
                            file = file,
                            onOpen = { selectedPhoto = file },
                            onDelete = {
                                selectedPhoto = file
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedPhoto != null && !showDeleteDialog) {
        Dialog(onDismissRequest = { selectedPhoto = null }) {
            val bitmap = BitmapFactory
                .decodeFile(selectedPhoto!!.absolutePath)
                ?.asImageBitmap()

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Foto grande",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("No se pudo cargar la imagen")
            }
        }
    }

    if (showDeleteDialog && selectedPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar foto?") },
            text = { Text("¿Seguro que deseas borrar esta foto?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedPhoto?.delete()
                        photos = listPhotoFiles(context)
                        showDeleteDialog = false
                        selectedPhoto = null
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PhotoItem(
    file: File,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val bitmap = remember(file) {
        BitmapFactory
            .decodeFile(file.absolutePath)
            ?.asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Foto",
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(70.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Foto capturada desde VitalWear",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                painter = painterResource(android.R.drawable.ic_menu_delete),
                contentDescription = "Borrar",
                tint = Color.White,
                modifier = Modifier
                    .size(30.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

fun listPhotoFiles(context: Context): List<File> {
    val dir = context.getExternalFilesDir(null)

    return dir
        ?.listFiles { file ->
            file.name.startsWith("photo_") && file.name.endsWith(".jpg")
        }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

fun formatHour(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}