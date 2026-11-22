package id.panenku.app.ui.connected

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectedAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun opensSellerLoginThroughRoleSelectionWithoutDemoAccess() {
        compose.setContent { PanenkuTheme { ConnectedPanenkuApp() } }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("role-petani").performClick()
        compose.onNodeWithText("Sudah memiliki akun? Masuk").performClick()
        compose.onNodeWithText("Selamat Datang Kembali!").assertExists()
        compose.onNodeWithTag("input-Email atau nomor HP").assertExists()
        compose.onNodeWithText("Masuk").assertIsEnabled()
        compose.onNodeWithText("Mode demo").assertDoesNotExist()
    }
}
