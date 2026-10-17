package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.repository.importer.HistoryFileParser
import es.sebas1705.haltera.data.repository.importer.ImportError
import es.sebas1705.haltera.data.repository.importer.ImportFormatException
import es.sebas1705.haltera.data.repository.importer.ImportSource
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.ImportedSession
import es.sebas1705.haltera.data.repository.importer.ParseOptions
import es.sebas1705.haltera.data.repository.importer.ParsedImport
import kotlinx.datetime.TimeZone
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun fixtureBytes(name: String): ByteArray =
    checkNotNull(HistoryFileParserTest::class.java.getResourceAsStream("/import/$name")) { "missing fixture $name" }
        .use { it.readBytes() }

@OptIn(ExperimentalTime::class)
class HistoryFileParserTest {
    private val utc = ParseOptions(TimeZone.UTC)

    private fun parse(name: String) = HistoryFileParser.parse(fixtureBytes(name), utc)

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private fun failure(bytes: ByteArray): ImportError =
        assertFailsWith<ImportFormatException> { HistoryFileParser.parse(bytes, utc) }.error

    // ----- Hevy -----

    @Test
    fun `hevy export is detected and read set by set`() {
        val parsed = parse("hevy.csv")

        assertEquals(ImportSource.HEVY, parsed.source)
        assertEquals(2, parsed.sessions.size)
        val push = parsed.sessions.first()
        assertEquals("Push Day", push.title)
        assertEquals(at("2025-04-29T18:00:00Z"), push.startedAt)
        assertEquals(at("2025-04-29T19:05:00Z"), push.endedAt)
        assertEquals("Felt strong, good session", push.notes)
        assertEquals(
            listOf("Bench Press (Barbell)", "Triceps Pushdown \"Rope\"", "Plank", "Pull Up"),
            push.exercises.map { it.name },
        )
        val bench = push.exercises.first()
        assertEquals("Pause on chest", bench.notes)
        assertEquals(listOf(SetKind.WARMUP, SetKind.WORKING, SetKind.FAILURE), bench.sets.map { it.kind })
        assertEquals(listOf(40.0, 82.5, 82.5), bench.sets.map { it.weight })
        assertEquals(8.5, bench.sets[1].rpe)
        assertTrue(bench.sets.all { it.weightUnit == ImportWeightUnit.KG })
        assertEquals(SetKind.DROP, push.exercises[1].sets[1].kind)
        assertEquals(60, push.exercises[2].sets.single().durationS)
        assertNull(push.exercises[2].sets.single().weight)
        // The second Pull Up set measures nothing and is dropped.
        assertEquals(1, push.exercises[3].sets.size)
        assertEquals(1, parsed.skippedRows)
        assertFalse(parsed.needsUnitChoice)
    }

    @Test
    fun `hevy distance is kilometres and becomes metres`() {
        val run = parse("hevy.csv").sessions.last()

        assertEquals("Morning Run", run.title)
        val set = run.exercises.single().sets.single()
        assertEquals(5250, set.distanceM)
        assertEquals(1500, set.durationS)
    }

    // ----- Strong -----

    @Test
    fun `strong ios export keeps set types, notes and workout duration and asks for the unit`() {
        val parsed = parse("strong-ios.csv")

        assertEquals(ImportSource.STRONG, parsed.source)
        assertTrue(parsed.needsUnitChoice)
        assertEquals(2, parsed.sessions.size)
        val legs = parsed.sessions.first()
        assertEquals("Leg Day, heavy", legs.title)
        assertEquals("Knees \"ok\"", legs.notes)
        assertEquals(legs.startedAt + 65 * 60_000, legs.endedAt)
        val squat = legs.exercises.first()
        assertEquals(listOf(SetKind.WARMUP, SetKind.WORKING, SetKind.WORKING), squat.sets.map { it.kind })
        assertEquals(listOf(60.0, 100.0, 102.5), squat.sets.map { it.weight })
        assertTrue(squat.sets.all { it.weightUnit == null })
        assertEquals("belt", squat.notes)
        assertEquals(8.0, squat.sets[1].rpe)
        // The rest timer row is not a set and not an error.
        assertEquals(3, squat.sets.size)
        assertEquals(SetKind.DROP, legs.exercises[1].sets[1].kind)
        assertEquals(45, legs.exercises[2].sets.single().durationS)
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun `strong android export uses semicolons, decimal commas and an explicit kilogram column`() {
        val parsed = parse("strong-android.csv")

        assertEquals(ImportSource.STRONG, parsed.source)
        assertFalse(parsed.needsUnitChoice)
        val session = parsed.sessions.single()
        assertEquals("Tirón", session.title)
        assertEquals(session.startedAt + 3900 * 1000L, session.endedAt)
        assertEquals("Buen día", session.notes)
        val row = session.exercises.first { it.name == "Remo con barra" }
        assertEquals(listOf(72.5, 72.5), row.sets.map { it.weight })
        assertTrue(row.sets.all { it.weightUnit == ImportWeightUnit.KG })
        assertEquals(7.5, row.sets[0].rpe)
        // A bodyweight set has no load.
        assertNull(session.exercises.first { it.name == "Dominada" }.sets.single().weight)
    }

    // ----- FitNotes -----

    @Test
    fun `fitnotes export with a byte order mark groups a day into one session`() {
        val parsed = parse("fitnotes.csv")

        assertEquals(ImportSource.FITNOTES, parsed.source)
        assertEquals(2, parsed.sessions.size)
        val day = parsed.sessions.first()
        assertEquals(at("2025-04-29T12:00:00Z"), day.startedAt)
        assertNull(day.title)
        val bench = day.exercises.first()
        assertEquals("Flat Barbell Bench Press", bench.name)
        assertEquals(2, bench.sets.size)
        assertEquals("easy, fast", bench.notes)
        assertTrue(bench.sets.all { it.weightUnit == ImportWeightUnit.KG })
        val treadmill = day.exercises[1]
        assertEquals(ExerciseCategory.CARDIO, treadmill.category)
        assertEquals(4989, treadmill.sets.single().distanceM) // 3.1 miles
        assertEquals(25 * 60 + 30, treadmill.sets.single().durationS)
        assertEquals(45.5, parsed.sessions.last().exercises.single().sets.single().weight)
        assertFalse(parsed.needsUnitChoice)
    }

    @Test
    fun `fitnotes 2 reads pounds from its own column and the kind of set`() {
        val parsed = parse("fitnotes-ios.csv")

        val sets = parsed.sessions.single().exercises.single().sets
        assertEquals(listOf(225.0, 135.0), sets.map { it.weight })
        assertTrue(sets.all { it.weightUnit == ImportWeightUnit.LB })
        assertEquals(listOf(SetKind.WORKING, SetKind.WARMUP), sets.map { it.kind })
    }

    // ----- ids -----

    @Test
    fun `session ids are stable across parses and differ per start`() {
        val a = parse("hevy.csv").sessions
        val b = parse("hevy.csv").sessions

        assertEquals(a.map { it.id }, b.map { it.id })
        assertNotEquals(a[0].id, a[1].id)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(a[0].id))
    }

    @Test
    fun `the same start in a different source gives a different id`() {
        assertNotEquals(parse("strong-ios.csv").sessions.first().id, parse("hevy.csv").sessions.first().id)
    }

    @Test
    fun `row order and a time zone change the times but not the ids`() {
        val shuffled = fixtureBytes("hevy.csv").decodeToString().lines().let { lines ->
            (listOf(lines.first()) + lines.drop(1).reversed()).joinToString("\n")
        }
        val a = HistoryFileParser.parse(shuffled.encodeToByteArray(), utc)
        val b = HistoryFileParser.parse(fixtureBytes("hevy.csv"), ParseOptions(TimeZone.of("Europe/Madrid")))

        assertEquals(parse("hevy.csv").sessions.map { it.id }, a.sessions.map { it.id })
        assertEquals(parse("hevy.csv").sessions.map { it.id }, b.sessions.map { it.id })
        assertEquals(parse("hevy.csv").sessions[0].startedAt - 2 * 3_600_000L, b.sessions[0].startedAt)
    }

    // ----- values -----

    @Test
    fun `dates in the formats of the exporters are read`() {
        fun start(date: String): Long {
            val csv = "title,start_time,exercise_title,reps\nT,\"$date\",X,5\n"
            return HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single().startedAt
        }
        val expected = at("2025-04-29T18:05:00Z")
        assertEquals(expected, start("2025-04-29 18:05:00"))
        assertEquals(expected, start("2025-04-29T18:05"))
        assertEquals(expected, start("29 Apr 2025, 18:05"))
        assertEquals(expected, start("Apr 29, 2025 18:05"))
        assertEquals(expected, start("29 abr 2025, 18:05"))
        assertEquals(expected, start("29 avr. 2025 18:05"))
        assertEquals(expected, start("29/04/2025 18:05"))
        assertEquals(expected, start("04/29/2025 18:05"))
    }

    @Test
    fun `weights in the number formats of the exporters are read`() {
        fun weight(raw: String): Double? {
            val csv = "Date;Workout Name;Exercise Name;Set Order;Weight (kg);Reps\n2025-01-01 10:00:00;W;X;1;\"$raw\";5\n"
            return HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single().exercises.single().sets
                .single().weight
        }
        assertEquals(82.5, weight("82.5"))
        assertEquals(82.5, weight("82,5"))
        assertEquals(1250.0, weight("1,250"))
        assertEquals(1234.5, weight("1,234.5"))
        assertEquals(1234.5, weight("1.234,5"))
        assertEquals(0.125, weight("0,125"))
    }

    @Test
    fun `quoted fields with line breaks, CRLF and LF line ends read the same`() {
        val lf = "Date,Exercise,Category,Weight (kgs),Reps,Distance,Distance Unit,Time,Comment\n" +
            "2025-01-01,\"Curl, EZ\",Biceps,20,10,,,,\"line one\nline two\"\n"
        val crlf = lf.replace("\n", "\r\n")

        val a = HistoryFileParser.parse(lf.encodeToByteArray(), utc).sessions.single().exercises.single()
        val b = HistoryFileParser.parse(crlf.encodeToByteArray(), utc).sessions.single().exercises.single()

        assertEquals("Curl, EZ", a.name)
        assertEquals("line one\nline two", a.notes.replace("\r\n", "\n"))
        assertEquals(a.name, b.name)
        assertEquals(1, a.sets.size)
    }

    @Test
    fun `latin 1 text is read when the bytes are not utf 8`() {
        val csv = "title,start_time,exercise_title,reps\nPierna,2025-01-01 10:00,Sentadilla búlgara,5\n"
        val bytes = csv.toByteArray(Charsets.ISO_8859_1)

        val parsed = HistoryFileParser.parse(bytes, utc)

        assertEquals("Sentadilla búlgara", parsed.sessions.single().exercises.single().name)
    }

    // ----- malformed -----

    @Test
    fun `an unrecognised or empty file fails loudly and writes nothing`() {
        assertEquals(ImportError.UNRECOGNISED, failure(ByteArray(0)))
        assertEquals(ImportError.UNRECOGNISED, failure("a,b,c\n1,2,3\n".encodeToByteArray()))
        assertEquals(ImportError.UNRECOGNISED, failure("just some text".encodeToByteArray()))
        assertEquals(ImportError.UNRECOGNISED, failure(byteArrayOf(0, 1, 2, 3, 4, -1, -2)))
        assertEquals(ImportError.UNRECOGNISED, failure("{\"json\": true}".encodeToByteArray()))
    }

    @Test
    fun `a known header without usable rows is reported as no data`() {
        val header = fixtureBytes("hevy.csv").decodeToString().lines().first()
        assertEquals(ImportError.NO_DATA, failure("$header\n".encodeToByteArray()))
        val garbage = "$header\n\"T\",\"not a date\",\"\",\"\",\"X\",\"\",\"\",0,\"normal\",50,5,,,\n"
        assertEquals(ImportError.NO_DATA, failure(garbage.encodeToByteArray()))
    }

    @Test
    fun `rows with a bad date or without an exercise are skipped and counted`() {
        val csv = "title,start_time,exercise_title,reps\n" +
            "T,2025-01-01 10:00,Squat,5\n" +
            "T,31/02/2025,Squat,5\n" +
            "T,2025-01-01 10:00,,5\n"

        val parsed = HistoryFileParser.parse(csv.encodeToByteArray(), utc)

        assertEquals(1, parsed.setCount)
        assertEquals(2, parsed.skippedRows)
    }

    @Test
    fun `a damaged zip is reported as corrupt`() {
        val zip = zipOf("sessions.csv" to "x")
        assertEquals(ImportError.CORRUPT, failure(zip.copyOf(zip.size / 2)))
        assertEquals(ImportError.CORRUPT, failure(byteArrayOf(0x50, 0x4B, 3, 4, 1, 2, 3)))
    }

    @Test
    fun `a compressed zip asks for the csv inside`() {
        val zip = zipOf("sessions.csv" to "x".repeat(2000), compress = true)

        assertEquals(ImportError.COMPRESSED_ZIP, failure(zip))
    }

    @Test
    fun `a zip that is not a haltera export is refused`() {
        assertEquals(ImportError.NOT_AN_EXPORT, failure(zipOf("notes.txt" to "hello")))
        val manifest = "{\"format\":\"something-else\",\"version\":1}"
        assertEquals(ImportError.NOT_AN_EXPORT, failure(zipOf("manifest.json" to manifest, "sessions.csv" to "x")))
    }

    @Test
    fun `an export from a newer format version is refused`() {
        val manifest = "{\"format\":\"haltera-export\",\"version\":2}"

        assertEquals(ImportError.NEWER_FORMAT, failure(zipOf("manifest.json" to manifest, "sessions.csv" to "x")))
    }

    // ----- haltera -----

    @Test
    fun `a haltera sessions csv is read with its own ids`() {
        val csv = "﻿" + fixtureBytes("haltera-sessions.csv").decodeToString().replace("\n", "\r\n")

        val parsed: ParsedImport = HistoryFileParser.parse(csv.encodeToByteArray(), utc)

        assertEquals(ImportSource.HALTERA, parsed.source)
        val session: ImportedSession = parsed.sessions.single()
        assertEquals("s-001", session.id)
        assertEquals("Push, A", session.title)
        assertEquals("Felt \"great\"\nPR attempt", session.notes.replace("\r\n", "\n"))
        assertEquals(at("2026-10-14T18:40:00Z"), session.endedAt)
        val bench = session.exercises.first()
        assertEquals("bench-press.barbell", bench.variantId)
        assertEquals(82_500L, bench.sets[1].loadGrams)
        assertEquals(SetKind.FAILURE, bench.sets[2].kind)
        val plate = session.exercises[1].sets.single()
        assertEquals(7L, plate.loadGrams)
        assertEquals("{\"type\":\"machinePlate\"}", plate.loadDetail)
    }

    @Test
    fun `a haltera session ends at ended_at even when its sets were completed later`() {
        // A past session logged on Oct 17 for Oct 12: its sets carry the time they were ticked, days after the end.
        val header = fixtureBytes("haltera-sessions.csv").decodeToString().lines().first()
        val logged = "2026-10-12,s-past,2026-10-12T00:15:00Z,2026-10-12T01:15:00Z,Day 1,,,bench-press," +
            "bench-press.barbell,Bench Press,barbell,1,,1,WORKING,8,60000,60,kg,,,,,2026-10-17T01:18:00Z,t-1"
        val open = "2026-10-13,s-open,2026-10-13T10:00:00Z,,Day 1,,,bench-press," +
            "bench-press.barbell,Bench Press,barbell,1,,1,WORKING,8,60000,60,kg,,,,,2026-10-13T10:40:00Z,t-2"

        val sessions = HistoryFileParser.parse("$header\n$logged\n$open\n".encodeToByteArray(), utc).sessions

        assertEquals(at("2026-10-12T01:15:00Z"), sessions.single { it.id == "s-past" }.endedAt)
        // Without ended_at the last completed set is still the end.
        assertEquals(at("2026-10-13T10:40:00Z"), sessions.single { it.id == "s-open" }.endedAt)
    }

    // ----- more edge cases -----

    private val hevyHeader = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index," +
        "set_type,weight_kg,reps,distance_km,duration_seconds,rpe"

    @Test
    fun `a start inside the daylight saving gap is read and keeps its id`() {
        val csv = "$hevyHeader\nT,\"30 Mar 2025, 02:30\",\"30 Mar 2025, 03:30\",,Squat,,,0,normal,100,5,,,\n"
        val madrid = HistoryFileParser.parse(csv.encodeToByteArray(), ParseOptions(TimeZone.of("Europe/Madrid")))
        val utcParse = HistoryFileParser.parse(csv.encodeToByteArray(), utc)

        // 02:30 does not exist in Madrid that night; it is moved forward, never dropped.
        assertEquals(at("2025-03-30T01:30:00Z"), madrid.sessions.single().startedAt)
        assertEquals(utcParse.sessions.single().id, madrid.sessions.single().id)
    }

    @Test
    fun `two workouts with the same start but different titles are two sessions`() {
        val csv = "$hevyHeader\n" +
            "A,\"1 May 2025, 10:00\",\"1 May 2025, 11:00\",,Squat,,,0,normal,100,5,,,\n" +
            "B,\"1 May 2025, 10:00\",\"1 May 2025, 11:00\",,Squat,,,0,normal,100,5,,,\n"

        val sessions = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions

        assertEquals(listOf("A", "B"), sessions.map { it.title })
        assertNotEquals(sessions[0].id, sessions[1].id)
    }

    @Test
    fun `an end before the start becomes the start and a missing end is the start`() {
        val csv = "$hevyHeader\n" +
            "A,\"1 May 2025, 10:00\",\"1 May 2025, 09:00\",,Squat,,,0,normal,100,5,,,\n" +
            "B,\"2 May 2025, 10:00\",,,Squat,,,0,normal,100,5,,,\n"

        val sessions = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions

        assertTrue(sessions.all { it.endedAt == it.startedAt })
    }

    @Test
    fun `columns may come in any order, with unknown ones and tabs`() {
        val tsv = "rpe\texercise_title\tnew_column\treps\tstart_time\tweight_kg\ttitle\n" +
            "8\tSquat\tx\t5\t2025-05-01 10:00\t100\tLegs\n"

        val parsed = HistoryFileParser.parse(tsv.encodeToByteArray(), utc)

        val set = parsed.sessions.single().exercises.single().sets.single()
        assertEquals(ImportSource.HEVY, parsed.source)
        assertEquals(100.0, set.weight)
        assertEquals(8.0, set.rpe)
    }

    @Test
    fun `assisted bodyweight keeps its negative load and zero is no load`() {
        val csv = "$hevyHeader\n" +
            "A,\"1 May 2025, 10:00\",,,Assisted Pull Up,,,0,normal,-20,8,,,\n" +
            "A,\"1 May 2025, 10:00\",,,Push Up,,,0,normal,0,15,,,\n"

        val exercises = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single().exercises

        assertEquals(-20.0, exercises[0].sets.single().weight)
        assertNull(exercises[1].sets.single().weight)
        assertEquals(15, exercises[1].sets.single().reps)
    }

    @Test
    fun `strong rows say their own unit when the export has a unit column`() {
        val csv = "Date;Workout Name;Exercise Name;Set Order;Weight;Weight Unit;Reps\n" +
            "2025-05-01 10:00:00;W;Squat;1;100;kg;5\n" +
            "2025-05-01 10:00:00;W;Squat;2;225;lbs;5\n"

        val parsed = HistoryFileParser.parse(csv.encodeToByteArray(), utc)

        assertFalse(parsed.needsUnitChoice)
        assertEquals(
            listOf(ImportWeightUnit.KG, ImportWeightUnit.LB),
            parsed.sessions.single().exercises.single().sets.map { it.weightUnit },
        )
    }

    @Test
    fun `a strong file of rest timers only has no data`() {
        val csv = "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE\n" +
            "2025-05-01 10:00:00,W,1h,Squat,Rest Timer,0,0,0,90,,,\n"

        assertEquals(ImportError.NO_DATA, failure(csv.encodeToByteArray()))
    }

    @Test
    fun `a strong note row gives the exercise its note and is not a set`() {
        val csv = "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE\n" +
            "2025-05-01 10:00:00,W,1h,Squat,Note,,,,,knee sleeves,,\n" +
            "2025-05-01 10:00:00,W,1h,Squat,1,100,5,0,0,,,\n"

        val squat = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single().exercises.single()

        assertEquals("knee sleeves", squat.notes)
        assertEquals(1, squat.sets.size)
    }

    @Test
    fun `fitnotes rows of one day are one session even across exercises in any order`() {
        val csv = "Date,Exercise,Category,Weight (kgs),Reps\n" +
            "2025-05-01,Squat,Legs,100,5\n2025-05-01,Bench,Chest,80,5\n2025-05-01,Squat,Legs,100,5\n"

        val session = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single()

        assertEquals(listOf("Squat", "Bench"), session.exercises.map { it.name })
        assertEquals(2, session.exercises[0].sets.size)
    }

    @Test
    fun `a haltera body weight csv imports alone`() {
        val csv = "﻿date,measured_at,weight_kg,weight_grams,unit,notes,id\r\n" +
            "2026-10-15,2026-10-15T07:00:00Z,80.45,80450,kg,\"morning, fasted\",w-001\r\n" +
            "2026-10-16,not a time,80,80000,kg,,w-002\r\n"

        val parsed = HistoryFileParser.parse(csv.encodeToByteArray(), utc)

        assertEquals(ImportSource.HALTERA, parsed.source)
        assertTrue(parsed.sessions.isEmpty())
        val entry = parsed.bodyWeights.single()
        assertEquals("w-001", entry.id)
        assertEquals(80_450L, entry.weightGrams)
        assertEquals("morning, fasted", entry.notes)
    }

    @Test
    fun `a haltera session still in progress ends at its last completed set`() {
        val header = fixtureBytes("haltera-sessions.csv").decodeToString().lines().first()
        val csv = "$header\n2026-10-14,s-9,2026-10-14T17:30:00Z,,,,,squat,squat.barbell,Squat,barbell,1,,1,WORKING,5," +
            "100000,100,kg,,,,,2026-10-14T17:50:00Z,t-9\n"

        val session = HistoryFileParser.parse(csv.encodeToByteArray(), utc).sessions.single()

        assertEquals(at("2026-10-14T17:50:00Z"), session.endedAt)
    }

    @Test
    fun `an export zip whose files are inside a folder is read`() {
        val sessions = fixtureBytes("haltera-sessions.csv").decodeToString()

        val parsed = HistoryFileParser.parse(zipOf("haltera-export/sessions.csv" to sessions), utc)

        assertEquals("s-001", parsed.sessions.single().id)
    }

    @Test
    fun `an empty zip or a broken manifest is refused`() {
        assertEquals(ImportError.NOT_AN_EXPORT, failure(zipOf()))
        assertEquals(ImportError.CORRUPT, failure(zipOf("manifest.json" to "{not json", "sessions.csv" to "x")))
    }

    @Test
    fun `a zip with a sessions file that is not a haltera one is refused`() {
        val zip = zipOf("sessions.csv" to "Date,Exercise,Category,Weight (kgs),Reps\n2025-05-01,Squat,Legs,100,5\n")

        assertEquals(ImportError.UNRECOGNISED, failure(zip))
    }

    private fun zipOf(vararg files: Pair<String, String>, compress: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in files) {
                val bytes = text.encodeToByteArray()
                val entry = ZipEntry(name)
                if (!compress) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = java.util.zip.CRC32().also { it.update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
