package id.panenku.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import id.panenku.app.R
import id.panenku.app.data.*
import id.panenku.app.ui.theme.*

@Composable
fun Page(title: String, subtitle: String = "", onBack: (() -> Unit)? = null,
         admin: Boolean = false, subtitleFirst: Boolean = false, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = Space.maxWidth).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(Space.page), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali")
                    }
                    Column(Modifier.weight(1f)) {
                        if (admin) Text("ADMIN · PANENKU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        if (subtitleFirst && subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (!subtitleFirst && subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
@Composable
fun Panel(title: String = "", modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(28.dp)) {
        Text(text)
    }
}
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(28.dp)) { Text(text) }
}
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "",
          password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
          multiline: Boolean = false, error: String? = null, readOnly: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            singleLine = !multiline, minLines = if (multiline) 3 else 1,
            shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium,
            readOnly = readOnly, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) ({ IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Sembunyikan kata sandi" else "Tampilkan kata sandi")
            } }) else null)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Cari nama produk") {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
        singleLine = true, placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Hapus pencarian") }
        })
}
@Composable
fun FilterChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected == option, { onSelect(option) }, label = { Text(option) }) }
    }
}
enum class Tone { SUCCESS, WARNING, ERROR, NEUTRAL, INFO }
@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    Tone.WARNING -> PanenkuColors.SoftOrange to PanenkuColors.Orange
    Tone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    Tone.INFO -> Color(0xFFEAF4FF) to Color(0xFF246794)
}
@Composable
fun StatusBadge(text: String, tone: Tone = Tone.SUCCESS) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, shape = RoundedCornerShape(7.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = fg, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
fun Notice(title: String, text: String, tone: Tone = Tone.SUCCESS) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, tint = fg, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = fg)
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable
fun MenuRow(title: String, subtitle: String = "", icon: ImageVector = Icons.Outlined.Inventory2, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}
@Composable
fun KeyValue(label: String, value: String, emphasis: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasis) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
    }
}
@Composable
fun ProductImage(key: String, modifier: Modifier = Modifier) {
    val id = when (key) { "cabbage" -> R.drawable.product_cabbage; "tomato" -> R.drawable.product_tomato
        "chili" -> R.drawable.product_chili; else -> R.drawable.product_shallot }
    Image(painterResource(id), null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
}
@Composable
fun EmptyState(title: String = "Belum ada data", text: String = "Coba ubah kata kunci atau filter.") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun LoadingState() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable
fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Notice("Data belum dapat dimuat", message, Tone.ERROR); Spacer(Modifier.height(16.dp)); PrimaryButton("Coba lagi", retry)
    }
}
@Composable
fun AdaptiveMetrics(items: List<Pair<String, String>>, onClick: (Int) -> Unit) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { col, item ->
                        val i = rowIndex * columns + col
                        Surface(onClick = { onClick(i) }, modifier = Modifier.weight(1f),
                            color = listOf(Color(0xFF0D270C), Color(0xFF2C6F26), Color(0xFF275823), Color(0xFF67C65B))[i % 4],
                            shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.heightIn(min = 112.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(item.first, color = Color.White, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(20.dp))
                                Text(item.second, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
@Composable
fun Timeline(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (title, detail) -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column { Text(title, style = MaterialTheme.typography.labelLarge); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }
    }
}
