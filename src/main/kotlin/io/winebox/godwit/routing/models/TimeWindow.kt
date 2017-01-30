package io.winebox.godwit.routing.models

import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow
import io.winebox.godwit.routing.Settings
import io.winebox.godwit.routing.utils.TimeWindowRepresentable

/**
 * Created by aj on 1/28/17.
 */

data class TimeWindow(
  override val start: Double = 0.0,
  override val end: Double = Settings.MAX_PLANNING_HORIZON
): TimeWindowRepresentable {
  internal fun transform(): TimeWindow {
    return TimeWindow.newInstance(start, end)
  }
}