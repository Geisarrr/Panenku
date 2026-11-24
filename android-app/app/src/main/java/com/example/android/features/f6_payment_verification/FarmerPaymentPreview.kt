package id.panenku.app.features.f6_payment_verification

import id.panenku.app.features.f5_order_status.OrderDetailScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.ui.components.*
import id.panenku.app.ui.theme.PanenkuTheme




@Preview(showBackground = true, widthDp = 375, heightDp = 1100)
@Composable private fun PaymentPreview() { PanenkuTheme { OrderDetailScreen(SampleData.order.copy(status = OrderStatus.REVIEW), {}, { _, _ -> }, {}) } }
