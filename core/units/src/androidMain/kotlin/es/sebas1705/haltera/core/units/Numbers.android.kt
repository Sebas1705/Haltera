package es.sebas1705.haltera.core.units

import java.util.Locale

/** Compose's `Locale.current` on Android reads the same default locale (it follows the per-app locale). */
actual fun appLanguage(): String = Locale.getDefault().language
