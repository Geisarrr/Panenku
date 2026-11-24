package id.panenku.app.ui.components

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.tooling.preview.Preview
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.Instant


@Composable
fun BuyerOrderItems(order: BuyerOrder) {
    order.order.lines.forEach { line ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(when (line.productId) { "sawi" -> "cabbage"; "tomat" -> "tomato"; "cabai" -> "chili"; else -> "shallot" }, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.labelLarge)
                Text("${line.quantity} kg × ${rupiah(line.price)}/kg", style = MaterialTheme.typography.bodySmall)
            }
            Text(rupiah(line.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
