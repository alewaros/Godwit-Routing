package io.winebox.godwit.routing.costs

import com.graphhopper.jsprit.core.problem.Location
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts
import com.graphhopper.jsprit.core.problem.cost.TransportDistance
import com.graphhopper.jsprit.core.problem.driver.Driver
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle
import com.graphhopper.jsprit.core.util.Coordinate
import io.winebox.godwit.routing.Godwit

/**
 * Created by aj on 1/28/17.
 */

class GodwitRoutingCosts(
  coordinates: Collection<Coordinate>,
  weighting: Godwit.Weighting
): AbstractForwardVehicleRoutingTransportCosts(), TransportDistance {
  private class CoordinateLocationWrapper(
    val coordinate: Coordinate
  ): io.winebox.godwit.routing.utils.LocationRepresentable {
    override val latitude: Double get() = coordinate.x
    override val longitude: Double get() = coordinate.y
  }

  private val routingCosts: Map<Pair<Coordinate, Coordinate>, Godwit.Path>

  init {
    val routingCosts = mutableMapOf<Pair<Coordinate, Coordinate>, Godwit.Path>()

    // TODO: Chain long paths to reduce number of query requests
    val wrappers = coordinates.map { CoordinateLocationWrapper(it) }
    wrappers
      .flatMap { from -> wrappers.map { to -> Pair(from, to) } }
      .forEach {
        val from = it.first
        val to = it.second

        val path = if (from == to) Godwit.Path(0.0, 0.0) else Godwit.engine.path(listOf(from, to), weighting)
        val key = Pair(from.coordinate, to.coordinate)
        routingCosts.set(key, path)
      }
    this.routingCosts = routingCosts
  }

  // TODO: Handle error on locations without coordinates
  override fun getTransportCost(from: Location, to: Location, departureTime: Double, driver: Driver?, vehicle: Vehicle?): Double {
    val key = Pair(from.coordinate, to.coordinate)
    val distance = routingCosts[key]!!.distance
    val perDistanceUnit = vehicle?.type?.vehicleCostParams?.perDistanceUnit ?: 1.0
    return distance * perDistanceUnit
  }

  override fun getTransportTime(from: Location, to: Location, departureTime: Double, driver: Driver?, vehicle: Vehicle?): Double {
    val key = Pair(from.coordinate, to.coordinate)
    return routingCosts[key]!!.time
  }

  override fun getDistance(from: Location, to: Location, departureTime: Double, vehicle: Vehicle?): Double {
    val key = Pair(from.coordinate, to.coordinate)
    return routingCosts[key]!!.distance
  }
}