package io.winebox.godwit.planning.models

import com.graphhopper.jsprit.core.problem.job.Job
import java.security.InvalidParameterException

/**
 * Created by aj on 5/11/17.
 */

abstract class Todo(
  val id: String,
  val priority: Priority,
  val type: Set<String>,
  val load: Int
) {

  enum class Priority {
    LOW, MEDIUM, HIGH;

    companion object {
      fun from(value: String): Priority {
        return when (value) {
          "low" -> LOW
          "medium" -> MEDIUM
          "high" -> HIGH
          else -> throw InvalidParameterException("Todo priority can not be inferred from \"$value\".")
        }
      }
    }

    val value: String get() {
      return when (this) {
        LOW -> "low"
        MEDIUM -> "medium"
        HIGH -> "high"
      }
    }

    internal fun toJobPriority(): Int {
      return when (this) {
        LOW -> 3
        MEDIUM -> 2
        HIGH -> 1
      }
    }

    override fun toString(): String {
      return value
    }
  }

  abstract internal fun toJob(): Job
}