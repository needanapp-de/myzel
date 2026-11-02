package de.needanapp.myzel.repo

import de.needanapp.myzel.repo.RepoUpdateStep.Checking
import de.needanapp.myzel.repo.RepoUpdateStep.Done
import de.needanapp.myzel.repo.RepoUpdateStep.Loaded
import de.needanapp.myzel.repo.RepoUpdateStep.Loading
import de.needanapp.myzel.repo.RepoUpdateStep.Saving
import de.needanapp.myzel.repo.RepoUpdateStep.Waiting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fdroid.index.IndexUpdateResult

/**
 * Führt den [RepoUpdateRun] der laufenden Aktualisierung: RepoUpdateManager meldet Anfang, Vorladen
 * und Ende, RepoUpdateListener den Fortschritt, den F-Droid beim Aktualisieren meldet. Das Vorladen
 * läuft parallel, darum dürfen mehrere Threads zugleich melden. Eine Phase geht nur vorwärts: Wer
 * schon geladen hat, fällt nicht zurück ins Prüfen, wenn F-Droid danach noch einmal entry.jar holt.
 * Prozente werden nur gemeldet, wenn sich die ganze Zahl ändert.
 */
class RepoUpdateBoard {

  private val _run = MutableStateFlow<RepoUpdateRun?>(null)
  val run: StateFlow<RepoUpdateRun?> = _run.asStateFlow()

  /** Ein neuer Lauf über [repoIds] beginnt, alle warten. */
  fun start(repoIds: List<Long>) {
    _run.value = RepoUpdateRun(repoIds)
  }

  /** Der Lauf ist zu Ende, auch nach einem Abbruch. */
  fun finish() {
    _run.update { run ->
      if (run == null || run.isFinished) run else run.copy(finishedAt = System.currentTimeMillis())
    }
  }

  /** [repoId] fragt, ob es etwas Neues gibt. */
  fun checking(repoId: Long) = move(repoId) { step -> if (step == Waiting) Checking else null }

  /** [repoId] lädt ihren Index; ohne [totalBytes] (etwa bei entry.jar) bleibt sie beim Prüfen. */
  fun loading(repoId: Long, bytesRead: Long, totalBytes: Long) {
    if (totalBytes <= 0) return
    val percent = (100L * bytesRead / totalBytes).toInt().coerceIn(0, 100)
    move(repoId) { step ->
      when (step) {
        Waiting,
        Checking -> Loading(percent)
        is Loading -> Loading(percent).takeIf { it != step }
        else -> null
      }
    }
  }

  /** [repoId] hat ihren Index vorgeladen. */
  fun loaded(repoId: Long) =
    move(repoId) { step -> if (step is Saving || step is Done) null else Loaded }

  /**
   * [repoId] schreibt [apps] von [totalApps] Apps in die Datenbank. F-Droid meldet jede App; ohne
   * [totalApps] zählt nur jede hundertste.
   */
  fun saving(repoId: Long, apps: Int, totalApps: Int) {
    val next = Saving(apps, totalApps)
    move(repoId) { step ->
      when {
        step is Done -> null
        step is Saving &&
          step.percent == next.percent &&
          (next.percent != null || next.apps - step.apps < UNCOUNTED_STEP) -> null
        else -> next
      }
    }
  }

  /** [repoId] ist fertig. */
  fun done(repoId: Long, result: IndexUpdateResult) =
    move(repoId) { step ->
      val apps = (step as? Saving)?.let { maxOf(it.apps, it.totalApps) } ?: 0
      Done(RepoOutcome.of(result), apps)
    }

  private inline fun move(repoId: Long, next: (RepoUpdateStep) -> RepoUpdateStep?) {
    _run.update { run ->
      if (run == null || run.isFinished || repoId !in run.repoIds) return@update run
      val step = next(run.step(repoId)) ?: return@update run
      run.copy(steps = run.steps + (repoId to step))
    }
  }
}

private const val UNCOUNTED_STEP = 100
