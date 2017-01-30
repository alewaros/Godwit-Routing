package io.winebox.godwit.routing.models

import com.graphhopper.jsprit.core.problem.job.Job
import com.graphhopper.jsprit.core.problem.job.Service
import io.winebox.godwit.routing.visit.OneStopVisitRepresentable

/**
 * Created by aj on 1/28/17.
 */

class Task(
  override val id: String,
  override val stop: Stop,
  override val priority: Priority = Visit.Priority.MEDIUM,
  override val type: Collection<String> = listOf()
): Visit(id, priority, type), OneStopVisitRepresentable {

  override fun transform(): Job {
    val serviceBuilder = Service.Builder.newInstance(id)
    serviceBuilder.setPriority(priority.transform())
    type.forEach { serviceBuilder.addRequiredSkill(it) }
    serviceBuilder.setLocation(stop.location.transform())
    serviceBuilder.setTimeWindow(stop.timeWindow.transform())
    serviceBuilder.setServiceTime(stop.duration)
    return serviceBuilder.build()
  }
}