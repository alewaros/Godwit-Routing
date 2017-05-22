package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.Capacity
import com.graphhopper.jsprit.core.problem.Skills
import com.graphhopper.jsprit.core.problem.job.Job
import com.graphhopper.jsprit.core.problem.job.Shipment

/**
 * Created by aj on 5/11/17.
 */

class Delivery(
  id: String,
  val pickup: Stop,
  val dropoff: Stop,
  priority: Priority = Todo.Priority.MEDIUM,
  type: Set<String> = setOf(),
  load: Int = 0
): Todo(id, priority, type, load) {

  override fun toJob(): Job {
    val shipmentBuilder = Shipment.Builder.newInstance(id)

    shipmentBuilder.setPriority(priority.toJobPriority())

    shipmentBuilder.setPickupLocation(pickup.place.toLocation())
    shipmentBuilder.addPickupTimeWindow(pickup.schedule.toTimeWindow())
    shipmentBuilder.setPickupServiceTime(pickup.duration)

    shipmentBuilder.setDeliveryLocation(dropoff.place.toLocation())
    shipmentBuilder.addDeliveryTimeWindow(dropoff.schedule.toTimeWindow())
    shipmentBuilder.setDeliveryServiceTime(dropoff.duration)

    shipmentBuilder.addSizeDimension(0, load)

    type.forEach({ skill -> shipmentBuilder.addRequiredSkill(skill) })

    val shipment = shipmentBuilder.build()
    return shipment
  }
}