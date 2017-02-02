package io.winebox.godwit.routing

import com.graphhopper.jsprit.core.algorithm.box.Jsprit
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.algorithm.state.StateUpdater
import com.graphhopper.jsprit.core.algorithm.state.VehicleDependentTraveledDistance
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem
import com.graphhopper.jsprit.core.problem.constraint.Constraint
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager
import com.graphhopper.jsprit.core.problem.constraint.MaxDistanceConstraint
import com.graphhopper.jsprit.core.problem.solution.route.activity.End
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.util.Solutions
import io.winebox.godwit.routing.constraint.MinActivityCapacityUtilisationConstraint
import io.winebox.godwit.routing.costs.GodwitRoutingCosts
import io.winebox.godwit.routing.models.Task
import io.winebox.godwit.routing.models.Transport
import io.winebox.godwit.routing.models.Visit
import io.winebox.godwit.routing.state.BackwardMinActivityCapacityUtilisation
import io.winebox.godwit.routing.state.ForwardMinActivityCapacityUtilisation
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


            internal fun fromActivityName(name: String): Type {
              return when (name) {
                "start" -> START
                "service", "pickup", "delivery" -> STOP
                "end" -> END
                else -> throw InvalidParameterException("Activity type can not be inferred from \"$name\".")
              }
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
    val constraints = mutableListOf<Constraint>()

    if (problemHandlesDropoffs) {
      val pastMinLoadStateId = stateManager.createStateId("past_min_load")
      val futureMinLoadStateId = stateManager.createStateId("future_min_load")
      val backwardMinActivityCapacityUtilisationUpdater = BackwardMinActivityCapacityUtilisation(stateManager, pastMinLoadStateId)
      val forwardMinActivityCapacityUtilisationUpdater = ForwardMinActivityCapacityUtilisation(stateManager, futureMinLoadStateId)
      stateUpdaters.add(backwardMinActivityCapacityUtilisationUpdater)
      stateUpdaters.add(forwardMinActivityCapacityUtilisationUpdater)

      val minActivityCapacityUtilisationConstraint = MinActivityCapacityUtilisationConstraint(stateManager, pastMinLoadStateId, futureMinLoadStateId)
      constraints.add(minActivityCapacityUtilisationConstraint)
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
      constraints.add(maxVehicleDistanceConstraint)
    }

    stateManager.addAllStateUpdater(stateUpdaters)
    val constraintManager = ConstraintManager(vrp, stateManager, constraints)

    val vra = Jsprit.Builder.newInstance(vrp)
      .setStateAndConstraintManager(stateManager, constraintManager)
      .buildAlgorithm()

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