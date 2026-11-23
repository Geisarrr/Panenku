package id.panenku.app.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h862dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SellerAuthTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun input(label: String, value: String) = compose.onNodeWithTag("input-$label").performScrollTo().performTextInput(value)

    @Test fun sellerRegistrationRequiresConsentAndSubmitsOnlyAccountDraft() {
        var submitted: RegistrationInput? = null
        compose.setContent { PanenkuTheme { OpeningRegistrationScreen(OpeningRole.FARMER, false, { submitted = it }, {}, {}) } }
        compose.onNodeWithText("Langkah 2 dari 3").assertIsDisplayed()
        compose.onNodeWithText("Alamat utama").assertDoesNotExist()
        input("Nama lengkap", "Petani Uji"); input("Nomor HP", "081234567890")
        input("Email", "petani@example.test"); input("Kata sandi", "password123")
        compose.onNodeWithText("Lanjut Verifikasi").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Lanjut Verifikasi").performClick()
        assertEquals(OpeningRole.FARMER, submitted!!.role)
        assertEquals("petani@example.test", submitted!!.email)
        assertEquals("", submitted!!.storeName)
    }

    @Test fun sellerLoginValidatesBeforeCallingBackendAndDoesNotFakeGoogleLogin() {
        var calls = 0
        compose.setContent { PanenkuTheme { OpeningLoginScreen(false, { _, _ -> calls++ }, {}, {}, OpeningRole.FARMER) } }
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.onNodeWithText("Isi email dan kata sandi terlebih dahulu.").assertExists()
        compose.onNodeWithText("Mengerti").performClick()
        input("Email atau nomor HP", "petani@example.test"); input("Kata sandi", "password123")
        compose.onNodeWithText("Masuk").performClick()
        assertEquals(1, calls)
        compose.onNodeWithText("Lanjutkan dengan Google").performScrollTo().performClick()
        compose.onNodeWithText("Login Google belum tersedia.", substring = true).assertExists()
        assertEquals(1, calls)
    }

    @Test fun largeFontVerificationScrollsAndRequiresDocument() {
        var submissions = 0
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
            PanenkuTheme { OpeningVerificationScreen(false, { submissions++ }, {}, {}) }
        } }
        input("Nama toko tani", "Kebun Uji"); input("Lokasi toko", "Malang")
        compose.onNodeWithText("Area pengantaran").assertDoesNotExist()
        compose.onNodeWithTag("verification-photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kirim Pendaftaran Petani").performScrollTo().performClick()
        compose.onNodeWithText("Lengkapi data toko dan pilih foto dokumen terlebih dahulu.").assertExists()
        assertEquals(0, submissions)
    }

    @Test fun renderSellerAndUnchangedBuyerScreens() {
        var page by mutableIntStateOf(0)
        compose.setContent { PanenkuTheme { CompositionLocalProvider(LocalSellerInsets provides WindowInsets(top = 47.dp, bottom = 34.dp)) { when (page) {
            0 -> OpeningLoginScreen(false, { _, _ -> }, {}, {}, OpeningRole.FARMER)
            1 -> OpeningRegistrationScreen(OpeningRole.FARMER, false, {}, {}, {})
            2 -> OpeningVerificationScreen(false, {}, {}, {})
            3 -> OpeningWaitingScreen(false, {})
            4 -> OpeningLoginScreen(false, { _, _ -> }, {}, {}, OpeningRole.BUYER)
            else -> OpeningRegistrationScreen(OpeningRole.BUYER, false, {}, {}, {})
        } } } }
        listOf("login", "registrasi", "verifikasi", "menunggu", "pembeli-login", "pembeli-registrasi").forEachIndexed { i, name ->
            compose.runOnIdle { page = i }
            compose.onRoot().captureRoboImage("build/outputs/screenshots/seller-$name.png")
        }
    }

    @Test fun reducedSystemFontDoesNotShrinkSellerBelowDesign() {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            PanenkuTheme { OpeningRegistrationScreen(OpeningRole.FARMER, false, {}, {}, {}) }
        } }
        val regular = compose.onNodeWithText("Lengkapi identitas Anda").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fontScale = 0.7f }
        val small = compose.onNodeWithText("Lengkapi identitas Anda").fetchSemanticsNode().boundsInRoot
        assertEquals(regular.width, small.width, 1f)
        assertEquals(regular.height, small.height, 1f)
    }
}
