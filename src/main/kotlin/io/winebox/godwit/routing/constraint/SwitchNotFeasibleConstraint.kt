package io.winebox.godwit.routing.constraint

import com.graphhopper.jsprit.core.algorithm.state.InternalStates
import com.graphhopper.jsprit.core.algorithm.state.StateId
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.problem.constraint.HardRouteConstraint
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext

/**
 * Created by aj on 2/18/17.
 */

class SwitchNotFeasibleConstraint(
  private val stateManager: StateManager,
  private val switchNotFeasibleStateId: StateId
) : HardRouteConstraint {

  override fun fulfilled(insertionContext: JobInsertionContext): Boolean {
    val notFeasible = stateManager.getRouteState(insertionContext.route, insertionContext.newVehicle, switchNotFeasibleStateId, Any::class.java) as? Boolean
    if (notFeasible == null || insertionContext.route.vehicle.vehicleTypeIdentifier == insertionContext.newVehicle.vehicleTypeIdentifier)
      return true
    else
      return !notFeasible
  }

}