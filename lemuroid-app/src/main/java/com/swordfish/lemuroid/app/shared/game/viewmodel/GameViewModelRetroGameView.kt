package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.firegps.FireGPSLogic
import com.swordfish.lemuroid.app.shared.firegps.LocationFileParser
import com.swordfish.lemuroid.app.shared.game.ShaderChooser
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.HDModeQuality
import com.swordfish.lemuroid.common.coroutines.MutableStateProperty
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.view.disableTouchEvents
import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.game.GameLoaderError
import com.swordfish.lemuroid.lib.game.GameLoaderException
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ImmersiveMode
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.VirtualFile
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class GameViewModelRetroGameView(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val settingsManager: SettingsManager,
    private val coreVariablesManager: CoreVariablesManager,
    private val sideEffects: GameViewModelSideEffects,
    private val rumbleManager: RumbleManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    sealed interface GameState {
        data object Uninitialized : GameState

        data class Loading(val message: String) : GameState

        data class Loaded(
            val gameData: GameLoader.GameData,
            val retroViewData: GLRetroViewData,
        ) : GameState

        data object Ready : GameState
    }

    private val gameState: MutableStateFlow<GameState> = MutableStateFlow(GameState.Uninitialized)

    private val retroGameViewFlow = MutableStateFlow<GLRetroView?>(null)
    var retroGameView: GLRetroView? by MutableStateProperty(retroGameViewFlow)

    val fireGPSLogic = FireGPSLogic()
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation ?: return
            val view = retroGameView ?: return
            fireGPSLogic.updateLocation(lastLocation, view)
        }
    }

    fun updateGpsStatus(status: Int) {
        fireGPSLogic.setStatus(status)
        val view = retroGameView ?: return
        fireGPSLogic.writeStatusToRam(view)
        
        // If permission was just granted, start updates immediately
        if (status == FireGPSLogic.STATUS_READY) {
            startLocationUpdates()
        }
    }

    fun getGameState(): Flow<GameState> {
        return gameState.debounce(200)
    }

    suspend fun initialize(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
    ) {
        val currentState = gameState.value
        if (currentState != GameState.Uninitialized) return

        val autoSaveEnabled = settingsManager.autoSave()
        val filter = settingsManager.screenFilter()
        val hdMode = settingsManager.hdMode()
        val hdModeQuality = settingsManager.hdModeQuality()
        val lowLatencyAudio = settingsManager.lowLatencyAudio()
        val enableRumble = settingsManager.enableRumble()
        val directLoad = settingsManager.allowDirectGameLoad()
        val enableImmersiveMode = settingsManager.enableImmersiveMode()

        val hasMicrophonePermission =
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        val enableMicrophone = systemCoreConfig.supportsMicrophone && hasMicrophonePermission

        val loadingStatesFlow =
            gameLoader.load(
                applicationContext,
                game,
                requestLoadSave && autoSaveEnabled,
                systemCoreConfig,
                directLoad,
            )

        loadingStatesFlow
            .flowOn(Dispatchers.IO)
            .catch {
                val message =
                    if (it is GameLoaderException) {
                        getErrorMessage(it.error)
                    } else {
                        ""
                    }
                sideEffects.requestFailureFinish(message)
            }
            .debounce(200)
            .collect { loadingState ->
                gameState.value =
                    if (loadingState is GameLoader.LoadingState.Ready) {
                        Timber.i("Setting state to loaded")
                        val retroViewData =
                            buildRetroViewData(
                                applicationContext,
                                systemCoreConfig,
                                loadingState.gameData,
                                hdMode,
                                hdModeQuality,
                                filter,
                                lowLatencyAudio,
                                enableRumble,
                                enableMicrophone,
                                enableImmersiveMode,
                            )
                        GameState.Loaded(
                            gameData = loadingState.gameData,
                            retroViewData = retroViewData,
                        )
                    } else {
                        GameState.Loading(getLoadingMessage(loadingState))
                    }
            }
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): Pair<GameLoader.GameData, GLRetroView> {
        val currentState = gameState.value
        if (currentState !is GameState.Loaded) throw IllegalStateException("Game is not loaded.")

        val result =
            GLRetroView(context, currentState.retroViewData)
                .apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }

        if (!system.hasTouchScreen) {
            result.disableTouchEvents()
        }

        lifecycle.lifecycle.addObserver(result)

        if (BuildConfig.DEBUG) {
            runCatching {
                printRetroVariables(result)
            }
        }

        val gameData = currentState.gameData
        
        scope.launch {
            val romUriString = gameData.game.fileUri
            var parseResult: LocationFileParser.ParseResult? = null
            var expectedCsvPath = ""
            
            // Try to load via SAF first (handles content:// URIs from folder picker)
            if (romUriString.startsWith("content://")) {
                expectedCsvPath = romUriString.replace(Regex("\\.[a-zA-Z0-9]+$"), "") + ".csv"
                if (expectedCsvPath == romUriString) {
                    expectedCsvPath = "$romUriString.csv"
                }
                
                try {
                    val csvUri = Uri.parse(expectedCsvPath)
                    withContext(Dispatchers.IO) {
                        appContext.contentResolver.openInputStream(csvUri)?.use { inputStream ->
                            parseResult = LocationFileParser.parse(inputStream)
                            Timber.i("FireGPS: Successfully read CSV from SAF URI: $expectedCsvPath")
                        }
                    }
                } catch (ignored: Exception) {
                    Timber.w("FireGPS: Could not open CSV via SAF URI, trying local file fallback.")
                }
            }

            if (parseResult == null) {
                // Re-identify ROM file
                val romFile = when (val gameFiles = gameData.gameFiles) {
                    is RomFiles.Standard -> gameFiles.files.firstOrNull()
                    is RomFiles.Virtual -> java.io.File(gameFiles.files.firstOrNull()?.filePath ?: "")
                    else -> null
                }

                if (romFile != null) {
                    val csvFile = java.io.File(romFile.parent, romFile.nameWithoutExtension + ".csv")
                    expectedCsvPath = csvFile.absolutePath
                    if (withContext(Dispatchers.IO) { csvFile.exists() }) {
                        try {
                            withContext(Dispatchers.IO) { 
                                parseResult = LocationFileParser.parse(csvFile.inputStream()) 
                            }
                            Timber.i("FireGPS: Successfully read CSV from local file: ${csvFile.name}")
                        } catch (e: Exception) {
                            Timber.e(e, "FireGPS: Error reading CSV file")
                        }
                    }
                }
            }

            // Handle Toast feedback for the user
            val resultObj = parseResult
            if (resultObj == null) {
                sideEffects.showToast("FireGPS: Location file not found at $expectedCsvPath")
                Timber.w("FireGPS: No location file found at $expectedCsvPath")
            } else {
                fireGPSLogic.setAreas(resultObj.areas)
                
                if (resultObj.malformedLineNumbers.isNotEmpty()) {
                    sideEffects.showToast("FireGPS: Found malformed line: ${resultObj.malformedLineNumbers.first()}")
                } else if (resultObj.areas.size != FireGPSLogic.MAX_AREAS) {
                    sideEffects.showToast("FireGPS: File has ${resultObj.areas.size}/${FireGPSLogic.MAX_AREAS} areas.")
                } else {
                    sideEffects.showToast("FireGPS: Successfully loaded ${FireGPSLogic.MAX_AREAS} areas.")
                }
            }
            
            delay(5.seconds)
            Timber.i("FireGPS: Starting injection of area names")
            fireGPSLogic.writeAreaNamesToRam(result)
            Timber.i("FireGPS: Names injected and kept active")
            
            // Sync initial status and location to RAM
            fireGPSLogic.writeStatusToRam(result)
            checkLastKnownLocation()
        }

        retroGameViewFlow.value = result
        gameState.value = GameState.Ready

        return currentState.gameData to result
    }

    fun triggerReinjection() {
        val view = retroGameView ?: return
        scope.launch {
            delay(1.seconds) // Wait a bit for the operation (reset/load) to settle
            Timber.i("FireGPS: Triggering re-injection sequence")
            fireGPSLogic.writeAreaNamesToRam(view)
            fireGPSLogic.writeAreaIdToRam(view, fireGPSLogic.getCurrentAreaId())
            fireGPSLogic.writeStatusToRam(view)
        }
    }

    suspend fun retroGameViewFlow() =
        retroGameViewFlow
            .filterNotNull()
            .first()

    suspend fun waitRetroGameViewInitialized() {
        retroGameViewFlow()
    }

    suspend inline fun <reified T> waitGLEvent() {
        val retroView = retroGameViewFlow()
        retroView.getGLRetroEvents()
            .filterIsInstance<T>()
            .first()
    }

    private fun buildRetroViewData(
        appContext: Context,
        systemCoreConfig: SystemCoreConfig,
        gameData: GameLoader.GameData,
        hdMode: Boolean,
        hdModeQuality: HDModeQuality,
        screenFilter: String,
        lowLatencyAudio: Boolean,
        requestRumble: Boolean,
        requestMicrophone: Boolean,
        enableImmersiveMode: Boolean,
    ): GLRetroViewData {
        return GLRetroViewData(appContext).apply {
            coreFilePath = gameData.coreLibrary

            when (val gameFiles = gameData.gameFiles) {
                is RomFiles.Standard -> {
                    gameFilePath = gameFiles.files.first().absolutePath
                }

                is RomFiles.Virtual -> {
                    gameVirtualFiles = gameFiles.files.map { VirtualFile(it.filePath, it.fd) }
                }
            }

            systemDirectory = gameData.systemDirectory.absolutePath
            savesDirectory = gameData.savesDirectory.absolutePath
            variables = gameData.coreVariables.map { Variable(it.key, it.value) }.toTypedArray()
            saveRAMState = gameData.saveRAMData
            shader =
                ShaderChooser.getShaderForSystem(
                    appContext,
                    hdMode,
                    hdModeQuality,
                    screenFilter,
                    GameSystem.findById(gameData.game.systemId),
                )
            preferLowLatencyAudio = lowLatencyAudio
            rumbleEventsEnabled = requestRumble
            skipDuplicateFrames = systemCoreConfig.skipDuplicateFrames
            enableMicrophone = requestMicrophone
            immersiveMode = buildImmersiveModeConfiguration(enableImmersiveMode)
        }
    }

    private fun buildImmersiveModeConfiguration(enableImmersiveMode: Boolean): ImmersiveMode? {
        return if (enableImmersiveMode) {
            ImmersiveMode(blendFactor = 0.05f)
        } else {
            null
        }
    }

    private fun getLoadingMessage(loadingState: GameLoader.LoadingState): String {
        return when (loadingState) {
            is GameLoader.LoadingState.LoadingCore -> {
                appContext.getString(com.swordfish.lemuroid.ext.R.string.game_loading_download_core)
            }

            is GameLoader.LoadingState.LoadingGame -> {
                appContext.getString(R.string.game_loading_preparing_game)
            }

            else -> ""
        }
    }

    private fun printRetroVariables(retroGameView: GLRetroView) {
        scope.launch {
            // Some cores do not immediately call SET_VARIABLES so we might need to wait a little bit
            delay(1.seconds)
            retroGameView.getVariables().forEach {
                Timber.i("Libretro variable: $it")
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.launchOnState(Lifecycle.State.STARTED) {
            initializeRetroGameViewErrorsFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeCoreVariablesFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeRumbleFlow()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startLocationUpdates()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // Immediate check on resume (phone unlock / app switch) with 1s delay
        scope.launch {
            delay(1.seconds)
            checkLastKnownLocation()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission", "VisibleForTests")
    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 20000 // 20 seconds
            fastestInterval = 10000 // 10 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            appContext.mainLooper
        )
        Timber.i("FireGPS: Started periodic location updates (20s)")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Timber.i("FireGPS: Stopped location updates")
    }

    @SuppressLint("MissingPermission")
    private fun checkLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateGpsStatus(FireGPSLogic.STATUS_PERMISSION_DENIED)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val view = retroGameView ?: return@addOnSuccessListener
                fireGPSLogic.updateLocation(it, view)
                Timber.i("FireGPS: Performed immediate resume location check")
            } ?: run {
                updateGpsStatus(FireGPSLogic.STATUS_SIGNAL_LOST)
                Timber.w("FireGPS: Last known location is null (Signal lost / Fix pending)")
            }
        }
    }

    private suspend fun initializeCoreVariablesFlow() {
        try {
            waitRetroGameViewInitialized()
            val options = coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig)
            updateCoreVariables(options)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private suspend fun initializeRumbleFlow() {
        val retroGameView = retroGameViewFlow()
        val rumbleEvents = retroGameView.getRumbleEvents()
        rumbleManager.collectAndProcessRumbleEvents(systemCoreConfig, rumbleEvents)
    }

    private suspend fun initializeRetroGameViewErrorsFlow() {
        retroGameViewFlow().getGLRetroErrors()
            .catch { Timber.e(it, "Exception in GLRetroErrors. Ironic.") }
            .collect { handleRetroViewError(it) }
    }

    private fun updateCoreVariables(options: List<CoreVariable>) {
        val updatedVariables =
            options.map { Variable(it.key, it.value) }
                .toTypedArray()

        updatedVariables.forEach {
            Timber.i("Updating core variable: ${it.key} ${it.value}")
        }

        retroGameView?.updateVariables(*updatedVariables)
    }

    private fun handleRetroViewError(errorCode: Int) {
        Timber.e("Error in GLRetroView $errorCode")
        val gameLoaderError =
            when (errorCode) {
                GLRetroView.ERROR_GL_NOT_COMPATIBLE -> GameLoaderError.GLIncompatible
                GLRetroView.ERROR_LOAD_GAME -> GameLoaderError.LoadGame
                GLRetroView.ERROR_LOAD_LIBRARY -> GameLoaderError.LoadCore
                GLRetroView.ERROR_SERIALIZATION -> GameLoaderError.Saves
                else -> GameLoaderError.Generic
            }

        sideEffects.requestFailureFinish(getErrorMessage(gameLoaderError))
    }

    private fun getErrorMessage(gameError: GameLoaderError): String {
        val message =
            when (gameError) {
                is GameLoaderError.GLIncompatible -> {
                    appContext.getString(R.string.game_loader_error_gl_incompatible)
                }
                is GameLoaderError.Generic -> {
                    appContext.getString(R.string.game_loader_error_generic)
                }
                is GameLoaderError.LoadCore -> {
                    appContext.getString(com.swordfish.lemuroid.ext.R.string.game_loader_error_load_core)
                }
                is GameLoaderError.LoadGame -> {
                    appContext.getString(R.string.game_loader_error_load_game)
                }
                is GameLoaderError.Saves -> {
                    appContext.getString(R.string.game_loader_error_save)
                }
                is GameLoaderError.UnsupportedArchitecture -> {
                    appContext.getString(R.string.game_loader_error_unsupported_architecture)
                }
                is GameLoaderError.MissingBiosFiles -> {
                    appContext.getString(R.string.game_loader_error_missing_bios, gameError.missingFiles)
                }
            }

        return message
    }
}
