package io.winebox.godwit.planning

import com.graphhopper.jsprit.core.algorithm.box.Jsprit
import com.graphhopper.jsprit.core.algorithm.state.*
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager
import com.graphhopper.jsprit.core.problem.constraint.MaxDistanceConstraint
import com.graphhopper.jsprit.core.problem.constraint.SwitchNotFeasible
import com.graphhopper.jsprit.core.problem.cost.WaitingTimeCosts
import com.graphhopper.jsprit.core.problem.solution.route.activity.End
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.util.ActivityTimeTracker
import com.graphhopper.jsprit.core.util.CrowFlyCosts
import com.graphhopper.jsprit.core.util.Solutions
import io.winebox.godwit.planning.algorithm.DelayedDepartureVehicles
import io.winebox.godwit.planning.models.Todo
import io.winebox.godwit.planning.models.Transport
import java.security.InvalidParameterException

/**
 * Created by aj on 5/11/17.
 */

object VehicleRoutingProblem {

  data class Solution internal constructor(
    val unassignedTodos: Set<String>,
    val routes: Set<Route>
  ) {

    data class Route internal constructor(
      val assignedTransport: String,
      val stops: List<Stop>
    ) {

      data class Stop internal constructor(
        val type: Type,
        val todo: String?,
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
                else -> throw InvalidParameterException("Activity type can not be inferred from \"$value\".")
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

          override fun toString(): String {
            return value
          }
        }
      }
    }
  }

  fun <T: Todo>solve(todos: Collection<T>, fleet: Collection<Transport>): Solution {
    val todosByJob = todos.associateBy({ todo -> todo.toJob() })
    val jobs = todosByJob.keys

    val fleetByVehicle = fleet.associateBy({ transport -> transport.toVehicle() })
    val vehicles = fleetByVehicle.keys

    val vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
      .addAllJobs(jobs)
      .addAllVehicles(vehicles)

    val locations = vrpBuilder.locations

    val waitingCosts = WaitingTimeCosts()
    val crowFlyCosts = CrowFlyCosts(locations)

    val activityCosts = ActivityCosts(
      getDuration = { activity ->
        waitingCosts.getActivityDuration(activity, 0.0, null, null)
      }
    )

    val transportCosts = TransportCosts(
      getDistance = { fromLocation, toLocation ->
        crowFlyCosts.getDistance(fromLocation, toLocation, 0.0, null)
      },
      getTime = { fromLocation, toLocation ->
        crowFlyCosts.getTransportTime(fromLocation, toLocation, 0.0, null, null)
      }
    )

    val vrp = vrpBuilder
      .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
      .setActivityCosts(activityCosts)
      .setRoutingCost(transportCosts)
      .build()

    val stateManager = StateManager(vrp)
    val constraintManager = ConstraintManager(vrp, stateManager)

    val problemHandlesOpenRoutes = fleet
      .any({ transport -> transport.arrival == null })
    val problemHandlesMaxDistances = fleet
      .any({ transport -> transport.maxDistance != null })
    val problemHandlesSkills = todos
      .any({ todo -> todo.type.isNotEmpty() })
    val problemHandlesLoads = todos
      .any({ todo -> todo.load != 0 })

    val switchNotFeasibleConstraint = SwitchNotFeasible(stateManager)
    constraintManager.addConstraint(switchNotFeasibleConstraint)

    val activityTimesUpdater = UpdateActivityTimes(transportCosts, ActivityTimeTracker.ActivityPolicy.AS_SOON_AS_TIME_WINDOW_OPENS, activityCosts)
    stateManager.addStateUpdater(activityTimesUpdater)

    val activityCostsUpdater = UpdateVariableCosts(activityCosts, transportCosts, stateManager)
    stateManager.addStateUpdater(activityCostsUpdater)

    val futureWaitingTimesUpdater = UpdateFutureWaitingTimes(stateManager, transportCosts)
    stateManager.addStateUpdater(futureWaitingTimesUpdater)

    val vehicleTimeWindowsUpdater = UpdateVehicleDependentPracticalTimeWindows(stateManager, transportCosts, activityCosts)
    stateManager.addStateUpdater(vehicleTimeWindowsUpdater)
    constraintManager.addTimeWindowConstraint()

    if (problemHandlesOpenRoutes) {
      val openRouteEndLocationUpdater = UpdateEndLocationIfRouteIsOpen()
      stateManager.addStateUpdater(openRouteEndLocationUpdater)
    }

    if (problemHandlesMaxDistances) {
      val transportDistance = transportCosts
      val maxDistancesByVehicle = fleetByVehicle
        .filterValues({ transport -> transport.maxDistance != null })
        .mapValues({ (_, transport) -> transport.maxDistance!! })
      val maxDistanceConstrainedVehicles = maxDistancesByVehicle.keys

      val distanceInRouteStateId = stateManager.createStateId("distance_in_route")

      val traveledVehicleDistanceUpdater = VehicleDependentTraveledDistance(transportDistance, stateManager, distanceInRouteStateId, maxDistanceConstrainedVehicles)
      stateManager.addStateUpdater(traveledVehicleDistanceUpdater)

      val maxDistanceConstraint = MaxDistanceConstraint(stateManager, distanceInRouteStateId, transportDistance, maxDistancesByVehicle)
      constraintManager.addConstraint(maxDistanceConstraint, ConstraintManager.Priority.LOW)
    }

    if (problemHandlesSkills) {
      stateManager.updateSkillStates()
      constraintManager.addSkillsConstraint()
    }

    if (problemHandlesLoads) {
      stateManager.updateLoadStates()
      constraintManager.addLoadConstraint()
    }

    val vra = Jsprit.Builder.newInstance(vrp)
      .addCoreStateAndConstraintStuff(false)
      .setStateAndConstraintManager(stateManager, constraintManager)
      .buildAlgorithm()

    val delayedDepartureVehicles = DelayedDepartureVehicles()
    vra.addListener(delayedDepartureVehicles)

    val solutions = vra.searchSolutions()
    val bestSolution = Solutions.bestOf(solutions)

    val unassignedTodos = bestSolution.unassignedJobs
      .map({ job -> job.id })
    val routes = bestSolution.routes
      .map({ route ->
        val activities = listOf(route.start) + route.activities + listOf(route.end)
        val stops = activities
          .map({ activity ->
            val type = Solution.Route.Stop.Type.fromActivityName(activity.name)
            val todo = if (activity is TourActivity.JobActivity) activity.job.id else null
            val start = if (activity !is Start) {
              if (activity !is End) {
                activity.endTime - activity.operationTime
              } else {
                activity.arrTime
              }
            } else null
            val end = if (activity !is End) activity.endTime else null
            val stop = Solution.Route.Stop(type, todo, start, end)
            stop
          })

        val route = Solution.Route(route.vehicle.id, stops)
        route
      })
    return Solution(unassignedTodos.toSet(), routes.toSet())
  }
}