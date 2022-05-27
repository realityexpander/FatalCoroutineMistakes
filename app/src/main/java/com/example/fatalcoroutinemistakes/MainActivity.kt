package com.example.fatalcoroutinemistakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.fatalcoroutinemistakes.ui.theme.FatalCoroutineMistakesTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FatalCoroutineMistakesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

// Mistake #1
suspend fun getUserFirstNames(userIds: List<String>): List<String> {
    val firstNames = mutableListOf<String>()
    for (userId in userIds) {
        firstNames.add(getFirstName(userId))
    }

    return firstNames
}

suspend fun getFirstName(userId: String): String {
    return "John"
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FatalCoroutineMistakesTheme {
        Greeting("Android")
    }
}