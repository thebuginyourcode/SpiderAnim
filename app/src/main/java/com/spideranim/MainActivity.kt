package com.spideranim

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.spideranim.editor.EditorState
import com.spideranim.ui.EditorScreen
import com.spideranim.ui.theme.SpiderTheme

class MainActivity : ComponentActivity() {

    private val editor: EditorState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SpiderTheme {
                EditorScreen(editor)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        editor.saveNow()
    }
}
