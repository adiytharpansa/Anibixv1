package com.anibix.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anibix.app.FlixApp
import com.anibix.app.data.Category
import com.anibix.app.data.Title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val app: FlixApp) : ViewModel() {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()
    val downloads = app.engine.states

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl = _streamUrl.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            _categories.value = app.catalog.categories()
            _titles.value = app.catalog.allTitles()
        }
    }

    suspend fun title(id: String) = app.catalog.title(id)

    fun stream(t: Title) = viewModelScope.launch {
        _streamUrl.value = null; _error.value = null
        runCatching { app.engine.startStream(t.id, t.name, t.magnet) }
            .onSuccess { _streamUrl.value = it }
            .onFailure { _error.value = it.message }
    }

    fun download(t: Title) = viewModelScope.launch {
        runCatching { app.engine.startDownload(t.id, t.name, t.magnet) }
            .onFailure { _error.value = it.message }
    }

    fun pause(id: String) = app.engine.pause(id)
    fun resume(id: String) = app.engine.resume(id)
    fun remove(id: String) = app.engine.remove(id)

    class Factory(private val app: FlixApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = MainViewModel(app) as T
    }
}
