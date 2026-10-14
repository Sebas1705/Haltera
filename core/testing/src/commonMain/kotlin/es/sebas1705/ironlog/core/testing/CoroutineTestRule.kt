package es.sebas1705.ironlog.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineTestRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) {
    fun setup() = Dispatchers.setMain(dispatcher)
    fun tearDown() = Dispatchers.resetMain()
}
