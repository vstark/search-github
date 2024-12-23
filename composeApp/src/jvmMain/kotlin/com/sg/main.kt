package com.sg

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sg.data.db.dbBuilder
import com.sg.data.db.getDatabaseBuilder

fun main() = application {

    dbBuilder = getDatabaseBuilder()

    val state = rememberWindowState(
        size = DpSize(600.dp, 1024.dp),
        position = WindowPosition(Alignment.Center)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "search-github",
        state = state,
    ) {
        App()
    }
}