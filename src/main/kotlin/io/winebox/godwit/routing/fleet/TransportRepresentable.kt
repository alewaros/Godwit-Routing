package io.winebox.godwit.routing.fleet

import io.winebox.godwit.routing.utils.*

/**
 * Created by aj on 1/28/17.
 */

interface TransportRepresentable : Identifiable {
  val startLocation: LocationRepresentable
  val endLocation: LocationRepresentable?
  val shift: TimeWindowRepresentable
  val type: Collection<String>
  val capacity: Int
  val maxDistance: Double?
}