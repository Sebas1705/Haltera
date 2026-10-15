package es.sebas1705.haltera

import androidx.compose.ui.window.ComposeUIViewController

/** Koin is started once by [startHalteraKoin], called from `iOSApp.init()`. */
fun MainViewController() = ComposeUIViewController { App() }
