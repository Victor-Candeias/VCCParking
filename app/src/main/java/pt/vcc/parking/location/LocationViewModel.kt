package pt.vcc.parking.location

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LocationUiState {
    data object Idle : LocationUiState

    data object Loading : LocationUiState

    data class Available(val location: UserLocation) : LocationUiState

    data object PermissionRequired : LocationUiState

    data object PermissionDenied : LocationUiState

    data object LocationDisabled : LocationUiState

    data object Unavailable : LocationUiState
}

class LocationViewModel(private val locationProvider: LocationProvider) : ViewModel() {

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun onPermissionRequired() {
        _uiState.value = LocationUiState.PermissionRequired
    }

    fun onPermissionPermanentlyDenied() {
        _uiState.value = LocationUiState.PermissionDenied
    }

    fun refreshLocation() {
        if (_uiState.value == LocationUiState.Loading) return
        _uiState.value = LocationUiState.Loading

        viewModelScope.launch {
            val state = locationProvider.currentLocation().toUiState()
            Log.d(TAG, "Resultado da localizacao: ${state.javaClass.simpleName}")
            _uiState.value = state
        }
    }

    private fun LocationResult.toUiState(): LocationUiState = when (this) {
        is LocationResult.Available -> LocationUiState.Available(location)
        LocationResult.PermissionMissing -> LocationUiState.PermissionRequired
        LocationResult.LocationDisabled -> LocationUiState.LocationDisabled
        is LocationResult.Unavailable -> LocationUiState.Unavailable
    }

    companion object {
        private const val TAG = "LocationViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                LocationViewModel(FusedLocationProvider(application))
            }
        }
    }
}
