package io.winebox.godwit.routing.visit

import com.graphhopper.jsprit.core.problem.job.Service

/**
 * Created by aj on 1/28/17.
 */

internal interface OneStopVisitRepresentable : VisitRepresentable {
  val stop: StopRepresentable
}