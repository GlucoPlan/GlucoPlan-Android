package com.glucoplan.app.core

import com.glucoplan.app.domain.model.AppSettings
import com.glucoplan.app.domain.model.CgmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Result wrapper for Nightscout API calls
 */
sealed class NsResult<out T> {
    data class Success<T>(val data: T) : NsResult<T>()
    data class Error(val code: Int? = null, val message: String, val exception: Exception? = null) : NsResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): NsResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
}

/**
 * Nightscout treatment data
 */
data class NsTreatment(
    val id: String?,
    val eventType: String,
    val createdAt: Instant,
    val glucose: Double?,
    val glucoseType: String?,
    val carbs: Double?,
    val insulin: Double?,
    val notes: String?
)

/**
 * Nightscout profile data
 */
data class NsProfile(
    val sens: Double,           // ISF (mg/dL per U)
    val carbratio: Double,      // I:C (g per U)
    val targetLow: Double,      // Target low (mg/dL)
    val targetHigh: Double,     // Target high (mg/dL)
    val basal: List<NsBasalEntry>
)

data class NsBasalEntry(
    val start: String,  // "00:00", "02:00", etc.
    val minutes: Int,
    val rate: Double
)

/**
 * Nightscout API client with comprehensive logging and SSL support.
 *
 * Баг 6 исправлен: небезопасный X509TrustManager, принимавший любой сертификат,
 * заменён на стандартную валидацию. Параметр [allowSelfSigned] можно явно
 * передать true только для локальных/домашних серверов с самоподписанным
 * сертификатом — в этом случае пользователь осознаёт риск.
 */
class NightscoutClient(
    private val baseUrl: String,
    private val apiSecret: String,
    private val allowSelfSigned: Boolean = false
) {
    private val tag = "Nightscout"

    // Trust-all implementation — ONLY used when allowSelfSigned == true
    private val unsafeTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(LoggingInterceptor())

        if (allowSelfSigned) {
            // Self-signed / home server: bypass certificate check explicitly
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(unsafeTrustManager), SecureRandom())
                builder
                    .sslSocketFactory(sslContext.socketFactory, unsafeTrustManager)
                    .hostnameVerifier { _, _ -> true }
                Timber.w("$tag: SSL certificate validation disabled (allowSelfSigned=true)")
            } catch (e: Exception) {
                Timber.e(e, "$tag: Failed to configure self-signed SSL")
            }
        }
        // Default path: system trust store validates the certificate chain
        builder.build()
    }

    private val hashedSecret: String by lazy {
        val bytes = apiSecret.toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Connection Check
    // -------------------------------------------------------------------------

    suspend fun checkConnection(): NsResult<Unit> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/status.json"
        Timber.d("$tag: Checking connection to $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string()

            Timber.d("$tag: Status response code=${response.code}")

            when {
                response.isSuccessful -> {
                    Timber.i("$tag: Connection successful")
                    NsResult.Success(Unit)
                }
                response.code == 401 -> {
                    Timber.w("$tag: Authentication failed - check API secret")
                    NsResult.Error(401, "Authentication failed - check API secret")
                }
                response.code == 404 -> {
                    Timber.w("$tag: Nightscout not found at this URL")
                    NsResult.Error(404, "Nightscout not found at this URL")
                }
                else -> {
                    Timber.w("$tag: Connection failed with code ${response.code}: $body")
                    NsResult.Error(response.code, "HTTP ${response.code}: ${body?.take(200)}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$tag: Connection exception")
            NsResult.Error(null, "Connection failed: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // CGM Entries
    // -------------------------------------------------------------------------

    suspend fun getLatestReading(): NsResult<CgmReading> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/entries/sgv.json?count=5"
        Timber.d("$tag: Fetching latest CGM reading from $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                val body = response.body?.string()
                Timber.w("$tag: Failed to get CGM data: ${response.code}")
                return NsResult.Error(response.code, "HTTP ${response.code}: ${body?.take(200)}")
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Timber.w("$tag: Empty response body")
                return NsResult.Error(null, "Empty response from Nightscout")
            }

            val entries = JSONArray(body)
            if (entries.length() == 0) {
                Timber.w("$tag: No CGM entries found")
                return NsResult.Error(null, "No CGM data available")
            }

            val latest = entries.getJSONObject(0)
            val glucoseMgdl = latest.optDouble("sgv", -1.0)
            if (glucoseMgdl < 0) {
                Timber.w("$tag: Invalid glucose value in response")
                return NsResult.Error(null, "Invalid glucose value in response")
            }

            val glucose = glucoseMgdl / 18.0  // Convert to mmol/L
            val direction = latest.optString("direction", "Flat")
            val dateMs = latest.optLong("date", 0L)
            val time = if (dateMs > 0) Instant.ofEpochMilli(dateMs) else Instant.now()

            // 20-min forecast from 2 readings
            var forecast20: Double? = null
            if (entries.length() >= 2) {
                try {
                    val prev = entries.getJSONObject(1)
                    val prevGlucose = prev.optDouble("sgv", -1.0) / 18.0
                    val prevMs = prev.optLong("date", 0L)
                    if (prevMs > 0 && dateMs > prevMs && prevGlucose > 0) {
                        val diffMin = (dateMs - prevMs) / 60000.0
                        if (diffMin > 0) {
                            val ratePerMin = (glucose - prevGlucose) / diffMin
                            forecast20 = glucose + ratePerMin * 20.0
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$tag: Failed to calculate forecast")
                }
            }

            val reading = CgmReading(
                glucose = glucose,
                direction = direction,
                time = time,
                forecast20min = forecast20
            )

            Timber.i("$tag: CGM reading: ${"%.1f".format(glucose)} mmol/L, direction=$direction")
            NsResult.Success(reading)

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception fetching CGM data")
            NsResult.Error(null, "Failed to get CGM: ${e.message}", e)
        }
    }

    suspend fun getEntries(since: Instant, count: Int = 100): NsResult<List<CgmReading>> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/entries/sgv.json?count=$count&find[date][\$gte]=${since.toEpochMilli()}"
        Timber.d("$tag: Fetching CGM entries since $since")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                return NsResult.Error(response.code, "HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return NsResult.Error(null, "Empty response")
            val entries = JSONArray(body)
            val readings = mutableListOf<CgmReading>()

            for (i in 0 until entries.length()) {
                try {
                    val entry = entries.getJSONObject(i)
                    val glucoseMgdl = entry.optDouble("sgv", -1.0)
                    if (glucoseMgdl > 0) {
                        val dateMs = entry.optLong("date", 0L)
                        readings.add(CgmReading(
                            glucose = glucoseMgdl / 18.0,
                            direction = entry.optString("direction", "Flat"),
                            time = if (dateMs > 0) Instant.ofEpochMilli(dateMs) else Instant.now(),
                            forecast20min = null
                        ))
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$tag: Failed to parse entry $i")
                }
            }

            Timber.i("$tag: Retrieved ${readings.size} CGM entries")
            NsResult.Success(readings)

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception fetching CGM entries")
            NsResult.Error(null, "Failed to get entries: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Treatments
    // -------------------------------------------------------------------------

    suspend fun getTreatments(since: Instant): NsResult<List<NsTreatment>> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/treatments.json?find[created_at][\$gte]=${DateTimeFormatter.ISO_INSTANT.format(since)}&count=100"
        Timber.d("$tag: Fetching treatments since $since")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                return NsResult.Error(response.code, "HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return NsResult.Error(null, "Empty response")
            val treatments = JSONArray(body)
            val result = mutableListOf<NsTreatment>()

            for (i in 0 until treatments.length()) {
                try {
                    val t = treatments.getJSONObject(i)
                    val createdAtStr = t.optString("created_at", null)
                    val createdAt = if (createdAtStr != null) {
                        Instant.parse(createdAtStr)
                    } else {
                        Instant.now()
                    }

                    result.add(NsTreatment(
                        id = t.optString("_id", null),
                        eventType = t.optString("eventType", "Unknown"),
                        createdAt = createdAt,
                        glucose = t.optDouble("glucose", -1.0).let { if (it > 0) it / 18.0 else null },
                        glucoseType = t.optString("glucoseType", null),
                        carbs = t.optDouble("carbs", -1.0).let { if (it > 0) it else null },
                        insulin = t.optDouble("insulin", -1.0).let { if (it > 0) it else null },
                        notes = t.optString("notes", null)
                    ))
                } catch (e: Exception) {
                    Timber.w(e, "$tag: Failed to parse treatment $i")
                }
            }

            Timber.i("$tag: Retrieved ${result.size} treatments")
            NsResult.Success(result)

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception fetching treatments")
            NsResult.Error(null, "Failed to get treatments: ${e.message}", e)
        }
    }

    suspend fun postTreatment(
        carbs: Double,
        insulin: Double,
        glucose: Double,
        notes: String
    ): NsResult<Unit> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/treatments"
        Timber.d("$tag: Posting treatment: carbs=$carbs, insulin=$insulin, glucose=$glucose")

        return try {
            val json = JSONObject().apply {
                put("eventType", "Meal Bolus")
                put("created_at", Instant.now().toString())
                if (carbs > 0) put("carbs", "%.1f".format(carbs))
                if (insulin > 0) put("insulin", "%.2f".format(insulin))
                if (glucose > 0) {
                    put("glucose", "%.0f".format(glucose * 18.0))
                    put("glucoseType", "Manual")
                    put("units", "mg/dl")
                }
                if (notes.isNotBlank()) put("notes", notes)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (response.isSuccessful) {
                Timber.i("$tag: Treatment posted successfully")
                NsResult.Success(Unit)
            } else {
                val errorBody = response.body?.string()
                Timber.w("$tag: Failed to post treatment: ${response.code}")
                NsResult.Error(response.code, "HTTP ${response.code}: ${errorBody?.take(200)}")
            }

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception posting treatment")
            NsResult.Error(null, "Failed to post treatment: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Profile
    // -------------------------------------------------------------------------

    suspend fun getProfile(): NsResult<NsProfile?> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/profile.json"
        Timber.d("$tag: Fetching profile from $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("api-secret", hashedSecret)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                return NsResult.Error(response.code, "HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return NsResult.Error(null, "Empty response")
            val profiles = JSONArray(body)

            if (profiles.length() == 0) {
                Timber.w("$tag: No profiles found")
                return NsResult.Success(null)
            }

            // Get active profile
            val profileObj = profiles.getJSONObject(0)
            val store = profileObj.optJSONObject("store")
            if (store == null) {
                Timber.w("$tag: No profile store found")
                return NsResult.Success(null)
            }

            // Get the first/default profile
            val profileName = store.keys().next()
            val profileData = store.getJSONObject(profileName)

            // Get basal schedule
            val basalArray = profileData.optJSONArray("basal") ?: JSONArray()
            val basal = mutableListOf<NsBasalEntry>()
            for (i in 0 until basalArray.length()) {
                val b = basalArray.getJSONObject(i)
                basal.add(NsBasalEntry(
                    start = b.optString("start", "00:00"),
                    minutes = b.optInt("minutes", 0),
                    rate = b.optDouble("rate", 0.0)
                ))
            }

            // Get ISF (sens), I:C (carbratio), targets
            val sensMgDl = profileData.optDouble("sens", 0.0)
            val carbratio = profileData.optDouble("carbratio", 0.0)

            val targetLowObj = profileData.optJSONObject("target_low")
            val targetHighObj = profileData.optJSONObject("target_high")

            val targetLowMgDl = targetLowObj?.optDouble("value", 70.0) ?: 70.0
            val targetHighMgDl = targetHighObj?.optDouble("value", 180.0) ?: 180.0

            val profile = NsProfile(
                sens = sensMgDl,  // mg/dL per unit
                carbratio = carbratio,  // g per unit
                targetLow = targetLowMgDl,
                targetHigh = targetHighMgDl,
                basal = basal
            )

            Timber.i("$tag: Profile loaded: ISF=$sensMgDl mg/dL/U, I:C=$carbratio g/U")
            NsResult.Success(profile)

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception fetching profile")
            NsResult.Error(null, "Failed to get profile: ${e.message}", e)
        }
    }


    // -------------------------------------------------------------------------
    // Profile Sync (AppSettings ↔ Nightscout)
    // -------------------------------------------------------------------------

    /**
     * Загружает профиль из Nightscout и конвертирует в AppSettings.
     * Стандартные поля — из profile.store.Default (или первого профиля),
     * кастомные поля GlucoPlan — из profile.store.GlucoPlan.
     *
     * Конвертация единиц: NS хранит мг/дл, мы используем ммоль/л (÷ 18).
     */
    suspend fun loadSettingsFromProfile(current: AppSettings): NsResult<AppSettings> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/profile.json"
        Timber.d("$tag: Loading settings from profile")

        return try {
            val request = Request.Builder()
                .url(url).header("api-secret", hashedSecret).get().build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful)
                return NsResult.Error(response.code, "HTTP ${response.code}")

            val body = response.body?.string()
                ?: return NsResult.Error(null, "Пустой ответ от сервера")

            val profiles = JSONArray(body)
            if (profiles.length() == 0)
                return NsResult.Error(null, "Профиль не найден в Nightscout")

            val store = profiles.getJSONObject(0).optJSONObject("store")
                ?: return NsResult.Error(null, "Нет секции store в профиле")

            val profileName = store.keys().next()
            val profileData = store.getJSONObject(profileName)
            val gpBlock = store.optJSONObject("GlucoPlan")

            // ISF: мг/дл → ммоль/л
            val sensMgDl = profileData.optDouble("sens", -1.0)
            val sensitivity = if (sensMgDl > 0) sensMgDl / 18.0 else current.sensitivity


            // Целевые значения
            val targetLowMgDl = profileData.optJSONArray("target_low")
                ?.optJSONObject(0)?.optDouble("value", -1.0) ?: -1.0
            val targetHighMgDl = profileData.optJSONArray("target_high")
                ?.optJSONObject(0)?.optDouble("value", -1.0) ?: -1.0
            val targetMin = if (targetLowMgDl > 0) targetLowMgDl / 18.0 else current.targetGlucoseMin
            val targetMax = if (targetHighMgDl > 0) targetHighMgDl / 18.0 else current.targetGlucoseMax
            val targetMid = if (targetMin > 0 && targetMax > 0) (targetMin + targetMax) / 2.0 else current.targetGlucose

            // Базальный
            val basalRate = profileData.optJSONArray("basal")
                ?.optJSONObject(0)?.optDouble("rate", -1.0) ?: -1.0

            // Кастомные поля GlucoPlan
            val carbsPerXe = gpBlock?.optDouble("carbsPerXe", -1.0)?.takeIf { it > 0 } ?: current.carbsPerXe
            val carbCoeff  = gpBlock?.optDouble("carbCoefficient", -1.0)?.takeIf { it > 0 } ?: current.carbCoefficient
            val insulinType = gpBlock?.optString("insulinType", "")?.takeIf { it.isNotBlank() } ?: current.insulinType
            val basalType   = gpBlock?.optString("basalType", "")?.takeIf { it.isNotBlank() } ?: current.basalType
            val basalTime   = gpBlock?.optString("basalTime", "")?.takeIf { it.isNotBlank() } ?: current.basalTime

            val result = current.copy(
                sensitivity      = sensitivity,
                carbsPerXe       = carbsPerXe,
                carbCoefficient  = carbCoeff,
                targetGlucoseMin = targetMin,
                targetGlucose    = targetMid,
                targetGlucoseMax = targetMax,
                basalDose        = if (basalRate > 0) basalRate else current.basalDose,
                insulinType      = insulinType,
                basalType        = basalType,
                basalTime        = basalTime
            )
            Timber.i("$tag: Settings loaded: ISF=$sensitivity, carbsPerXe=$carbsPerXe, carbCoeff=$carbCoeff")
            NsResult.Success(result)

        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception loading settings from profile")
            NsResult.Error(null, "Ошибка чтения профиля: ${e.message}", e)
        }
    }

    /**
     * Сохраняет AppSettings в Nightscout как профиль.
     * Стандартные поля — в profile.store.Default,
     * кастомные — в profile.store.GlucoPlan.
     *
     * Конвертация единиц: ммоль/л → мг/дл (× 18).
     */
    suspend fun saveSettingsToProfile(settings: AppSettings): NsResult<Unit> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/profile"
        Timber.d("$tag: Saving settings to NS profile")

        return try {
            val sensMgDl   = settings.sensitivity * 18.0
            val targetLow  = settings.targetGlucoseMin * 18.0
            val targetHigh = settings.targetGlucoseMax * 18.0
            // carbratio = carbsPerXe / carbCoefficient (г углеводов на 1 ед)
            val carbratio  = if (settings.carbCoefficient > 0)
                settings.carbsPerXe / settings.carbCoefficient else settings.carbsPerXe

            val defaultProfile = JSONObject().apply {
                put("sens",      sensMgDl)
                put("carbratio", carbratio)
                put("units",     "mmol")
                put("target_low", JSONArray().put(
                    JSONObject().put("time", "00:00").put("value", targetLow).put("timeAsSeconds", 0)
                ))
                put("target_high", JSONArray().put(
                    JSONObject().put("time", "00:00").put("value", targetHigh).put("timeAsSeconds", 0)
                ))
                put("basal", JSONArray().put(
                    JSONObject()
                        .put("time", settings.basalTime)
                        .put("value", settings.basalDose)
                        .put("timeAsSeconds", timeToSeconds(settings.basalTime))
                ))
            }

            val gpProfile = JSONObject().apply {
                put("carbsPerXe",      settings.carbsPerXe)
                put("carbCoefficient", settings.carbCoefficient)
                put("insulinType",     settings.insulinType)
                put("basalType",       settings.basalType)
                put("basalTime",       settings.basalTime)
                put("insulinStep",     settings.insulinStep)
            }

            val payload = JSONObject().apply {
                put("defaultProfile", "Default")
                put("store", JSONObject().apply {
                    put("Default",   defaultProfile)
                    put("GlucoPlan", gpProfile)
                })
                put("startDate", java.time.Instant.now().toString())
                put("units", "mmol")
            }

            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url).header("api-secret", hashedSecret)
                .header("Content-Type", "application/json")
                .post(body).build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (response.isSuccessful) {
                Timber.i("$tag: Settings saved to NS profile")
                NsResult.Success(Unit)
            } else {
                NsResult.Error(response.code, "HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            }
        } catch (e: Exception) {
            Timber.e(e, "$tag: Exception saving settings to profile")
            NsResult.Error(null, "Ошибка записи профиля: ${e.message}", e)
        }
    }

    private fun timeToSeconds(time: String): Int {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 3600 + m * 60
    }

    // -------------------------------------------------------------------------
    // Logging Interceptor
    // -------------------------------------------------------------------------

    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val t1 = System.nanoTime()

            Timber.v("NS HTTP: ${request.method} ${request.url}")

            return try {
                val response = chain.proceed(request)
                val t2 = System.nanoTime()
                val duration = (t2 - t1) / 1e6

                Timber.v("NS HTTP: ${response.code} ${request.url} (${ "%.1f".format(duration) }ms)")

                response
            } catch (e: IOException) {
                Timber.e(e, "NS HTTP: Failed ${request.method} ${request.url}")
                throw e
            }
        }
    }
}
