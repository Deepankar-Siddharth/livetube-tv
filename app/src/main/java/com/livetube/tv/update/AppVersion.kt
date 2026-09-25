package com.livetube.tv.update

/** Small semantic-version value object used for deterministic update comparisons. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
) : Comparable<AppVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version components cannot be negative" }
    }

    override fun compareTo(other: AppVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        prerelease?.let { append('-').append(it) }
    }

    companion object {
        private val PATTERN = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$")

        fun parse(value: String): AppVersion? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            return runCatching {
                AppVersion(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toInt(),
                    patch = match.groupValues[3].toInt(),
                    prerelease = match.groupValues[4].ifEmpty { null },
                )
            }.getOrNull()
        }
    }
}
