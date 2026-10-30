package de.needanapp.myzel.ui.compat

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.needanapp.design.components.ContentDialog
import de.needanapp.design.components.SignalCircularProgressIndicator
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.myzel.ui.toIconBitmap
import org.fdroid.R
import org.fdroid.ui.settings.AppIcon
import org.fdroid.ui.settings.appIcons
import org.fdroid.ui.utils.startActivitySafe

/*
 * Ersatz für die kleinen Dialoge von F-Droid mit denselben Signaturen, über
 * myzel/tools/apply-imports.py eingebunden: der Dialog des Design-Pakets mit Symbol oben, wie die
 * übrigen Dialoge von Myzel; Löschen in der Fehlerfarbe, „Abbrechen“ aus dem Design-Paket.
 *
 * Bei einem Update von F-Droid mit DeleteDialog.kt, QrCodeDialog.kt, IgnoreIssueDialog.kt,
 * NotAvailableDialog.kt, AppIconDialog.kt und den Dialogen in UserMirrors.kt vergleichen.
 */

/** Paketquelle löschen? */
@Composable
fun DeleteDialog(onDismissDialog: () -> Unit, onDelete: () -> Unit) {
  ContentDialog(
    title = stringResource(R.string.repo_confirm_delete_title),
    confirmLabel = stringResource(R.string.delete),
    onConfirm = onDelete,
    onDismiss = onDismissDialog,
    isDestructive = true,
    icon = Icons.Outlined.Delete,
  ) {
    Text(stringResource(R.string.repo_confirm_delete_body))
  }
}

/** Den Spiegelserver eines Nutzers löschen? */
@Composable
fun DeleteMirrorDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
  ContentDialog(
    title = stringResource(R.string.repo_confirm_delete_mirror_title),
    confirmLabel = stringResource(R.string.delete),
    onConfirm = {
      onDelete()
      onDismiss()
    },
    onDismiss = onDismiss,
    isDestructive = true,
    icon = Icons.Outlined.Delete,
  ) {
    Text(stringResource(R.string.repo_confirm_delete_mirror_body))
  }
}

/** Der QR-Code einer Paketquelle auf weißem Grund, damit jede Kamera ihn liest. */
@Composable
fun QrCodeDialog(onDismissDialog: () -> Unit, generateQrCode: suspend () -> Bitmap?) {
  val qrCode: ImageBitmap? by produceState(null) { value = generateQrCode()?.asImageBitmap() }
  ContentDialog(
    title = stringResource(R.string.share_repository),
    confirmLabel = stringResource(R.string.myzel_close),
    onConfirm = onDismissDialog,
    onDismiss = onDismissDialog,
    dismissLabel = null,
    icon = Icons.Outlined.QrCode,
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(LocalDesignShapes.current.group))
          .background(Color.White)
          .padding(12.dp),
    ) {
      val bitmap = qrCode
      if (bitmap == null) {
        SignalCircularProgressIndicator()
      } else {
        Image(bitmap = bitmap, contentDescription = stringResource(R.string.swap_scan_qr))
      }
    }
  }
}

/** Den Fehler einer App ausblenden? */
@Composable
fun IgnoreIssueDialog(appName: String, onIgnore: () -> Unit, onDismiss: () -> Unit) {
  ContentDialog(
    title = stringResource(R.string.my_apps_ignore_dialog_title),
    confirmLabel = stringResource(R.string.my_apps_ignore_dialog_button),
    onConfirm = onIgnore,
    onDismiss = onDismiss,
    icon = Icons.Outlined.VisibilityOff,
  ) {
    Text(stringResource(R.string.my_apps_ignore_dialog_text, appName))
  }
}

/** Die App gibt es in keiner Paketquelle mehr; „App-Info“ öffnet sie in den Einstellungen. */
@Composable
fun NotAvailableDialog(packageName: String, onDismiss: () -> Unit) {
  val context = LocalContext.current
  ContentDialog(
    title = stringResource(R.string.app_issue_not_available_title),
    confirmLabel = stringResource(R.string.app_issue_not_available_button),
    onConfirm = {
      val intent =
        Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", packageName, null)
        }
      context.startActivitySafe(intent)
      onDismiss()
    },
    onDismiss = onDismiss,
    dismissLabel = stringResource(R.string.myzel_close),
    icon = Icons.Outlined.Info,
  ) {
    Text(stringResource(R.string.app_issue_not_available_text))
  }
}

/** Eine Paketquelle ausschalten? Mit dem Hinweis auf verlorene Bevorzugungen. */
@Composable
fun DisableRepoDialog(onDisable: () -> Unit, onDismiss: () -> Unit) {
  ContentDialog(
    title = stringResource(R.string.repo_disable_dialog_title),
    confirmLabel = stringResource(R.string.repo_disable_warning_button),
    onConfirm = {
      onDisable()
      onDismiss()
    },
    onDismiss = onDismiss,
    isDestructive = true,
    icon = Icons.Outlined.PauseCircle,
  ) {
    Text(stringResource(R.string.repo_disable_dialog_text))
    Row(
      verticalAlignment = Alignment.Top,
      modifier =
        Modifier.padding(top = 12.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(LocalDesignShapes.current.inner * 2))
          .background(MaterialTheme.colorScheme.tertiaryContainer)
          .padding(12.dp),
    ) {
      Icon(
        Icons.Outlined.WarningAmber,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        stringResource(R.string.repo_disable_dialog_warning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
      )
    }
  }
}

/** Das App-Symbol wählen: Symbole im Raster, das gewählte mit Ring in der Hauptfarbe. */
@Composable
fun AppIconDialog(
  currentAppIcon: AppIcon,
  onChangeIcon: (AppIcon) -> Unit,
  onDismissRequest: () -> Unit,
) {
  ContentDialog(
    title = stringResource(R.string.icon_title),
    confirmLabel = stringResource(R.string.myzel_close),
    onConfirm = onDismissRequest,
    onDismiss = onDismissRequest,
    dismissLabel = null,
    icon = Icons.Outlined.Widgets,
  ) {
    Text(stringResource(R.string.icon_intro))
    Spacer(Modifier.height(12.dp))
    // die Symbole mittig in Reihen, das Raster scrollt nicht selbst, der Dialog scrollt als Ganzes
    FlowRow(
      horizontalArrangement = spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      appIcons.forEach { icon ->
        val isSelected = icon == currentAppIcon
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier =
            Modifier.width(88.dp)
              .clip(RoundedCornerShape(LocalDesignShapes.current.inner * 3))
              .clickable { onChangeIcon(icon) }
              .padding(vertical = 8.dp),
        ) {
          val context = LocalContext.current
          // adaptive Symbole selbst zeichnen, sonst steht Myzels durchsichtiges Symbol auf Schwarz
          val bitmap =
            remember(icon) {
              ContextCompat.getDrawable(context, icon.iconRes)?.toIconBitmap()?.asImageBitmap()
            }
          if (bitmap != null)
            Image(
              bitmap = bitmap,
              contentDescription = null,
              modifier =
                Modifier.size(56.dp)
                  .then(
                    if (isSelected)
                      Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                  )
                  .padding(if (isSelected) 5.dp else 0.dp)
                  .clip(CircleShape),
            )
          Spacer(Modifier.height(6.dp))
          Text(
            stringResource(icon.labelRes),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
