package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun setupUrlPreference(
    context: Context,
    screen: PreferenceScreen,
    preferences: SharedPreferences,
) {
    EditTextPreference(context).apply {
        key = BASE_URL_PREF
        title = "网址"
        dialogTitle = "网址"
        dialogMessage = "默认从 https://rou.pub/dizhi 自动获取最新地址；也可手动填写。"
        text = preferences.baseUrl
        summary = "${preferences.baseUrl}\n访问失败时会自动从官方地址页更新。"

        setOnPreferenceChangeListener { preference, newValue ->
            val text = (newValue as String).trim().trimEnd('/')
            val httpUrl = text.toHttpUrlOrNull()
            if (httpUrl == null) return@setOnPreferenceChangeListener false

            val sanitized = buildString {
                append(httpUrl.scheme)
                append("://")
                append(httpUrl.host)
                val defaultPort = if (httpUrl.scheme == "https") 443 else 80
                if (httpUrl.port != defaultPort) append(':').append(httpUrl.port)
            }

            preferences.baseUrl = sanitized
            (preference as EditTextPreference).text = sanitized
            preference.summary = "$sanitized\n访问失败时会自动从官方地址页更新。"
            true
        }
    }.also(screen::addPreference)
}
