package io.winebox.godwit.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.reader.osm.GraphHopperOSM
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.shapes.GHPoint
import io.winebox.godwit.routing.models.Coordinate
import io.winebox.godwit.routing.models.Matrix
import io.winebox.godwit.routing.models.Path
import io.winebox.godwit.routing.models.Route
import java.security.InvalidParameterException

/**
 * Created by aj on 5/10/17.
 */

class Engine internal constructor(
  configuration: Configuration
) {

  data class Configuration(
    val dataReaderFile: java.nio.file.Path,
    val graphCacheDir: java.nio.file.Path
  )

  private val hopperInstance: GraphHopper = {
    val hopperInstance = GraphHopperOSM().forServer()
      .setDataReaderFile(configuration.dataReaderFile.toString())
      .setGraphHopperLocation(configuration.graphCacheDir.toString())
      .setEncodingManager(EncodingManager("car"))
      .importOrLoad()

    hopperInstance
  }()

  fun path(origin: Coordinate, destination: Coordinate, options: Request.Options = Request.Options()): Path {
    val request = GHRequest()

    val originPoint = GHPoint(origin.latitude, origin.longitude)
    request.addPoint(originPoint)

    val destinationPoint = GHPoint(destination.latitude, destination.longitude)
    request.addPoint(destinationPoint)

    request.hints.put("calcPoints", false)
    request.hints.put("instructions", false)

    val response = hopperInstance.route(request)
    // TODO: Throw exception with error information
    if (response.hasErrors()) {
      println(response.errors)
      return Path(Double.MAX_VALUE, Double.MAX_VALUE)
    }

    val path = response.best
    val distance = path.distance
    val time = path.time / 1000.0 * options.traffic.delayFactor

    return Path(distance, time)
  }

  fun route(coordinates: List<Coordinate>, options: Request.Options = Request.Options()): Route {
    if (coordinates.size < 2) {
      throw InvalidParameterException("Routing request can not be processed with less than two coordinates.")
    }

    val journeys = coordinates.zip(coordinates.drop(1))
      .map({ (origin, destination) -> Pair(origin, destination) })
    val paths = journeys
      .map({ (origin, destination) ->
        val path = path(origin, destination, options)
        path
      })
    val route = Route(paths)

    return route
  }

  fun matrix(coordinates: List<Coordinate>, options: Request.Options = Request.Options()): Matrix {
    if (coordinates.size < 2) {
      throw InvalidParameterException("Routing request can not be processed with less than two coordinates.")
    }

    val journeys = coordinates
      .flatMap({ origin ->
        coordinates
          .map({ destination -> Pair(origin, destination) })
      })
    val paths = journeys
      .associateBy({ it }, { (origin, destination) ->
        val path = path(origin, destination, options)
        path
      })
    val matrix = Matrix(paths)

    return matrix
  }
}