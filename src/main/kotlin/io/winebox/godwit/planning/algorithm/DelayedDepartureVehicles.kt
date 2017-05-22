package io.winebox.godwit.planning.algorithm

import com.graphhopper.jsprit.core.algorithm.listener.AlgorithmEndsListener
import com.graphhopper.jsprit.core.algorithm.state.UpdateActivityTimes
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution
import com.graphhopper.jsprit.core.problem.solution.route.RouteActivityVisitor
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle
import com.graphhopper.jsprit.core.util.Solutions

/**
 * Created by aj on 5/21/17.
 */

class DelayedDepartureVehicles: AlgorithmEndsListener {

  override fun informAlgorithmEnds(problem: VehicleRoutingProblem, solutions: Collection<VehicleRoutingProblemSolution>) {
    val solution = Solutions.bestOf(solutions)
    solution.routes
      .forEach({ route ->
        if (!route.isEmpty) {
          val departureTime = route.departureTime
          val firstActivity = route.activities[0]

          val transportTimeFromDepartureToFirstActivity = problem.transportCosts.getTransportTime(route.start.location, firstActivity.location,
            departureTime, null, route.vehicle)
          val latestDepartureTime = Math.max(departureTime, firstActivity.theoreticalEarliestOperationStartTime - transportTimeFromDepartureToFirstActivity)
          val newDepartureTime = Math.max(latestDepartureTime, route.vehicle.earliestDeparture)

          route.start.setEndTime(newDepartureTime)

          val routeVisitor = RouteActivityVisitor()

          val activityTimesUpdater = UpdateActivityTimes(problem.transportCosts, problem.activityCosts)
          routeVisitor.addActivityVisitor(activityTimesUpdater)

          routeVisitor.visit(route)
        }
      })
  }
}