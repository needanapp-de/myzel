package de.needanapp.myzel.repo

import org.fdroid.index.IndexUpdateResult

/**
 * Eine Aktualisierung der Paketquellen, wie sie die Anzeigen in Entdecken und über den Paketquellen
 * zeigen: die Paketquellen [repoIds] in der Reihenfolge, in der sie gespeichert werden, und wie
 * weit jede ist ([step]). Die Indexe laden parallel vor, gespeichert wird nacheinander; [current]
 * ist die, die gerade dran ist. Nach dem Ende bleibt der Lauf mit [finishedAt] stehen, damit eine
 * Anzeige das Ergebnis noch kurz zeigen kann. Geführt wird er von [RepoUpdateBoard].
 */
data class RepoUpdateRun(
  val repoIds: List<Long>,
  val steps: Map<Long, RepoUpdateStep> = emptyMap(),
  val startedAt: Long = System.currentTimeMillis(),
  val finishedAt: Long? = null,
) {
  val total: Int
    get() = repoIds.size

  val isFinished: Boolean
    get() = finishedAt != null

  /** Wie lange der Lauf gedauert hat, bzw. bisher dauert. */
  val durationMillis: Long
    get() = (finishedAt ?: System.currentTimeMillis()) - startedAt

  fun step(repoId: Long): RepoUpdateStep = steps[repoId] ?: RepoUpdateStep.Waiting

  /** Die Paketquelle, die gerade gespeichert wird oder als nächste dran ist; null am Ende. */
  val current: Long?
    get() = repoIds.firstOrNull { step(it) !is RepoUpdateStep.Done }

  /** Die wievielte [current] ist, ab 1; am Ende [total]. */
  val number: Int
    get() = current?.let { repoIds.indexOf(it) + 1 } ?: total

  /** Wie viele Paketquellen fertig sind mit [outcome]. */
  fun count(outcome: RepoOutcome): Int = repoIds.count {
    (step(it) as? RepoUpdateStep.Done)?.outcome == outcome
  }

  /** Wie weit alle zusammen sind, von 0 bis 1. */
  val progress: Float
    get() =
      if (repoIds.isEmpty()) 1f
      else repoIds.sumOf { step(it).fraction.toDouble() }.toFloat() / total

  /**
   * Ob eine Paketquelle seit [other] in eine andere Phase gekommen ist; ein neuer Prozentstand
   * allein zählt nicht. Die Anzeige hält jede Phase eine Mindestzeit, damit sie lesbar bleibt.
   */
  fun phaseChangedFrom(other: RepoUpdateRun?): Boolean =
    other == null ||
      other.repoIds != repoIds ||
      other.isFinished != isFinished ||
      repoIds.any { other.step(it)::class != step(it)::class }
}

/**
 * Wie weit eine Paketquelle in einem [RepoUpdateRun] ist. [fraction] ist ihr Anteil am Ganzen von 0
 * bis 1: die erste Hälfte für das Laden, die zweite für das Speichern.
 */
sealed interface RepoUpdateStep {
  val fraction: Float

  /** Wartet, bis sie laden darf; es laden höchstens drei gleichzeitig. */
  data object Waiting : RepoUpdateStep {
    override val fraction = 0f
  }

  /** Fragt die Paketquelle, ob es etwas Neues gibt (entry.jar). */
  data object Checking : RepoUpdateStep {
    override val fraction = 0f
  }

  /** Lädt den Index oder dessen Änderungen, [percent] von 0 bis 100. */
  data class Loading(val percent: Int) : RepoUpdateStep {
    override val fraction
      get() = percent / 200f
  }

  /** Hat den Index geladen und wartet, bis sie gespeichert wird. */
  data object Loaded : RepoUpdateStep {
    override val fraction = 0.5f
  }

  /** Schreibt [apps] von [totalApps] Apps in die Datenbank; ohne [totalApps] (0) kein [percent]. */
  data class Saving(val apps: Int, val totalApps: Int) : RepoUpdateStep {
    val percent: Int?
      get() = if (totalApps > 0) (100L * apps / totalApps).toInt().coerceIn(0, 100) else null

    override val fraction
      get() = 0.5f + (percent ?: 0) / 200f
  }

  /** Fertig mit [outcome]; bei [RepoOutcome.CHANGED] mit so vielen neu geschriebenen [apps]. */
  data class Done(val outcome: RepoOutcome, val apps: Int = 0) : RepoUpdateStep {
    override val fraction = 1f
  }
}

/** Wie eine Paketquelle aus einem [RepoUpdateRun] kam. */
enum class RepoOutcome {
  /** Es gab Neues, das jetzt gespeichert ist. */
  CHANGED,

  /** Nichts Neues seit dem letzten Mal. */
  UNCHANGED,

  /** Ließ sich nicht laden. */
  FAILED;

  companion object {
    fun of(result: IndexUpdateResult): RepoOutcome =
      when (result) {
        IndexUpdateResult.Processed -> CHANGED
        IndexUpdateResult.Unchanged -> UNCHANGED
        IndexUpdateResult.NotFound,
        is IndexUpdateResult.Error -> FAILED
      }
  }
}
