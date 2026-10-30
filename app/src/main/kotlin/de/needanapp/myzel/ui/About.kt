package de.needanapp.myzel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.ContentRow
import de.needanapp.design.components.ListRow
import de.needanapp.design.components.SettingsGroup
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import org.fdroid.BuildConfig
import org.fdroid.BuildConfig.VERSION_NAME
import org.fdroid.R
import org.fdroid.ui.FDroidContent
import org.fdroid.ui.utils.openUriSafe

private const val WEBSITE_URL = "https://needanapp.de"
private const val SOURCE_URL = "https://git.needanapp.de/needanapp.de/myzel"
private const val FDROID_URL = "https://f-droid.org"
private const val FDROID_FORUM_URL = "https://forum.f-droid.org"
private const val FDROID_DONATE_URL = "https://f-droid.org/donate/"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val HEADER_ICON_SIZE = 96

/**
 * Ersatz für org.fdroid.ui.About mit derselben Signatur; Main.kt bindet ihn über den Import ein
 * (myzel/tools/apply-imports.py). Über Myzel im Stil von needanapp.de, mit F-Droid als Grundlage.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun About(version: String = VERSION_NAME, onBackClicked: (() -> Unit)?) {
  val scrollBehavior = enterAlwaysScrollBehavior(rememberTopAppBarState())
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = { if (onBackClicked != null) BackButton(onClick = onBackClicked) },
        title = { Text(stringResource(R.string.about_title_full), maxLines = 1) },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { paddingValues ->
    AboutContent(version, Modifier.fillMaxWidth(), paddingValues)
  }
}

/** Ersatz für org.fdroid.ui.AboutContent, auch auf großen Bildschirmen ohne gewählte App. */
@Composable
fun AboutContent(
  version: String,
  modifier: Modifier = Modifier,
  paddingValues: PaddingValues = PaddingValues(),
) {
  val uriHandler = LocalUriHandler.current
  LazyColumn(contentPadding = paddingValues, modifier = modifier) {
    item { Header(version) }
    item {
      SettingsGroup(stringResource(R.string.links)) {
        row {
          ListRow(
            it,
            stringResource(R.string.menu_website),
            summary = WEBSITE_URL.removePrefix("https://"),
            icon = Icons.Outlined.Language,
            onClick = { uriHandler.openUriSafe(WEBSITE_URL) },
          )
        }
        row {
          ListRow(
            it,
            stringResource(R.string.about_source),
            summary = SOURCE_URL.removePrefix("https://"),
            icon = Icons.Outlined.Code,
            onClick = { uriHandler.openUriSafe(SOURCE_URL) },
          )
        }
      }
    }
    item {
      SettingsGroup(stringResource(R.string.myzel_foundation)) {
        row {
          ListRow(
            it,
            "F-Droid",
            summary = stringResource(R.string.myzel_fdroid_summary),
            icon = Icons.Outlined.Storefront,
            onClick = { uriHandler.openUriSafe(FDROID_URL) },
          )
        }
        row {
          ListRow(
            it,
            stringResource(R.string.about_forum),
            summary = FDROID_FORUM_URL.removePrefix("https://"),
            icon = Icons.Outlined.Forum,
            onClick = { uriHandler.openUriSafe(FDROID_FORUM_URL) },
          )
        }
        row {
          ListRow(
            it,
            stringResource(R.string.donate_title),
            summary = stringResource(R.string.myzel_fdroid_donate_summary),
            icon = Icons.Outlined.VolunteerActivism,
            onClick = { uriHandler.openUriSafe(FDROID_DONATE_URL) },
          )
        }
      }
    }
    item {
      SettingsGroup(stringResource(R.string.about_license)) {
        row {
          ContentRow(it, null) {
            Text(
              stringResource(R.string.about_license_text),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        row {
          ListRow(
            it,
            stringResource(R.string.myzel_show_license_text),
            summary = "GPL-3.0-or-later",
            icon = Icons.AutoMirrored.Outlined.Article,
            onClick = { uriHandler.openUriSafe(LICENSE_URL) },
          )
        }
      }
    }
    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun Header(version: String) {
  SettingsGroup(null) {
    row {
      ContentRow(it, null) {
        Column(
          Modifier.fillMaxWidth().padding(vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(Modifier.size(HEADER_ICON_SIZE.dp), contentAlignment = Alignment.Center) {
            // das Myzel füllt etwa die Hälfte der 108-dp-Fläche des Symbols
            Image(
              painterResource(R.drawable.ic_launcher_foreground),
              contentDescription = null,
              modifier = Modifier.requiredSize((HEADER_ICON_SIZE * 2).dp),
            )
          }
          Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            stringResource(R.string.myzel_by),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            "${stringResource(R.string.about_version)} $version",
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            stringResource(R.string.myzel_based_on, BuildConfig.FDROID_VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun AboutPreview() {
  FDroidContent { About {} }
}
