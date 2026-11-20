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
    @Test fun startsAtRealLoginAndDoesNotOfferDemoAccess() {
        compose.setContent { PanenkuTheme { ConnectedPanenkuApp() } }
        compose.onNodeWithText("Selamat Datang Kembali!").assertExists()
        compose.onNodeWithText("Masuk").assertIsNotEnabled()
        compose.onNodeWithText("Mode demo").assertDoesNotExist()
    }
}
