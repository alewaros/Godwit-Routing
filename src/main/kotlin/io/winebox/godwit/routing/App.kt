package io.winebox.godwit.routing

import io.winebox.godwit.routing.models.*

/**
 * Created by aj on 1/28/17.
 */

fun main(args: Array<String>) {

  Godwit.run()

  val task1 = Task("v1", Stop(Location(37.770435, -122.418987), TimeWindow(50.0), duration = 5.0))
  val task2 = Task("v2", Stop(Location(37.690036, -122.467519), TimeWindow(120.0, 550.0), duration = 7.0))
  val task3 = Task("v3", Stop(Location(37.77964296, -122.44549306), TimeWindow(30.0, 510.0), duration = 10.0))
  val task4 = Task("v4", Stop(Location(37.730371, -122.387108), TimeWindow(850.0, 880.0), duration = 20.0))
  val task5 = Task("v5", Stop(Location(37.71864142, -122.4778186), TimeWindow(780.0, 960.0), duration = 2.0))

  val car1 = Transport("t1", Location(37.777973, -122.415746), endLocation = Location(37.771435, -122.415987), shift = TimeWindow(end = 200.0), shouldDelayDeparture = false)
  val car2 = Transport("t2", Location(37.623528, -122.464332), shift = TimeWindow(480.0, 1200.0))

  val tasks = setOf(task1, task2, task3, task4, task5)
  val fleet = setOf(car1, car2)

  val solution = VehicleRoutingProblem.solve(tasks, fleet)
  println(solution.unassignedVisits)
  solution.routes.forEach { route ->
    println(route.assignedTransport)
    route.stops.forEach { stop ->
      println(stop)
    }
    println()
  }
  println(solution.score)
}