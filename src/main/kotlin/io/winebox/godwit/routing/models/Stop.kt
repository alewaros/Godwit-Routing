package io.winebox.godwit.routing.models

import io.winebox.godwit.routing.visit.StopRepresentable

/**
 * Created by aj on 1/28/17.
 */

data class Stop(
  override val location: Location,
  override val timeWindow: TimeWindow = TimeWindow(),
  override val duration: Double = 0.0
) : StopRepresentable