package com.morningsteps.kids.ui.theme

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningsteps.kids.R
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.domain.routines.StepIcon

object Palette {
    val Ivory = Color(0xFFFBF6EC)
    val Paper = Color(0xFFFFFCF6)
    val PaperLine = Color(0xFFE9E0CF)
    val Slate = Color(0xFF2F3B48)
    val SlateSoft = Color(0xFF55636F)
    val Stone = Color(0xFFF1EBDF)

    val Sky = Color(0xFFBFE0F6)
    val SkyDeep = Color(0xFF3F7EA8)
    val Lavender = Color(0xFFD9CFEE)
    val LavenderDeep = Color(0xFF6D5A99)
    val Mint = Color(0xFFC3E8D7)
    val MintDeep = Color(0xFF2F7A5C)
    val Peach = Color(0xFFF9D6BE)
    val PeachDeep = Color(0xFF9A5A33)

    val Highlight = Color(0xFFE0A84B)
}

/** Colors for one routine theme: a soft surface tone and a deep tone with good contrast on ivory. */
data class RoutineColors(val soft: Color, val deep: Color)

fun RoutineTheme.colors(): RoutineColors = when (this) {
    RoutineTheme.MORNING -> RoutineColors(Palette.Sky, Palette.SkyDeep)
    RoutineTheme.EVENING -> RoutineColors(Palette.Lavender, Palette.LavenderDeep)
    RoutineTheme.BEFORE_SCHOOL -> RoutineColors(Palette.Mint, Palette.MintDeep)
    RoutineTheme.AFTER_SCHOOL -> RoutineColors(Palette.Peach, Palette.PeachDeep)
}

@DrawableRes
fun RoutineTheme.iconRes(): Int = when (this) {
    RoutineTheme.MORNING -> R.drawable.ic_theme_morning
    RoutineTheme.EVENING -> R.drawable.ic_theme_evening
    RoutineTheme.BEFORE_SCHOOL -> R.drawable.ic_theme_before_school
    RoutineTheme.AFTER_SCHOOL -> R.drawable.ic_theme_after_school
}

@DrawableRes
fun StepIcon.drawableRes(): Int = when (this) {
    StepIcon.WASH_FACE -> R.drawable.ic_step_wash_face
    StepIcon.GET_DRESSED -> R.drawable.ic_step_get_dressed
    StepIcon.BREAKFAST -> R.drawable.ic_step_breakfast
    StepIcon.BRUSH_TEETH -> R.drawable.ic_step_brush_teeth
    StepIcon.PACK_BAG -> R.drawable.ic_step_pack_bag
    StepIcon.BAG_AWAY -> R.drawable.ic_step_bag_away
    StepIcon.SHOES -> R.drawable.ic_step_shoes
    StepIcon.TIDY -> R.drawable.ic_step_tidy
    StepIcon.LIGHT_OFF -> R.drawable.ic_step_light_off
    StepIcon.PAJAMAS -> R.drawable.ic_step_pajamas
    StepIcon.HANGER -> R.drawable.ic_step_hanger
    StepIcon.CHECK_CLOTHES -> R.drawable.ic_step_check_clothes
    StepIcon.WATER_BOTTLE -> R.drawable.ic_step_water_bottle
    StepIcon.WASH_HANDS -> R.drawable.ic_step_wash_hands
    StepIcon.SNACK -> R.drawable.ic_step_snack
    StepIcon.SCHOOL_TASKS -> R.drawable.ic_step_school_tasks
    StepIcon.QUIET_ACTIVITY -> R.drawable.ic_step_quiet_activity
    StepIcon.STAR -> R.drawable.ic_step_star
}

@DrawableRes
fun stepIconRes(key: String): Int = StepIcon.fromKey(key).drawableRes()

private val AppColors = lightColorScheme(
    primary = Palette.Slate,
    onPrimary = Color.White,
    primaryContainer = Palette.Sky,
    onPrimaryContainer = Palette.Slate,
    secondary = Palette.SlateSoft,
    onSecondary = Color.White,
    secondaryContainer = Palette.Stone,
    onSecondaryContainer = Palette.Slate,
    tertiary = Palette.MintDeep,
    background = Palette.Ivory,
    onBackground = Palette.Slate,
    surface = Palette.Paper,
    onSurface = Palette.Slate,
    surfaceVariant = Palette.Stone,
    onSurfaceVariant = Palette.SlateSoft,
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.Paper,
    surfaceContainerHighest = Palette.Stone,
    surfaceContainerLow = Palette.Ivory,
    outline = Color(0xFF8A96A1),
    outlineVariant = Palette.PaperLine,
    error = Color(0xFFB3261E),
)

/** Child-facing text is 18sp or larger; everything scales with the system font size (sp units). */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

/** True when motion should be minimal: in-app setting or the system "remove animations" option. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun MorningStepsTheme(reduceMotionSetting: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemReduced = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides (reduceMotionSetting || systemReduced)) {
        MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
