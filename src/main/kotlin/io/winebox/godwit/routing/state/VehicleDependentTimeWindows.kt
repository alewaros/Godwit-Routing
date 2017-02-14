package io.winebox.godwit.routing.state

import com.graphhopper.jsprit.core.algorithm.state.InternalStates
import com.graphhopper.jsprit.core.algorithm.state.StateId
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.algorithm.state.StateUpdater
import com.graphhopper.jsprit.core.problem.Location
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute
import com.graphhopper.jsprit.core.problem.solution.route.activity.ReverseActivityVisitor
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle

/**
 * Created by aj on 2/18/17.
 */

class VehicleDependentTimeWindows(
  private val stateManager: StateManager,
  private val transportCosts: VehicleRoutingTransportCosts,
  private val activityCosts: VehicleRoutingActivityCosts,
  private val latestEndStartTimeStateId: StateId,
  private val latestOperationStartTimeStateId: StateId,
  private val switchNotFeasibleStateId: StateId,
  private val departurePolicyByVehicle: Map<Vehicle, Boolean>
) : ReverseActivityVisitor, StateUpdater {

  private data class State(val location: Location, val latestArrivalTime: Double)

  private val vehicles: Collection<Vehicle>
  private lateinit var route: VehicleRoute
  private lateinit var states: Map<Vehicle, State>

  init {
    this.vehicles = departurePolicyByVehicle.keys.distinctBy { it.vehicleTypeIdentifier }
  }

  override fun begin(route: VehicleRoute) {
    this.route = route
    this.states = vehicles.associateBy({ it }, { vehicle ->
      val shouldVehicleDelayDeparture = departurePolicyByVehicle[vehicle]!!

      val latestArrivalTime = if (shouldVehicleDelayDeparture) {
        val earliestDepartureTime = route.departureTime
        val firstActivity = route.activities[0]
        val transportTimeFromStartToFirstActivity = transportCosts.getTransportTime(route.start.location, firstActivity.location, earliestDepartureTime, route.driver, route.vehicle)
        val newDepartureTime = Math.max(earliestDepartureTime, firstActivity.theoreticalEarliestOperationStartTime - transportTimeFromStartToFirstActivity)
        val latestArrivalAtEnd = Math.min(route.end.theoreticalLatestOperationStartTime, newDepartureTime + route.vehicle.latestArrival - route.vehicle.earliestDeparture)
        latestArrivalAtEnd
      } else {
        vehicle.latestArrival
      }
      val location = if (vehicle.isReturnToDepot) vehicle.endLocation else route.end.location
      State(location, latestArrivalTime)
    })
    this.states.forEach {
      stateManager.putRouteState(route, it.key, latestEndStartTimeStateId, it.value.latestArrivalTime)
    }
  }

  override fun visit(activity: TourActivity) {
    states = states.mapValues {
      val vehicle = it.key
      val previousState = it.value
      val backwardsTransportTimeFromCurrentToPreviousActivity = transportCosts.getBackwardTransportTime(
        activity.location, previousState.location, previousState.latestArrivalTime, route.driver, vehicle
      )
      val activityDuration = activityCosts.getActivityDuration(activity, previousState.latestArrivalTime, route.driver, vehicle)
      val potentialLatestArrivalTimeAtCurrentActivity = previousState.latestArrivalTime -
        backwardsTransportTimeFromCurrentToPreviousActivity - activityDuration
      val latestArrivalTime = Math.min(activity.theoreticalLatestOperationStartTime, potentialLatestArrivalTimeAtCurrentActivity)
      State(activity.location, latestArrivalTime)
    }
    states.forEach {
      if (it.value.latestArrivalTime < activity.theoreticalEarliestOperationStartTime) {
        stateManager.putRouteState(route, it.key, switchNotFeasibleStateId, true)
      }
      stateManager.putActivityState(activity, it.key, latestOperationStartTimeStateId, it.value.latestArrivalTime)
    }
  }

  override fun finish() {}
}