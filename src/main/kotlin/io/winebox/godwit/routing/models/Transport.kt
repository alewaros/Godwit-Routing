package io.winebox.godwit.routing.models

import com.graphhopper.jsprit.core.problem.vehicle.Vehicle
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl
import io.winebox.godwit.routing.fleet.TransportRepresentable

/**
 * Created by aj on 1/28/17.
 */

class Transport(
  override val id: String,
  override val startLocation: Location,
  override val endLocation: Location? = null,
  override val shift: TimeWindow = TimeWindow(),
  override val type: Collection<String> = listOf(),
  override val maxDistance: Double? = null
): TransportRepresentable {
  internal fun transform(): Vehicle {
    val vehicleBuilder = VehicleImpl.Builder.newInstance(id)
    vehicleBuilder.setStartLocation(startLocation.transform())
    vehicleBuilder.setEndLocation(endLocation?.transform())
    vehicleBuilder.setReturnToDepot(endLocation != null)
    vehicleBuilder.setEarliestStart(shift.start)
    vehicleBuilder.setLatestArrival(shift.end)
    type.forEach { vehicleBuilder.addSkill(it) }
    return vehicleBuilder.build()
  }
}