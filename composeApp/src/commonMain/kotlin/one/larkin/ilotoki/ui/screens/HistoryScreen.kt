package one.larkin.ilotoki.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.MainViewModel
import one.larkin.ilotoki.data.HistoryEntry
import one.larkin.ilotoki.data.nowMillis
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.Chip
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.screenBottomInsets
import one.larkin.ilotoki.ui.Stamp
import one.larkin.ilotoki.ui.tap
import one.larkin.ilotoki.ui.theme.IloTokiTheme

/**
 * The stack of past translations, newest first, grouped by day.
 *
 * `olin` is the only thing that makes an entry permanent — it is both a filter and
 * what survives a clear-out, which is why it is a mark on the card rather than a
 * menu item.
 */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    viewModel: MainViewModel,
    onReuse: () -> Unit,
) {
    var onlyOlin by remember { mutableStateOf(false) }
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type

    val shown = if (onlyOlin) entries.filter { it.olin } else entries
    val now = remember(entries) { nowMillis() }

    Column(Modifier.fillMaxSize().screenBottomInsets()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                text = "ALL ${entries.size}",
                onClick = { onlyOlin = false },
                background = if (onlyOlin) Color.Transparent else colors.ink,
                contentColor = if (onlyOlin) colors.ink else colors.bg,
                style = type.stamp,
                radius = 999.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            )
            OlinFilterChip(
                count = entries.count { it.olin },
                active = onlyOlin,
                onClick = { onlyOlin = true },
            )
            // Clearing keeps whatever is marked — that is what the mark is for, so
            // the button says so rather than asking for a confirmation nobody reads.
            if (entries.any { !it.olin }) {
                Spacer(Modifier.weight(1f))
                Chip(
                    text = "CLEAR",
                    onClick = viewModel::clearHistory,
                    background = Color.Transparent,
                    contentColor = colors.loje,
                    border = colors.loje.copy(alpha = 0.5f),
                    style = type.stamp,
                    radius = 999.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (shown.isEmpty()) {
            Plate(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                background = colors.paper,
                border = colors.faint,
                radius = 18.dp,
                shadow = 0.dp,
                dashed = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
            ) {
                AppText(
                    text = if (onlyOlin) {
                        "nothing marked yet — tap olin on a card to keep it"
                    } else {
                        "nothing here yet — translate something and it lands in this stack"
                    },
                    style = type.meta.copy(fontSize = 12.5.sp),
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // Day separators are part of the list rather than sticky headers: the
            // stack is short and a floating header would fight the plates.
            var lastDay: String? = null
            shown.forEach { entry ->
                val day = dayStamp(entry.id, now)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day") {
                        Stamp(text = day, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                item(key = entry.id) {
                    HistoryCard(
                        entry = entry,
                        onToggleOlin = { viewModel.toggleOlin(entry.id) },
                        onRemove = { viewModel.removeHistoryEntry(entry.id) },
                        onReuse = {
                            viewModel.reuse(entry.source, entry.fromTokiPona, entry.other)
                            onReuse()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OlinFilterChip(count: Int, active: Boolean, onClick: () -> Unit) {
    val colors = IloTokiTheme.colors
    Plate(
        modifier = Modifier.tap(onClick = onClick),
        background = if (active) colors.accent else Color.Transparent,
        contentColor = if (active) colors.onAccent else colors.ink,
        radius = 999.dp,
        shadow = 0.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = "olin",
                style = IloTokiTheme.type.sitelen.copy(fontSize = 20.sp, lineHeight = 20.sp),
            )
            AppText("$count", IloTokiTheme.type.stamp)
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onToggleOlin: () -> Unit,
    onRemove: () -> Unit,
    onReuse: () -> Unit,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    Plate(
        modifier = Modifier.fillMaxWidth(),
        radius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = entry.pair,
                    style = type.stamp.copy(fontSize = 9.5.sp),
                    color = colors.muted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallSquare(
                        onClick = onToggleOlin,
                        background = if (entry.olin) colors.accent else Color.Transparent,
                        border = if (entry.olin) colors.line else colors.faint,
                        contentColor = if (entry.olin) colors.onAccent else colors.muted,
                        dim = !entry.olin,
                    ) {
                        AppText(
                            text = "olin",
                            style = type.sitelen.copy(fontSize = 21.sp, lineHeight = 21.sp),
                        )
                    }
                    SmallSquare(
                        onClick = onRemove,
                        background = Color.Transparent,
                        border = colors.faint,
                        contentColor = colors.muted,
                        dim = false,
                    ) {
                        AppText("✕", type.stamp.copy(fontSize = 12.sp, letterSpacing = 0.sp))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            AppText(
                text = entry.source,
                style = type.meta.copy(fontSize = 13.5.sp),
                color = colors.muted,
            )
            Spacer(Modifier.height(5.dp))
            AppText(
                text = entry.result,
                // Only a toki pona result has a second script to be shown in.
                style = if (entry.resultIsTokiPona) type.historySitelen else type.historyResult,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().tap(onClick = onReuse),
            )
        }
    }
}

@Composable
private fun SmallSquare(
    onClick: () -> Unit,
    background: Color,
    border: Color,
    contentColor: Color,
    dim: Boolean,
    content: @Composable () -> Unit,
) {
    Plate(
        modifier = Modifier.size(30.dp).tap(onClick = onClick).alpha(if (dim) 0.6f else 1f),
        background = background,
        contentColor = contentColor,
        border = border,
        radius = 9.dp,
        shadow = 0.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * «TODAY», «YESTERDAY» or the date, in the local calendar.
 *
 * Entry ids are the wall clock at the moment the translation finished, so they are
 * also its timestamp; there is nothing else to store.
 */
internal expect fun dayStamp(millis: Long, now: Long): String
