package com.livetube.tv.data

import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * JSON parsing and validation shared by the bundled cache, remote repository, and tests.
 *
 * Schema version 1 is accepted as a migration input. It is always normalized to the
 * current version 2 model before it reaches the rest of the application.
 */
object JsonUtils {
    const val SCHEMA_VERSION = 2
    const val LEGACY_SCHEMA_VERSION = 1

    private val ROOT_FIELDS = setOf(
        "schema_version",
        "data_version",
        "updated_at",
        "channels",
    )
    val REQUIRED_FIELDS = setOf(
        "id",
        "name",
        "category",
        "subcategory",
        "language",
        "region",
        "logo",
        "youtube_handle",
        "live_url",
        "enabled",
        "sort_order",
    )
    private val LEGACY_REQUIRED_FIELDS = setOf(
        "id",
        "name",
        "category",
        "logo",
        "youtube_handle",
        "live_url",
        "enabled",
        "sort_order",
    )
    val VALID_CATEGORIES = ChannelCatalog.categories
        .filter(CategoryDefinition::sourceBacked)
        .mapTo(linkedSetOf(), CategoryDefinition::name)

    private val LEGACY_CLASSIFICATION = mapOf(
        "English News" to Classification("News", "English & Global News", "English", "National"),
        "National Hindi News" to Classification("News", "Hindi News", "Hindi", "National"),
        "Business News" to Classification("News", "Business & Market", "Hindi", "National"),
        "Devotional & Spiritual" to Classification("Devotional", "Hindu Devotional", "Hindi", "National"),
        "Entertainment" to Classification("Music & Entertainment", "Youth & Entertainment", "Hindi", "National"),
        "Sports" to Classification("Sports & Live", "Sports News", "Hindi", "National"),
        "Regional News" to Classification("Regional", "Uttar Pradesh & Uttarakhand", "Hindi", "North India"),
        "Documentary" to Classification("Knowledge", "Documentaries", "English", "National"),
        "Music" to Classification("Music & Entertainment", "Bollywood & Retro", "Hindi", "National"),
        "Other" to Classification("News", "Hindi News", "Hindi", "National"),
    )

    private val ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    private val HANDLE_PATTERN = Regex("@[A-Za-z0-9._-]{1,100}")
    private val ISO_UTC_PATTERN = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d+)?Z$",
    )
    private val CONTROL_CHARACTER_PATTERN = Regex("[\\x00-\\x1f\\x7f]")

    fun parseDocument(raw: String): ChannelDocument {
        if (raw.startsWith('\uFEFF')) {
            throw InvalidChannelDocumentException("Channel JSON must not start with a UTF-8 BOM")
        }
        val root = try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw InvalidChannelDocumentException("Invalid channel JSON: ${error.message}", error)
        }
        if (!hasExactKeys(root, ROOT_FIELDS)) {
            throw InvalidChannelDocumentException(
                "Catalogue root must contain only schema_version, data_version, updated_at, and channels",
            )
        }
        val schemaVersion = requiredInt(root, "schema_version")
        if (schemaVersion !in setOf(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION)) {
            throw InvalidChannelDocumentException(
                "Unsupported schema_version $schemaVersion; expected 1 or $SCHEMA_VERSION",
            )
        }
        val dataVersion = requiredInt(root, "data_version")
        if (dataVersion < 1) {
            throw InvalidChannelDocumentException("data_version must be a positive integer")
        }
        val updatedAt = requiredString(root, "updated_at", 40)
        if (!isRealUtcTimestamp(updatedAt)) {
            throw InvalidChannelDocumentException("updated_at must be a real ISO-8601 UTC timestamp")
        }
        val channelsArray = root.optJSONArray("channels")
            ?: throw InvalidChannelDocumentException("channels must be an array")
        if (channelsArray.length() == 0) {
            throw InvalidChannelDocumentException("channels must contain at least one channel")
        }

        val channels = buildList {
            for (index in 0 until channelsArray.length()) {
                val value = channelsArray.opt(index)
                if (value !is JSONObject) {
                    throw InvalidChannelDocumentException("channels[$index] must be an object")
                }
                add(parseChannel(value, index, schemaVersion))
            }
        }
        validateUnique(channels)
        return ChannelDocument(
            schemaVersion = SCHEMA_VERSION,
            dataVersion = dataVersion,
            updatedAt = updatedAt,
            channels = channels.sortedBy(Channel::sortOrder),
        )
    }

    fun encodeDocument(document: ChannelDocument): String {
        validateDocument(document)
        return document.toJson().toString(2)
    }

    fun validateDocument(document: ChannelDocument) {
        if (document.schemaVersion != SCHEMA_VERSION) {
            throw InvalidChannelDocumentException("Only schema_version $SCHEMA_VERSION can be encoded")
        }
        if (document.dataVersion < 1) {
            throw InvalidChannelDocumentException("data_version must be positive")
        }
        if (!isRealUtcTimestamp(document.updatedAt)) {
            throw InvalidChannelDocumentException("updated_at must be a real ISO-8601 UTC timestamp")
        }
        if (document.channels.isEmpty()) {
            throw InvalidChannelDocumentException("At least one channel is required")
        }
        validateUnique(document.channels)
        document.channels.forEachIndexed { index, channel -> validateChannel(channel, index) }
    }

    fun parseChannel(
        json: JSONObject,
        index: Int = 0,
        schemaVersion: Int = SCHEMA_VERSION,
    ): Channel {
        require(schemaVersion == LEGACY_SCHEMA_VERSION || schemaVersion == SCHEMA_VERSION) {
            "Unsupported channel schema_version: $schemaVersion"
        }
        val expectedFields = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
            LEGACY_REQUIRED_FIELDS
        } else {
            REQUIRED_FIELDS
        }
        if (!hasExactKeys(json, expectedFields)) {
            throw InvalidChannelDocumentException("channels[$index] has missing or unknown fields")
        }

        val storedCategory = requiredString(json, "category", 64)
        val classification = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
            LEGACY_CLASSIFICATION[storedCategory]
                ?: throw InvalidChannelDocumentException(
                    "channels[$index].category is not a supported legacy category: $storedCategory",
                )
        } else {
            if (storedCategory !in VALID_CATEGORIES) {
                throw InvalidChannelDocumentException(
                    "channels[$index].category is not a supported category: $storedCategory",
                )
            }
            val subcategory = requiredString(json, "subcategory", 96)
            if (!ChannelCatalog.isValidClassification(storedCategory, subcategory)) {
                throw InvalidChannelDocumentException(
                    "channels[$index].subcategory does not belong to $storedCategory",
                )
            }
            Classification(
                category = storedCategory,
                subcategory = subcategory,
                language = requiredString(json, "language", 64),
                region = requiredString(json, "region", 64),
            )
        }

        val channel = Channel(
            id = requiredString(json, "id", 64),
            name = requiredString(json, "name", 160),
            category = classification.category,
            subcategory = classification.subcategory,
            language = classification.language,
            region = classification.region,
            logo = requiredString(json, "logo", 2048),
            youtubeHandle = requiredString(json, "youtube_handle", 101),
            liveUrl = requiredString(json, "live_url", 256),
            enabled = requiredBoolean(json, "enabled"),
            sortOrder = requiredInt(json, "sort_order"),
        )
        validateChannel(channel, index)
        return channel
    }

    fun validateChannel(channel: Channel, index: Int = 0) {
        validateText(channel.id, "channels[$index].id", 64)
        if (!ID_PATTERN.matches(channel.id)) {
            throw InvalidChannelDocumentException("channels[$index].id has an invalid format")
        }
        validateText(channel.name, "channels[$index].name", 160)
        validateText(channel.category, "channels[$index].category", 64)
        if (channel.category !in VALID_CATEGORIES) {
            throw InvalidChannelDocumentException(
                "channels[$index].category is not a supported category: ${channel.category}",
            )
        }
        validateText(channel.subcategory, "channels[$index].subcategory", 96)
        if (!ChannelCatalog.isValidClassification(channel.category, channel.subcategory)) {
            throw InvalidChannelDocumentException(
                "channels[$index].subcategory does not belong to ${channel.category}",
            )
        }
        validateText(channel.language, "channels[$index].language", 64)
        validateText(channel.region, "channels[$index].region", 64)
        validateText(channel.logo, "channels[$index].logo", 2048)
        if (!isHttpsUrl(channel.logo)) {
            throw InvalidChannelDocumentException("channels[$index].logo must be an HTTPS URL")
        }
        validateText(channel.youtubeHandle, "channels[$index].youtube_handle", 101)
        val normalizedHandle = normalizeHandle(channel.youtubeHandle)
        if (channel.youtubeHandle != normalizedHandle) {
            throw InvalidChannelDocumentException(
                "channels[$index].youtube_handle must be normalized as $normalizedHandle",
            )
        }
        validateText(channel.liveUrl, "channels[$index].live_url", 256)
        val expectedLiveUrl = canonicalLiveUrl(normalizedHandle)
        if (channel.liveUrl != expectedLiveUrl) {
            throw InvalidChannelDocumentException(
                "channels[$index].live_url must be the canonical URL $expectedLiveUrl",
            )
        }
        if (channel.sortOrder < 1) {
            throw InvalidChannelDocumentException("channels[$index].sort_order must be positive")
        }
    }

    fun normalizeHandle(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "YouTube handle is empty" }
        val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            val uri = try {
                URI(value)
            } catch (error: Exception) {
                throw IllegalArgumentException("The YouTube URL is invalid", error)
            }
            val host = uri.host?.lowercase(Locale.ROOT)
            require(
                uri.scheme.equals("https", true) &&
                    host in setOf("www.youtube.com", "youtube.com", "m.youtube.com") &&
                    uri.userInfo == null && uri.query == null && uri.fragment == null,
            ) {
                "Only an HTTPS YouTube handle URL is accepted"
            }
            val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
            require(
                segments.size in 1..2 &&
                    segments.first().startsWith("@") &&
                    (segments.size == 1 || segments[1] == "live")
            ) {
                "URL does not contain a YouTube handle live path"
            }
            segments.first()
        } else {
            value
        }
        val normalized = if (candidate.startsWith("@")) candidate else "@$candidate"
        require(HANDLE_PATTERN.matches(normalized)) {
            "Use a YouTube handle such as @channel"
        }
        return normalized
    }

    fun canonicalLiveUrl(handleOrUrl: String): String {
        val handle = normalizeHandle(handleOrUrl)
        return "https://www.youtube.com/$handle/live"
    }

    fun isCanonicalLiveUrl(value: String): Boolean = try {
        value == canonicalLiveUrl(value)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun validateUnique(channels: List<Channel>) {
        val duplicateIds = channels.groupingBy { it.id.lowercase(Locale.ROOT) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw InvalidChannelDocumentException(
                "duplicate channel ids: ${duplicateIds.sorted().joinToString()}",
            )
        }
        val duplicateHandles = channels.groupingBy { it.youtubeHandle.lowercase(Locale.ROOT) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateHandles.isNotEmpty()) {
            throw InvalidChannelDocumentException(
                "duplicate YouTube handles: ${duplicateHandles.sorted().joinToString()}",
            )
        }
        val duplicateLiveUrls = channels.groupingBy { it.liveUrl.lowercase(Locale.ROOT) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateLiveUrls.isNotEmpty()) {
            throw InvalidChannelDocumentException(
                "duplicate live URLs: ${duplicateLiveUrls.sorted().joinToString()}",
            )
        }
        val duplicateOrders = channels.groupingBy(Channel::sortOrder)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateOrders.isNotEmpty()) {
            throw InvalidChannelDocumentException(
                "duplicate sort_order values: ${duplicateOrders.sorted().joinToString()}",
            )
        }
    }

    private fun requiredString(json: JSONObject, name: String, maxLength: Int): String {
        if (!json.has(name) || json.isNull(name)) {
            throw InvalidChannelDocumentException("missing string field: $name")
        }
        val value = json.opt(name)
        if (value !is String) {
            throw InvalidChannelDocumentException("field $name must be a string")
        }
        validateText(value, "field $name", maxLength)
        return value
    }

    private fun requiredInt(json: JSONObject, name: String): Int {
        if (!json.has(name) || json.isNull(name)) {
            throw InvalidChannelDocumentException("missing integer field: $name")
        }
        val value = json.opt(name)
        val number = (value as? Number)?.toDouble()
        if (number == null || !number.isFinite() || number % 1.0 != 0.0 ||
            number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()
        ) {
            throw InvalidChannelDocumentException("field $name must be a 32-bit integer")
        }
        return number.toInt()
    }

    private fun requiredBoolean(json: JSONObject, name: String): Boolean {
        if (!json.has(name) || json.isNull(name)) {
            throw InvalidChannelDocumentException("missing boolean field: $name")
        }
        val value = json.opt(name)
        if (value !is Boolean) {
            throw InvalidChannelDocumentException("field $name must be a boolean")
        }
        return value
    }

    private fun validateText(value: String, field: String, maxLength: Int) {
        if (value.isBlank() || value != value.trim() || value.length > maxLength) {
            throw InvalidChannelDocumentException(
                "$field must be non-empty, trimmed, and at most $maxLength characters",
            )
        }
        if (CONTROL_CHARACTER_PATTERN.containsMatchIn(value)) {
            throw InvalidChannelDocumentException("$field must not contain control characters")
        }
    }

    private fun hasExactKeys(json: JSONObject, expected: Set<String>): Boolean {
        val actual = HashSet<String>(expected.size)
        val keys = json.keys()
        while (keys.hasNext()) actual += keys.next()
        return actual == expected
    }

    private fun isRealUtcTimestamp(value: String): Boolean {
        val match = ISO_UTC_PATTERN.matchEntire(value) ?: return false
        val year = match.groupValues[1].toIntOrNull() ?: return false
        val month = match.groupValues[2].toIntOrNull() ?: return false
        val day = match.groupValues[3].toIntOrNull() ?: return false
        val hour = match.groupValues[4].toIntOrNull() ?: return false
        val minute = match.groupValues[5].toIntOrNull() ?: return false
        val second = match.groupValues[6].toIntOrNull() ?: return false
        if (year !in 1..9999 || month !in 1..12 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return false
        }
        val leap = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
        val daysInMonth = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..daysInMonth
    }

    private fun isHttpsUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.port in -1..65_535 && uri.userInfo == null && uri.fragment == null
    } catch (_: Exception) {
        false
    }

    private data class Classification(
        val category: String,
        val subcategory: String,
        val language: String = "",
        val region: String = "",
    )
}

class InvalidChannelDocumentException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
