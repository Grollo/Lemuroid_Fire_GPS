package com.swordfish.lemuroid.app.shared.firegps

import android.location.Location
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.delay
import timber.log.Timber

class FireGPSLogic {
    companion object {
        const val STATUS_INITIALIZING = 0
        const val STATUS_READY = 1
        const val STATUS_PERMISSION_DENIED = 2
        const val STATUS_SIGNAL_LOST = 3
        
        const val MAX_AREAS = 39
    }

    private var loadedAreas: List<GpsArea> = emptyList()
    private var currentAreaId: Int = 0
    private var currentStatus: Int = STATUS_INITIALIZING

    private val baseAddress = 0x0203F468
    private val statusLocation = baseAddress
    private val areaIdLocation = baseAddress + 0x02
    private val areaNamesLocation = baseAddress + 0x04
    private val areaNameMaxLength = 26

    fun setAreas(areas: List<GpsArea>) {
        this.loadedAreas = areas
        Timber.i("FireGPS: Loaded ${areas.size} areas")
    }

    fun setStatus(status: Int) {
        if (currentStatus != status) {
            currentStatus = status
            Timber.i("FireGPS: Status changed to $status")
        }
    }

    fun updateLocation(location: Location, view: GLRetroView): Int {
        var foundAreaId = 0 // Default to ID 0
        
        for (area in loadedAreas) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                area.latitude, area.longitude,
                results
            )
            
            if (results[0] <= area.radiusMeters) {
                foundAreaId = area.id
                break
            }
        }

        if (currentAreaId != foundAreaId) {
            currentAreaId = foundAreaId
            Timber.i("FireGPS: Area changed to $currentAreaId")
        }

        // Re-confirm READY status if we're getting location updates
        setStatus(STATUS_READY)

        // Always ensure the Area ID and current Status are written to RAM
        writeAreaIdToRam(view, currentAreaId)
        writeStatusToRam(view)
        
        return currentAreaId
    }
    
    fun getCurrentAreaId(): Int = currentAreaId

    fun writeAreaIdToRam(view: GLRetroView, areaId: Int) {
        val b1 = areaId and 0xFF
        val b2 = (areaId shr 8) and 0xFF
        val code = "%08X:%02X%02X".format(areaIdLocation, b2, b1)
        view.setCheat(1, true, code) // Index 1 for area
    }

    fun writeStatusToRam(view: GLRetroView) {
        val b1 = currentStatus and 0xFF
        val b2 = (currentStatus shr 8) and 0xFF
        val code = "%08X:%02X%02X".format(statusLocation, b2, b1)
        view.setCheat(0, true, code) // Using index 0 for Status
    }

    suspend fun writeAreaNamesToRam(view: GLRetroView) {
        var cheatIndex = 2 // index 2 and up for area names
        for (area in loadedAreas) {
            val bytes = PokemonEncoding.encode(area.displayName, areaNameMaxLength)
            var currentAddr = areaNamesLocation + (area.id * areaNameMaxLength)

            for (i in 0 until bytes.size step 2) {
                val b1 = bytes[i].toInt() and 0xFF
                val b2 = bytes[i + 1].toInt() and 0xFF
                val code = "%08X:%02X%02X".format(currentAddr, b2, b1)
                
                view.setCheat(cheatIndex++, true, code)
                currentAddr += 2

                delay(5)
            }
        }
        Timber.i("FireGPS: Throttled name injection complete.")
    }
}
