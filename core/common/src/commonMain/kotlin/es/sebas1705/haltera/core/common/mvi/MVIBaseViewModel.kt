package es.sebas1705.haltera.core.common.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class MVIBaseViewModel<S : MVIBaseState, I : MVIBaseIntent>(
    initialState: S,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected val currentState: S get() = _uiState.value

    abstract fun intentHandler(intent: I): Job

    fun onEvent(intent: I): Job = intentHandler(intent)

    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    protected fun execute(block: suspend () -> Unit): Job =
        viewModelScope.launch { block() }
}
