package id.panenku.app.ui.auth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import id.panenku.app.R

@OptIn(ExperimentalTextApi::class)
private val SellerFont = FontFamily(
    Font(R.font.seller_inter, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.seller_inter, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
// Preview/test inset injection; production uses the real Android safe drawing insets.
internal val LocalSellerInsets = staticCompositionLocalOf<WindowInsets?> { null }
internal val SellerGreen = Color(0xFF3EAB36)
internal val SellerInk = Color(0xFF183317)
internal val SellerMuted = Color(0xFF707070)

/** The Figma frames use a 375 px canvas. Scale spacing and type together on phones.
 * Larger accessibility fonts remain enabled; small system fonts never shrink these forms below the design. */
@Composable internal fun SellerLayout(decorated: Boolean = false, inset: Dp = 25.dp, plain: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val device = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scale = maxWidth.value.coerceAtMost(480f) / 375f
        CompositionLocalProvider(LocalDensity provides Density(device.density * scale, device.fontScale.coerceAtLeast(1f))) {
            ProvideTextStyle(TextStyle(fontFamily = SellerFont, letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false), color = SellerInk)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to (if (plain) OpeningPaper else Color(0xFFF3FBF2)), 0.3f to OpeningPaper, 1f to OpeningPaper)), contentAlignment = Alignment.TopCenter) {
                    if (decorated) Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0x216AC662), 78.25.dp.toPx(), Offset(368.25.dp.toPx(), 107.25.dp.toPx()))
                        drawCircle(Color(0x216AC662), 47.82.dp.toPx(), Offset((-13.04).dp.toPx(), 134.76.dp.toPx()))
                    }
                    Column(Modifier.windowInsetsPadding(LocalSellerInsets.current ?: WindowInsets.safeDrawing).imePadding()
                        .widthIn(max = 375.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = inset), content = content)
                }
            }
        }
    }
}

@Composable internal fun SellerText(text: String, size: Float = 10.05f, bold: Boolean = false,
    color: Color = SellerMuted, modifier: Modifier = Modifier, line: Float = size * 1.25f,
    align: TextAlign? = null) {
    Text(text, modifier, color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = SellerFont, letterSpacing = 0.sp, lineHeight = line.sp, textAlign = align)
}

@Composable internal fun SellerStep(step: Int, onBack: () -> Unit, busy: Boolean) {
    Spacer(Modifier.height(11.dp))
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White).border(0.84.dp, Color(0xFFE6E8E5), CircleShape)
            .clickable(enabled = !busy, onClick = onBack), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.seller_back), "Kembali", Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SellerText("Langkah $step dari 3", 8.4f)
            Box(Modifier.fillMaxWidth().height(4.2.dp).clip(CircleShape).background(Color(0xFFE7EBE7))) {
                Box(Modifier.fillMaxWidth(step / 3f).fillMaxHeight().background(Color(0xFF36912E)))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable internal fun SellerHeading(eyebrow: String, title: String, description: String) {
    SellerText(eyebrow, 10.9f, true, Color(0xFF36912E), line = 20f)
    Spacer(Modifier.height(5.dp))
    SellerText(title, 20.1f, true, SellerInk, line = 22.6f)
    Spacer(Modifier.height(5.dp))
    SellerText(description, line = 12.6f)
    Spacer(Modifier.height(27.dp))
}

@Composable internal fun SellerInput(label: String, value: String, onChange: (String) -> Unit,
    placeholder: String, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true, login: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SellerText(label, bold = true, color = Color(0xFF404040), line = 13f)
        Spacer(Modifier.height(6.dp))
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag("input-$label"), enabled = enabled,
            singleLine = true, textStyle = TextStyle(fontFamily = SellerFont, fontSize = 11.73.sp,
                lineHeight = 15.sp, color = Color(0xFF232323), platformStyle = PlatformTextStyle(includeFontPadding = false)),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(SellerGreen), decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.3.dp)).background(Color.White)
                    .border(0.85.dp, Color(0xFFDDE2DC), RoundedCornerShape(9.3.dp))
                    .heightIn(min = 40.2.dp).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (login) {
                        if (password) SellerText("••", 10.3f, color = Color(0xFF737A72))
                        else Image(painterResource(R.drawable.seller_person), null, Modifier.size(16.3.dp))
                        Spacer(Modifier.width(8.6.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) SellerText(placeholder, if (login) 10.3f else 11.73f,
                            bold = login, color = if (login || password) Color(0x80737A72) else Color(0x80232323), line = 15f)
                        inner()
                    }
                    if (password) Box(Modifier.padding(start = 6.dp).size(24.dp)
                        .clickable(enabled = enabled) { visible = !visible }, contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.seller_eye),
                            if (visible) "Sembunyikan kata sandi" else "Tampilkan kata sandi", Modifier.size(16.dp, 14.dp))
                    }
                }
            })
    }
}

@Composable internal fun SellerAction(label: String, enabled: Boolean = true, busy: Boolean = false,
    dark: Boolean = false, labelSize: Float = 13.4f, onClick: () -> Unit) {
    Button(onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 41.dp),
        shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFF477B35) else SellerGreen,
            disabledContainerColor = Color(0xFFDFE3DE), disabledContentColor = Color(0xFF9CA09B)),
        border = BorderStroke(0.85.dp, if (enabled && !busy) Color(0xFF267621) else Color(0xFFCFD4CE))) {
        if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        SellerText(if (busy) "Memproses…" else label, labelSize, true,
            if (enabled && !busy) Color.White else Color(0xFF9CA09B), line = 20f)
    }
}

@Composable internal fun SellerFooter(prefix: String, action: String, busy: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 30.dp).heightIn(min = 32.dp)
        .clickable(enabled = !busy, onClick = onClick), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        SellerText("$prefix "); SellerText(action, bold = true, color = Color(0xFF36912E))
    }
}

@Composable internal fun SellerLoginScreen(busy: Boolean, onLogin: (String, String) -> Unit, onRegister: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    SellerLayout(decorated = true, inset = 22.dp) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Image(painterResource(R.drawable.panenku_symbol), "Panenku", Modifier.size(30.dp, 34.4.dp))
            Spacer(Modifier.height(25.7.dp))
            SellerText("Selamat Datang Kembali!", 20.58f, true, SellerInk, line = 31f)
            Spacer(Modifier.height(3.dp))
            SellerText("Kelola hasil panen, stok, dan pesanan toko tani Anda.", 10.29f)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.7.dp)).background(Color(0xFFEEF8EC))
                .padding(horizontal = 10.dp, vertical = 8.6.dp), verticalAlignment = Alignment.Top) {
                Image(painterResource(R.drawable.seller_cube), null, Modifier.padding(top = 1.dp).size(15.4.dp))
                Spacer(Modifier.width(7.7.dp))
                SellerText("Gunakan akun petani yang sudah didaftarkan. Produk dapat dijual setelah identitas disetujui admin.",
                    8.575f, color = Color(0xFF275823), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            SellerInput("Email atau nomor HP", email, { email = it }, "contoh@email.com", keyboard = KeyboardType.Email, enabled = !busy, login = true)
            Spacer(Modifier.height(20.dp))
            SellerInput("Kata sandi", password, { password = it }, "Masukkan kata sandi", password = true, enabled = !busy, login = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.heightIn(min = 31.dp).clickable(enabled = !busy) {
                    info = "Pemulihan kata sandi belum tersedia. Hubungi pengelola Panenku untuk bantuan akses akun."
                }, contentAlignment = Alignment.Center) { SellerText("Lupa kata sandi?", 10.29f, true, Color(0xFF36912E)) }
            }
            Spacer(Modifier.height(2.dp))
            SellerAction("Masuk", busy = busy) {
                if (email.isBlank() || password.isBlank()) info = "Isi email dan kata sandi terlebih dahulu."
                else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) info = "Gunakan email akun Anda. Masuk dengan nomor HP belum tersedia."
                else onLogin(email.trim(), password)
            }
            Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE4E5E4))
                SellerText("atau", 9.43f, color = Color(0xFFA3A3A3), modifier = Modifier.padding(horizontal = 9.dp))
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE4E5E4))
            }
            OutlinedButton(onClick = { info = "Login Google belum tersedia. Silakan masuk menggunakan email dan kata sandi akun Panenku." },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 40.3.dp), shape = CircleShape,
                contentPadding = PaddingValues(10.dp), border = BorderStroke(0.85.dp, Color(0xFFDFE3DF)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                Box(Modifier.size(18.dp).background(Color(0xFFF3F6FB), CircleShape), contentAlignment = Alignment.Center) {
                    SellerText("G", 11.15f, true, Color(0xFF4285F4))
                }
                Spacer(Modifier.width(8.6.dp)); SellerText("Lanjutkan dengan Google", 10.29f, true, Color(0xFF1C1C1C))
            }
            Spacer(Modifier.height(20.dp))
        }
        SellerFooter("Belum memiliki akun?", "Daftar sekarang", busy, onRegister)
    }
    if (info != null) AlertDialog(onDismissRequest = { info = null }, title = { Text("Bantuan masuk") }, text = { Text(info!!) },
        confirmButton = { TextButton(onClick = { info = null }) { Text("Mengerti") } })
}

@Composable internal fun SellerRegistrationScreen(busy: Boolean, onRegister: (RegistrationInput) -> Unit,
    onLogin: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && phone.length in 8..15 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
        password.length >= 8 && password.toByteArray(Charsets.UTF_8).size <= 72 && agreed
    SellerLayout(plain = true) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SellerStep(2, onBack, busy)
            SellerHeading("Data akun", "Lengkapi identitas Anda", "Gunakan data aktif agar informasi transaksi dan pengiriman dapat dikonfirmasi.")
            Column(verticalArrangement = Arrangement.spacedBy(13.5.dp)) {
                SellerInput("Nama lengkap", name, { name = it.take(255) }, "Sesuai identitas", enabled = !busy)
                SellerInput("Nomor HP", phone, { phone = it.filter(Char::isDigit).take(15) }, "08xxxxxxxxxx", keyboard = KeyboardType.Phone, enabled = !busy)
                SellerInput("Email", email, { email = it.take(255) }, "contoh@email.com", keyboard = KeyboardType.Email, enabled = !busy)
                SellerInput("Kata sandi", password, { password = it }, "Minimal 8 karakter", password = true, enabled = !busy)
            }
            Spacer(Modifier.height(11.7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(agreed, { agreed = it }, enabled = !busy, modifier = Modifier.size(17.dp),
                        colors = CheckboxDefaults.colors(checkedColor = SellerGreen, uncheckedColor = Color(0xFFBBBBBB)))
                }
                Spacer(Modifier.width(7.5.dp))
                SellerText("Saya menyetujui Syarat & Ketentuan dan Kebijakan Privasi Panenku.", color = Color(0xFF404040),
                    modifier = Modifier.weight(1f).clickable(enabled = !busy) { agreed = !agreed })
            }
            Spacer(Modifier.height(17.dp))
            SellerAction("Lanjut Verifikasi", valid, busy) {
                onRegister(RegistrationInput(OpeningRole.FARMER, name.trim(), phone, email.trim(), password, "", ""))
            }
            Spacer(Modifier.height(24.dp))
        }
        SellerFooter("Sudah memiliki akun?", "Masuk", busy, onLogin)
    }
}
