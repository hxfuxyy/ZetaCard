package io.github.hxfuxyy.zetacard.core

data class BackupCard(val id: String, val name: String, val caption: Boolean)

object BackupManifest {
    const val MANIFEST_ENTRY = "zetacard-backup.tsv"
    const val ART_DIRECTORY = "art/"
    const val FORMAT_LINE = "zetacard-backup\t1"
    const val MAX_ENTRIES = 512
    const val MAX_ART_BYTES = 12L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 256L * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    fun format(cards: List<BackupCard>): String = buildString {
        append(FORMAT_LINE).append('\n')
        for (card in cards) {
            if (!CardIds.isValid(card.id)) continue
            append(card.id).append('\t')
                .append(CardLabels.forUserInput(card.name)).append('\t')
                .append(if (card.caption) '1' else '0').append('\n')
        }
    }

    fun parse(text: String): List<BackupCard> {
        val lines = text.split('\n')
        require(lines.firstOrNull()?.trim() == FORMAT_LINE) { "Not a ZetaCard backup" }
        val cards = LinkedHashMap<String, BackupCard>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            val id = parts[0].trim()
            if (!CardIds.isValid(id) || cards.containsKey(id)) continue
            val name = CardLabels.forUserInput(parts.getOrNull(1).orEmpty()).ifEmpty { CardLabels.DEFAULT }
            cards[id] = BackupCard(id, name, parts.getOrNull(2)?.trim() == "1")
        }
        return cards.values.toList()
    }

    fun artEntry(id: String): String = "$ART_DIRECTORY$id.png"

    fun artId(entryName: String): String? {
        if (!entryName.startsWith(ART_DIRECTORY) || !entryName.endsWith(".png")) return null
        val id = entryName.substring(ART_DIRECTORY.length, entryName.length - 4)
        return id.takeIf { CardIds.isValid(it) }
    }
}
