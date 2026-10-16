package es.sebas1705.haltera.core.search

/**
 * Curated Spanish ↔ English gym vocabulary for exercise search (docs/designs/2026-10-16-fuzzy-search-design.md).
 *
 * Each line is a group of phrases that mean the same thing. Names containing one phrase are also found by
 * the others, so "press banca" finds "Incline Bench Press" (Spanish name "Press inclinado") and "jalón"
 * finds a custom exercise named only "Lat pulldown". Keep groups tight: a loose group ("press" = "prensa")
 * makes every search noisier. Muscle, equipment and category words are already indexed in both languages
 * from the exercise data (see `ExerciseSearchDocuments` in `data:repository`); list them here only when
 * people use other words for them in names ("db", "pesa rusa", "isquios").
 */
object GymSynonyms {

    val groups: List<List<String>> = listOf(
        // Movements
        listOf("press banca", "bench press", "press pecho plano", "press plano"),
        listOf("banca", "bench", "banco"),
        listOf("inclinado", "incline"),
        listOf("declinado", "decline"),
        listOf("press militar", "military press", "overhead press", "ohp", "press hombro", "shoulder press"),
        listOf("jalon", "pulldown", "pull down"),
        listOf("jalon pecho", "lat pulldown"),
        listOf("remo", "row"),
        listOf("remo barra", "barbell row", "bent over row"),
        listOf("sentadilla", "squat"),
        listOf("sentadilla bulgara", "bulgarian split squat"),
        listOf("prensa", "leg press", "prensa pierna"),
        listOf("apertura", "fly", "flye", "pec deck", "mariposa", "contractora"),
        listOf("cruce polea", "cable crossover", "crossover"),
        listOf("dominada", "pull up", "pullup", "chin up", "chinup"),
        listOf("dominada supina", "chin up"),
        listOf("dominada prona", "pull up"),
        listOf("zancada", "lunge", "estocada"),
        listOf("peso muerto", "deadlift"),
        listOf("peso muerto rumano", "romanian deadlift", "rdl"),
        listOf("fondo", "dip"),
        listOf("flexion", "push up", "pushup", "lagartija"),
        listOf("elevacion", "raise"),
        listOf("elevacion lateral", "lateral raise", "side raise"),
        listOf("elevacion frontal", "front raise"),
        listOf("pajaro", "rear delt fly", "reverse fly", "pajaros"),
        listOf("encogimiento hombro", "shrug"),
        listOf("crunch", "encogimiento abdominal"),
        listOf("plancha", "plank"),
        listOf("hip thrust", "empuje cadera", "puente gluteo", "glute bridge"),
        listOf("patada", "kickback"),
        listOf("rompecraneo", "skull crusher", "press frances", "french press"),
        listOf("face pull", "tiron cara", "jalon cara"),
        listOf("buenos dias", "good morning"),
        listOf("hiperextension", "hyperextension", "back extension", "extension lumbar"),
        listOf("rueda abdominal", "ab wheel", "ab roller", "rollout"),
        listOf("predicador", "scott", "preacher"),
        listOf("concentrado", "concentration"),
        listOf("martillo", "hammer"),
        listOf("pullover", "pull over"),
        listOf("zancada caminando", "walking lunge"),
        listOf("salto", "jump"),
        listOf("estiramiento", "stretch"),
        // Grips, positions and sides
        listOf("agarre", "grip"),
        listOf("agarre cerrado", "close grip", "narrow grip"),
        listOf("agarre abierto", "agarre ancho", "wide grip"),
        listOf("agarre neutro", "neutral grip"),
        listOf("inverso", "reverse", "invertido"),
        listOf("sentado", "seated"),
        listOf("de pie", "standing"),
        listOf("tumbado", "lying", "acostado"),
        listOf("una mano", "un brazo", "one arm", "single arm", "unilateral"),
        listOf("una pierna", "one leg", "single leg"),
        listOf("frontal", "front"),
        listOf("trasero", "rear", "posterior"),
        // Equipment words people use in names
        listOf("mancuerna", "dumbbell", "db"),
        listOf("barra", "barbell", "bb"),
        listOf("barra z", "ez bar", "ezbar"),
        listOf("barra hexagonal", "trap bar", "hex bar"),
        listOf("polea", "cable", "pulley"),
        listOf("maquina", "machine"),
        listOf("multipower", "smith", "maquina smith"),
        listOf("kettlebell", "pesa rusa", "kb"),
        listOf("goma", "banda", "band", "goma elastica", "resistance band"),
        listOf("peso corporal", "bodyweight", "body weight"),
        listOf("fitball", "swiss ball", "pelota suiza", "stability ball"),
        listOf("balon medicinal", "medicine ball"),
        listOf("cinta", "treadmill"),
        listOf("bicicleta", "bici", "bike", "cycling"),
        listOf("comba", "jump rope", "saltar comba", "skipping"),
        listOf("correr", "running", "run", "carrera"),
        // Body parts people use in names
        listOf("pecho", "chest", "pectoral"),
        listOf("espalda", "back"),
        listOf("hombro", "shoulder", "deltoide", "delt"),
        listOf("cuadriceps", "quad", "quadriceps"),
        listOf("femoral", "isquio", "isquiotibial", "hamstring", "leg curl"),
        listOf("gemelo", "calf", "calves", "pantorrilla"),
        listOf("gluteo", "glute"),
        listOf("abdominal", "abs", "ab", "core"),
        listOf("dorsal", "lat"),
        listOf("trapecio", "trap"),
        listOf("antebrazo", "forearm"),
        listOf("muneca", "wrist"),
        listOf("cadera", "hip"),
        listOf("pierna", "leg"),
        listOf("brazo", "arm"),
        listOf("cuello", "neck"),
        listOf("lumbar", "lower back"),
        listOf("aductor", "adductor"),
    )

    val table: SynonymTable = SynonymTable(groups)
}
