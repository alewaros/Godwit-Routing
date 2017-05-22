package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow

/**
 * Created by aj on 5/21/17.
 */

data class Schedule(
  val start: Double,
  val end: Double
) {

  internal fun toTimeWindow(): TimeWindow {
    val timeWindow = TimeWindow.newInstance(start, end)
    return timeWindow
  }
}