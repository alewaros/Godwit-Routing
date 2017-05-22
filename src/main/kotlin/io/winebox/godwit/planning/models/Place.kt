package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.Location
import com.graphhopper.jsprit.core.util.Coordinate

/**
 * Created by aj on 5/11/17.
 */

data class Place(
  val latitude: Double,
  val longitude: Double
) {

  internal fun toLocation(): Location {
    val locationBuilder = Location.Builder.newInstance()

    val coordinate = Coordinate.newInstance(latitude, longitude)
    locationBuilder.setCoordinate(coordinate)

    val location = locationBuilder.build()
    return location
  }
}