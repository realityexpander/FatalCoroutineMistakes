package com.example.fatalcoroutinemistakes

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.fatalcoroutinemistakes.ui.theme.FatalCoroutineMistakesTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

// from Philip Lackner - 5 Fatal Coroutine Mistakes Nobody Tells You About
// https://www.youtube.com/watch?v=cr5xLjPC4-0

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userIds = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
//        var userFirstNames = listOf<String>()
//        lifecycleScope.launch {
//            userFirstNames = getUserFirstNames(userIds)
//            println("userFirstnames: $userFirstNames")
//        }

        // Mistake #2 driver - use of CancellationExceptions
        lifecycleScope.launch {
            try {
                doSomething()
            } catch (e: CancellationException) {
                println("main CancellationException: $e") // this will not be printed
            }
        }

        // Mistake #3 example
        lifecycleScope.launch {
            try {
                doNetworkCall()
            } catch (e: Exception) {
                println("main exception: $e") // this will not be printed
            }
        }

        setContent {
            var coroutineScope = rememberCoroutineScope()

            FatalCoroutineMistakesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val userState by viewModel.userState.collectAsState()

                    Column {
                        // Example #1 driver
                        var names by remember { mutableStateOf(listOf<String>()) }
                        LaunchedEffect(Unit) {
                            names = getUserFirstNames(userIds)
                        }
                        Output("FirstNames: $names")
                        Spacer(modifier = Modifier.height(16.dp))

                        // Example #3 driver
                        var networkResult by remember { mutableStateOf(Result.success("Pending")) }
                        LaunchedEffect(Unit) {
                            networkResult = doNetworkCall()
                        }
                        Output("NetworkResult: $networkResult")
                        Spacer(modifier = Modifier.height(16.dp))

                        // Example #4 driver
                        var calcResult by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) {
                            try {
                                calcResult = riskyTask()
                            } catch (e: Exception) {
                                calcResult = "CalcResult in main exception: $e"
                            }
                        }
                        Output("CalcResult: $calcResult")
                        Spacer(modifier = Modifier.height(16.dp))

                        // Example #5 driver
                        xmlInCompose(viewModel, lifecycleScope, userState)

                    }

                }
            }
        }
    }
}


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

////////////////////////////////////////////////////////////////////////////

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

////////////////////////////////////////////////////////////////////////////

// Mistake #3 - network call is not main safe
suspend fun doNetworkCall(): Result<String> {
    var result = networkCall()
    return if (result == "Success") {
        Result.success("Success")
    } else {
        Result.failure(Exception("Error"))
    }
}

suspend fun networkCall(): String {
    // Problem - this is not main safe
//    delay(1000)
//    return if (Random.nextBoolean()) "Success" else "Error"

    // Solution - this is main safe (room and retrofit already do this)
    return withContext(Dispatchers.IO) {
        delay(500)
        if (Random.nextBoolean()) "Success" else "Error"
    }
}

////////////////////////////////////////////////////////////////////////////

// Mistake #4 - Suspend function catch CancellationExceptions
suspend fun riskyTask(): String {
    // throw CancellationException("Cancelled") // *WILL* be passed to parent coroutine


//    // Problem - cancellationExceptions are not thrown to parent
//    return try {
//        delay(1000)
//        "The answer is ${10/0}"
//    } catch (e: Exception) {
//        "Error in riskyTask" // parent scope *NOT* notified about ArithmeticException
//    }

    // Solution - cancellationExceptions are thrown to parent
    return try {
        delay(1000)

        // simulate job cancellation
        throw CancellationException("Cancelled") // will *NOT* be passed to parent coroutine, will be caught below

        // simulate math error
        "The answer is ${10 / 0}"
    } catch (e: ArithmeticException) {
        "from RiskyTask: ArithmeticException: $e" // parent scope *NOT* notified about ArithmeticException
    } catch (e: CancellationException) {
        "from RiskyTask: CancellationException: $e"
        throw e // solution: *WILL* be passed to parent coroutine
    } catch (e: Exception) {
        "from RiskyTask: Error in riskyTask: $e" // parent scope *NOT* notified about cancellation or math error
    }
}

////////////////////////////////////////////////////////////////////////////

// Mistake #5 - Exposing Viewmodel suspending functions to the UI lifecycle
class MainViewModel : ViewModel() {

    // problem: ViewModel should *NOT* expose suspending functions to the UI, bc the lifetime of this suspend function is not bound to the lifecycle of the view
//    suspend fun postValueToApi() {
//        delay(1000)
//        println("postValueToApi")
//    }

    val userState = MutableStateFlow<UserState>(UserState.StartState)

    init {
        userState.value = UserState.StartState
    }

    fun postValueToApi(button: Button) {
        viewModelScope.launch {  // bound to the lifetime scope of the viewModel
            delay(1000)
            println("postValueToApi")
            button.text = "Posted!" // not safe to do this, but here for demo purposes
        }
    }

    suspend fun postValueToApi2(): String {
        return viewModelScope.async {  // bound to the lifetime scope of the viewModel
            delay(1000)
            println("postValueToApi")
            return@async "Posted!"
        }.await()
    }

    fun postValueToApi3() {
        viewModelScope.launch {  // bound to the lifetime scope of the viewModel
            delay(1000)
            println("postValueToApi")
            //userState.value = UserState.Success  // works even if not in a coroutine
            userState.tryEmit(UserState.Success) // for use in coroutines, respects buffer
        }
    }
}

sealed class UserState(val message: String) {
    object StartState : UserState("Post Value to API")
    object LoadingState : UserState("Posting...")
    object Success : UserState("Success")
    object Error : UserState("Error")
}


@Composable
fun Output(data: String) {
    Text(text = "Output= $data!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FatalCoroutineMistakesTheme {
        Output("pending...")
    }
}

@Composable
fun xmlInCompose(viewModel: MainViewModel, lifeCycleScope: LifecycleCoroutineScope, userState: UserState) {
    AndroidView(factory = {
        View.inflate(it, R.layout.button_layout, null)
    },
        modifier = Modifier.fillMaxSize(),
        update = {
            val button = it.findViewById<Button>(R.id.button)
            button.setOnClickListener { view ->
                Toast.makeText(view.context, "Posting to Api", Toast.LENGTH_SHORT).show()
//                lifeCycleScope.launch { // Problem: Call will be sent successfully, but if during a config change, the coroutine will be cancelled (not good)
//                    viewModel.postValueToApi()
//                }

                // Solution: Make the call in the coroutine scope of the ViewModel
//                viewModel.postValueToApi(button)
//                button.text = "Posting..."
                viewModel.userState.value = UserState.LoadingState // ok to set directly bc we are not in a coroutine
                // viewModel.userState.emit(UserState.LoadingState) // for use in coroutines

//                lifeCycleScope.launch {
//                    button.text = viewModel.postValueToApi2() // gets value from the api call directly, problem: if config change values are reset.
//                }

                viewModel.postValueToApi3()
            }
            button.text = userState.message
        }
    )
}