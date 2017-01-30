package io.winebox.godwit.routing.models

import com.graphhopper.jsprit.core.problem.job.Job
import io.winebox.godwit.routing.visit.VisitRepresentable
import java.security.InvalidParameterException

/**
 * Created by aj on 1/28/17.
 */

abstract class Visit(
  override val id: String,
  override val priority: Priority,
  override val type: Collection<String>
) : VisitRepresentable {

  enum class Priority : VisitRepresentable.Priority {
    LOW, MEDIUM, HIGH;

    companion object {
      fun from(value: String): Priority {
        return when (value) {
          "low" -> LOW
          "medium" -> MEDIUM
          "high" -> HIGH
          else -> throw InvalidParameterException("Visit priority can not be inferred from \"$value\".")
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

    internal fun transform(): Int {
      return when (this) {
        LOW -> 3
        MEDIUM -> 2
        HIGH -> 1
      }
    }
  }

  internal abstract fun transform(): Job
}