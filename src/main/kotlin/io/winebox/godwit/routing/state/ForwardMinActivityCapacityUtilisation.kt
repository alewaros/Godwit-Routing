package io.winebox.godwit.routing.state

import com.graphhopper.jsprit.core.algorithm.state.InternalStates
import com.graphhopper.jsprit.core.algorithm.state.StateId
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.algorithm.state.StateUpdater
import com.graphhopper.jsprit.core.problem.Capacity
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute
import com.graphhopper.jsprit.core.problem.solution.route.activity.ReverseActivityVisitor
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity

/**
 * Created by aj on 2/1/17.
 */

class MinCapacityUtilisationAtActivitiesByLookingForwardInRoute(
  private val stateManager: StateManager,
  private val futureMinLoadStateId: StateId
): StateUpdater, ReverseActivityVisitor {

  private lateinit var route: VehicleRoute
  private lateinit var minLoad: Capacity

  override fun begin(route: VehicleRoute) {
    this.route = route
    this.minLoad = stateManager.getRouteState(route, InternalStates.LOAD_AT_END, Capacity::class.java) ?:
      Capacity.Builder.newInstance().build()
  }

  override fun visit(activity: TourActivity) {
    val currentLoad = stateManager.getActivityState(activity, InternalStates.LOAD, Capacity::class.java) ?:
      Capacity.Builder.newInstance().build()
    val minLoad = Capacity.min(this.minLoad, currentLoad)
    this.minLoad = minLoad
    stateManager.putActivityState(activity, futureMinLoadStateId, minLoad)
  }

  override fun finish() {}
}