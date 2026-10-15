package es.sebas1705.haltera.core.units

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** Same source as Compose's `Locale.current` on iOS: the first preferred language ("es-ES" -> "es"). */
actual fun appLanguage(): String {
    val tag = NSLocale.preferredLanguages.firstOrNull() as? String ?: return "en"
    return tag.substringBefore('-').substringBefore('_')
}
