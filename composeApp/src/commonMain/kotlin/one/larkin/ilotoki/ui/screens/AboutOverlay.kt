package one.larkin.ilotoki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.Language
import one.larkin.ilotoki.appVersion
import one.larkin.ilotoki.model.ModelSpec
import one.larkin.ilotoki.openUrl
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.Chip
import one.larkin.ilotoki.ui.MarkTile
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.cardDrop
import one.larkin.ilotoki.ui.fadeIn
import one.larkin.ilotoki.ui.tap
import one.larkin.ilotoki.ui.theme.IloTokiTheme

private const val SOURCE_URL = "https://github.com/NetherQuartz/ilo-toki-app"
private const val TOKI_PONA_URL = "https://tokipona.org"

/**
 * About, as a card over the screen rather than a screen of its own — it is the one
 * place in the app with nothing to do, so it should be dismissable in one tap.
 *
 * It does not answer the back gesture with any movement of its own. Back here means
 * «close this», and a card that slides about under the thumb before closing is
 * asking to be read as going somewhere, which it is not — see `previewed` on
 * [one.larkin.ilotoki.rememberBackGesture].
 */
@Composable
fun AboutOverlay(model: ModelSpec, onDismiss: () -> Unit) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        // The scrim starts below the header so the mark that opened this stays visible.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 58.dp)
                .fadeIn(170)
                .background(Color(0xFF14130F).copy(alpha = 0.42f))
                .tap(onClick = onDismiss),
        )
        // It comes down from behind the header, where the mark that opened it is.
        Plate(
            modifier = Modifier
                .fillMaxWidth()
                .cardDrop()
                .padding(start = 19.dp, end = 19.dp, top = 66.dp),
            shadow = 5.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkTile(size = 46.dp, radius = 14.dp, markSize = 29.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        AppText("ilo toki", type.title.copy(fontSize = 20.sp))
                        Spacer(Modifier.height(5.dp))
                        AppText(
                            text = "$appVersion · ${Language.entries.size} languages, " +
                                "none of them online",
                            style = type.meta.copy(fontSize = 11.5.sp),
                            color = colors.muted,
                        )
                    }
                    Plate(
                        modifier = Modifier.size(30.dp).tap(pressScale = 0.9f, onClick = onDismiss),
                        background = Color.Transparent,
                        radius = 9.dp,
                        shadow = 0.dp,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppText("✕", type.stamp.copy(fontSize = 13.sp, letterSpacing = 0.sp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Plate(
                    modifier = Modifier.fillMaxWidth(),
                    background = colors.accent,
                    contentColor = colors.onAccent,
                    radius = 16.dp,
                    shadow = 0.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Column {
                        AppText(
                            text = "sina pona tawa mi",
                            style = type.sitelen.copy(fontSize = 34.sp, lineHeight = 42.5.sp),
                        )
                        Spacer(Modifier.height(7.dp))
                        AppText(
                            text = "sina pona tawa mi — thank you for using this.",
                            style = type.meta.copy(fontSize = 12.5.sp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppText(
                    text = "translates with ${model.displayName} on llama.cpp, " +
                        "on this phone only",
                    style = type.meta.copy(fontSize = 11.5.sp),
                    color = colors.muted,
                )
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutLink("SOURCE", SOURCE_URL)
                    AboutLink("THE MODEL", model.pageUrl)
                    AboutLink("TOKI PONA", TOKI_PONA_URL)
                }
            }
        }
    }
}

@Composable
private fun AboutLink(label: String, url: String) {
    Chip(
        text = label,
        onClick = { openUrl(url) },
        background = Color.Transparent,
        style = IloTokiTheme.type.stamp.copy(fontSize = 11.sp, letterSpacing = 0.66.sp),
        radius = 999.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    )
}
