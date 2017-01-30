package io.winebox.godwit.routing.visit

import io.winebox.godwit.routing.utils.LocationRepresentable
import io.winebox.godwit.routing.utils.TimeWindowRepresentable

/**
 * Created by aj on 1/28/17.
 */

internal interface StopRepresentable {
  val location: LocationRepresentable
  val timeWindow: TimeWindowRepresentable
  val duration: Double
}