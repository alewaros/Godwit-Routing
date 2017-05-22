package io.winebox.godwit.planning.models

/**
 * Created by aj on 5/11/17.
 */

data class Stop(
  val place: Place,
  val schedule: Schedule,
  val duration: Double
)