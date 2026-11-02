package de.needanapp.myzel.repo

import de.needanapp.myzel.repo.RepoUpdateStep.Checking
import de.needanapp.myzel.repo.RepoUpdateStep.Done
import de.needanapp.myzel.repo.RepoUpdateStep.Loaded
import de.needanapp.myzel.repo.RepoUpdateStep.Loading
import de.needanapp.myzel.repo.RepoUpdateStep.Saving
import de.needanapp.myzel.repo.RepoUpdateStep.Waiting
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.fdroid.index.IndexUpdateResult
import org.junit.Test

internal class RepoUpdateBoardTest {

  private val board = RepoUpdateBoard()

  private val run
    get() = board.run.value!!

  @Test
  fun `a run goes through all phases`() {
    board.start(listOf(1L, 2L))
    assertEquals(Waiting, run.step(1))
    assertEquals(1L, run.current)
    assertEquals(1, run.number)

    board.checking(1)
    assertEquals(Checking, run.step(1))
    // entry.jar hat keine Größe und bleibt beim Prüfen
    board.loading(1, 100, -1)
    assertEquals(Checking, run.step(1))
    board.loading(1, 450, 1000)
    assertEquals(Loading(45), run.step(1))
    board.loaded(1)
    assertEquals(Loaded, run.step(1))
    board.saving(1, 30, 120)
    assertEquals(Saving(30, 120), run.step(1))
    assertEquals(25, (run.step(1) as Saving).percent)
    board.saving(1, 120, 120)
    board.done(1, IndexUpdateResult.Processed)
    assertEquals(Done(RepoOutcome.CHANGED, 120), run.step(1))
    assertEquals(2L, run.current)
    assertEquals(2, run.number)

    board.checking(2)
    board.done(2, IndexUpdateResult.Unchanged)
    assertNull(run.current)
    assertEquals(1, run.count(RepoOutcome.CHANGED))
    assertEquals(1, run.count(RepoOutcome.UNCHANGED))
    assertEquals(1f, run.progress)
    assertFalse(run.isFinished)

    board.finish()
    assertTrue(run.isFinished)
  }

  @Test
  fun `phases only go forward`() {
    board.start(listOf(1L))
    board.loaded(1)
    // F-Droid holt beim Update entry.jar noch einmal
    board.checking(1)
    assertEquals(Loaded, run.step(1))
    board.saving(1, 1, 10)
    board.loaded(1)
    assertEquals(Saving(1, 10), run.step(1))
  }

  @Test
  fun `the same percent is reported once`() {
    board.start(listOf(1L))
    board.loading(1, 101, 1000)
    val before = board.run.value
    board.loading(1, 109, 1000)
    assertSame(before, board.run.value)
    board.saving(1, 1, 1000)
    val saving = board.run.value
    board.saving(1, 5, 1000)
    assertSame(saving, board.run.value)
  }

  @Test
  fun `errors count as failed`() {
    board.start(listOf(1L, 2L))
    board.done(1, IndexUpdateResult.Error(Exception("offline")))
    board.done(2, IndexUpdateResult.NotFound)
    assertEquals(2, run.count(RepoOutcome.FAILED))
  }

  @Test
  fun `nothing changes after the end or for other repos`() {
    board.start(listOf(1L))
    board.checking(7)
    assertEquals(emptyMap(), run.steps)
    board.finish()
    val finished = board.run.value
    board.checking(1)
    board.finish()
    assertSame(finished, board.run.value)
  }

  @Test
  fun `a new phase counts as a change, a new percent does not`() {
    val loading = RepoUpdateRun(listOf(1L), mapOf(1L to Loading(10)), startedAt = 0)
    assertTrue(loading.phaseChangedFrom(null))
    assertFalse(loading.phaseChangedFrom(loading.copy(steps = mapOf(1L to Loading(90)))))
    assertTrue(loading.phaseChangedFrom(loading.copy(steps = mapOf(1L to Checking))))
    assertTrue(loading.phaseChangedFrom(loading.copy(finishedAt = 1)))
  }
}
