package id.panenku.app.features.f1_catalog

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
fun RatingStars(rating: Int, onRating: ((Int) -> Unit)? = null) {
    Row(Modifier.semantics { if (onRating == null) contentDescription = "$rating dari 5 bintang" }) {
        (1..5).forEach { value ->
            if (onRating != null) IconButton(onClick = { onRating(value) }, modifier = Modifier.size(48.dp).semantics { selected = value == rating }) {
                Icon(if (value <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    "Beri nilai $value bintang", Modifier.size(32.dp), tint = if (value <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            } else Icon(if (value <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
