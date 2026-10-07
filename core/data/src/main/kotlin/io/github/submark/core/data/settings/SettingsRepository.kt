package io.github.submark.core.data.settings

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Atomically replaces the settings with `transform(current)`. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData(transform)
    }
}

/** JSON file serializer. [defaultValue] is used when no file exists yet (first launch). */
class AppSettingsSerializer(
    override val defaultValue: AppSettings,
    private val json: Json = SettingsJson,
) : Serializer<AppSettings> {

    override suspend fun readFrom(input: InputStream): AppSettings {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(AppSettings.serializer(), text)
        } catch (e: SerializationException) {
            Log.w("AppSettingsSerializer", "unreadable settings, resetting", e)
            throw CorruptionException("unreadable settings", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("unreadable settings", e)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }

    companion object {
        val SettingsJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
