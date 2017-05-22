package io.winebox.godwit.routing.models

/**
 * Created by aj on 5/11/17.
 */

class Route internal constructor(
  val paths: List<Path>
) {

  fun path(index: Int): Path? {
    if (index >= paths.size) {
      return null
    }
    val path = paths[index]
    return path
  }
}