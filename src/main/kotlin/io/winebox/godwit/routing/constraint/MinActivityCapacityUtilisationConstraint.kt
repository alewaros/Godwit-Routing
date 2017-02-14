package io.winebox.godwit.routing.constraint

import com.graphhopper.jsprit.core.algorithm.state.StateId
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.problem.Capacity
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext
import com.graphhopper.jsprit.core.problem.solution.route.activity.*

/**
 * Created by aj on 2/1/17.
 */

class MinActivityCapacityUtilisationConstraint(
  private val stateManager: StateManager,
  private val pastMinLoadStateId: StateId,
  private val futureMinLoadStateId: StateId
): HardActivityConstraint {

  override fun fulfilled(iFacts: JobInsertionContext, prevAct: TourActivity, newAct: TourActivity, nextAct: TourActivity, prevActDepTime: Double): HardActivityConstraint.ConstraintsStatus {
    if (newAct !is PickupService && newAct !is ServiceActivity && newAct !is DeliverService) {
      return HardActivityConstraint.ConstraintsStatus.FULFILLED
    }



    val newActivityLoad = (newAct as TourActivity).size

    val defaultCapacity = Capacity.Builder.newInstance().build()
    val futureMinLoadForPreviousActivity = stateManager.getActivityState(prevAct, futureMinLoadStateId, Capacity::class.java)
    if (prevAct !is Start && !futureMinLoadForPreviousActivity.isGreaterOrEqual(defaultCapacity)) {
      return HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }

    val pastMinLoadForNextActivity = stateManager.getActivityState(nextAct, pastMinLoadStateId, Capacity::class.java)
    if (nextAct !is End && !pastMinLoadForNextActivity.isGreaterOrEqual(defaultCapacity)) {
      return HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }

    if (iFacts.route.isEmpty && !newActivityLoad.isGreaterOrEqual(defaultCapacity)) {
      return HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

    val newFutureMinLoadForPreviousActivity = Capacity.addup(futureMinLoadForPreviousActivity ?: defaultCapacity, newActivityLoad)
    if (prevAct !is Start && !newFutureMinLoadForPreviousActivity.isGreaterOrEqual(defaultCapacity)) {
      return HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

    val newPastMinLoadForNextActivity = Capacity.addup(pastMinLoadForNextActivity ?: defaultCapacity, newActivityLoad)
    if (nextAct !is End && !newPastMinLoadForNextActivity.isGreaterOrEqual(defaultCapacity)) {
      return HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

    return HardActivityConstraint.ConstraintsStatus.FULFILLED
  }
}