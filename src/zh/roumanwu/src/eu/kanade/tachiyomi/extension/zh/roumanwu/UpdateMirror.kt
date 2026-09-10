package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

const val BASE_URL_PREF = "overrideBaseUrl"
const val DEFAULT_BASE_URL = "https://roum29.xyz"

private val DIZHI_URLS = listOf(
    "https://rou.pub/dizhi",
    "https://rdz3.xyz/dizhi",
)

var SharedPreferences.baseUrl: String
    get() = getString(BASE_URL_PREF, DEFAULT_BASE_URL)!!.trimEnd('/')
    set(value) = edit().putString(BASE_URL_PREF, value.trimEnd('/')).apply()

class UpdateMirror(
    private val preferences: SharedPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val baseHost = preferences.baseUrl.toHttpUrlOrNull()?.host
        if (baseHost == null || request.url.host != baseHost) {
            return chain.proceed(request)
        }

        val failedResult = try {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Result.success(response)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            Result.failure(e)
        }

        val newUrl = fetchLatestUrl(chain) ?: return failedResult.getOrThrow()
        if (newUrl == preferences.baseUrl) return failedResult.getOrThrow()

        preferences.baseUrl = newUrl
        val newHttpUrl = newUrl.toHttpUrlOrNull() ?: return failedResult.getOrThrow()
        val retryUrl = request.url.newBuilder()
            .scheme(newHttpUrl.scheme)
            .host(newHttpUrl.host)
            .port(newHttpUrl.port)
            .build()
        return chain.proceed(request.newBuilder().url(retryUrl).build())
    }

    private fun fetchLatestUrl(chain: Interceptor.Chain): String? {
        for (dizhiUrl in DIZHI_URLS) {
            val html = try {
                chain.proceed(GET(dizhiUrl, headers = chain.request().headers)).use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.string()
                }
            } catch (_: Throwable) {
                null
            } ?: continue

            parseRoumanwuUrl(html)?.let { return it }
        }
        return null
    }
}

internal fun parseRoumanwuUrl(html: String): String? {
    val doc = Jsoup.parse(html)
    for (section in doc.select("section.sec")) {
        val title = section.selectFirst("h2")?.text() ?: continue
        if (!title.contains("肉漫屋")) continue

        section.select("a[href]").forEach { anchor ->
            val href = anchor.attr("abs:href").trimEnd('/')
            if (ROUM_XYZ.matches(href)) return href
        }
        section.selectFirst("a[href]")?.attr("abs:href")?.trimEnd('/')?.let { return it }
    }
    return ROUM_XYZ.find(html)?.value?.trimEnd('/')
}

private val ROUM_XYZ = Regex("""https://roum\d+\.xyz""")
