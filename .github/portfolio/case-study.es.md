# Haltera: migrar una app Kotlin Multiplatform a AGP 9

Cómo pasé un cuaderno de entrenamiento KMP del Android Gradle Plugin 8.13 al 9.4 en dos pull requests, y encontré un fallo de empaquetado que ningún test había detectado.

## Contexto

[Haltera](https://github.com/Sebas1705/Haltera) es una app Kotlin Multiplatform (Android e iOS, UI en Compose Multiplatform) con un build modular: módulos `core`, `data`, `domain` y `feature`, todos configurados con convention plugins en `build-logic`. Las releases se compilan en CI con una acción compuesta y un script las verifica antes de subirlas a Firebase App Distribution y Google Play.

## Problema

La actualización vino forzada por una dependencia. Dependabot abrió la subida de coil de 3.5.0 a 3.6.3 ([#7](https://github.com/Sebas1705/Haltera/pull/7)). Se fusionó en verde, pero coil 3.6.3 arrastra androidx.compose 1.12, que exige AGP 9.1, y el proyecto estaba en AGP 8.13.2. Los checks de esa PR no incluían el job de Android, así que la rotura solo apareció en `main` y la revertí ([fb0953e](https://github.com/Sebas1705/Haltera/commit/fb0953e)). Detrás esperaban el grupo de Compose ([#45](https://github.com/Sebas1705/Haltera/pull/45)), ktor ([#6](https://github.com/Sebas1705/Haltera/pull/6)) y el wrapper de Gradle ([#8](https://github.com/Sebas1705/Haltera/pull/8)). Quedarse en AGP 8 suponía congelar dependencias.

En un proyecto KMP, AGP 9 no es un simple cambio de versión: ya no permite `com.android.library` junto a `org.jetbrains.kotlin.multiplatform`, y solo acepta la forma antigua del módulo de aplicación (`com.android.application` más el `androidTarget()` de KMP) con dos flags de exclusión que AGP 10 eliminará.

## Enfoque

**Paso 1: la actualización ([#46](https://github.com/Sebas1705/Haltera/pull/46)).** AGP 8.13.2 → 9.4.1 y Gradle 9.5.1 → 9.8.1, en tres commits verificados por separado:

- Todos los módulos de librería pasaron al plugin de librería Android para KMP (`com.android.kotlin.multiplatform.library`). Como todos usan `haltera.kmp.library`, fue un único cambio en `build-logic`: `kotlin { androidLibrary { … } }` en lugar de `androidTarget()` más un bloque `android {}`. Las librerías pierden los build types, así que los tests JVM se movieron de `src/androidUnitTest` a `src/androidHostTest` y se ejecutan con `testAndroidHostTest`. Los recursos Android pasaron a ser opcionales y los activé solo en módulos con `src/androidMain/res`.
- El módulo de app mantuvo su forma antigua tras `android.builtInKotlin=false` y `android.newDsl=false`, documentados como temporales. A propósito, esta PR no tocó el pipeline de release.
- `compileSdk` subió a 37 (lo exigen coil 3.6.3 y androidx.compose 1.12), las exclusiones de tests de Detekt incluyeron `androidHostTest`, y CI, documentación y plantilla de PR ejecutan ahora ambas tareas de test. Las subidas de coil, ktor, Compose Multiplatform (1.12.2) y el wrapper se reaplicaron, sustituyendo las tres PRs de Dependabot.

La suite pasó con 1.161 tests y el APK de debug compiló.

**Paso 2: quitar las exclusiones ([#47](https://github.com/Sebas1705/Haltera/pull/47)).** Separé la aplicación Android en un nuevo módulo puramente Android, `androidApp` (`com.android.application` con el Kotlin integrado de AGP y el DSL nuevo, mediante una convención `haltera.android.app`). Se llevó el manifiesto, los recursos del launcher, las activities, la firma, las reglas de R8, `google-services` y LeakCanary; `composeApp` quedó como una librería KMP normal con la capa compartida. CI, la acción de release, los workflows de release y distribución y los scripts de verificación apuntan ya a las nuevas rutas de salida.

**El fallo que los tests no vieron.** Tras la separación, la app se cerraba al arrancar: faltaba en el APK el catálogo de recursos Compose del propio `composeApp`. Compose Multiplatform empaqueta los recursos de una librería como assets de Android, y el plugin de librería KMP solo mete assets en el AAR si los recursos Android están activados. Como #46 solo los activaba en módulos con `res/`, los textos de `core:ui` y de cinco módulos de feature ya faltaban en el APK compilado desde `main`; lo confirmé inspeccionando ese APK de debug. El arreglo fue una condición en la convención:

```kotlin
androidLibrary {
    this.namespace = namespace
    compileSdk = COMPILE_SDK
    minSdk = MIN_SDK
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    // El plugin solo empaqueta assets (donde Compose Multiplatform deja los
    // recursos de una librería) en el AAR si los recursos Android están activos.
    androidResources {
        enable = file("src/androidMain/res").isDirectory ||
            file("src/commonMain/composeResources").isDirectory
    }
    withHostTest {}
}
```

## Resultado

- Las dos PRs se fusionaron el mismo día; el proyecto compila con los valores por defecto de AGP 9 y sin flags de exclusión.
- Tras #47, `detekt testDebugUnitTest testAndroidHostTest verifySqlDelightMigration :androidApp:assembleDebug` pasó en verde con 1.241 tests (160 de ellos en `composeApp`, ahora en `androidHostTest`).
- APK de debug en un emulador con Android 16: arranque, rutinas, el catálogo de rutinas, una sesión libre completa hasta el resumen y datos que sobreviven a un reinicio, en inglés y de nuevo en español con el idioma por app, sin errores en logcat.
- Reproduje en local el camino de release tal como lo ejecuta CI: `assembleRelease bundleRelease` firmados, `verify-release.mjs` (SDK objetivo y mínimo, alineación de 16 KB, fichero de mapping), `apksigner` con el certificado de subida, `aapt2` con paquete y versión, y los recursos Compose de los 8 módulos presentes.
- Lo que no pude verificar: la subida a Play y a App Distribution solo se ejecutan en `main` y en tags, así que solo comprobé sus rutas de entrada.

## Lecciones

- **Una suite en verde no demuestra que el artefacto esté bien.** Los tests de host leen los recursos del módulo, no del APK. Los recursos perdidos solo se veían en la app en marcha y en el propio paquete, así que para mí una migración de build no termina hasta inspeccionar y ejecutar el APK.
- **CI debe vigilar lo que fusionan los bots.** La rotura de coil llegó a `main` porque la PR de Dependabot no ejecutó el job de Android.
- **Dividir las migraciones arriesgadas.** Dejar el pipeline de release fuera de #46 hizo cada PR revisable y mantuvo un camino de release funcional en cada paso.
- **Los convention plugins se amortizan al migrar.** Cambiar el target Android de una veintena de módulos, y luego arreglar el empaquetado de recursos de todos, fueron ediciones únicas en `build-logic`.
