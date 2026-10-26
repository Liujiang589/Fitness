package app.duolian.local.outdoor

import android.content.Context
import android.os.SystemClock
import app.duolian.local.BuildConfig
import app.duolian.local.domain.OutdoorPlace
import app.duolian.local.domain.RoutePoint
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption

/** GPS is sampled once by OutdoorService. This client adds Wi-Fi/cell positions for display only. */
internal class AmapLocationEngine(private val context: Context,
    private val position: (OutdoorPlace) -> Unit, private val failure: (String) -> Unit) {
    private var client: AMapLocationClient? = null
    @Volatile private var generation = 0
    fun start() {
        if (client != null || !BuildConfig.AMAP_CONFIGURED) return
        val token = ++generation
        try {
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTPS)
            AMapLocationClientOption.setOpenAlwaysScanWifi(false)
            val option = AMapLocationClientOption().apply {
                setLocationMode(AMapLocationClientOption.AMapLocationMode.Battery_Saving)
                setInterval(2000); setOnceLocation(false); setOnceLocationLatest(false)
                setNeedAddress(false); setMockEnable(false); setOffset(true)
                setLocationCacheEnable(false); setCacheCallBack(false); setHttpTimeOut(8000)
                setNoLocReqCgiEnable(false); setSelfStartServiceEnable(false)
            }
            val locator = AMapLocationClient(context.applicationContext)
            client = locator
            locator.apply {
                locator.setLocationOption(option)
                locator.setLocationListener { fix ->
                    if (token != generation || fix == null) return@setLocationListener
                    if (fix.errorCode != 0) {
                        failure(when (fix.errorCode) {
                            7 -> "高德定位授权未通过，已继续使用系统定位。"
                            12 -> "辅助定位缺少权限，已继续使用系统定位。"
                            else -> "网络辅助定位暂未成功，系统定位仍在继续。"
                        }); return@setLocationListener
                    }
                    if (fix.isMock) return@setLocationListener
                    val gcj02 = when (fix.coordType) {
                        AMapLocation.COORD_TYPE_GCJ02 -> true
                        AMapLocation.COORD_TYPE_WGS84 -> false
                        else -> return@setLocationListener
                    }
                    // A receipt timestamp must not turn an old cached fix into a fresh measurement.
                    val age = System.currentTimeMillis() - fix.time
                    if (age !in 0..OutdoorPlace.MAX_AGE_MS) return@setLocationListener
                    position(OutdoorPlace(RoutePoint(fix.latitude, fix.longitude, fix.time,
                        if (fix.hasAccuracy()) fix.accuracy else Float.POSITIVE_INFINITY),
                        SystemClock.elapsedRealtime() - age, gcj02,
                        fix.isFixLastLocation || fix.locationType in setOf(AMapLocation.LOCATION_TYPE_FIX_CACHE,
                            AMapLocation.LOCATION_TYPE_LAST_LOCATION_CACHE), "高德 Wi-Fi / 基站辅助"))
                }
                locator.startLocation()
            }
        } catch (_: Exception) { stop(); failure("辅助定位暂不可用，已继续使用系统定位。") }
        catch (_: LinkageError) { stop(); failure("此设备暂不支持辅助定位，已继续使用系统定位。") }
    }
    fun stop() {
        generation++
        client?.let { locator -> runCatching { locator.stopLocation() }; runCatching { locator.onDestroy() } }
        client = null
    }
}
