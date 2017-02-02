package io.winebox.godwit.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.reader.osm.GraphHopperOSM
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.shapes.GHPoint
import io.winebox.godwit.routing.utils.LocationRepresentable
import java.lang.Math.pow
import java.security.InvalidParameterException
import java.util.concurrent.locks.StampedLock

/**
 * Created by aj on 1/28/17.
 */

object Godwit {

  enum class Weighting {
    FASTEST, SHORTEST;

    companion object {
      fun from(value: String): Weighting {
        return when (value) {
          "fastest" -> FASTEST
          "shortest" -> SHORTEST
          else -> throw InvalidParameterException("Route weighting can not be inferred from \"$value\".")
        }
      }
    }

    val value: String get() {
      return when (this) {
        FASTEST -> "fastest"
        SHORTEST -> "shortest"
      }
    }
  }

  enum class Traffic {
    NONE, LIGHT, MODERATE, HEAVY;

    companion object {
      fun from(value: String): Traffic {
        return when (value) {
          "none" -> NONE
          "light" -> LIGHT
          "moderate" -> MODERATE
          "heavy" -> HEAVY
          else -> throw InvalidParameterException("Road traffic can not be inferred from \"$value\".")
        }
      }
    }

    val value: String get() {
      return when (this) {
        NONE -> "none"
        LIGHT -> "light"
        MODERATE -> "moderate"
        HEAVY -> "heavy"
      }
    }

    val delayFactor: Double get() {
      return when (this) {
        NONE -> pow(1.44, 2.0)
        LIGHT -> pow(1.44, 1.0)
        MODERATE -> pow(1.44, 2.0)
        HEAVY -> pow(1.44, 3.0)
      }
    }
  }

  data class Path internal constructor(val distance: Double, val time: Double)

  class Engine internal constructor(
    configuration: Configuration
  ) {

    data class Configuration(
      val dataReaderFile: String,
      val graphHopperCacheDirectory: String
    )

    private val hopperInstance: GraphHopper = {
      val hopperInstance = GraphHopperOSM().forServer()
        .setDataReaderFile(configuration.dataReaderFile)
        .setGraphHopperLocation(configuration.graphHopperCacheDirectory)
        .setEncodingManager(EncodingManager("car"))
      hopperInstance.chFactoryDecorator.setWeightingsAsStrings(Weighting.FASTEST.value, Weighting.SHORTEST.value)
      hopperInstance.importOrLoad()
    }()

    fun path(locations: Collection<LocationRepresentable>, weighting: Weighting = Weighting.FASTEST): Path {
      val request = GHRequest()
      locations.map { location -> GHPoint(location.latitude, location.longitude) }
        .forEach { point -> request.addPoint(point) }
      request.hints.put("calcPoints", false)
      request.hints.put("instructions", false)
      request.weighting = weighting.value
      val response = hopperInstance.route(request)

      // TODO: Throw exception with error information
      if (response.hasErrors()) {
        println(response.errors)
        return Path(Double.MAX_VALUE, Double.MAX_VALUE)
      }
      val path = response.best
      return Path(path.distance, (path.time / 1000 / 60).toDouble())
    }
  }

  private val lock = StampedLock()

  private lateinit var _engine: Engine

  var engine: Engine
  get() {
    val stamp = lock.readLock()
    try {
      return _engine
    } finally {
      lock.unlockRead(stamp)
    }
  }
  private set(value) {
    val stamp = lock.writeLock()
    try {
      _engine = value
    } finally {
      lock.unlockWrite(stamp)
    }
  }

  fun run() {
    val configuration = Engine.Configuration(
      dataReaderFile = "data/planet.osm.pbf",
      graphHopperCacheDirectory = "out/graph-cache"
    )
    _engine = Engine(configuration)
  }
}