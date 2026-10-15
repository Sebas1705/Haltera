package es.sebas1705.haltera.core.ui.theme

import es.sebas1705.haltera.core.ui.resources.Res

/** A bundled font and the text of its licence (SIL Open Font License 1.1). */
data class FontLicence(val family: String, val text: String)

/** The licences of the fonts bundled in core:ui, for Settings → Credits. */
suspend fun readFontLicences(): List<FontLicence> = listOf(
    FontLicence("Barlow Semi Condensed", Res.readBytes("files/licenses/OFL-BarlowSemiCondensed.txt").decodeToString()),
    FontLicence("Inter", Res.readBytes("files/licenses/OFL-Inter.txt").decodeToString()),
)
