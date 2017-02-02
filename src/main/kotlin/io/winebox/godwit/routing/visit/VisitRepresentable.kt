package io.winebox.godwit.routing.visit

import com.graphhopper.jsprit.core.problem.job.Job
import io.winebox.godwit.routing.utils.Identifiable

/**
 * Created by aj on 1/28/17.
 */

interface VisitRepresentable: Identifiable {
  val priority: Priority
  val type: Collection<String>
  val load: Int

  interface Priority
}