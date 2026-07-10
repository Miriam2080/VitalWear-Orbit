package com.janneth.vitalwear

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.janneth.vitalwear.ui.theme.WearAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

enum class OrbitScreen {
    HOME,
    HEALTH,
    ENVIRONMENT,
    MOTION,
    CAMERA,
    EVENTS
}

class MainActivity : ComponentActivity(),
    SensorEventListener,
    MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {

    private lateinit var sensorManager: SensorManager

    private var stepSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var heartBeatSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var magneticFieldSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    private var steps by mutableStateOf(0)
    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var batteryLevel by mutableStateOf<Int?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)
    private var acceleration by mutableStateOf<Float?>(null)
    private var motionStatus by mutableStateOf("Estable")

    private var hasBodySensorsPermission = false

    private var cameraReady by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private var photoUri by mutableStateOf<String?>(null)

    private var notificationMessage by mutableStateOf<String?>(null)
    private var notificationType by mutableStateOf(NotificationType.INFO)
    private var showNotification by mutableStateOf(false)

    private var lastSyncText by mutableStateOf("Sin sincronizar")
    private var lastMotionAlertTime = 0L

    private val eventLog = mutableStateListOf<String>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val dataClient by lazy { Wearable.getDataClient(this) }

    enum class NotificationType {
        SUCCESS,
        ERROR,
        INFO
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSensorReading()
        } else {
            showNotification(
                message = "Permiso de actividad denegado",
                type = NotificationType.ERROR
            )
        }
    }

    private val requestBodySensorsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBodySensorsPermission = isGranted

        if (isGranted) {
            heartRateSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            heartBeatSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            addEvent("Sensores corporales no autorizados")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartBeatSensor = sensorManager.getDefaultSensor(65572)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            WearAppTheme {
                var selectedScreen by remember { mutableStateOf(OrbitScreen.HOME) }

                Box(modifier = Modifier.fillMaxSize()) {
                    OrbitApp(
                        selectedScreen = selectedScreen,
                        onScreenChange = { selectedScreen = it },
                        steps = steps,
                        pressure = pressure,
                        temperature = temperature,
                        heartRate = if (heartRateSensor == null || !hasBodySensorsPermission) null else heartRate,
                        heartBeat = if (heartBeatSensor == null || !hasBodySensorsPermission) null else heartBeat,
                        battery = batteryLevel,
                        heartRateAvailable = heartRateSensor != null,
                        heartBeatAvailable = heartBeatSensor != null,
                        humidity = humidity,
                        proximity = proximity,
                        light = light,
                        magneticField = magneticField,
                        acceleration = acceleration,
                        motionStatus = motionStatus,
                        lastSyncText = lastSyncText,
                        events = eventLog,
                        onIncrement = { incrementSteps() },
                        onDecrement = { decrementSteps() },
                        onSyncClick = { syncWearData() },
                        onSosClick = { sendSafetyAlert() },
                        onTakePhoto = { sendTakePhotoMessage() },
                        isProcessing = isProcessing,
                        cameraReady = cameraReady,
                        photoUri = photoUri
                    )

                    if (showNotification) {
                        ModernNotification(
                            message = notificationMessage ?: "",
                            type = notificationType,
                            onDismiss = { showNotification = false }
                        )
                    }
                }
            }
        }

        checkAndRequestPermissions()
        getBatteryLevel()

        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)

        addEvent("VitalWear Orbit iniciado")
    }

    private fun incrementSteps() {
        steps += 10
        addEvent("Pasos +10")
    }

    private fun decrementSteps() {
        if (steps >= 10) {
            steps -= 10
            addEvent("Pasos -10")
        }
    }

    private fun checkAndRequestPermissions() {
        val activityRecognitionGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val bodySensorsGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        hasBodySensorsPermission = bodySensorsGranted

        if (activityRecognitionGranted) {
            startSensorReading()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (!bodySensorsGranted) {
            requestBodySensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        } else {
            heartRateSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            heartBeatSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun startSensorReading() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        temperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        humiditySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        magneticFieldSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> steps = event.values[0].toInt()
            Sensor.TYPE_PRESSURE -> pressure = event.values[0]
            Sensor.TYPE_AMBIENT_TEMPERATURE -> temperature = event.values[0]
            Sensor.TYPE_HEART_RATE -> heartRate = event.values[0]
            65572 -> heartBeat = event.values[0]
            Sensor.TYPE_RELATIVE_HUMIDITY -> humidity = event.values[0]
            Sensor.TYPE_PROXIMITY -> proximity = event.values[0]
            Sensor.TYPE_LIGHT -> light = event.values[0]
            Sensor.TYPE_MAGNETIC_FIELD -> magneticField = event.values[0]

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val force = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                acceleration = force

                motionStatus = when {
                    force >= 25f -> "Movimiento brusco"
                    force >= 15f -> "Movimiento activo"
                    else -> "Estable"
                }

                if (force >= 25f) {
                    val now = System.currentTimeMillis()

                    if (now - lastMotionAlertTime > 8000) {
                        lastMotionAlertTime = now
                        addEvent("Movimiento brusco detectado")
                        showNotification(
                            message = "Movimiento brusco",
                            type = NotificationType.ERROR
                        )
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere para esta práctica.
    }

    private fun getBatteryLevel() {
        val batteryStatus: Intent? = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        batteryLevel = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        )
    }

    private fun syncWearData() {
        scope.launch {
            try {
                val status = buildStatusText()
                val riskScore = calculateRiskScore()

                val dataMap = com.google.android.gms.wearable.PutDataMapRequest
                    .create("/steps")
                    .apply {
                        dataMap.putInt("steps", steps)

                        pressure?.let { dataMap.putFloat("pressure", it) }
                        temperature?.let { dataMap.putFloat("temperature", it) }
                        heartRate?.let { dataMap.putFloat("heartRate", it) }
                        heartBeat?.let { dataMap.putFloat("heartBeat", it) }
                        batteryLevel?.let { dataMap.putInt("battery", it) }
                        humidity?.let { dataMap.putFloat("humidity", it) }
                        proximity?.let { dataMap.putFloat("proximity", it) }
                        light?.let { dataMap.putFloat("light", it) }
                        magneticField?.let { dataMap.putFloat("magneticField", it) }
                        acceleration?.let { dataMap.putFloat("acceleration", it) }

                        dataMap.putString("status", status)
                        dataMap.putString("motionStatus", motionStatus)
                        dataMap.putInt("riskScore", riskScore)
                        dataMap.putLong("timestamp", System.currentTimeMillis())
                    }
                    .asPutDataRequest()

                dataClient.putDataItem(dataMap).await()

                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                lastSyncText = "Sync ${formatter.format(Date())}"

                addEvent("Datos enviados al celular")

                showNotification(
                    message = "Sincronizado",
                    type = NotificationType.SUCCESS
                )
            } catch (e: Exception) {
                addEvent("Error al sincronizar")
                showNotification(
                    message = "Error al sincronizar",
                    type = NotificationType.ERROR
                )
            }
        }
    }

    private fun calculateRiskScore(): Int {
        var score = 0

        if ((heartRate ?: 0f) >= 120f) score += 35
        if ((batteryLevel ?: 100) <= 20) score += 20
        if ((light ?: 100f) <= 5f) score += 15
        if ((temperature ?: 22f) >= 35f) score += 15
        if ((acceleration ?: 0f) >= 25f) score += 35

        return score.coerceIn(0, 100)
    }

    private fun buildStatusText(): String {
        val riskScore = calculateRiskScore()

        return when {
            riskScore >= 70 -> "Alerta alta"
            riskScore >= 40 -> "Precaución"
            else -> "Normal"
        }
    }

    private fun sendSafetyAlert() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isEmpty()) {
                    runOnUiThread {
                        showNotification(
                            message = "Empareja el celular",
                            type = NotificationType.ERROR
                        )
                    }
                    return@launch
                }

                val alertText = """
                    SOS VitalWear
                    Estado: ${buildStatusText()}
                    Movimiento: $motionStatus
                    Riesgo: ${calculateRiskScore()}%
                    Pasos: $steps
                """.trimIndent()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/sos_alert",
                        alertText.toByteArray()
                    ).await()
                }

                runOnUiThread {
                    addEvent("SOS enviado al celular")
                    showNotification(
                        message = "SOS enviado",
                        type = NotificationType.SUCCESS
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addEvent("Error al enviar SOS")
                    showNotification(
                        message = "Error al enviar SOS",
                        type = NotificationType.ERROR
                    )
                }
            }
        }
    }

    fun sendTakePhotoMessage() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isNotEmpty()) {
                    if (!cameraReady) {
                        messageClient.sendMessage(
                            nodes[0].id,
                            "/take_photo",
                            null
                        ).await()

                        runOnUiThread {
                            addEvent("Solicitud para abrir cámara")
                        }
                    } else {
                        runOnUiThread {
                            isProcessing = true
                        }

                        messageClient.sendMessage(
                            nodes[0].id,
                            "/capture_photo",
                            null
                        ).await()

                        runOnUiThread {
                            addEvent("Solicitud para tomar foto")
                        }
                    }
                } else {
                    runOnUiThread {
                        showNotification(
                            message = "Empareja el celular",
                            type = NotificationType.ERROR
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showNotification(
                        message = "Error de cámara",
                        type = NotificationType.ERROR
                    )
                }
            }
        }
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        when (event.path) {
            "/photo_taken" -> {
                runOnUiThread {
                    cameraReady = true
                    addEvent("Cámara abirta")
                    showNotification(
                        message = "Cámara lista",
                        type = NotificationType.SUCCESS
                    )
                }
            }

            "/photo_captured" -> {
                runOnUiThread {
                    isProcessing = false
                    cameraReady = false
                    addEvent("Foto capturada")
                    showNotification(
                        message = "Foto tomada",
                        type = NotificationType.SUCCESS
                    )
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem

                if (dataItem.uri.path?.compareTo("/photo_image") == 0) {
                    val asset = dataItem.assets["photo"]

                    asset?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val inputStream = Wearable
                                    .getDataClient(this@MainActivity)
                                    .getFdForAsset(it)
                                    .await()
                                    .inputStream

                                val tempFile = File(
                                    cacheDir,
                                    "photo_${System.currentTimeMillis()}.jpg"
                                )

                                tempFile.outputStream().use { output ->
                                    inputStream.copyTo(output)
                                }

                                runOnUiThread {
                                    photoUri = tempFile.absolutePath
                                    addEvent("Foto recibida")
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    showNotification(
                                        message = "Error al recibir imagen",
                                        type = NotificationType.ERROR
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addEvent(text: String) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val event = "${formatter.format(Date())}  $text"

        eventLog.add(0, event)

        if (eventLog.size > 6) {
            eventLog.removeAt(eventLog.lastIndex)
        }
    }

    private fun showNotification(
        message: String,
        type: NotificationType = NotificationType.INFO
    ) {
        notificationMessage = message
        notificationType = type
        showNotification = true
    }

    override fun onDestroy() {
        super.onDestroy()

        sensorManager.unregisterListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
    }
}

@Composable
fun OrbitApp(
    selectedScreen: OrbitScreen,
    onScreenChange: (OrbitScreen) -> Unit,
    steps: Int,
    pressure: Float?,
    temperature: Float?,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?,
    heartRateAvailable: Boolean,
    heartBeatAvailable: Boolean,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?,
    acceleration: Float?,
    motionStatus: String,
    lastSyncText: String,
    events: List<String>,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSyncClick: () -> Unit,
    onSosClick: () -> Unit,
    onTakePhoto: () -> Unit,
    isProcessing: Boolean,
    cameraReady: Boolean,
    photoUri: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF203A43),
                        Color(0xFF102A43),
                        Color.Black
                    )
                )
            )
    ) {
        when (selectedScreen) {
            OrbitScreen.HOME -> HomeScreen(
                steps = steps,
                battery = battery,
                heartRate = heartRate,
                acceleration = acceleration,
                motionStatus = motionStatus,
                lastSyncText = lastSyncText,
                onScreenChange = onScreenChange,
                onSyncClick = onSyncClick
            )

            OrbitScreen.HEALTH -> HealthScreen(
                steps = steps,
                heartRate = heartRate,
                heartBeat = heartBeat,
                battery = battery,
                heartRateAvailable = heartRateAvailable,
                heartBeatAvailable = heartBeatAvailable,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                onHome = { onScreenChange(OrbitScreen.HOME) }
            )

            OrbitScreen.ENVIRONMENT -> EnvironmentScreen(
                pressure = pressure,
                temperature = temperature,
                humidity = humidity,
                proximity = proximity,
                light = light,
                magneticField = magneticField,
                onHome = { onScreenChange(OrbitScreen.HOME) }
            )

            OrbitScreen.MOTION -> MotionScreen(
                acceleration = acceleration,
                motionStatus = motionStatus,
                onSosClick = onSosClick,
                onHome = { onScreenChange(OrbitScreen.HOME) }
            )

            OrbitScreen.CAMERA -> CameraScreen(
                onTakePhoto = onTakePhoto,
                isProcessing = isProcessing,
                cameraReady = cameraReady,
                photoUri = photoUri,
                onHome = { onScreenChange(OrbitScreen.HOME) }
            )

            OrbitScreen.EVENTS -> EventsScreen(
                events = events,
                onHome = { onScreenChange(OrbitScreen.HOME) }
            )
        }
    }
}

@Composable
fun HomeScreen(
    steps: Int,
    battery: Int?,
    heartRate: Float?,
    acceleration: Float?,
    motionStatus: String,
    lastSyncText: String,
    onScreenChange: (OrbitScreen) -> Unit,
    onSyncClick: () -> Unit
) {
    val riskScore = calculateUiRiskScore(
        heartRate = heartRate,
        battery = battery,
        acceleration = acceleration
    )

    val riskColor = when {
        riskScore >= 70 -> Color(0xFFFF5252)
        riskScore >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFF00E676)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = 34.dp,
            bottom = 34.dp,
            start = 14.dp,
            end = 14.dp
        )
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "VitalWear",
                    color = Color.White,
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Orbit",
                    color = Color(0xFF80DEEA),
                    style = MaterialTheme.typography.caption1,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            OrbitRingCard(
                value = riskScore,
                label = "riesgo",
                color = riskColor,
                centerText = "$riskScore%"
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            MiniStatusCard(
                motionStatus = motionStatus,
                steps = steps,
                battery = battery,
                lastSyncText = lastSyncText
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            ActionButton(
                text = "Sincronizar",
                color = Color(0xFF00B8D4),
                textColor = Color.Black,
                onClick = onSyncClick
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            SectionTitle("Menú")
            Spacer(modifier = Modifier.height(6.dp))
        }

        item {
            CompactMenuGrid(
                onHealth = { onScreenChange(OrbitScreen.HEALTH) },
                onEnvironment = { onScreenChange(OrbitScreen.ENVIRONMENT) },
                onMotion = { onScreenChange(OrbitScreen.MOTION) },
                onCamera = { onScreenChange(OrbitScreen.CAMERA) },
                onEvents = { onScreenChange(OrbitScreen.EVENTS) }
            )
        }
    }
}

@Composable
fun MiniStatusCard(
    motionStatus: String,
    steps: Int,
    battery: Int?,
    lastSyncText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = motionStatus,
                color = Color.White,
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Pasos $steps · Batería ${battery ?: "-"}%",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center
            )

            Text(
                text = lastSyncText,
                color = Color(0xFF80DEEA),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HealthScreen(
    steps: Int,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?,
    heartRateAvailable: Boolean,
    heartBeatAvailable: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onHome: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp)
    ) {
        item {
            HeaderSection(
                title = "Salud",
                subtitle = "Datos biométricos",
                onHome = onHome
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Favorite,
                title = "Ritmo cardiaco",
                value = when {
                    !heartRateAvailable -> "No disponible"
                    heartRate == null -> "Sin lectura"
                    else -> "${heartRate.toInt()} bpm"
                },
                color = Color(0xFFFF5252)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Favorite,
                title = "Latido",
                value = when {
                    !heartBeatAvailable -> "No disponible"
                    heartBeat == null -> "Sin lectura"
                    else -> heartBeat.toInt().toString()
                },
                color = Color(0xFFFF4081)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.BatteryStd,
                title = "Batería",
                value = "${battery ?: "-"}%",
                color = Color(0xFF00E676)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Sensors,
                title = "Pasos",
                value = steps.toString(),
                color = Color(0xFF40C4FF)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = onDecrement,
                    modifier = Modifier.size(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF253746)
                    )
                ) {
                    Text("-10", color = Color.White)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onIncrement,
                    modifier = Modifier.size(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF253746)
                    )
                ) {
                    Text("+10", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EnvironmentScreen(
    pressure: Float?,
    temperature: Float?,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?,
    onHome: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp)
    ) {
        item {
            HeaderSection(
                title = "Ambiente",
                subtitle = "Sensores externos",
                onHome = onHome
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Thermostat,
                title = "Temperatura",
                value = "${temperature?.toInt() ?: "-"} °C",
                color = Color(0xFFFF7043)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Air,
                title = "Presión",
                value = "${pressure?.toInt() ?: "-"} hPa",
                color = Color(0xFF18FFFF)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.WaterDrop,
                title = "Humedad",
                value = "${humidity?.toInt() ?: "-"}%",
                color = Color(0xFF40C4FF)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.LightMode,
                title = "Luz",
                value = "${light?.toInt() ?: "-"} lx",
                color = Color(0xFFFFEA00)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Sensors,
                title = "Proximidad",
                value = "${proximity ?: "-"} cm",
                color = Color(0xFFFF9800)
            )
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Sensors,
                title = "Campo magnético",
                value = "${magneticField?.toInt() ?: "-"} μT",
                color = Color(0xFFE040FB)
            )
        }
    }
}

@Composable
fun MotionScreen(
    acceleration: Float?,
    motionStatus: String,
    onSosClick: () -> Unit,
    onHome: () -> Unit
) {
    val accValue = acceleration ?: 0f

    val color = when {
        accValue >= 25f -> Color(0xFFFF5252)
        accValue >= 15f -> Color(0xFFFFC107)
        else -> Color(0xFF00E676)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp)
    ) {
        item {
            HeaderSection(
                title = "Movimiento",
                subtitle = "Acelerómetro",
                onHome = onHome
            )
        }

        item {
            OrbitRingCard(
                value = ((accValue / 30f) * 100).toInt().coerceIn(0, 100),
                label = "mov.",
                color = color,
                centerText = accValue.toInt().toString()
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            SensorTile(
                icon = Icons.Rounded.Sensors,
                title = "Estado",
                value = motionStatus,
                color = color
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            ActionButton(
                text = "Enviar SOS",
                color = Color(0xFFFF5252),
                textColor = Color.White,
                onClick = onSosClick
            )
        }
    }
}

@Composable
fun CameraScreen(
    onTakePhoto: () -> Unit,
    isProcessing: Boolean,
    cameraReady: Boolean,
    photoUri: String?,
    onHome: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp)
    ) {
        item {
            HeaderSection(
                title = "Cámara",
                subtitle = "Control remoto",
                onHome = onHome
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onTakePhoto,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = when {
                                isProcessing -> Color.Gray
                                cameraReady -> Color(0xFF00E676)
                                else -> Color(0xFF00B8D4)
                            }
                        ),
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 200.dp)
                            .height(54.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                tint = if (cameraReady) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )

                            Text(
                                text = when {
                                    isProcessing -> "Procesando"
                                    cameraReady -> "Tomar foto"
                                    else -> "Abrir cámara"
                                },
                                color = if (cameraReady) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.caption1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            isProcessing -> "Esperando al celular..."
                            cameraReady -> "Cámara lista"
                            else -> "Abre cámara del teléfono"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        photoUri?.let { uri ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
                ZoomableImage(imageUri = uri)
            }
        }
    }
}

@Composable
fun EventsScreen(
    events: List<String>,
    onHome: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp)
    ) {
        item {
            HeaderSection(
                title = "Eventos",
                subtitle = "Historial rápido",
                onHome = onHome
            )
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "Aún no hay eventos",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            events.forEach { event ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = event,
                            color = Color.White,
                            style = MaterialTheme.typography.caption2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMenuGrid(
    onHealth: () -> Unit,
    onEnvironment: () -> Unit,
    onMotion: () -> Unit,
    onCamera: () -> Unit,
    onEvents: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            MiniMenuButton("Salud", Color(0xFFFF5252), onHealth)
            Spacer(modifier = Modifier.width(6.dp))
            MiniMenuButton("Ambiente", Color(0xFF18FFFF), onEnvironment)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            MiniMenuButton("Mov.", Color(0xFFFFC107), onMotion)
            Spacer(modifier = Modifier.width(6.dp))
            MiniMenuButton("Cámara", Color(0xFF00B8D4), onCamera)
        }

        Spacer(modifier = Modifier.height(6.dp))

        MiniMenuButton("Eventos", Color(0xFFB388FF), onEvents)
    }
}

@Composable
fun MiniMenuButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(76.dp)
            .height(36.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = color
        )
    ) {
        Text(
            text = text,
            color = Color.Black,
            style = MaterialTheme.typography.caption2,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HeaderSection(
    title: String,
    subtitle: String,
    onHome: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onHome,
            modifier = Modifier.size(34.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.White.copy(alpha = 0.16f)
            )
        ) {
            Text(
                text = "⌂",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            color = Color(0xFF80DEEA),
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun OrbitRingCard(
    value: Int,
    label: String,
    color: Color,
    centerText: String
) {
    Box(
        modifier = Modifier
            .size(98.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = value / 100f,
            modifier = Modifier.size(90.dp),
            indicatorColor = color,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeWidth = 7.dp
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                color = Color.White,
                style = MaterialTheme.typography.title2,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.caption2
            )
        }
    }
}

@Composable
fun SensorTile(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.caption2
                )

                Text(
                    text = value,
                    color = Color.White,
                    style = MaterialTheme.typography.caption1,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = color
        )
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF80DEEA),
        style = MaterialTheme.typography.caption1,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun ZoomableImage(imageUri: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Foto tomada",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun ModernNotification(
    message: String,
    type: MainActivity.NotificationType,
    onDismiss: () -> Unit
) {
    val backgroundColor = when (type) {
        MainActivity.NotificationType.SUCCESS -> Color(0xFF00C853)
        MainActivity.NotificationType.ERROR -> Color(0xFFFF5252)
        MainActivity.NotificationType.INFO -> Color(0xFF2196F3)
    }

    val icon = when (type) {
        MainActivity.NotificationType.SUCCESS -> Icons.Filled.CheckCircle
        MainActivity.NotificationType.ERROR -> Icons.Filled.Error
        MainActivity.NotificationType.INFO -> Icons.Filled.Info
    }

    val offsetY by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 300),
        label = "notification_animation"
    )

    LaunchedEffect(message) {
        delay(3000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .zIndex(1000f)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(LocalDensity.current) { offsetY.toDp() })
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.caption2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun calculateUiRiskScore(
    heartRate: Float?,
    battery: Int?,
    acceleration: Float?
): Int {
    var score = 0

    if ((heartRate ?: 0f) >= 120f) score += 35
    if ((battery ?: 100) <= 20) score += 20
    if ((acceleration ?: 0f) >= 25f) score += 35

    return score.coerceIn(0, 100)
}