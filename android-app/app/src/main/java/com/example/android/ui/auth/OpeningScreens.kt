package id.panenku.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import id.panenku.app.R
import id.panenku.app.ui.theme.PanenkuColors
import kotlin.math.max

enum class OpeningRole(val apiValue: String, val title: String) {
    BUYER("pembeli", "Pembeli"), FARMER("petani", "Petani")
}
internal enum class OpeningPage { SPLASH, ROLE, LOGIN, REGISTER, VERIFICATION, WAITING }
data class RegistrationInput(val role: OpeningRole, val name: String, val phone: String,
    val email: String, val password: String, val address: String, val storeName: String)
internal val OpeningPaper = Color(0xFFFAFAFA)
internal val OpeningMint = Color(0xFFE6F3E1)
internal val OpeningForest = Color(0xFF193A18)

data class FarmerVerificationInput(val storeName: String, val location: String, val area: String, val photoUri: String)
data class FarmerOnboardingState(val draftReady: Boolean = false, val accountCreated: Boolean = false,
    val waiting: Boolean = false, val email: String = "")

/** Account role from the server remains authoritative after login. */
@Composable
fun PanenkuOpeningFlow(busy: Boolean, onLogin: (String, String) -> Unit, onRegister: (RegistrationInput) -> Unit,
    farmer: FarmerOnboardingState = FarmerOnboardingState(), onPrepareFarmer: (RegistrationInput) -> Unit = {},
    onVerifyFarmer: (FarmerVerificationInput) -> Unit = {}, onVerificationExit: () -> Unit = {}) {
    var pageName by rememberSaveable { mutableStateOf(OpeningPage.SPLASH.name) }
    var selectedName by rememberSaveable { mutableStateOf(OpeningRole.BUYER.name) }
    val page = OpeningPage.valueOf(pageName)
    val selected = OpeningRole.valueOf(selectedName)
    val savedForms = rememberSaveableStateHolder()
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    fun back() {
        if (busy) return
        if (page == OpeningPage.WAITING || (page == OpeningPage.VERIFICATION && farmer.accountCreated)) {
            onVerificationExit(); pageName = OpeningPage.LOGIN.name
        } else pageName = if (page == OpeningPage.VERIFICATION) OpeningPage.REGISTER.name else OpeningPage.ROLE.name
    }
    BackHandler(page !in listOf(OpeningPage.ROLE, OpeningPage.SPLASH)) { back() }
    LaunchedEffect(farmer.waiting) { if (farmer.waiting) { selectedName = OpeningRole.FARMER.name; pageName = OpeningPage.WAITING.name } }
    LaunchedEffect(page, farmer.draftReady, farmer.accountCreated) {
        // Password drafts are not persisted to disk. Process death returns to the account form safely.
        if (page == OpeningPage.VERIFICATION && !farmer.draftReady && !farmer.accountCreated) pageName = OpeningPage.REGISTER.name
    }
    AnimatedContent(targetState = page, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "opening-page") { destination ->
        savedForms.SaveableStateProvider(destination.name) {
        when (destination) {
            OpeningPage.SPLASH -> AnimatedOpeningSplash { pageName = OpeningPage.ROLE.name }
            OpeningPage.ROLE -> OpeningRoleScreen(selected, { selectedName = it.name },
                { pageName = OpeningPage.REGISTER.name }, { pageName = OpeningPage.LOGIN.name }, { dispatcher?.onBackPressed() })
            OpeningPage.LOGIN -> OpeningLoginScreen(busy, onLogin, { pageName = OpeningPage.REGISTER.name }, ::back, selected)
            OpeningPage.REGISTER -> OpeningRegistrationScreen(selected, busy, { input ->
                if (input.role == OpeningRole.FARMER) { onPrepareFarmer(input); pageName = OpeningPage.VERIFICATION.name }
                else onRegister(input)
            }, { pageName = OpeningPage.LOGIN.name }, ::back)
            OpeningPage.VERIFICATION -> OpeningVerificationScreen(busy, onVerifyFarmer, {
                onVerificationExit(); pageName = OpeningPage.LOGIN.name
            }, ::back, farmer.accountCreated)
            OpeningPage.WAITING -> OpeningWaitingScreen(busy, {
                onVerificationExit(); pageName = OpeningPage.LOGIN.name
            })
        }
        }
    }
}

/** A single timeline respects Android's animation duration scale, including disabled animations. */
@Composable fun AnimatedOpeningSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2400, easing = LinearEasing))
        finished()
    }
    OpeningSplashFrame(progress.value)
}

/** Four supplied frames: paper → circles → logo → mint wipe. No baked-in phone chrome. */
@Composable fun OpeningSplashFrame(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    fun phase(start: Float, end: Float) = ((p - start) / (end - start)).coerceIn(0f, 1f)
    val circles = FastOutSlowInEasing.transform(phase(0.08f, 0.30f))
    val reveal = FastOutSlowInEasing.transform(phase(0.31f, 0.52f))
    val wipe = FastOutSlowInEasing.transform(phase(0.76f, 1f))
    Box(Modifier.fillMaxSize().background(OpeningPaper).testTag("opening-splash")) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 375f
            val topRadius = 115f * unit
            drawCircle(OpeningMint, topRadius,
                center = androidx.compose.ui.geometry.Offset(size.width + topRadius * (1f - circles), 155f * unit), alpha = circles)
            val radius = 185f * unit
            val center = androidx.compose.ui.geometry.Offset(-radius * (1f - circles), size.height)
            drawCircle(Color(0xFFEDF6E9), radius, center, alpha = circles)
            drawCircle(OpeningMint, radius * 0.88f, center, alpha = circles)
            drawCircle(Color(0xFFD0E7C8), radius * 0.88f, center, alpha = circles, style = Stroke(unit))
        }
        Image(painterResource(R.drawable.panenku_brand), "Panenku", Modifier.align(Alignment.Center)
            .width(174.dp).graphicsLayer {
                alpha = reveal * (1f - phase(0.74f, 0.88f))
                scaleX = 0.92f + reveal * 0.08f; scaleY = scaleX
                translationY = (1f - reveal) * 14.dp.toPx()
            })
        if (wipe > 0f) Canvas(Modifier.fillMaxSize()) {
            // The last supplied frame is a full mint field with a large leaf-like arc.
            drawCircle(OpeningMint, max(size.width, size.height) * 1.6f * wipe,
                androidx.compose.ui.geometry.Offset(0f, size.height))
            val radius = size.width * 1.12f
            val center = androidx.compose.ui.geometry.Offset(-size.width * 0.10f, size.height)
            drawCircle(Color(0xFFEDF6E9), radius, center, alpha = wipe)
            drawCircle(Color(0xFFE3F0DD), radius * 0.84f, center, alpha = wipe)
            drawCircle(Color(0xFFD0E7C8), radius * 0.84f, center, alpha = wipe, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable internal fun OpeningLayout(decorated: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF3FBF2), OpeningPaper), endY = 500f)), contentAlignment = Alignment.TopCenter) {
        if (decorated) Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 375f
            drawCircle(Color(0xFFE4F2DF), 88f * unit, androidx.compose.ui.geometry.Offset(size.width, 110f * unit))
            drawCircle(Color(0xFFE7F4E2), 46f * unit, androidx.compose.ui.geometry.Offset(-26f * unit, 174f * unit))
        }
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().widthIn(max = 480.dp).fillMaxWidth()
            .fillMaxHeight().padding(horizontal = 22.dp), content = content)
    }
}
@Composable internal fun OpeningBack(onBack: () -> Unit, busy: Boolean = false) {
    FilledIconButton(onClick = onBack, enabled = !busy, modifier = Modifier.size(40.dp), shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = OpeningForest)) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", Modifier.size(18.dp))
    }
}
@Composable internal fun OpeningStep(step: Int, onBack: () -> Unit, busy: Boolean = false, total: Int = 2) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        OpeningBack(onBack, busy)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Langkah $step dari $total", fontSize = 11.sp, color = PanenkuColors.Muted)
            LinearProgressIndicator(progress = { if (step == 1) 0.33f else step.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = PanenkuColors.Green, trackColor = Color(0xFFE4EAE4), drawStopIndicator = {})
        }
    }
}
@Composable internal fun OpeningFooter(prefix: String, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = PanenkuColors.Muted, fontWeight = FontWeight.Normal)) { append(prefix); append(" ") }
            withStyle(SpanStyle(color = PanenkuColors.Green, fontWeight = FontWeight.SemiBold)) { append(action) }
        }, fontSize = 12.sp)
    }
}
@Composable internal fun OpeningAction(label: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = PanenkuColors.Green),
        border = BorderStroke(1.dp, if (enabled && !busy) Color(0xFF2B8B25) else Color.Transparent)) {
        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(if (busy) "Memproses…" else label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun OpeningRoleScreen(selected: OpeningRole, onSelect: (OpeningRole) -> Unit,
    onContinue: () -> Unit, onLogin: () -> Unit, onBack: () -> Unit) {
    OpeningLayout {
        OpeningStep(1, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(32.dp))
            Text("Buat akun baru", color = Color(0xFF298A23), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Anda ingin menggunakan\nPanenku sebagai?", color = OpeningForest, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 31.sp)
            Spacer(Modifier.height(12.dp))
            Text("Pilih peran sesuai kebutuhan. Peran tidak dapat diubah setelah akun diverifikasi.", color = PanenkuColors.Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(34.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OpeningRole.entries.forEach { role ->
                    val active = selected == role
                    Surface(shape = RoundedCornerShape(12.dp), color = if (active) Color(0xFFF3FAF1) else Color.White,
                        border = BorderStroke(1.dp, if (active) PanenkuColors.Green else Color(0xFFE4E6E4)),
                        modifier = Modifier.fillMaxWidth().selectable(active, role = Role.RadioButton, onClick = { onSelect(role) }).testTag("role-${role.apiValue}")) {
                        Row(Modifier.padding(12.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(OpeningMint), contentAlignment = Alignment.Center) {
                                Icon(if (role == OpeningRole.BUYER) Icons.Outlined.ShoppingCart else Icons.Outlined.Inventory2,
                                    null, Modifier.size(20.dp), tint = Color(0xFF2A9625))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(role.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF202420))
                                Spacer(Modifier.height(3.dp))
                                Text(if (role == OpeningRole.BUYER) "Belanja hasil tani langsung dari petani lokal." else "Jual hasil panen dan kelola pesanan toko.",
                                    fontSize = 11.sp, lineHeight = 16.sp, color = PanenkuColors.Muted)
                            }
                            RadioButton(selected = active, onClick = null, modifier = Modifier.size(20.dp),
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2A9625), unselectedColor = Color(0xFFC5CBC5)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            OpeningAction("Lanjutkan", onClick = onContinue)
            Spacer(Modifier.height(20.dp))
        }
        OpeningFooter("Sudah memiliki akun?", "Masuk", onClick = onLogin)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable internal fun OpeningInput(label: String, value: String, onChange: (String) -> Unit,
    placeholder: String = "", password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false, enabled: Boolean = true, login: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF454B45))
        BasicTextField(value = value, onValueChange = onChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("input-$label"),
            singleLine = !multiline, minLines = if (multiline) 2 else 1,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF263126)),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(PanenkuColors.Green),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFE0E5E0), RoundedCornerShape(12.dp))
                    .heightIn(min = if (multiline) 66.dp else 44.dp).padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (login) {
                        if (password) Text("••", fontSize = 13.sp, color = Color(0xFF859085))
                        else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(17.dp), tint = Color(0xFF7E897E))
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF9AA39A))
                        inner()
                    }
                    if (password) IconButton(onClick = { visible = !visible }, enabled = enabled, modifier = Modifier.size(26.dp)) {
                        Icon(if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            if (visible) "Sembunyikan kata sandi" else "Tampilkan kata sandi", Modifier.size(18.dp), tint = Color(0xFF738073))
                    }
                }
            })
    }
}

@Composable fun OpeningLoginScreen(busy: Boolean, onLogin: (String, String) -> Unit, onRegister: () -> Unit,
    onBack: () -> Unit, role: OpeningRole = OpeningRole.BUYER) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    val farmer = role == OpeningRole.FARMER
    OpeningLayout(decorated = true) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(28.dp))
            Image(painterResource(R.drawable.panenku_symbol), "Panenku", Modifier.size(34.dp, 38.dp))
            Spacer(Modifier.height(25.dp))
            Text("Selamat Datang Kembali!", fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = OpeningForest)
            Spacer(Modifier.height(8.dp))
            Text(if (farmer) "Kelola hasil panen, stok, dan pesanan toko tani Anda."
                else "Panen segar, langsung dari petani. Masuk ke akunmu dan temukan hasil panen terbaik hari ini.",
                fontSize = 12.sp, lineHeight = 17.sp, color = PanenkuColors.Muted)
            if (farmer) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFEDF7E9)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Inventory2, null, Modifier.size(17.dp), tint = Color(0xFF3D7434))
                    Spacer(Modifier.width(10.dp))
                    Text("Gunakan akun petani yang sudah didaftarkan. Produk dapat dijual setelah identitas disetujui admin.",
                        fontSize = 10.sp, lineHeight = 14.sp, color = Color(0xFF43713B))
                }
            }
            Spacer(Modifier.height(20.dp))
            OpeningInput("Email", email, { email = it }, "contoh@email.com", keyboard = KeyboardType.Email, enabled = !busy, login = true)
            Spacer(Modifier.height(20.dp))
            OpeningInput("Kata sandi", password, { password = it }, "Masukkan kata sandi", password = true, enabled = !busy, login = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { info = "Pemulihan kata sandi belum tersedia. Hubungi pengelola Panenku untuk bantuan akses akun." },
                    enabled = !busy, contentPadding = PaddingValues(vertical = 6.dp), modifier = Modifier.heightIn(min = 34.dp)) {
                    Text("Lupa kata sandi?", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            OpeningAction("Masuk", email.isNotBlank() && password.isNotBlank(), busy) { onLogin(email.trim(), password) }
            Spacer(Modifier.height(if (farmer) 24.dp else 66.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE6E9E6))
                Text("atau", modifier = Modifier.padding(horizontal = 12.dp), fontSize = 11.sp, color = Color(0xFF9AA29A))
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE6E9E6))
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { info = "Login Google belum tersedia. Silakan masuk menggunakan email dan kata sandi akun Panenku." },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5E0)), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                Box(Modifier.size(21.dp).clip(CircleShape).background(Color(0xFFF0F5FF)), contentAlignment = Alignment.Center) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("Lanjutkan dengan Google", fontSize = 12.sp, color = Color(0xFF202420), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
        OpeningFooter("Belum memiliki akun?", "Daftar sekarang", !busy, onRegister)
        Spacer(Modifier.height(14.dp))
    }
    if (info != null) AlertDialog(onDismissRequest = { info = null }, title = { Text("Bantuan masuk") }, text = { Text(info!!) },
        confirmButton = { TextButton(onClick = { info = null }) { Text("Mengerti") } })
}

@Composable fun OpeningRegistrationScreen(role: OpeningRole, busy: Boolean, onRegister: (RegistrationInput) -> Unit,
    onLogin: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val farmer = role == OpeningRole.FARMER
    val valid = name.isNotBlank() && phone.length in 8..15 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
        password.length >= 8 && password.toByteArray(Charsets.UTF_8).size <= 72 && agreed && (farmer || address.isNotBlank())
    OpeningLayout {
        OpeningStep(2, onBack, busy, if (farmer) 3 else 2)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(22.dp))
            if (farmer) {
                Text("Data akun", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF398D2D))
                Spacer(Modifier.height(14.dp))
            }
            Text(if (farmer) "Lengkapi identitas Anda" else "Bergabung dengan Panenku", fontSize = 21.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, color = OpeningForest)
            Spacer(Modifier.height(8.dp))
            Text(if (farmer) "Gunakan data aktif agar informasi transaksi dan pengiriman dapat dikonfirmasi."
                else "Satu langkah lebih dekat dengan hasil panen segar dan pasar yang lebih luas.",
                fontSize = 12.sp, lineHeight = 17.sp, color = PanenkuColors.Muted)
            Spacer(Modifier.height(if (farmer) 30.dp else 36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OpeningInput("Nama lengkap", name, { name = it.take(255) }, "Sesuai identitas", enabled = !busy)
                OpeningInput("Nomor HP", phone, { phone = it.filter(Char::isDigit).take(15) }, "08xxxxxxxxxx", keyboard = KeyboardType.Phone, enabled = !busy)
                OpeningInput("Email", email, { email = it.take(255) }, "contoh@email.com", keyboard = KeyboardType.Email, enabled = !busy)
                if (!farmer) OpeningInput("Alamat utama", address, { address = it.take(2000) }, "Nama jalan, kecamatan, kota", multiline = true, enabled = !busy)
                OpeningInput("Kata sandi", password, { password = it }, "Minimal 8 karakter", password = true, enabled = !busy)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(agreed, { agreed = it }, enabled = !busy, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(6.dp))
                Text("Saya menyetujui Syarat & Ketentuan dan Kebijakan Privasi Panenku.", fontSize = 11.sp, lineHeight = 16.sp,
                    color = Color(0xFF424842), modifier = Modifier.weight(1f).padding(top = 3.dp))
            }
            Spacer(Modifier.height(20.dp))
            OpeningAction(if (farmer) "Lanjut Verifikasi" else "Buat Akun Pembeli", valid, busy) {
                onRegister(RegistrationInput(role, name.trim(), phone, email.trim(), password, address.trim(), ""))
            }
            Spacer(Modifier.height(24.dp))
        }
        OpeningFooter("Sudah memiliki akun?", "Masuk", !busy, onLogin)
        Spacer(Modifier.height(14.dp))
    }
}
