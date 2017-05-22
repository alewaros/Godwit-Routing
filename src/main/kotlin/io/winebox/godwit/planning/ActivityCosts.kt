package io.winebox.godwit.planning

import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts
import com.graphhopper.jsprit.core.problem.driver.Driver
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle

/**
 * Created by aj on 5/13/17.
 */

internal class ActivityCosts(
  private val getDuration: (activity: TourActivity) -> Double
): VehicleRoutingActivityCosts {

  override fun getActivityCost(tourAct: TourActivity, arrivalTime: Double, driver: Driver, vehicle: Vehicle?): Double {
    if (vehicle == null) {
      return 0.0
    }

    val waiting = vehicle.type.vehicleCostParams.perWaitingTimeUnit * Math.max(0.0, tourAct.theoreticalEarliestOperationStartTime - arrivalTime)
    val servicing = vehicle.type.vehicleCostParams.perServiceTimeUnit * getActivityDuration(tourAct, arrivalTime, driver, vehicle)
    return waiting + servicing
  }

  override fun getActivityDuration(tourAct: TourActivity, arrivalTime: Double, driver: Driver, vehicle: Vehicle): Double {
    return getDuration(tourAct)
  }
}