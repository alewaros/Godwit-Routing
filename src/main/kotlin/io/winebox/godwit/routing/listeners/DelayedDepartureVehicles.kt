package io.winebox.godwit.routing.listeners

import com.graphhopper.jsprit.core.algorithm.listener.AlgorithmEndsListener
import com.graphhopper.jsprit.core.algorithm.state.UpdateActivityTimes
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import com.graphhopper.jsprit.core.problem.solution.route.RouteActivityVisitor
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle
import com.graphhopper.jsprit.core.util.Solutions

/**
 * Created by aj on 2/18/17.
 */

class DelayedDepartureVehicles(
  private val departurePolicyByVehicle: Map<Vehicle, Boolean>
) : AlgorithmEndsListener {

  override fun informAlgorithmEnds(problem: VehicleRoutingProblem, solutions: Collection<VehicleRoutingProblemSolution>) {
    val solution = Solutions.bestOf(solutions)
    solution.routes
      .filter { route -> departurePolicyByVehicle.containsKey(route.vehicle) && departurePolicyByVehicle[route.vehicle]!! }
      .forEach { route ->
      if (!route.isEmpty) {
        val earliestDepartureTime = route.departureTime
        val firstActivity = route.activities[0]
        val tpTime_startToFirst = problem.transportCosts.getTransportTime(route.start.location, firstActivity.location,
          earliestDepartureTime, null, route.vehicle)
        val newDepartureTime = Math.max(earliestDepartureTime, firstActivity.theoreticalEarliestOperationStartTime - tpTime_startToFirst)

        route.start.setEndTime(Math.max(newDepartureTime, route.vehicle.getEarliestDeparture()))

        val routeVisitor = RouteActivityVisitor()
        routeVisitor.addActivityVisitor(UpdateActivityTimes(problem.transportCosts, problem.activityCosts))
        routeVisitor.visit(route)
      }
    }
  }
}