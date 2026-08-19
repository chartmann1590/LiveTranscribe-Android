package com.charles.livecaptionn.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.billing.PremiumProduct
import com.charles.livecaptionn.ui.l10n.LocalUiStrings

@Composable
fun PremiumCard(
    state: PremiumUiState,
    onPurchase: (PremiumProduct) -> Unit,
    onManageSubscription: () -> Unit,
    onRestoreEmailChange: (String) -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalUiStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t["Upgrade"],
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = t["Ad-Free removes all ads. Pro unlocks larger on-device speech models, more translation languages, and extra overlay themes."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            FilledTonalButton(
                onClick = { onPurchase(PremiumProduct.AD_FREE_MONTHLY) },
                enabled = !state.isBusy && !state.premium.hasAdFree,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.premium.hasAdFree) t["Ad-Free active"] else t["Go Ad-Free"])
            }

            FilledTonalButton(
                onClick = { onPurchase(PremiumProduct.PRO_MONTHLY) },
                enabled = !state.isBusy && !state.premium.hasPro,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.premium.hasPro) t["Pro active"] else t["Go Pro"])
            }

            if (state.premium.hasAdFree || state.premium.hasPro) {
                OutlinedButton(
                    onClick = onManageSubscription,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t["Manage subscription"])
                }
            }

            if (state.supportsEmailRestore) {
                OutlinedTextField(
                    value = state.restoreEmail,
                    onValueChange = onRestoreEmailChange,
                    label = { Text(t["Email used at checkout"]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !state.isBusy && state.restoreEmail.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t["Restore purchase"])
                }
            }

            TextButton(onClick = onRefresh, enabled = !state.isBusy) {
                Text(t["Refresh status"])
            }
        }
    }
}
