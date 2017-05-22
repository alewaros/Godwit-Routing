package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.Capacity
import com.graphhopper.jsprit.core.problem.Skills
import com.graphhopper.jsprit.core.problem.job.Job
import com.graphhopper.jsprit.core.problem.job.Service

/**
 * Created by aj on 5/11/17.
 */

class Chore(
  id: String,
  val stop: Stop,
  priority: Priority = Todo.Priority.MEDIUM,
  type: Set<String> = setOf(),
  load: Int = 0
): Todo(id, priority, type, load) {

  override fun toJob(): Job {
    val serviceBuilder = Service.Builder.newInstance(id)

    serviceBuilder.setPriority(priority.toJobPriority())

    serviceBuilder.setLocation(stop.place.toLocation())
    serviceBuilder.addTimeWindow(stop.schedule.toTimeWindow())
    serviceBuilder.setServiceTime(stop.duration)

    type.forEach({ skill -> serviceBuilder.addRequiredSkill(skill) })

    val capacityBuilder = Capacity.Builder.newInstance()
    capacityBuilder.addDimension(0, load)

    val capacity = capacityBuilder.build()
    serviceBuilder.addAllSizeDimensions(capacity)

    val service = serviceBuilder.build()
    return service
  }
}