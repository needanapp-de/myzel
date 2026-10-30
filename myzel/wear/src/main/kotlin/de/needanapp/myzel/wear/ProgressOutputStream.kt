package de.needanapp.myzel.wear

import java.io.OutputStream

/**
 * Schreibt nach [out] und meldet dabei, wie viel von [size] Bytes schon da ist, in ganzen Prozent
 * und nur, wenn sich die Zahl ändert.
 */
class ProgressOutputStream(
  private val out: OutputStream,
  private val size: Long,
  private val onPercent: (Int) -> Unit,
) : OutputStream() {
  private var written = 0L
  private var percent = -1

  override fun write(b: Int) {
    out.write(b)
    advance(1)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    out.write(b, off, len)
    advance(len.toLong())
  }

  override fun flush() = out.flush()

  override fun close() = out.close()

  private fun advance(bytes: Long) {
    written += bytes
    val now = if (size > 0) (written * 100 / size).toInt().coerceAtMost(100) else 100
    if (now != percent) {
      percent = now
      onPercent(now)
    }
  }
}
