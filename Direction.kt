package com.example.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class Direction(
  val code: String,
  val title: String,
  val subtitle: String,
) {
  BODY("BODY", "BODY", "Физическое развитие, тренировки, восстановление"),
  BASKETBALL("BASKETBALL", "BASKETBALL", "Ведение, броски, реакция, координация, работа ног"),
  MIND("MIND", "MIND", "Учёба, чтение, языки, концентрация, знания"),
  DISCIPLINE("DISCIPLINE", "DISCIPLINE", "Режим дня, сон, привычки, порядок, фокус"),
  STYLE("STYLE", "STYLE", "Гигиена, уход за волосами и кожей, опрятность"),
  PROJECTS("PROJECTS", "PROJECTS", "Автосъёмка, видеомонтаж, CapCut, контент"),
  LIFE_SKILLS("LIFE_SKILLS", "LIFE SKILLS", "Самостоятельность, быт, организация, финансы");

  val icon: ImageVector
    get() =
      when (this) {
        BODY -> Icons.Rounded.FitnessCenter
        BASKETBALL -> Icons.Rounded.SportsBasketball
        MIND -> Icons.Rounded.Psychology
        DISCIPLINE -> Icons.Rounded.CheckCircle
        STYLE -> Icons.Rounded.AutoAwesome
        PROJECTS -> Icons.Rounded.Videocam
        LIFE_SKILLS -> Icons.Rounded.Build
      }
}
