package de.needanapp.myzel.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import de.needanapp.myzel.watch.PairedWatch
import de.needanapp.myzel.watch.WatchBridge
import de.needanapp.myzel.watch.WatchException
import de.needanapp.myzel.watch.WatchInfo
import de.needanapp.myzel.watch.WatchStore
import de.needanapp.myzel.watch.WearUpdateState
import de.needanapp.myzel.watch.setup.SetupState
import de.needanapp.myzel.watch.setup.WatchSetup
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Ziel der Navigation: die Seite „Uhr“, erreichbar über die Menüpille. */
@Serializable data object WatchPage : NavKey

/** Ergebnis von „Verbindung testen“. */
sealed interface CheckState {
  data object Idle : CheckState

  data object Running : CheckState

  data class Ok(val info: WatchInfo) : CheckState

  data class Failed(val reason: WatchException.Reason) : CheckState
}

@HiltViewModel
class WatchViewModel
@Inject
constructor(
  private val setup: WatchSetup,
  private val bridge: WatchBridge,
  private val store: WatchStore,
) : ViewModel() {
  val watch: StateFlow<PairedWatch?> = bridge.watch
  val setupState: StateFlow<SetupState> = setup.state
  val wearUpdate: StateFlow<WearUpdateState?> = bridge.wearUpdate
  val askAfterInstall: StateFlow<Boolean> = store.askAfterInstall

  private val _check = MutableStateFlow<CheckState>(CheckState.Idle)
  val check: StateFlow<CheckState> = _check.asStateFlow()

  /** Der Assistent ist offen: ohne Uhr immer, sonst nach „Neu einrichten“. */
  private val _assistant = MutableStateFlow(bridge.watch.value == null)
  val assistant: StateFlow<Boolean> = _assistant.asStateFlow()

  /** Gerade eingerichtet: Die Übersicht zeigt das Ergebnis. */
  private val _justSetUp = MutableStateFlow(false)
  val justSetUp: StateFlow<Boolean> = _justSetUp.asStateFlow()

  private var job: Job? = null

  /** Sucht Uhren; erst, wenn das Recht „Geräte in der Nähe“ da ist. */
  fun startSearch() = setup.start()

  fun select(name: String) = setup.select(name)

  fun hideDevice(name: String) = setup.hide(name)

  fun setUp(code: String) {
    if (job?.isActive == true) return
    job = viewModelScope.launch {
      setup.setUp(code.takeIf { it.isNotBlank() })
      if (setup.state.value.finished) {
        setup.reset()
        _check.value = CheckState.Idle
        _justSetUp.value = true
        _assistant.value = false
      }
    }
  }

  fun setUpAgain() {
    _justSetUp.value = false
    _assistant.value = true
  }

  /** Zurück aus dem Assistenten zur Übersicht, wenn schon eine Uhr eingerichtet ist. */
  fun closeAssistant(): Boolean {
    if (!_assistant.value || watch.value == null || setupState.value.running) return false
    _assistant.value = false
    return true
  }

  fun checkConnection() {
    if (_check.value == CheckState.Running) return
    _check.value = CheckState.Running
    viewModelScope.launch {
      _check.value =
        try {
          CheckState.Ok(bridge.check())
        } catch (e: WatchException) {
          CheckState.Failed(e.reason)
        } catch (e: Exception) {
          CheckState.Failed(WatchException.Reason.UNREACHABLE)
        }
    }
  }

  /**
   * Nach einem gescheiterten Update von Myzel Wear: gleich neu versuchen, mit dem Verbindungstest.
   */
  fun retryWearUpdate() {
    bridge.retryWearUpdate()
    checkConnection()
  }

  fun setAskAfterInstall(ask: Boolean) = store.setAskAfterInstall(ask)

  fun forget() {
    store.forget()
    _justSetUp.value = false
    _check.value = CheckState.Idle
    _assistant.value = true
  }

  override fun onCleared() = setup.stop()
}
