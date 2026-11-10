package io.github.hxfuxyy.zetacard.data

data class Preset(val id: String, val name: String) {
    val asset: String get() = "presets/$id.webp"
}

object Presets {
    val all = listOf(
        Preset("zeta_violet", "Zeta Violet"),
        Preset("zeta_midnight", "Zeta Midnight"),
        Preset("zeta_aurora", "Zeta Aurora"),
        Preset("zeta_ocean", "Zeta Ocean"),
        Preset("zeta_holo", "Zeta Holo"),
        Preset("zeta_pearl", "Zeta Pearl"),
    )

    fun find(id: String): Preset? = all.firstOrNull { it.id == id }
}
