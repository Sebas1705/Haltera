package es.sebas1705.ironlog.core.datastore

import com.russhwolf.settings.Settings

/**
 * The load stepper increment the user chose per exercise variant. Device-local on purpose: it is a
 * convenience of this phone's load entry, so it never goes through [SettingsRepository] or sync.
 *
 * Weight steps are grams; machine stack ("plate #") steps are a number of plates, kept apart because the
 * same machine can be logged both ways. Null means "use the default for the load type".
 */
interface LoadStepStore {
    fun step(variantId: String, plates: Boolean = false): Long?

    /** Remembers [step] for [variantId]; null or a non-positive value goes back to the default. */
    fun setStep(variantId: String, plates: Boolean = false, step: Long?)

    /** Forgets every remembered step ("Also erase data on this device"). */
    fun clear() = Unit
}

/** [LoadStepStore] on multiplatform-settings under `loadStep.<variantId>` (and `loadStep.<variantId>.plates`). */
class SettingsLoadStepStore(private val store: Settings) : LoadStepStore {
    override fun step(variantId: String, plates: Boolean): Long? =
        store.getLongOrNull(key(variantId, plates))?.takeIf { it > 0 }

    override fun setStep(variantId: String, plates: Boolean, step: Long?) {
        val key = key(variantId, plates)
        if (step == null || step <= 0) store.remove(key) else store.putLong(key, step)
    }

    override fun clear() {
        store.keys.filter { it.startsWith(PREFIX) }.forEach(store::remove)
    }

    private fun key(variantId: String, plates: Boolean) = "$PREFIX$variantId${if (plates) PLATES_SUFFIX else ""}"

    companion object {
        const val PREFIX = "loadStep."
        private const val PLATES_SUFFIX = ".plates"
    }
}
