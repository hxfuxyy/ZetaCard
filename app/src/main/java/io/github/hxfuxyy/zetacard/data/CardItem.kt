package io.github.hxfuxyy.zetacard.data

import java.io.File

data class CardItem(
    val id: String,
    val name: String,
    val stock: File,
    val custom: File,
    val hasCustom: Boolean,
    val stockRevision: Long,
    val customRevision: Long,
    val syncedRevision: Long = -1,
) {
    val syncState: SyncState
        get() = when {
            hasCustom && syncedRevision == customRevision -> SyncState.APPLIED
            hasCustom -> SyncState.PENDING_APPLY
            syncedRevision >= 0 -> SyncState.PENDING_RESTORE
            else -> SyncState.ORIGINAL
        }

    val displayName: CardName get() = CardName.parse(name)
}

enum class SyncState { ORIGINAL, APPLIED, PENDING_APPLY, PENDING_RESTORE }

data class CardName(val title: String, val suffix: String?) {
    companion object {
        private val MASKED_SUFFIX = Regex("""^(.+?)\s+([•●·*]{1,4}\s*\d{4})$""")

        fun parse(name: String): CardName {
            val match = MASKED_SUFFIX.matchEntire(name) ?: return CardName(name, null)
            return CardName(match.groupValues[1], match.groupValues[2])
        }
    }
}
