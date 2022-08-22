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
                    val userState by viewModel.apiCallState.collectAsState()

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
                        XmlInCompose(viewModel, lifecycleScope, userState)

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
    for (userId in userIds) {
        firstNames.add(getFirstName(userId)) // problem - will be executed in serial
    }

    // solution - will be executed in parallel
    val firstNames2 = mutableListOf<Deferred<String>>()
    coroutineScope {
        for (userId in userIds) {
            firstNames2.add(
                async {
                    getFirstName(userId)
                }.also {
                }
            ) // solution - will be setup to be executed in parallel
        }
    }

    // Alternative solution - will be executed in parallel, alternative to using a loop - using a map
    val firstNames3 =
        coroutineScope {
            userIds.map { userId ->
                async {
                    getFirstName(userId + 100)
                }.also {
                }
            }
        }.also {
        }.awaitAll()

    // Alternative solution - executed in parallel, but allows for each item to cause exception
    val firstNames4 =
        coroutineScope {
            userIds.map { userId ->
                async {
                    try {
                        getFirstNameWithExceptions(userId + 1000)
                    } catch (e: Exception) {
                        println("Exception in getUserFirstNames: $e")
                        "Error for id=${userId + 1000}: $e"
                    }
                }.also {
                }
            }.also{
            }
        }.also {
        }.awaitAll()

    println("firstNames: $firstNames")
    println("firstNames2: ${firstNames2.awaitAll()}")
    println("firstNames3: $firstNames3")
    println("firstNames4: $firstNames4")

    //return firstNames
    //return firstNames2.awaitAll() // execute all the async calls in parallel, and wait until they are all done
    return firstNames4 // execute all the async calls in parallel, and wait until they are all done

}

suspend fun getFirstName(userId: Int): String {
    delay(Random.nextLong(1000))
    println("getFirstName: $userId")
    return "John $userId"
}

suspend fun getFirstNameWithExceptions(userId: Int): String {
    delay(500)
    println("getFirstName: $userId")

    if (userId > 1005) {
        throw Exception("userId > 505")
    }

    return "John $userId"
}

////////////////////////////////////////////////////////////////// 5//////////

// Mistake #2 - Not checking for cancellation
suspend fun doSomething() {
    println("doSomething")
    var random: Int = 0

//    // ** DON'T DO THIS **
//    val badJob = CoroutineScope(Dispatchers.IO).launch {
//        random = Random.nextInt(100_000)
//        while(random != 50000) {
//            random = Random.nextInt(100_000)
//            // NOTICE: Not checking for cancellation
//        }
//    }
//    delay(500L)
//    println("Job Cancelled...")
//    badJob.cancel()

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

    // Solution - by using a dispatcher, this is main safe (room and retrofit already do this)
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
    } catch (e: CancellationException) { // SPECIFICALLY catch cancellation exceptions
        "from RiskyTask: CancellationException: $e"
        throw e // solution: *WILL* be passed to parent coroutine (re-thrown)
    } catch (e: Exception) {
        "from RiskyTask: Error in riskyTask: $e" // parent scope *NOT* notified about cancellation or math error
    }
}

////////////////////////////////////////////////////////////////////////////

// Mistake #5 - Exposing Viewmodel suspending functions to the UI (Activity/Fragment) lifecycle
class MainViewModel : ViewModel() {

    // PROBLEM: ViewModel should *NOT* expose suspend functions to the UI, bc
    //   the lifecycle of this suspend function is STILL bound to the lifecycle of the UI (Activity/Fragment)
    //   because the UI was the one that called it from the UI lifecycle scope.
    suspend fun postValueToApi() {
        apiCallState.tryEmit(ApiCallState.LoadingState)

        delay(1000) //  if config change occurs, the coroutine will be cancelled and the apiCallState will not be updated.
        println("postValueToApi")

        apiCallState.tryEmit(ApiCallState.Success)   // .tryEmit is used from coroutines, respects buffer.
    }

    // Note: to launch a coroutine that lasts longer than the ViewModel, you need to use a CoroutineScope:
    val longLivingCoroutineScope = CoroutineScope(Dispatchers.Main) // remember to cancel any jobs in the scope when finished

    val apiCallState = MutableStateFlow<ApiCallState>(ApiCallState.StartState)

    init {
        apiCallState.value = ApiCallState.StartState
    }

    // SOLUTION 1 - STILL A PROBLEM - DON'T DO THIS - View is passed (exposed) to the ViewModel
    fun postValueToApi1(button: Button) {
        viewModelScope.launch {  // bound to the lifecycle scope of the VIEWMODEL
            delay(2000)

            println("postValueToApi")
            button.text = "Posted!" // not safe to do this bc activity may be destroyed during config change, but here for demo purposes
        }
    }

    // SOLUTION 2 - STILL A PROBLEM - DON'T DO THIS - this solution exposes ViewModel suspending function to the UI lifecycle
    suspend fun postValueToApi2(): String {
        return viewModelScope.async {  // bound to the lifetime scope of the viewModel
            delay(2000)

            println("postValueToApi")
            return@async "Posted!"
        }.await()  // Call will complete OK, but if the view is destroyed during config change,
                   //   the API return value WON'T be passed back because the View has been DESTROYED.
    }

    // SOLUTION 3 - To return the API call value, emit to a flow instead of returning a value.
    //              The Flow is bound to the lifecycle of the ViewModel and retains the value here.
    fun postValueToApi3() {
        viewModelScope.launch {  // bound to the lifetime scope of the viewModel
            apiCallState.tryEmit(ApiCallState.LoadingState)

            delay(1000)
            println("postValueToApi")
            //userState.value = UserState.Success  // setting .value works even if not in a coroutine.
            apiCallState.tryEmit(ApiCallState.Success)   // .tryEmit is used from coroutines, respects buffer.
        }
    }
}

sealed class ApiCallState(val message: String) {
    object StartState : ApiCallState("Post Value to API")
    object LoadingState : ApiCallState("Posting...")
    object Success : ApiCallState("Success")
    object Error : ApiCallState("Error")
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
fun XmlInCompose(
    viewModel: MainViewModel,
    lifeCycleScope: LifecycleCoroutineScope,
    apiCallState: ApiCallState
) {
    AndroidView(factory = {
        View.inflate(it, R.layout.button_layout, null)
    },
        modifier = Modifier.fillMaxSize(),
        update = {
            val button = it.findViewById<Button>(R.id.button)
            button.setOnClickListener { view ->
                Toast.makeText(view.context, "Posting to Api", Toast.LENGTH_SHORT).show()

//                // PROBLEM - DON'T DO THIS - Calling the suspending function from the UI lifecycle.
//                lifeCycleScope.launch {  // lifeCycle Scope is the ACTIVITY/FRAGMENT lifecycle scope
//                    viewModel.postValueToApi() // PROBLEM: Call will be sent successfully, but during a
//                                               //   config change the coroutine will be cancelled, cancelling the API call.
//                }

                // EXAMPLE 1 - STILL A PROBLEM - DON'T DO THIS: This makes the call in the coroutine scope of the ViewModel, and passes in the button view.
//                viewModel.postValueToApi1(button) // Notice this passes in the button View. PROBLEM: This exposes the ViewModel suspending function to the UI lifecycle.
//                button.text = "Posting..."        // If a config change occurs, the ACTIVITY/FRAGMENT will be destroyed, and the `button` will be invalid.


//                // EXAMPLE 2 - STILL A PROBLEM - DON'T DO THIS: the value will not be returned from coroutine if the UI has a config change
                lifeCycleScope.launch { // lifecycle scope of the ACTIVITY/FRAGMENT lifecycle scope
                    button.text = viewModel.postValueToApi2() // Gets value from the api call as a return value OK, but PROBLEM: if a config change happens, values are reset.
                }

//                // EXAMPLE 3 - SOLUTION: Use a flow/Livedata instead of a return value.
//                viewModel.apiCallState.value = ApiCallState.LoadingState // ok to set directly bc we are not in a coroutine.
//                // viewModel.userState.emit(UserState.LoadingState) // can also use `emit`, but must be used in coroutines.
//                viewModel.postValueToApi3()
            }

            // Show result of API call.
            button.text = apiCallState.message
        }
    )
}