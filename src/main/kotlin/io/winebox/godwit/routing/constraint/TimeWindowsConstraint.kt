package io.winebox.godwit.routing.constraint

import com.graphhopper.jsprit.core.algorithm.state.StateId
import com.graphhopper.jsprit.core.algorithm.state.StateManager
import com.graphhopper.jsprit.core.problem.Location
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext
import com.graphhopper.jsprit.core.problem.solution.route.activity.End
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.problem.solution.route.state.RouteAndActivityStateGetter

/**
 * Created by aj on 2/18/17.
 */

class TimeWindowsConstraint(
  private val stateManager: StateManager,
  private val routingCosts: VehicleRoutingTransportCosts,
  private val activityCosts: VehicleRoutingActivityCosts,
  private val latestEndStartTimeStateId: StateId,
  private val latestOperationStartTimeStateId: StateId
) : HardActivityConstraint {

  override fun fulfilled(iFacts: JobInsertionContext, prevAct: TourActivity, newAct: TourActivity, nextAct: TourActivity, prevActDepTime: Double): HardActivityConstraint.ConstraintsStatus {
    val latestVehicleArrival = stateManager.getRouteState(iFacts.route, iFacts.newVehicle, latestEndStartTimeStateId, Number::class.java)?.toDouble() ?: iFacts.newVehicle.latestArrival
    val latestArrivalAtNextActivity = if (nextAct is End) {
      latestVehicleArrival
    } else {
      stateManager.getActivityState(nextAct, iFacts.newVehicle, latestOperationStartTimeStateId, Number::class.java)?.toDouble() ?: nextAct.theoreticalLatestOperationStartTime
    }

    val nextActivityLocation = if (nextAct is End) {
      if (iFacts.newVehicle.isReturnToDepot) iFacts.newVehicle.endLocation else newAct.location
    } else {
      nextAct.location
    }

    if (latestVehicleArrival < prevAct.theoreticalEarliestOperationStartTime ||
      latestVehicleArrival < newAct.theoreticalEarliestOperationStartTime ||
      latestVehicleArrival < nextAct.theoreticalEarliestOperationStartTime) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }

    if (newAct.theoreticalLatestOperationStartTime < prevAct.theoreticalEarliestOperationStartTime) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }

    val arrivalTimeAtNextActivityFromPreviousActivity = prevActDepTime + routingCosts.getTransportTime(prevAct.location, nextActivityLocation, prevActDepTime, iFacts.newDriver, iFacts.newVehicle)
    if (arrivalTimeAtNextActivityFromPreviousActivity > latestArrivalAtNextActivity) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }

    if (newAct.theoreticalEarliestOperationStartTime > nextAct.theoreticalLatestOperationStartTime) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

    val arrivalTimeAtNewActivityFromPreviousActivity = prevActDepTime + routingCosts.getTransportTime(prevAct.location, newAct.location, prevActDepTime, iFacts.newDriver, iFacts.newVehicle)
    val departureTimeFromNewActivity = Math.max(arrivalTimeAtNewActivityFromPreviousActivity, newAct.theoreticalEarliestOperationStartTime) + activityCosts.getActivityDuration(newAct, arrivalTimeAtNewActivityFromPreviousActivity, iFacts.newDriver, iFacts.newVehicle)
    val latestArrivalTimeAtNewActivity = Math.min(newAct.theoreticalLatestOperationStartTime,
      latestArrivalAtNextActivity -
        routingCosts.getBackwardTransportTime(newAct.location, nextActivityLocation, latestArrivalAtNextActivity, iFacts.newDriver, iFacts.newVehicle)
        - activityCosts.getActivityDuration(newAct, arrivalTimeAtNewActivityFromPreviousActivity, iFacts.newDriver, iFacts.newVehicle)
    )

    if (arrivalTimeAtNewActivityFromPreviousActivity > latestArrivalTimeAtNewActivity) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

    if (nextAct is End) {
      if (!iFacts.newVehicle.isReturnToDepot) {
        return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.FULFILLED
      }
    }

    val arrivalTimeAtNextActivityFromNewActivity = departureTimeFromNewActivity + routingCosts.getTransportTime(newAct.location, nextActivityLocation, departureTimeFromNewActivity, iFacts.newDriver, iFacts.newVehicle)

    // TODO: Is this necessary?
    if (arrivalTimeAtNextActivityFromNewActivity > latestVehicleArrival) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
    }
    if (arrivalTimeAtNextActivityFromNewActivity > latestArrivalAtNextActivity) {
      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
    }

//    var latestArrTimeAtNextAct: Double?
//    var nextActLocation: Location
//    if (nextAct is End) {
//      latestArrTimeAtNextAct = latestVehicleArrival
//      nextActLocation = iFacts.newVehicle.endLocation
//      if (!iFacts.newVehicle.isReturnToDepot) {
//        nextActLocation = newAct.location
//      }
//    } else {
//      latestArrTimeAtNextAct = stateManager.getActivityState(nextAct, iFacts.newVehicle, latestOperationStartTimeStateId, Number::class.java)?.toDouble()
//      if (latestArrTimeAtNextAct == null) {//otherwise set it to theoretical_latest_operation_startTime
//        latestArrTimeAtNextAct = nextAct.theoreticalLatestOperationStartTime
//      }
//      nextActLocation = nextAct.location
//    }
//
//    /*
//             * if latest arrival of vehicle (at its end) is smaller than earliest operation start times of activities,
//			 * then vehicle can never conduct activities.
//			 *
//			 *     |--- vehicle's operation time ---|
//			 *                        					|--- prevAct or newAct or nextAct ---|
//			 */
//    val newAct_theoreticalEarliestOperationStartTime = newAct.theoreticalEarliestOperationStartTime
//
//    if (latestVehicleArrival < prevAct.theoreticalEarliestOperationStartTime ||
//      latestVehicleArrival < newAct_theoreticalEarliestOperationStartTime ||
//      latestVehicleArrival < nextAct.theoreticalEarliestOperationStartTime) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
//    }
//    /*
//             * if the latest operation start-time of new activity is smaller than the earliest start of prev. activity,
//			 * then
//			 *
//			 *                    |--- prevAct ---|
//			 *  |--- newAct ---|
//			 */
//    if (newAct.theoreticalLatestOperationStartTime < prevAct.theoreticalEarliestOperationStartTime) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
//    }
//
//    /*
//             *  |--- prevAct ---|
//			 *                                          |- earliest arrival of vehicle
//			 *                       |--- nextAct ---|
//			 */
//    val arrTimeAtNextOnDirectRouteWithNewVehicle = prevActDepTime + routingCosts.getTransportTime(prevAct.location, nextActLocation, prevActDepTime, iFacts.newDriver, iFacts.newVehicle)
//    if (arrTimeAtNextOnDirectRouteWithNewVehicle > latestArrTimeAtNextAct) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED_BREAK
//    }
//
//    /*
//             *                     |--- newAct ---|
//			 *  |--- nextAct ---|
//			 */
//    if (newAct.theoreticalEarliestOperationStartTime > nextAct.theoreticalLatestOperationStartTime) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
//    }
//    //			log.info("check insertion of " + newAct + " between " + prevAct + " and " + nextAct + ". prevActDepTime=" + prevActDepTime);
//    val arrTimeAtNewAct = prevActDepTime + routingCosts.getTransportTime(prevAct.location, newAct.location, prevActDepTime, iFacts.newDriver, iFacts.newVehicle)
//    val endTimeAtNewAct = Math.max(arrTimeAtNewAct, newAct.theoreticalEarliestOperationStartTime) + activityCosts.getActivityDuration(newAct, arrTimeAtNewAct, iFacts.newDriver, iFacts.newVehicle)
//    val latestArrTimeAtNewAct = Math.min(newAct.theoreticalLatestOperationStartTime,
//      latestArrTimeAtNextAct -
//        routingCosts.getBackwardTransportTime(newAct.location, nextActLocation, latestArrTimeAtNextAct, iFacts.newDriver, iFacts.newVehicle)
//        - activityCosts.getActivityDuration(newAct, arrTimeAtNewAct, iFacts.newDriver, iFacts.newVehicle)
//    )
//
//    /*
//             *  |--- prevAct ---|
//			 *                       		                 |--- vehicle's arrival @newAct
//			 *        latest arrival of vehicle @newAct ---|
//			 */
//    if (arrTimeAtNewAct > latestArrTimeAtNewAct) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
//    }
//
//    if (nextAct is End) {
//      if (!iFacts.newVehicle.isReturnToDepot) {
//        return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.FULFILLED
//      }
//    }
//    //			log.info(newAct + " arrTime=" + arrTimeAtNewAct);
//
//    val arrTimeAtNextAct = endTimeAtNewAct + routingCosts.getTransportTime(newAct.location, nextActLocation, endTimeAtNewAct, iFacts.newDriver, iFacts.newVehicle)
//
//    /*
//             *  |--- newAct ---|
//			 *                       		                 |--- vehicle's arrival @nextAct
//			 *        latest arrival of vehicle @nextAct ---|
//			 */
//    if (arrTimeAtNextAct > latestArrTimeAtNextAct) {
//      return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED
//    }
    return com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus.FULFILLED
  }
}

