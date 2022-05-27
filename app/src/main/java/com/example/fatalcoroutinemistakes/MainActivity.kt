package com.example.fatalcoroutinemistakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.fatalcoroutinemistakes.ui.theme.FatalCoroutineMistakesTheme
import kotlinx.coroutines.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userIds = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
//        var userFirstNames = listOf<String>()
//        lifecycleScope.launch {
//            userFirstNames = getUserFirstNames(userIds)
//            println("userFirstnames: $userFirstNames")
//        }

        // Mistake #2 example - use of CancellationExceptions
        lifecycleScope.launch {
            try {
                doSomething()
            } catch (e: CancellationException) {
                println("main CancellationException: $e") // this will not be printed
            }
        }

        setContent {
            var coroutineScope = rememberCoroutineScope()

            FatalCoroutineMistakesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    var names by remember { mutableStateOf(listOf<String>()) }

                    LaunchedEffect(Unit) {
                        names = getUserFirstNames(userIds)
                    }

                    Greeting(names.toString())

                }
            }
        }
    }
}


// from Philip Lackner - 5 Fatal Coroutine Mistakes Nobody Tells You About
// https://www.youtube.com/watch?v=cr5xLjPC4-0

// Mistake #1 - dont need to do things sequentially here
suspend fun getUserFirstNames(userIds: List<Int>): List<String> {
    // problem - will be executed in serial
    val firstNames = mutableListOf<String>()
//    for (userId in userIds) {
//        firstNames.add(getFirstName(userId)) // problem - will be executed in serial
//    }

    // solution - will be executed in parallel
    val firstNames2 = mutableListOf<Deferred<String>>()
    coroutineScope {
        for (userId in userIds) {
            firstNames2.add(async { getFirstName(userId) }) // solution - will be setup to be executed in parallel
        }
    }

    //return firstNames
    return firstNames2.awaitAll() // execute all the async calls in parallel, and wait until they are all done
}

suspend fun getFirstName(userId: Int): String {
    delay(500)
    return "John $userId"
}

// Mistake #2 - dont check for cancellation
suspend fun doSomething() {
    println("doSomething")
    var random: Int = 0

    val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            random = Random.nextInt(5_000_000)

            while (random != 50_000) {
            // while(random != 50_000 && isActive) { // check for cancellation
                random = Random.nextInt(5_000_000)
                // if(!isActive) return@launch // check for cancellation
                ensureActive()  // throws cancellation exception if cancelled
            }
        } catch (e: CancellationException) {
            println("job CancellationException: $e") // *NOT* passed to parent coroutine
        } finally {
            println("done, isActive: $isActive, random: $random")
        }

    }
    delay(500L)
    println("Job Cancelled...")
    job.cancel()
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