package es.sebas1705.ironlog

import androidx.compose.ui.window.ComposeUIViewController

/** Koin is started once by [initKoin], called from `iOSApp.init()`. */
fun MainViewController() = ComposeUIViewController { App() }
