package io.winebox.godwit.planning

import com.graphhopper.jsprit.core.problem.Location
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts
import com.graphhopper.jsprit.core.problem.cost.TransportDistance
import com.graphhopper.jsprit.core.problem.driver.Driver
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle

/**
 * Created by aj on 5/13/17.
 */

internal class TransportCosts(
  private val getDistance: (fromLocation: Location, toLocation: Location) -> Double,
  private val getTime: (fromLocation: Location, toLocation: Location) -> Double
): AbstractForwardVehicleRoutingTransportCosts(), TransportDistance {

  override fun getTransportCost(from: Location, to: Location, departureTime: Double, driver: Driver?, vehicle: Vehicle?): Double {
    if (vehicle == null) {
      return getDistance(from, to, departureTime, vehicle)
    }

    val distance = vehicle.type.vehicleCostParams.perDistanceUnit * getDistance(from, to, departureTime, vehicle)
    val transporting = vehicle.type.vehicleCostParams.perTransportTimeUnit * getTransportTime(from, to, departureTime, driver, vehicle)
    return distance + transporting
  }

  override fun getTransportTime(from: Location, to: Location, departureTime: Double, driver: Driver?, vehicle: Vehicle?): Double {
    return this.getTime(from, to)
  }

  override fun getDistance(from: Location, to: Location, departureTime: Double, vehicle: Vehicle?): Double {
    return this.getDistance(from, to)
  }
}