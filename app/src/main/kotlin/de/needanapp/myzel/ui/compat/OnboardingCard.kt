package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viktormykhailiv.compose.hints.HintHost
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.designCard
import org.fdroid.R

// Ersatz für die Hinweiskarten von F-Droid (org.fdroid.ui.utils.OnboardingCard), über den Import
// eingebunden (myzel/tools/apply-imports.py): Karte des Design-Pakets statt ElevatedCard, die
// Bestätigung als Pille, dahinter ein dunkler Schleier statt der Akzentfarbe.

/**
 * Ersatz für org.fdroid.ui.utils.HintOverlayContainer: der Schleier des Themes wie bei Menüs und
 * Dialogen. F-Droid nimmt die Akzentfarbe, bei Myzel ein greller türkiser Vorhang.
 */
@Composable
fun HintOverlayContainer(content: @Composable () -> Unit) {
  HintHost(overlay = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), content = content)
}

/** Ersatz für org.fdroid.ui.utils.OnboardingPopupCard mit derselben Signatur. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnboardingPopupCard(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  buttonText: String = stringResource(R.string.got_it),
  onGotIt: () -> Unit = {},
) {
  val focusRequester = remember { FocusRequester() }
  OnboardingCard(
    title = title,
    message = message,
    modifier = modifier.widthIn(max = TooltipDefaults.richTooltipMaxWidth),
    buttonText = buttonText,
    buttonModifier = Modifier.focusRequester(focusRequester),
    onGotIt = onGotIt,
  )
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Ersatz für org.fdroid.ui.utils.OnboardingCard mit derselben Signatur. */
@Composable
fun OnboardingCard(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  buttonText: String = stringResource(R.string.got_it),
  buttonModifier: Modifier = Modifier,
  onGotIt: () -> Unit = {},
) {
  Column(modifier.designCard().padding(16.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
    )
    PillButton(buttonText, onClick = onGotIt, modifier = buttonModifier.fillMaxWidth())
  }
}
