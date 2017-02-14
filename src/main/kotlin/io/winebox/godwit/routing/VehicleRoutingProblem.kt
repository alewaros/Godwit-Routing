package io.winebox.godwit.routing

import com.graphhopper.jsprit.core.algorithm.box.Jsprit
import com.graphhopper.jsprit.core.algorithm.state.*
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem
import com.graphhopper.jsprit.core.problem.constraint.*
import com.graphhopper.jsprit.core.problem.solution.route.activity.End
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.util.ActivityTimeTracker
import com.graphhopper.jsprit.core.util.Solutions
import io.winebox.godwit.routing.constraint.MinActivityCapacityUtilisationConstraint
import io.winebox.godwit.routing.constraint.SwitchNotFeasibleConstraint
import io.winebox.godwit.routing.constraint.TimeWindowsConstraint
import io.winebox.godwit.routing.costs.GodwitRoutingCosts
import io.winebox.godwit.routing.listeners.DelayedDepartureVehicles
import io.winebox.godwit.routing.models.Task
import io.winebox.godwit.routing.models.Transport
import io.winebox.godwit.routing.models.Visit
import io.winebox.godwit.routing.state.*
import java.security.InvalidParameterException

/**
 * Created by aj on 1/28/17.
 */

object VehicleRoutingProblem {

  data class Options(
    val weighting: Godwit.Weighting = Godwit.Weighting.FASTEST,
    val traffic: Godwit.Traffic = Godwit.Traffic.NONE
  )

  data class Solution internal constructor(
    val unassignedVisits: Set<String>,
    val routes: Set<Route>,
    val score: Double
  ) {

    data class Route internal constructor(
      val assignedTransport: String,
      val stops: List<Stop>
    ) {

      data class Stop internal constructor(
        val type: Type,
        val visit: String?,
        val start: Double?,
        val end: Double?
      ) {

        enum class Type {
          START, STOP, END;

          companion object {
            fun from(value: String): Type {
              return when (value) {
                "start" -> START
                "stop" -> STOP
                "end" -> END
                else -> throw InvalidParameterException("Route weighting can not be inferred from \"$value\".")
              }
            }

            internal fun fromActivityName(name: String): Type {
              return when (name) {
                "start" -> START
                "service", "pickup", "delivery" -> STOP
                "end" -> END
                else -> throw InvalidParameterException("Activity type can not be inferred from \"$name\".")
              }
            }
          }

          val value: String get() {
            return when (this) {
              START -> "start"
              STOP -> "stop"
              END -> "end"
            }
          }
        }
      }
    }
  }

  fun solve(visits: Collection<Task>, fleet: Collection<Transport>, options: Options = Options()): Solution {
    val visitsByJob = visits.associateBy(Visit::transform)
    val jobs = visitsByJob.keys

    val fleetByVehicle = fleet.associateBy(Transport::transform)
    val vehicles = fleetByVehicle.keys

    val problemHandlesOpenEndedRoutes = fleet.any { transport -> transport.endLocation == null }
    val problemHandlesDelayedDepartures = fleet.any { transport -> transport.shouldDelayDeparture }
    val problemHandlesTimeWindows = visits.any { visit -> visit.stop.timeWindow.start > 0 || visit.stop.timeWindow.end < Settings.MAX_PLANNING_HORIZON } ||
      fleet.any { transport -> transport.shift.start > 0 || transport.shift.end < Settings.MAX_PLANNING_HORIZON } ||
      problemHandlesDelayedDepartures

    val problemHandlesSkills = visits.any { visit -> visit.type.isNotEmpty() }
    val problemHandlesLoads = visits.any { visit -> visit.load != 0 }
    val problemHandlesDropoffs = visits.any { visit -> visit.load < 0 }
    val problemHandlesVehicleMaxDistances = fleet.any { transport -> transport.maxDistance != null }

    val vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
      .addAllJobs(jobs)
      .addAllVehicles(vehicles)

    val routingCosts = GodwitRoutingCosts(vrpBuilder.locationMap.values, options.weighting, options.traffic)
    val vrp = vrpBuilder
      .setRoutingCost(routingCosts)
      .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
      .build()

    val stateManager = StateManager(vrp)
    val stateUpdaters = mutableListOf<StateUpdater>()
    val constraintManager = ConstraintManager(vrp, stateManager)

    val switchNotFeasibleStateId = stateManager.createStateId("godwit-switch_not_feasible")
    val switchNotFeasibleConstraint = SwitchNotFeasibleConstraint(stateManager, switchNotFeasibleStateId)
    constraintManager.addConstraint(switchNotFeasibleConstraint)

    if (problemHandlesOpenEndedRoutes) {
      val openRouteEndUpdater = UpdateEndLocationIfRouteIsOpen()
      stateUpdaters.add(openRouteEndUpdater)
    }

    if (problemHandlesTimeWindows) {
      val departurePolicyByVehicle = fleetByVehicle
        .mapValues { it.value.shouldDelayDeparture }
      val latestEndStartTimeStateId = stateManager.createStateId("godwit-latest_end_start_time")
      val latestOperationStartTimeStateId = stateManager.createStateId("godwit-latest_operation_start_time")
      val timeWindowsUpdater = VehicleDependentTimeWindows(stateManager, routingCosts, vrp.activityCosts, latestEndStartTimeStateId, latestOperationStartTimeStateId, switchNotFeasibleStateId, departurePolicyByVehicle)
      stateUpdaters.add(timeWindowsUpdater)

      constraintManager.addConstraint(TimeWindowsConstraint(stateManager, routingCosts, vrp.activityCosts, latestEndStartTimeStateId, latestOperationStartTimeStateId), ConstraintManager.Priority.HIGH)
    }

    if (problemHandlesSkills) {
      stateManager.updateSkillStates()
      constraintManager.addSkillsConstraint()
    }

    if (problemHandlesLoads) {
      stateManager.updateLoadStates()
      constraintManager.addLoadConstraint()

      if (problemHandlesDropoffs) {
        val pastMinLoadStateId = stateManager.createStateId("godwit-past_min_load")
        val futureMinLoadStateId = stateManager.createStateId("godwit-future_min_load")
        val backwardMinActivityCapacityUtilisationUpdater = BackwardMinActivityCapacityUtilisation(stateManager, pastMinLoadStateId)
        val forwardMinActivityCapacityUtilisationUpdater = ForwardMinActivityCapacityUtilisation(stateManager, futureMinLoadStateId)
        stateUpdaters.add(backwardMinActivityCapacityUtilisationUpdater)
        stateUpdaters.add(forwardMinActivityCapacityUtilisationUpdater)

        val minActivityCapacityUtilisationConstraint = MinActivityCapacityUtilisationConstraint(stateManager, pastMinLoadStateId, futureMinLoadStateId)
        constraintManager.addConstraint(minActivityCapacityUtilisationConstraint, ConstraintManager.Priority.HIGH)
      }
    }

    if (problemHandlesVehicleMaxDistances) {
      val transportDistance = routingCosts
      val maxDistancesByVehicle = fleetByVehicle
        .filterValues { transport -> transport.maxDistance != null }
        .mapValues { it.value.maxDistance!! }
      val maxDistanceConstrainedVehicles = maxDistancesByVehicle.keys
      val distanceInRouteStateId = stateManager.createStateId("distance_in_route")
      val vehicleDependentTraveledDistanceUpdater = VehicleDependentTraveledDistance(transportDistance, stateManager, distanceInRouteStateId, maxDistanceConstrainedVehicles)
      stateUpdaters.add(vehicleDependentTraveledDistanceUpdater)

      val maxVehicleDistanceConstraint = MaxDistanceConstraint(stateManager, distanceInRouteStateId, transportDistance, maxDistancesByVehicle)
      constraintManager.addConstraint(maxVehicleDistanceConstraint, ConstraintManager.Priority.LOW)
    }

    stateUpdaters.add(UpdateActivityTimes(routingCosts, ActivityTimeTracker.ActivityPolicy.AS_SOON_AS_TIME_WINDOW_OPENS, vrp.activityCosts))
    stateUpdaters.add(UpdateVariableCosts(vrp.activityCosts, routingCosts, stateManager))
    stateUpdaters.add(UpdateFutureWaitingTimes(stateManager, routingCosts))

    stateManager.addAllStateUpdater(stateUpdaters)

    val vra = Jsprit.Builder.newInstance(vrp)
      .addCoreStateAndConstraintStuff(false)
      .setStateAndConstraintManager(stateManager, constraintManager)
      .buildAlgorithm()

    if (problemHandlesDelayedDepartures) {
      val departurePolicyByVehicle = fleetByVehicle
        .mapValues { it.value.shouldDelayDeparture }
      vra.addListener(DelayedDepartureVehicles(departurePolicyByVehicle))
    }

    val solutions = vra.searchSolutions()
    val bestSolution = Solutions.bestOf(solutions)

    val routes = bestSolution.routes.map { route ->
      val activities = listOf(route.start) + route.activities + listOf(route.end)
      val stops = activities.map { activity ->
        val type = Solution.Route.Stop.Type.fromActivityName(activity.name)
        val visit = if (activity is TourActivity.JobActivity) activity.job.id else null
        val start = if (activity !is Start) activity.arrTime else null
        val end = if (activity !is End) activity.endTime else null
        Solution.Route.Stop(type, visit, start, end)
      }
      Solution.Route(route.vehicle.id, stops)
    }
    val unassignedVisits = bestSolution.unassignedJobs.map { job -> job.id }
    return Solution(unassignedVisits.toSet(), routes.toSet(), bestSolution.cost)
  }
}