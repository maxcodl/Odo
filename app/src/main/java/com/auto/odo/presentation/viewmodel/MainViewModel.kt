package com.auto.odo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auto.odo.core.NavBarStyle
import com.auto.odo.core.AppThemeMode
import com.auto.odo.core.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: UserSessionManager
) : ViewModel() {

    val navBarStyle: StateFlow<NavBarStyle> = sessionManager.navBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavBarStyle.SOLID)

    val fullScreenStatusBar: StateFlow<Boolean> = sessionManager.fullScreenStatusBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoHideTitleBar: StateFlow<Boolean> = sessionManager.autoHideTitleBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appThemeMode: StateFlow<AppThemeMode> = sessionManager.appThemeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.STANDARD)
}
