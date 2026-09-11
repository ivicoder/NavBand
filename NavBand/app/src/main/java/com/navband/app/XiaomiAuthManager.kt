package com.navband.app

import android.content.Context

object XiaomiAuthManager {

    private const val PREFS = "navband_xiaomi"
    private const val KEY_AUTH = "auth_key"

    fun saveAuthKey(
        context: Context,
        value: String
    ): Boolean {

        val normalized = value.trim()

        if (!isValidAuthKey(normalized)) {
            return false
        }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(KEY_AUTH, normalized.lowercase())
            .apply()

        return true
    }

    fun getAuthKey(
        context: Context
    ): ByteArray? {

        val value =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(KEY_AUTH, null)
                ?: return null

        if (!isValidAuthKey(value)) {
            return null
        }

        return hexToBytes(value)
    }

    fun hasAuthKey(
        context: Context
    ): Boolean {
        return getAuthKey(context) != null
    }

    private fun isValidAuthKey(
        value: String
    ): Boolean {

        if (value.length != 32) {
            return false
        }

        return value.all {
            it in "0123456789abcdefABCDEF"
        }
    }

    private fun hexToBytes(
        value: String
    ): ByteArray {

        val result = ByteArray(16)

        for (i in result.indices) {
            val index = i * 2

            result[i] =
                value
                    .substring(index, index + 2)
                    .toInt(16)
                    .toByte()
        }

        return result
    }
}
