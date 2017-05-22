package io.winebox.godwit.routing

import java.security.InvalidParameterException

/**
 * Created by aj on 5/10/17.
 */

object Request {

  data class Options(
    val traffic: Traffic = Traffic.NONE
  ) {

    enum class Traffic {
      NONE, LIGHT, MODERATE, HEAVY;

      companion object {
        fun from(value: String): Traffic {
          return when (value) {
            "none" -> Request.Options.Traffic.NONE
            "light" -> Request.Options.Traffic.LIGHT
            "moderate" -> Request.Options.Traffic.MODERATE
            "heavy" -> Request.Options.Traffic.HEAVY
            else -> throw InvalidParameterException("Road traffic can not be inferred from \"$value\".")
          }
        }
      }

      val value: String get() {
        return when (this) {
          Request.Options.Traffic.NONE -> "none"
          Request.Options.Traffic.LIGHT -> "light"
          Request.Options.Traffic.MODERATE -> "moderate"
          Request.Options.Traffic.HEAVY -> "heavy"
        }
      }

      internal val delayFactor: Double get() {
        val base = 2.0
        val exponent = when (this) {
          Request.Options.Traffic.NONE -> 0.0
          Request.Options.Traffic.LIGHT -> 0.5
          Request.Options.Traffic.MODERATE -> 1.0
          Request.Options.Traffic.HEAVY -> 1.5
        }
        return Math.pow(base, exponent)
      }

      override fun toString(): String {
        return value
      }
    }
  }
}