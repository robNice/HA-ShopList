package de.robnice.homeshoplist.data.backup

import android.content.Context
import android.net.Uri
import de.robnice.homeshoplist.data.SettingsDataStore
import de.robnice.homeshoplist.data.history.ProductHistoryEntity
import de.robnice.homeshoplist.data.history.ProductHistoryRepository
import de.robnice.homeshoplist.model.ShoppingArea
import de.robnice.homeshoplist.model.ShoppingMarkets
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupSection { CONNECTION, CATEGORIES, PRODUCT_HISTORY }

data class SettingsBackupPayload(
    val connection: BackupConnection?,
    val categories: BackupCategories?,
    val productHistory: List<ProductHistoryEntity>?
) {
    val sections: Set<BackupSection>
        get() = buildSet {
            if (connection != null) add(BackupSection.CONNECTION)
            if (categories != null) add(BackupSection.CATEGORIES)
            if (productHistory != null) add(BackupSection.PRODUCT_HISTORY)
        }
}

data class BackupConnection(val url: String, val token: String)
data class BackupMarket(val name: String, val order: List<String>, val enabled: List<String>)

/** All markets with their category settings; [activeIndex] points into [markets]. */
data class BackupCategories(
    val markets: List<BackupMarket>,
    val activeIndex: Int,
    /** Remembered store per list: list key -> index into [markets]. */
    val listLinks: Map<String, Int> = emptyMap()
) {
    val activeMarket: BackupMarket?
        get() = markets.getOrNull(activeIndex) ?: markets.firstOrNull()
}

class SettingsBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SettingsBackupService(context: Context) {
    private val appContext = context.applicationContext
    private val settings = SettingsDataStore(appContext)
    private val history = ProductHistoryRepository.getInstance(appContext)

    suspend fun export(uri: Uri, password: CharArray, sections: Set<BackupSection>) {
        require(sections.isNotEmpty())
        val snapshot = settings.readBackupSettings()
        val payload = SettingsBackupPayload(
            connection = if (BackupSection.CONNECTION in sections) {
                BackupConnection(snapshot.haUrl, snapshot.haToken)
            } else null,
            categories = if (BackupSection.CATEGORIES in sections) {
                BackupCategories(
                    markets = snapshot.markets.map { market ->
                        BackupMarket(
                            name = market.name,
                            order = market.areaOrder.map { it.key },
                            enabled = market.enabledAreas.map { it.key }
                        )
                    },
                    activeIndex = snapshot.markets
                        .indexOfFirst { it.id == snapshot.activeMarketId }
                        .coerceAtLeast(0),
                    listLinks = snapshot.listMarketLinks.mapNotNull { (listKey, marketId) ->
                        snapshot.markets.indexOfFirst { it.id == marketId }
                            .takeIf { it >= 0 }
                            ?.let { listKey to it }
                    }.toMap()
                )
            } else null,
            productHistory = if (BackupSection.PRODUCT_HISTORY in sections) {
                history.getHistorySnapshot()
            } else null
        )
        val encrypted = SettingsBackupCodec.encrypt(payload, password)
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
            ?: throw SettingsBackupException("Die Zieldatei konnte nicht geöffnet werden.")
    }

    fun decrypt(uri: Uri, password: CharArray): SettingsBackupPayload {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BACKUP_BYTES) throw SettingsBackupException("Die Sicherungsdatei ist zu groß.")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw SettingsBackupException("Die Sicherungsdatei konnte nicht geöffnet werden.")
        return SettingsBackupCodec.decrypt(bytes, password)
    }

    suspend fun import(payload: SettingsBackupPayload, sections: Set<BackupSection>) {
        val connection = payload.connection
            ?.takeIf { BackupSection.CONNECTION in sections }
            ?.let { it.url to it.token }
        val markets = payload.categories
            ?.takeIf { BackupSection.CATEGORIES in sections }
            ?.let(::validatedMarkets)
        val sanitizedHistory = payload.productHistory
            ?.takeIf { BackupSection.PRODUCT_HISTORY in sections }
            ?.let { imported ->
                if (imported.size > MAX_HISTORY_ITEMS) {
                    throw SettingsBackupException("Die Produkthistorie enthält zu viele Einträge.")
                }
                imported.mapNotNull { item ->
                    val name = item.displayName.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    ProductHistoryEntity(
                        normalizedName = ProductHistoryRepository.normalizeName(name),
                        displayName = name.take(MAX_PRODUCT_NAME_LENGTH),
                        areaKey = item.areaKey?.takeIf { ShoppingArea.fromKey(it) != null },
                        useCount = item.useCount.coerceAtLeast(1),
                        lastUsedAt = item.lastUsedAt.coerceAtLeast(0L)
                    )
                }
            }
        settings.importBackupSettings(connection, markets)
        sanitizedHistory?.let { history.mergeHistory(it) }
    }

    /** Imported markets replace all existing ones and get new ids. */
    private fun validatedMarkets(categories: BackupCategories): SettingsDataStore.ImportedMarkets {
        if (categories.markets.isEmpty()) {
            throw SettingsBackupException("Die Sicherung enthält keine Märkte.")
        }
        if (categories.markets.size > MAX_MARKETS) {
            throw SettingsBackupException("Die Sicherung enthält zu viele Märkte.")
        }
        val markets = categories.markets.map { market ->
            ShoppingMarkets.create(
                name = market.name,
                areaOrder = validatedAreaKeys(market.order, requireAtLeastOne = true),
                enabledAreas = validatedAreaKeys(market.enabled, requireAtLeastOne = true)
            )
        }
        val activeId = markets.getOrNull(categories.activeIndex)?.id
        val links = categories.listLinks.mapNotNull { (listKey, index) ->
            markets.getOrNull(index)?.let { listKey.take(MAX_LIST_KEY_LENGTH) to it.id }
        }.take(MAX_MARKETS * 10).toMap()
        return SettingsDataStore.ImportedMarkets(
            state = ShoppingMarkets.sanitize(markets, activeId),
            listMarketLinks = links
        )
    }

    private fun validatedAreaKeys(keys: List<String>, requireAtLeastOne: Boolean): List<ShoppingArea> {
        val result = keys.mapNotNull(ShoppingArea::fromKey).distinct()
        if (requireAtLeastOne && result.isEmpty()) {
            throw SettingsBackupException("Die Sicherung enthält keine gültigen Kategorien.")
        }
        return result
    }

    companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val FILE_EXTENSION = ".hassl-backup"
        private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
        private const val MAX_HISTORY_ITEMS = 100_000
        private const val MAX_MARKETS = 100
        private const val MAX_LIST_KEY_LENGTH = 255
        private const val MAX_PRODUCT_NAME_LENGTH = 500
    }
}

internal object SettingsBackupCodec {
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'S'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000
    private const val TAG_BITS = 128

    fun encrypt(payload: SettingsBackupPayload, password: CharArray): ByteArray {
        if (password.isEmpty()) throw SettingsBackupException("Das Passwort darf nicht leer sein.")
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_SIZE + NONCE_SIZE)
            .put(MAGIC).put(VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
        val key = deriveKey(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(toJson(payload).toByteArray(Charsets.UTF_8))
        } finally {
            key.encoded?.fill(0)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray): SettingsBackupPayload {
        val headerSize = MAGIC.size + 1 + 4 + SALT_SIZE + NONCE_SIZE
        if (file.size <= headerSize || password.isEmpty()) throw SettingsBackupException("Ungültige Sicherungsdatei oder falsches Passwort.")
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        val version = buffer.get()
        val iterations = buffer.int
        val salt = ByteArray(SALT_SIZE).also(buffer::get)
        val nonce = ByteArray(NONCE_SIZE).also(buffer::get)
        if (!magic.contentEquals(MAGIC) || version != VERSION || iterations !in 100_000..2_000_000) {
            throw SettingsBackupException("Nicht unterstützte Sicherungsdatei.")
        }
        val header = file.copyOfRange(0, headerSize)
        val key = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            fromJson(cipher.doFinal(file, headerSize, file.size - headerSize).toString(Charsets.UTF_8))
        } catch (e: AEADBadTagException) {
            throw SettingsBackupException("Ungültige Sicherungsdatei oder falsches Passwort.", e)
        } catch (e: SettingsBackupException) {
            throw e
        } catch (e: Exception) {
            throw SettingsBackupException("Die Sicherungsdatei ist beschädigt oder nicht unterstützt.", e)
        } finally {
            key.encoded?.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    internal fun toJson(payload: SettingsBackupPayload): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("createdAt", System.currentTimeMillis())
        payload.connection?.let { put("connection", JSONObject().put("url", it.url).put("token", it.token)) }
        payload.categories?.let { categories ->
            val markets = JSONArray().apply {
                categories.markets.forEach { market ->
                    put(JSONObject().put("name", market.name)
                        .put("order", JSONArray(market.order)).put("enabled", JSONArray(market.enabled)))
                }
            }
            // "order"/"enabled" of the active market stay for app versions without markets
            val active = categories.activeMarket
            put("categories", JSONObject()
                .put("order", JSONArray(active?.order.orEmpty()))
                .put("enabled", JSONArray(active?.enabled.orEmpty()))
                .put("markets", markets)
                .put("activeMarket", categories.activeIndex)
                .put("listLinks", JSONObject().apply {
                    categories.listLinks.forEach { (listKey, index) -> put(listKey, index) }
                }))
        }
        payload.productHistory?.let { items ->
            put("productHistory", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().put("normalizedName", item.normalizedName).put("displayName", item.displayName)
                        .put("areaKey", item.areaKey ?: JSONObject.NULL).put("useCount", item.useCount).put("lastUsedAt", item.lastUsedAt))
                }
            })
        }
    }.toString()

    internal fun fromJson(json: String): SettingsBackupPayload {
        val root = JSONObject(json)
        if (root.optInt("schemaVersion", -1) != 1) throw SettingsBackupException("Nicht unterstützte Backup-Version.")
        val connection = root.optJSONObject("connection")?.let { BackupConnection(it.getString("url"), it.getString("token")) }
        val categories = root.optJSONObject("categories")?.let(::categoriesFromJson)
        val history = root.optJSONArray("productHistory")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ProductHistoryEntity(item.getString("normalizedName"), item.getString("displayName"),
                    item.optString("areaKey").takeIf { it.isNotEmpty() && it != "null" }, item.getInt("useCount"), item.getLong("lastUsedAt"))
            }
        }
        val payload = SettingsBackupPayload(connection, categories, history)
        if (payload.sections.isEmpty()) throw SettingsBackupException("Die Sicherung enthält keine unterstützten Daten.")
        return payload
    }

    /** Backups without "markets" (before markets existed) become the single market "Default". */
    private fun categoriesFromJson(json: JSONObject): BackupCategories {
        val markets = json.optJSONArray("markets")
            ?: return BackupCategories(
                markets = listOf(
                    BackupMarket(
                        name = ShoppingMarkets.DEFAULT_MARKET_NAME,
                        order = json.getJSONArray("order").strings(),
                        enabled = json.getJSONArray("enabled").strings()
                    )
                ),
                activeIndex = 0
            )
        val parsed = List(markets.length()) { index ->
            val market = markets.getJSONObject(index)
            BackupMarket(
                name = market.getString("name"),
                order = market.getJSONArray("order").strings(),
                enabled = market.getJSONArray("enabled").strings()
            )
        }
        val activeIndex = json.optInt("activeMarket", 0).takeIf { it in parsed.indices } ?: 0
        val listLinks = json.optJSONObject("listLinks")?.let { links ->
            links.keys().asSequence()
                .mapNotNull { key -> links.optInt(key, -1).takeIf { it in parsed.indices }?.let { key to it } }
                .toMap()
        }.orEmpty()
        return BackupCategories(
            markets = parsed,
            activeIndex = activeIndex,
            listLinks = listLinks
        )
    }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}
