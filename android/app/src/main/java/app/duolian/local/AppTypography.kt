package app.duolian.local

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** One offline CJK/Latin typeface across vendors; system accessibility scaling stays enabled. */
@OptIn(ExperimentalTextApi::class)
internal val DuolianFont = FontFamily(
    Font(R.font.noto_sans_sc, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_sc, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_sc, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_sc, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
