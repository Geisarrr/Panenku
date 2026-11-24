package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*

@Composable internal fun BuyerNavigation(selected:String,go:(String)->Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceAround) {
        listOf("catalog" to "Beranda","buyer-stock" to "Stok","cart" to "Tambah belanja","orders" to "Pesanan","account" to "Profil").forEachIndexed { i,(route,label)->
            if(i==2) Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF6AC662),Color(0xFF36912E))))
                .clickable(role=Role.Button){go(route)},contentAlignment=Alignment.Center){FIcon(R.drawable.buyer8_nav_plus,24.dp,label,Color.White)}
            else Column(Modifier.weight(1f).clickable(role=Role.Tab){go(route)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                FIcon(listOf(R.drawable.buyer8_nav_home,R.drawable.buyer8_nav_stock,0,R.drawable.buyer8_nav_orders,R.drawable.buyer8_nav_user)[i],24.dp,label,if(selected==route) FGreen else Color(0xFFA3A3A3))
                FText(label,12f,color=if(selected==route) FGreen else Color(0xFFA3A3A3))
            }
        }
    }
}
@Composable internal fun BCompactHeader(title:String,orderId:String,back:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(40.dp).background(Color.White,CircleShape).border(1.dp,FBorder,CircleShape).clickable(role=Role.Button,onClick=back),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer_shared_back,24.dp,"Kembali")}
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)){FText("#${shortId(orderId)}",14f,color=FMuted,line=20f);FText(title,16f,600,line=24f)}
    }
}
@Composable internal fun BOrderLayout(title:String,id:String,back:()->Unit,footer:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().background(FPaper)){
        BCompactHeader(title,id,back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        footer?.let{Box(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=24.dp,vertical=12.dp)){it()}}
    }
}
@Composable internal fun BMenuRow(icon:Int,title:String,body:String,click:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=click),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        FIcon(icon,22.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){FText(title,14f,600);FText(body,12f,color=FMuted)}
        FIcon(R.drawable.buyer_shared_chevron,16.dp)
    }
}
@Composable internal fun BOrderItemRows(items:List<JsonObject>,photos:List<JsonObject>) {
    items.forEach { item ->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            val photo=photos.firstOrNull{it.text("id")==item.text("productId")}?.text("photoAccessUrl").orEmpty()
            FPhoto(photo,item.text("productNameSnapshot"),Modifier.size(48.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { FText(item.text("productNameSnapshot"),14f,500,FForest,line=20f);FText("${quantity(item.text("quantityKg"))} Kg",14f,color=FMuted,line=20f) }
            FText(money(item.long("itemSubtotal")),14f,600,FForest,line=20f)
        }
    }
}
@Composable internal fun BHistory(title:String,rows:List<JsonObject>,pending:String?=null) {
    FCard(title){
        rows.forEach{event->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Box(Modifier.size(24.dp).background(Color(0xFFE3F7E1),CircleShape),contentAlignment=Alignment.Center){FText("✓",12f,600,FForest)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){FText(refundStatus(event.text("toStatus")),13f,600);FText("${dateTimeLabel(event.text("changedAt"))} · ${event.text("note")}",12f,color=FMuted)}
        }}
        if(rows.isEmpty()) FText("Riwayat belum tersedia.",12f,color=FMuted)
        pending?.let {FText(it,13f,600,color=FMuted)}
    }
}
internal fun refundStatus(value:String)=when(value){"diajukan"->"Pengajuan refund dikirim";"disetujui"->"Disetujui petani";"ditolak"->"Ditolak petani";"menunggu_konfirmasi_dana"->"Menunggu konfirmasi dana";"dieskalasi"->"Dalam peninjauan admin";"selesai"->"Refund selesai";else->value.replace('_',' ')}

@Composable internal fun BOrderAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,primary:Boolean=true) {
    Row(modifier.fillMaxWidth().background(if(primary)FGreen else Color.White,RoundedCornerShape(1000.dp)).border(1.dp,if(primary)Color(0xFF2C6F26) else Color(0xFFDFE3DF),RoundedCornerShape(1000.dp))
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=14.dp,vertical=6.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        FText(label,13f,600,if(primary)Color.White else FInk,line=16f)
        Spacer(Modifier.width(6.dp));FIcon(R.drawable.buyer8_action,14.dp,tint=if(primary)Color.White else FInk)
    }
}
