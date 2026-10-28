package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * PillTabBar
 * ----------
 * One bar = [standalone circle tab(s)] + [pill container with the other tabs], with ONE sliding indicator.
 *
 * How the bugs of the old version are avoided
 *  - One coordinate space. Every x / width is computed from the same constants, so the indicator is never "between"
 *    two separately laid out widgets. The indicator is not a child of either container: it is drawn by the pill
 *    container's drawBehind at an offset that reaches out over the standalone circle(s) (drawBehind is not clipped by
 *    the container's bounds, and the bar never calls clip()).
 *  - Layering. Draw order = circle backgrounds -> pill background -> indicator -> icons and labels.
 *    The standalone circle's icon sits at zIndex(1f), so it is always above the indicator.
 *  - Resting geometry is deterministic: the label widths are measured with a TextMeasurer, so for every tab we know
 *    the exact x / width the indicator must have when that tab is open. No onGloballyPositioned feedback loop, no
 *    one-frame-late rectangles.
 *  - Fluid motion. Tap / settle: the indicator's x and width spring (NoBouncy, MediumLow) with animateDpAsState to the
 *    resting geometry of the target page, and the label uses the same spring, so both reach their size together.
 *    Finger on the pager: x and width are interpolated live from currentPage + currentPageOffsetFraction (no
 *    animation, snap spec), and on release the spring takes over from exactly that value.
 */

/** One tab. The first [PillTabBar] `standaloneCount` tabs are the round icon-only buttons, the rest live in the pill. */
@Immutable
data class PillTab(val label: String, val icon: ImageVector)

@Immutable
data class PillTabBarColors(
    val container: Color,
    val indicator: Color,
    val selectedContent: Color,
    val unselectedContent: Color
)

object PillTabBarDefaults {
    @Composable
    fun colors(
        container: Color = MaterialTheme.colorScheme.surfaceVariant,
        indicator: Color = MaterialTheme.colorScheme.inverseSurface,
        selectedContent: Color = MaterialTheme.colorScheme.inverseOnSurface,
        unselectedContent: Color = MaterialTheme.colorScheme.onSurfaceVariant
    ) = PillTabBarColors(container, indicator, selectedContent, unselectedContent)

    /** The label style. It is also what the label width is measured with, so keep Text and measurer on this one. */
    val LabelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

// ---- geometry (all in dp; change here and everything, indicator included, follows)
private val TabHeight = 48.dp          // height of the indicator and of every tab
private val BarPadding = 4.dp          // space between a background (circle / pill) and the indicator
private val IconSize = 24.dp
private val TabHPadding = 14.dp        // horizontal padding of a pill tab: collapsed width = IconSize + 2 * this
private val LabelGap = 8.dp            // icon -> label
private val BarGap = 8.dp              // standalone circle -> pill container

private val CircleOuter = TabHeight + BarPadding * 2   // the standalone circle's background = the pill's height
private val CollapsedTabWidth = IconSize + TabHPadding * 2

private fun <T> tabSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * @param pagerState the pager whose pages are the tabs (page i = tabs[i]).
 * @param onTabClick called with the tapped index, e.g. `{ scope.launch { pagerState.animateScrollToPage(it) } }`.
 * @param standaloneCount how many of the first tabs are round, icon-only buttons outside the pill (default 1).
 */
@Composable
fun PillTabBar(
    tabs: List<PillTab>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    standaloneCount: Int = 1,
    colors: PillTabBarColors = PillTabBarDefaults.colors()
) {
    val n = tabs.size
    if (n == 0) return
    val sc = standaloneCount.coerceIn(0, n - 1)          // at least one tab stays in the pill

    // ---- exact label widths, so the resting geometry is known without measuring the layout
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = LocalTextStyle.current.merge(PillTabBarDefaults.LabelStyle)
    val labelWidths: List<Dp> = remember(tabs, labelStyle, density) {
        tabs.map { tab ->
            val px = measurer.measure(AnnotatedString(tab.label), labelStyle, maxLines = 1, softWrap = false).size.width
            with(density) { px.toDp() }
        }
    }

    // ---- where the indicator is when tab i is open: x (from the bar's left edge) and width
    val containerStart = (CircleOuter + BarGap) * sc
    val restX = List(n) { i ->
        if (i < sc) (CircleOuter + BarGap) * i + BarPadding                      // inside a circle
        else containerStart + BarPadding + CollapsedTabWidth * (i - sc)          // in the pill, earlier tabs collapsed
    }
    val restW = List(n) { i ->
        if (i < sc) TabHeight                                                    // a perfect circle
        else CollapsedTabWidth + LabelGap + labelWidths[i]                       // icon + label pill
    }

    // ---- pager state -> indicator target
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (n - 1).toFloat())
    val target = pagerState.targetPage.coerceIn(0, n - 1)
    // which tab shows its label: while dragging the nearest one, otherwise the page we are heading to
    val selected = if (dragged) position.roundToInt().coerceIn(0, n - 1) else target

    val liveX: Dp
    val liveW: Dp
    if (dragged) {
        // finger on the pager: interpolate between the two neighbouring resting geometries, in real time
        val i0 = floor(position).toInt().coerceIn(0, n - 1)
        val i1 = min(i0 + 1, n - 1)
        val f = position - i0
        liveX = lerp(restX[i0], restX[i1], f)
        liveW = lerp(restW[i0], restW[i1], f)
    } else {
        liveX = restX[target]
        liveW = restW[target]
    }
    // animateDpAsState with the spring when idle / settling, snap() while the finger drives it (so on release the
    // spring starts from the exact value the finger left it on)
    val spec: AnimationSpec<Dp> = if (dragged) snap() else tabSpring()
    val animX = animateDpAsState(liveX, spec, label = "indicatorX")
    val animW = animateDpAsState(liveW, spec, label = "indicatorWidth")

    val noRipple = remember { MutableInteractionSource() }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- standalone circle tabs: only the icon, above the indicator (zIndex); their background is drawn below
        for (i in 0 until sc) {
            val tint by animateColorAsState(
                if (selected == i) colors.selectedContent else colors.unselectedContent, tween(180), label = "tint"
            )
            Box(
                Modifier
                    .zIndex(1f)
                    .size(CircleOuter)
                    .clip(CircleShape)
                    .semantics { this.selected = selected == i }
                    .clickable(noRipple, indication = null, role = Role.Tab) { onTabClick(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(tabs[i].icon, contentDescription = tabs[i].label, tint = tint, modifier = Modifier.size(IconSize))
            }
        }

        // ---- the pill container. It draws: circle backgrounds (to its left) -> its own background -> the indicator.
        Row(
            Modifier
                .drawBehind {
                    val circle = CircleOuter.toPx()
                    val step = (CircleOuter + BarGap).toPx()
                    val originX = containerStart.toPx()               // this container's left edge in bar coordinates
                    // 1. backgrounds of the standalone circles (they are outside this container's bounds, which is fine)
                    for (k in 0 until sc) {
                        drawRoundRect(
                            colors.container, Offset(k * step - originX, 0f), Size(circle, circle),
                            CornerRadius(circle / 2f)
                        )
                    }
                    // 2. the pill itself
                    drawRoundRect(colors.container, Offset.Zero, size, CornerRadius(size.height / 2f))
                    // 3. the indicator: same x / width space for the circle tab and the pill tabs; fully rounded always
                    val x = (if (dragged) liveX else animX.value).toPx() - originX
                    val w = (if (dragged) liveW else animW.value).toPx()
                    val h = TabHeight.toPx()
                    drawRoundRect(
                        colors.indicator, Offset(x, BarPadding.toPx()), Size(w, h),
                        CornerRadius(min(w, h) / 2f)
                    )
                }
                .padding(BarPadding)
        ) {
            for (i in sc until n) {
                PillTabItem(
                    tab = tabs[i],
                    selected = selected == i,
                    colors = colors,
                    labelStyle = labelStyle,
                    interactionSource = noRipple,
                    onClick = { onTabClick(i) }
                )
            }
        }
    }
}

@Composable
private fun PillTabItem(
    tab: PillTab,
    selected: Boolean,
    colors: PillTabBarColors,
    labelStyle: TextStyle,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        if (selected) colors.selectedContent else colors.unselectedContent, tween(180), label = "tint"
    )
    Row(
        Modifier
            .height(TabHeight)
            .clip(CircleShape)                                  // only clips the touch / semantics area of this tab
            .semantics { this.selected = selected }
            .clickable(interactionSource, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = TabHPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(IconSize))
        // the label opens and closes with the same spring as the indicator, so they finish together
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tabSpring()) + expandHorizontally(tabSpring(), expandFrom = Alignment.Start),
            exit = fadeOut(tabSpring()) + shrinkHorizontally(tabSpring(), shrinkTowards = Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(LabelGap))
                Text(tab.label, color = tint, style = labelStyle, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ example

/** Example: trending circle on the left, Home / Subscriptions / Library in the pill, a pager under the bar. */
@Composable
fun PillTabBarExample() {
    val tabs = remember {
        listOf(
            PillTab("Trending", Icons.Rounded.LocalFireDepartment),
            PillTab("Home", Icons.Rounded.Home),
            PillTab("Subscriptions", Icons.Rounded.Subscriptions),
            PillTab("Library", Icons.Rounded.VideoLibrary)
        )
    }
    val pagerState = rememberPagerState(initialPage = 1) { tabs.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PillTabBar(
            tabs = tabs,
            pagerState = pagerState,
            onTabClick = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            standaloneCount = 1
        )
        HorizontalPager(pagerState, Modifier.weight(1f)) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tabs[page].label) }
        }
    }
}
