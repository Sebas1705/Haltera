package es.sebas1705.ironlog

import androidx.compose.ui.window.ComposeUIViewController

/** Koin is started once by [startIronlogKoin], called from `iOSApp.init()`. */
fun MainViewController() = ComposeUIViewController { App() }
