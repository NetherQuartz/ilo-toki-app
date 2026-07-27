package one.larkin.ilotoki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.resources.Res
import one.larkin.ilotoki.resources.sitelen_pona_pona
import one.larkin.ilotoki.resources.space_grotesk_bold
import one.larkin.ilotoki.resources.space_grotesk_medium
import one.larkin.ilotoki.resources.space_grotesk_regular
import org.jetbrains.compose.resources.Font

/** Which palette to draw with, independently of what the system asks for. */
enum class ThemeSetting { Light, Dark, System }

/**
 * The «sitelen» palette.
 *
 * The whole visual language is ink on paper with exactly one saturated colour, so
 * these are named after what they are rather than after Material roles: there is no
 * primary/secondary hierarchy to map onto. [accent] means one thing only — *the
 * target of the translation, or the active choice* — and is the only filled colour
 * surface on any screen. Adding a second accent-filled surface breaks that code.
 */
@Immutable
data class IloTokiColors(
    /** Screen background. Bone in light, near-black in dark. */
    val bg: Color,
    /** Plates, icon squares, cards. */
    val paper: Color,
    /** Text and the filled slab. */
    val ink: Color,
    /** Borders. Equal to [ink] in light; lighter than it in dark so 2 dp reads. */
    val line: Color,
    /** The hard, blur-free offset shadow. */
    val shadow: Color,
    /** jelo — target plate, active choice, mark tile. */
    val accent: Color,
    /** Text on [accent]. Stays dark in both themes: the accent never darkens. */
    val onAccent: Color,
    /** laso — the model currently in use. */
    val laso: Color,
    /** Text on [laso]. */
    val onLaso: Color,
    /** loje — alerts, delete, the update dot. */
    val loje: Color,
    /** Secondary text. */
    val muted: Color,
    /** Hairlines and inactive outlines. */
    val faint: Color,
    val isDark: Boolean,
)

private val Jelo = Color(0xFFF5C63D)
private val Laso = Color(0xFF2E6E7E)
private val Loje = Color(0xFFD4553C)
private val InkBlack = Color(0xFF14130F)
private val BonePaper = Color(0xFFEFE9DC)

internal fun lightColors(accent: Color = Jelo) = IloTokiColors(
    bg = BonePaper,
    paper = Color(0xFFFFFDF6),
    ink = InkBlack,
    line = InkBlack,
    shadow = InkBlack,
    accent = accent,
    onAccent = InkBlack,
    laso = Laso,
    onLaso = Color(0xFFFFFDF6),
    loje = Loje,
    muted = InkBlack.copy(alpha = 0.55f),
    faint = InkBlack.copy(alpha = 0.14f),
    isDark = false,
)

internal fun darkColors(accent: Color = Jelo) = IloTokiColors(
    // True black, so an OLED panel simply switches the background pixels off.
    bg = Color.Black,
    paper = Color(0xFF211F17),
    ink = BonePaper,
    line = Color(0xFF4A4436),
    // And the hard shadow with it. Nothing is darker than the background it falls
    // on, so on true black a shadow can only ever be a halo — the mock's #0A0906
    // would read as a faint glow around every plate. In dark the 2 dp [line] is
    // what separates a plate from the screen; the shadow was already all but
    // invisible against the old #14130F, so this gives up next to nothing.
    shadow = Color.Black,
    accent = accent,
    // The accent is unchanged in dark, so what sits on it must stay dark too.
    onAccent = InkBlack,
    laso = Laso,
    onLaso = Color(0xFFFFFDF6),
    loje = Loje,
    muted = BonePaper.copy(alpha = 0.55f),
    faint = BonePaper.copy(alpha = 0.18f),
    isDark = true,
)

/**
 * The type scale, in one place because nearly every size in the design is a
 * one-off: sizes are chosen per element, not from a role ladder.
 *
 * `letterSpacing` is given in `em` by the design; in Compose it is absolute, so
 * each value below is `em × size` (0.09em at 10 sp is 0.9.sp).
 */
@Immutable
data class IloTokiTypography(
    /** Latin input and output, the two big plates. */
    val body: TextStyle,
    /** The same strings in sitelen pona. */
    val sitelen: TextStyle,
    /** The wordmark and every screen title. */
    val title: TextStyle,
    /** Pair labels, IN USE, TODAY — pill stamps and micro labels. */
    val stamp: TextStyle,
    /** APPEARANCE, STORAGE — plate section headers. */
    val section: TextStyle,
    /** The primary action. */
    val slab: TextStyle,
    /** Model plate names. */
    val modelTitle: TextStyle,
    /** Settings rows. */
    val row: TextStyle,
    /** Secondary lines, `muted`. */
    val meta: TextStyle,
    /** Storage figures. */
    val bigNumber: TextStyle,
    /** History card result, latin. */
    val historyResult: TextStyle,
    /** History card result, sitelen pona. */
    val historySitelen: TextStyle,
)

/**
 * sitelen pona is a **ligature** font: the latin string is what gets laid out and
 * the glyphs appear on their own, so the same text can be rendered either way by
 * swapping the family. Nothing ever draws a glyph by hand.
 */
private const val LIGATURES = "liga, calt, clig"

private val SitelenLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun typographyOf(space: FontFamily, sitelen: FontFamily) = IloTokiTypography(
    body = TextStyle(
        fontFamily = space,
        fontSize = 25.sp,
        lineHeight = 33.75.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.375).sp,
    ),
    sitelen = TextStyle(
        fontFamily = sitelen,
        fontSize = 42.sp,
        lineHeight = 54.6.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = LIGATURES,
        // The font carries a lot of ascent it does not draw into, so a glyph
        // centred by its line box sits visibly high — obvious wherever a single
        // word is used as a mark inside a square. Centre on the line box and trim
        // the leading instead of nudging each call site by eye.
        lineHeightStyle = SitelenLineHeight,
    ),
    title = TextStyle(
        fontFamily = space,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.34).sp,
    ),
    stamp = TextStyle(
        fontFamily = space,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
    ),
    section = TextStyle(
        fontFamily = space,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    ),
    slab = TextStyle(
        fontFamily = space,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.68.sp,
    ),
    modelTitle = TextStyle(
        fontFamily = space,
        fontSize = 19.sp,
        lineHeight = 21.85.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.38).sp,
    ),
    row = TextStyle(
        fontFamily = space,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium,
    ),
    meta = TextStyle(
        fontFamily = space,
        fontSize = 12.sp,
        lineHeight = 17.4.sp,
        fontWeight = FontWeight.Medium,
    ),
    bigNumber = TextStyle(
        fontFamily = space,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.44).sp,
    ),
    historyResult = TextStyle(
        fontFamily = space,
        fontSize = 16.sp,
        lineHeight = 21.6.sp,
        fontWeight = FontWeight.Medium,
    ),
    historySitelen = TextStyle(
        fontFamily = sitelen,
        fontSize = 30.sp,
        lineHeight = 37.5.sp,
        fontFeatureSettings = LIGATURES,
        lineHeightStyle = SitelenLineHeight,
    ),
)

private val LocalColors: ProvidableCompositionLocal<IloTokiColors> =
    staticCompositionLocalOf { lightColors() }

private val LocalTypography: ProvidableCompositionLocal<IloTokiTypography> =
    staticCompositionLocalOf { error("IloTokiTheme has not been applied") }

/** Everything the design refers to by token name. */
object IloTokiTheme {
    val colors: IloTokiColors
        @Composable get() = LocalColors.current

    val type: IloTokiTypography
        @Composable get() = LocalTypography.current
}

/**
 * The platform's own accent, for the «system colours» switch, or null where the
 * platform has none. It must stay light enough to carry [IloTokiColors.onAccent]
 * text, because the target plate is filled with it.
 */
@Composable
expect fun systemAccentOrNull(): Color?

/** Whether to offer the «system colours» row at all — it is omitted, not disabled. */
expect val systemColoursAvailable: Boolean

@Composable
fun IloTokiTheme(
    setting: ThemeSetting = ThemeSetting.System,
    useSystemColours: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (setting) {
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
        ThemeSetting.System -> isSystemInDarkTheme()
    }
    val accent = (if (useSystemColours) systemAccentOrNull() else null) ?: Jelo
    val colors = remember(dark, accent) { if (dark) darkColors(accent) else lightColors(accent) }

    val space = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )
    val sitelen = FontFamily(Font(Res.font.sitelen_pona_pona))
    val typography = remember(space, sitelen) { typographyOf(space, sitelen) }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
    ) {
        // Material is no longer what the app looks like, but a few primitives still
        // consult it — the text cursor and the selection handles most visibly — so
        // it is kept pointed at the same tokens rather than left at its defaults.
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = colors.ink,
                    background = colors.bg,
                    surface = colors.paper,
                    onSurface = colors.ink,
                    error = colors.loje,
                )
            } else {
                lightColorScheme(
                    primary = colors.ink,
                    background = colors.bg,
                    surface = colors.paper,
                    onSurface = colors.ink,
                    error = colors.loje,
                )
            },
            content = content,
        )
    }
}
