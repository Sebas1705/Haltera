package es.sebas1705.haltera.data.repository.search

import es.sebas1705.haltera.data.local.catalog.CatalogImporter
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.repository.inMemoryDatabase
import java.io.File

/** The bundled catalogue and foods as shipped in the app (composeApp/.../files). */
internal object BundledData {
    private val files = File("../../composeApp/src/commonMain/composeResources/files")
    val catalogJson: String by lazy { File(files, "catalog.json").readText() }
    val foodsJson: String by lazy { File(files, "foods.json").readText() }

    /** A fresh in-memory database with the real catalogue (and, optionally, the generic foods). */
    fun database(foods: Boolean = false): AppDatabase = inMemoryDatabase().also { db ->
        CatalogImporter(db, now = { 0L }).importIfNewer(catalogJson)
        if (foods) FoodImporter(db, now = { 0L }).importIfNewer(foodsJson)
    }
}
