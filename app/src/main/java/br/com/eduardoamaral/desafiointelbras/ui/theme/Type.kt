package br.com.eduardoamaral.desafiointelbras.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.eduardoamaral.desafiointelbras.R

/**
 * Open Sans (Google Fonts, licença OFL), empacotada em res/font. Empacotar evita depender
 * do Google Play Services para baixar a fonte e garante o visual também offline.
 * O guia usa só dois pesos: Regular (400) e SemiBold (600).
 */
val OpenSans = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_semibold, FontWeight.SemiBold),
)

private val default = Typography()

private fun TextStyle.openSans(weight: FontWeight = FontWeight.Normal) =
    copy(fontFamily = OpenSans, fontWeight = weight)

val Typography = Typography(
    displayLarge = default.displayLarge.openSans(),
    displayMedium = default.displayMedium.openSans(),
    displaySmall = default.displaySmall.openSans(),
    headlineLarge = default.headlineLarge.openSans(FontWeight.SemiBold),
    headlineMedium = default.headlineMedium.openSans(FontWeight.SemiBold),
    // "Meus dispositivos"
    headlineSmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    // Títulos das barras: "Novo dispositivo", "Informações"
    titleLarge = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    // Nome do dispositivo na lista e nos cards
    titleMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    // Subtítulos: "3 dispositivos", serial/MAC na lista
    bodySmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
)
