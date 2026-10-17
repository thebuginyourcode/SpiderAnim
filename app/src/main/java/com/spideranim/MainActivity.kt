package com.spideranim

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spideranim.editor.Editor
import com.spideranim.ui.EditorScreen
import com.spideranim.ui.GalleryScreen
import com.spideranim.ui.theme.SpiderTheme

class MainActivity : ComponentActivity() {

    private val editor: Editor by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Used by the CI smoke test to land straight in the editor.
        if (savedInstanceState == null && intent.getBooleanExtra("openLatest", false)) {
            editor.gallery.firstOrNull()?.let { editor.openProject(it.id) }
        }
        setContent {
            SpiderTheme {
                if (editor.isOpen) EditorScreen(editor) else GalleryScreen(editor)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        editor.saveNow()
    }
}
