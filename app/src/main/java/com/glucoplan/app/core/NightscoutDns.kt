package com.glucoplan.app.core

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS для Nightscout.
 *
 * У Jino делегирование nightscout-jino.ru часто не отвечает резолверам
 * Google / «Частный DNS» Android → UnknownHostException, хотя Chrome
 * открывает сайт (у него свой DoH и кэш).
 *
 * Порядок: системный DNS → Cloudflare DoH по IP 1.1.1.1 (без DNS).
 */
internal object NightscoutDns : Dns {

    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    private val bootstrap: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .dns(Dns.SYSTEM)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { return it }

        try {
            val sys = preferV4(Dns.SYSTEM.lookup(hostname))
            if (sys.isNotEmpty()) {
                cache[hostname] = sys
                return sys
            }
        } catch (e: UnknownHostException) {
            Timber.w(e, "Nightscout DNS: system lookup failed for $hostname")
        }

        val doh = lookupDoh(hostname)
        if (doh.isNotEmpty()) {
            cache[hostname] = doh
            Timber.i("Nightscout DNS: $hostname -> ${doh.map { it.hostAddress }} via DoH")
            return doh
        }

        throw UnknownHostException(hostname)
    }

    private fun preferV4(list: List<InetAddress>): List<InetAddress> {
        val v4 = list.filterIsInstance<Inet4Address>()
        return if (v4.isNotEmpty()) v4 + list.filter { it !is Inet4Address } else list
    }

    private fun lookupDoh(hostname: String): List<InetAddress> {
        val urls = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=A"
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
                bootstrap.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val ips = parseDohA(hostname, body)
                    if (ips.isNotEmpty()) return ips
                }
            } catch (e: Exception) {
                Timber.w(e, "Nightscout DoH failed: $url")
            }
        }
        return emptyList()
    }
}

internal fun parseDohA(hostname: String, json: String): List<InetAddress> {
    val root = JSONObject(json)
    if (root.optInt("Status", -1) != 0) return emptyList()
    val answers = root.optJSONArray("Answer") ?: return emptyList()
    val result = mutableListOf<InetAddress>()
    for (i in 0 until answers.length()) {
        val a = answers.getJSONObject(i)
        if (a.optInt("type") != 1) continue
        val parts = a.optString("data").split('.')
        if (parts.size != 4) continue
        val nums = parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
        if (nums.size != 4) continue
        val bytes = ByteArray(4) { nums[it].toByte() }
        result += InetAddress.getByAddress(hostname, bytes)
    }
    return result
}
