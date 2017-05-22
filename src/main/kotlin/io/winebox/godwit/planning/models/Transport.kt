package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.vehicle.Vehicle
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl
import java.util.*

/**
 * Created by aj on 5/11/17.
 */

class Transport(
  val id: String,
  val shift: Schedule,
  val departure: Place,
  val arrival: Place? = null,
  val type: Set<String> = setOf(),
  val capacity: Int = 0,
  val maxDistance: Double? = null
) {

  internal fun toVehicle(): Vehicle {
    val vehicleBuilder = VehicleImpl.Builder.newInstance(id)

    vehicleBuilder.setEarliestStart(shift.start)
    vehicleBuilder.setLatestArrival(shift.end)

    vehicleBuilder.setStartLocation(departure.toLocation())
    vehicleBuilder.setEndLocation(arrival?.toLocation())
    vehicleBuilder.setReturnToDepot(arrival != null)

    type.forEach({ skill -> vehicleBuilder.addSkill(skill) })

    val vehicleTypeId = UUID.randomUUID().toString()
    val vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance(vehicleTypeId)
    vehicleTypeBuilder.addCapacityDimension(0, capacity)

    val vehicleType = vehicleTypeBuilder.build()
    vehicleBuilder.setType(vehicleType)

    val vehicle = vehicleBuilder.build()
    return vehicle
  }
}