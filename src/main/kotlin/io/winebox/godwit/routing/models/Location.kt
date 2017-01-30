package io.winebox.godwit.routing.models

import com.graphhopper.jsprit.core.problem.Location
import io.winebox.godwit.routing.utils.LocationRepresentable

/**
 * Created by aj on 1/28/17.
 */

data class Location(
  override val latitude: Double,
  override val longitude: Double
) : LocationRepresentable {
  internal fun transform(): Location {
    return Location.newInstance(latitude, longitude)
  }
}