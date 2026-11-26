package com.chessoffline.core.model

/**
 * Cadences prédéfinies pour la pendule d'échecs intégrée.
 */
enum class TimeControl(
    val id: String,
    val labelFr: String,
    val categoryFr: String,
    val initialMillis: Long,
    val incrementMillis: Long
) {
    UNLIMITED("unlimited", "Sans chrono", "Libre", 0L, 0L),
    BULLET_1_0("bullet_1_0", "1 + 0", "Bullet", 60_000L, 0L),
    BLITZ_3_2("blitz_3_2", "3 + 2", "Blitz", 180_000L, 2_000L),
    BLITZ_5_0("blitz_5_0", "5 + 0", "Blitz", 300_000L, 0L),
    RAPID_10_0("rapid_10_0", "10 + 0", "Rapide", 600_000L, 0L),
    RAPID_15_10("rapid_15_10", "15 + 10", "Rapide", 900_000L, 10_000L);

    val hasClock: Boolean get() = initialMillis > 0L

    companion object {
        fun fromId(id: String?): TimeControl = entries.find { it.id == id } ?: BLITZ_5_0
    }
}
