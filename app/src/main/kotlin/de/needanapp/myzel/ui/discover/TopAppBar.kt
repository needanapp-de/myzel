package de.needanapp.myzel.ui.discover

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.needanapp.design.theme.isNeedanappDesign
import org.fdroid.R

/**
 * Ersatz für androidx.compose.material3.TopAppBar in Discover.kt, über den Import eingebunden
 * (myzel/tools/apply-imports.py). F-Droid zeigt dort als Titel den App-Namen; Myzel zeigt statt
 * [title] das Logo und den klein geschriebenen Schriftzug „myzel“. Alles andere reicht er an die
 * TopAppBar von Myzel ([de.needanapp.myzel.ui.compat.TopAppBar]) durch.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
  colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
  scrollBehavior: TopAppBarScrollBehavior? = null,
) {
  de.needanapp.myzel.ui.compat.TopAppBar(
    title = { Wordmark() },
    modifier = modifier,
    navigationIcon = navigationIcon,
    actions = actions,
    expandedHeight = expandedHeight,
    windowInsets = windowInsets,
    colors = colors,
    scrollBehavior = scrollBehavior,
  )
}

/**
 * Logo in seinen eigenen Farben und daneben „myzel“ im Stil des Titels; im Design needanapp etwas
 * kleiner und halbfett wie in der Vorschau.
 */
@Composable
private fun Wordmark() {
  val needanapp = isNeedanappDesign
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painter = painterResource(R.drawable.myzel_logo),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.size(if (needanapp) 30.dp else 32.dp),
    )
    Spacer(Modifier.width(if (needanapp) 8.dp else 10.dp))
    if (needanapp) {
      Text(
        stringResource(R.string.myzel_wordmark),
        maxLines = 1,
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
          ),
      )
    } else {
      Text(stringResource(R.string.myzel_wordmark), maxLines = 1)
    }
  }
}
