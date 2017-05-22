package io.winebox.godwit.routing.models

/**
 * Created by aj on 5/10/17.
 */

class Matrix internal constructor(
  val paths: Map<Pair<Coordinate, Coordinate>, Path>
) {

  fun path(origin: Coordinate, destination: Coordinate): Path? {
    val key = Pair(origin, destination)
    val path = paths[key]
    return path
  }
}