package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*

@Composable private fun BIconAction(icon:Int,label:String,click:()->Unit) {
    Box(Modifier.size(40.dp).clickable(role=Role.Button,onClick=click),contentAlignment=Alignment.Center){FIcon(icon,24.dp,label)}
}
@Composable internal fun BCatalogHeader(title:String,back:()->Unit,cart:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(36.dp).background(Color.White,CircleShape).clickable(role=Role.Button,onClick=back),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer_shared_back,20.dp,"Kembali")}
        FText(title,18f,700,modifier=Modifier.weight(1f),line=27f)
        cart?.let{BIconAction(R.drawable.buyer15_cart,"Keranjang",it)}
    }
}
@Composable internal fun BChoice(selected:Boolean,label:String,click:()->Unit) {
    Box(Modifier.clip(CircleShape).background(if(selected)Color(0xFF36912E) else Color.White).border(1.dp,if(selected)Color(0xFF36912E) else FBorder,CircleShape).clickable(role=Role.Tab,onClick=click).padding(horizontal=12.dp,vertical=7.dp)) { FText(label,11f,600,if(selected)Color.White else FMuted,line=14f) }
}
@Composable private fun BSearchPrompt(click:()->Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Color.White).border(1.dp,FBorder,CircleShape).clickable(role=Role.Button,onClick=click).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        FIcon(R.drawable.buyer15_search,20.dp);FText("Cari bahan segar...",12f,color=FMuted)
    }
}
@Composable internal fun BProductTile(p:JsonObject,vm:ConnectedViewModel,open:(String)->Unit,modifier:Modifier=Modifier,busy:Boolean=false) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp,FBorder,RoundedCornerShape(16.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        FPhoto(p.text("photoAccessUrl"),p.text("name"),Modifier.fillMaxWidth().height(151.dp).clickable(role=Role.Button){open(p.text("id"))})
        Column(Modifier.fillMaxWidth().clickable(role=Role.Button){open(p.text("id"))},verticalArrangement=Arrangement.spacedBy(2.dp)){
            FText(p.text("name"),14f,600,line=18f);FText("Harga per 1 kg",10f,color=FMuted,line=13f)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            FText(money(p.long("pricePerKg")),14f,700,modifier=Modifier.weight(1f),line=18f)
            val available=p.flag("availableForPurchase") && (p.text("stockAvailable").toBigDecimalOrNull()?.signum() ?: 0)>0
            Box(Modifier.size(28.dp).background(if(available)Color(0xFFE3F7E1) else FBorder,CircleShape).clickable(enabled=available && !busy,role=Role.Button){vm.add(p.text("id"),"1")},contentAlignment=Alignment.Center){FIcon(R.drawable.buyer8_nav_plus,18.dp,"Tambah ${p.text("name")}",FGreen)}
        }
    }
}
@Composable private fun BProductGrid(products:List<JsonObject>,vm:ConnectedViewModel,open:(String)->Unit,busy:Boolean) {
    products.chunked(2).forEach { row-> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        row.forEach{BProductTile(it,vm,open,Modifier.weight(1f),busy)};if(row.size==1)Spacer(Modifier.weight(1f))
    }}
}
@Composable internal fun BuyerHomeScreen(s:ConnectedState,vm:ConnectedViewModel,search:()->Unit,cart:()->Unit,notices:()->Unit,addresses:()->Unit,product:(String)->Unit) {
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f).clickable(role=Role.Button,onClick=addresses),verticalArrangement=Arrangement.spacedBy(3.dp)){
                FText("Panenku · Segar dari petani",10f,color=FForest,line=13f);FText("Kirim ke",12f,color=FMuted,line=15f)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    FIcon(R.drawable.buyer_shared_location,16.dp)
                    FText(s.profile?.text("deliveryAddress").orEmpty().ifBlank{"Pilih alamat pengiriman"}.take(42),14f,600,line=18f)
                }
            }
            Box { BIconAction(R.drawable.buyer8_bell,"Notifikasi",notices)
                if(s.unreadNotificationCount>0)Box(Modifier.align(Alignment.TopEnd).background(Color(0xFFEF2D3E),CircleShape).padding(horizontal=4.dp,vertical=2.dp)){FText(s.unreadNotificationCount.coerceAtMost(99).toString(),9f,700,Color.White,line=11f)}
            }
            BIconAction(R.drawable.buyer15_cart,"Keranjang",cart)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            BSearchPrompt(search)
            Box(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF36912E))){
                Image(painterResource(R.drawable.buyer15_basket),null,Modifier.align(Alignment.BottomEnd).width(174.dp).height(141.dp),contentScale=ContentScale.Fit)
                Column(Modifier.padding(16.dp).width(198.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    FText("Panen segar\npilihan petani",22f,700,Color.White,line=27f)
                    FText("Belanja hasil panen langsung dari toko tani pilihan Anda.",11f,color=Color.White,line=15f)
                    Box(Modifier.background(Color.White,CircleShape).clickable(role=Role.Button,onClick=search).padding(horizontal=14.dp,vertical=8.dp)){FText("Belanja sekarang",11f,600,FForest)}
                }
            }
            // Only one live campaign; no decorative dots implying nonexistent promotions.
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Box(Modifier.size(24.dp,5.dp).background(Color(0xFF36912E),CircleShape))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText("Rekomendasi",16f,700);FText("Lihat Semua",12f,500,FGreen,Modifier.clickable(role=Role.Button,onClick=search))}
            if(s.buyerHome.isEmpty() && !s.busy)BNotice("Belum ada produk","Produk dari petani akan muncul di sini saat tersedia.")
            BProductGrid(s.buyerHome,vm,product,s.busy)
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable internal fun BuyerResultsScreen(s:ConnectedState,vm:ConnectedViewModel,criteria:BuyerSearch,change:(BuyerSearch)->Unit,back:()->Unit,cart:()->Unit,product:(String)->Unit,store:(String)->Unit) {
    var query by rememberSaveable(criteria.query){mutableStateOf(criteria.query)}
    Column(Modifier.fillMaxSize()){
        BCatalogHeader("Cari hasil panen",back,cart)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            FText("Bandingkan harga per kg dan pilih toko favorit Anda.",12f,color=FMuted)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box(Modifier.weight(1f)){FField("Cari hasil panen",query,{query=it},"Cari bahan segar...",showLabel=false,round=1000.dp)}
                Box(Modifier.size(40.dp).background(FGreen,CircleShape).clickable(role=Role.Button){change(criteria.copy(query=query))},contentAlignment=Alignment.Center){FIcon(R.drawable.buyer15_search,20.dp,"Cari",Color.White)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(false to "Produk",true to "Toko").forEach{(value,label)->BChoice(criteria.stores==value,label){change(criteria.copy(query=query,stores=value))}}
            }
            FCard {
                FSelectField("Komoditas",s.commodities.firstOrNull{it.text("id")==criteria.commodity}?.text("name") ?: "Semua komoditas",listOf("" to "Semua komoditas")+s.commodities.filter{it.flag("isActive")}.map{it.text("id") to it.text("name")},criteria.commodity,{change(criteria.copy(commodity=it,query=query))})
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("price_asc" to "Termurah (Rp/kg)","price_desc" to "Termahal").forEach{(value,label)->BChoice(criteria.sort==value,label){change(criteria.copy(sort=value,query=query))}}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText(if(criteria.commodity.isBlank())"Semua komoditas" else s.commodities.firstOrNull{it.text("id")==criteria.commodity}?.text("name").orEmpty(),11f,600,FForest);FText("Reset semua",11f,600,FGreen,Modifier.clickable(role=Role.Button){query="";change(BuyerSearch())})}
            if(s.buyerSearchLoaded && s.buyerSearch==criteria){
                FText("${s.buyerSearchTotal} ${if(criteria.stores)"toko" else "produk"} ditemukan",13f,600)
                s.buyerResults.forEach{p->FCard {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        FPhoto(p.text("photoAccessUrl"),p.text("name"),Modifier.size(96.dp).clickable(role=Role.Button){if(criteria.stores)store(p.text("id")) else product(p.text("id"))})
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            FText(if(criteria.stores)p.text("storeName") else p.text("name"),14f,600,modifier=Modifier.clickable(role=Role.Button){if(criteria.stores)store(p.text("id")) else product(p.text("id"))})
                            FText(p.text("storeName")+(if(p.flag("farmerVerified"))" · Terverifikasi" else ""),11f,color=FMuted)
                            FText("${money(p.long("pricePerKg"))}/kg",16f,700,FForest)
                            FText("Stok ${quantity(p.text("stockAvailable"))} kg",11f,color=FMuted)
                        }
                    }
                    FButton(if(criteria.stores)"Lihat toko" else "Tambah ke keranjang",{if(criteria.stores)store(p.text("id")) else vm.add(p.text("id"),"1")},primary=false,enabled=!s.busy && (criteria.stores || p.flag("availableForPurchase")))
                }}
                if(s.buyerSearchMore)BButton("Muat produk lainnya",{vm.buyerSearch(criteria,s.buyerSearchPage+1)},primary=false,enabled=!s.busy)
                if(s.buyerResults.isEmpty())BNotice("Belum ditemukan","Coba kata kunci atau komoditas lain.")
            }
            BNotice("Pilih sesuai kebutuhan","Harga dan stok mengikuti informasi terbaru dari petani. Jadwal tersedia saat checkout.")
        }
    }
}
private fun categoryPhoto(name:String):Int=when{
    name.contains("buah",true)->R.drawable.buyer15_fruit
    name.contains("bawang",true)->R.drawable.buyer15_onions
    name.contains("umbi",true)->R.drawable.buyer15_tubers
    name.contains("biji",true)||name.contains("beras",true)||name.contains("jagung",true)->R.drawable.buyer15_grains
    name.contains("rempah",true)||name.contains("bumbu",true)->R.drawable.buyer15_spices
    else->R.drawable.buyer15_vegetables
}
@Composable internal fun BuyerCategoriesScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,cart:()->Unit,search:(String)->Unit,product:(String)->Unit) {
    Column(Modifier.fillMaxSize()){
        BCatalogHeader("Kategori",back,cart)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
            BSearchPrompt{search("")}
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){FText("Mau belanja apa?",18f,700);FText("Pilih kategori favoritmu untuk mulai belanja.",12f,color=FMuted)}
            s.commodities.filter{it.flag("isActive")}.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                row.forEach{category->Column(Modifier.weight(1f).clickable(role=Role.Button){search(category.text("id"))},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Image(painterResource(categoryPhoto(category.text("name"))),null,Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                    FText(category.text("name"),13f,600)
                }}
                repeat(3-row.size){Spacer(Modifier.weight(1f))}
            }}
            if(s.commodities.none{it.flag("isActive")} && !s.busy)BNotice("Kategori belum tersedia","Coba muat ulang katalog.")
            FText("Terakhir dilihat",16f,700)
            if(s.buyerRecent.isEmpty())FText("Produk yang Anda lihat akan muncul di sini.",12f,color=FMuted)
            BProductGrid(s.buyerRecent,vm,product,s.busy)
        }
    }
}
@Composable internal fun BuyerProductScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,cart:()->Unit,store:(String)->Unit) {
    val detail=s.buyerProduct;val p=detail?.getAsJsonObject("product")
    var amount by rememberSaveable(p?.text("id")){mutableStateOf("1")}
    val kg=amount.toBigDecimalOrNull();val stock=p?.text("stockAvailable")?.toBigDecimalOrNull()
    val valid=kg!=null && kg.signum()>0 && kg.stripTrailingZeros().scale()<=2 && stock!=null && kg<=stock && p.flag("availableForPurchase")
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            BIconAction(R.drawable.buyer_shared_back,"Kembali",back)
            FText(p?.text("name") ?: "Detail produk",15.5f,modifier=Modifier.weight(1f),align=androidx.compose.ui.text.style.TextAlign.Center)
            BIconAction(R.drawable.buyer15_cart,"Keranjang",cart)
        }
        if(p!=null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp))){
                    FPhoto(p.text("photoAccessUrl"),p.text("name"),Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp).size(24.dp,6.dp).background(FGreen,CircleShape))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FText(p.text("name"),14f,line=21f);FText("${money(p.long("pricePerKg"))} /kg",21f,700,line=28f)}
                    FText("${quantity(p.text("stockAvailable"))} kg tersedia",10.5f,color=FMuted)
                }
                Row(Modifier.fillMaxWidth().clickable(role=Role.Button){store(p.text("id"))},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    FIcon(R.drawable.buyer8_store,32.dp)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){FText(p.text("storeName"),12f,600);FText(if(p.flag("farmerVerified"))"Petani terverifikasi" else "Toko petani",10f,color=FMuted);FText(detail.text("pickupLocation"),10f,color=FMuted)}
                    FIcon(R.drawable.buyer_shared_chevron,16.dp)
                }
                if(p.text("readyDate").isNotBlank())Column(Modifier.fillMaxWidth().background(BPaper,RoundedCornerShape(10.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    FText("Siap panen ${dateLabel(p.text("readyDate"))}",10.5f,600,FForest);FText("Pilih jadwal yang tersedia saat checkout.",10.5f,color=FForest)
                }
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                    FText("Deskripsi Produk",12f);FText(p.text("description").ifBlank{"Belum ada deskripsi produk."},11f,color=FMuted,line=14f)
                }
                val rating=detail.getAsJsonObject("farmerRating")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    FText("Ulasan",12f)
                    FText(if(rating!=null && rating.long("reviewCount")>0)"${rating.text("average")} (${rating.long("reviewCount")} ulasan toko)" else "Belum ada penilaian",11f,600,color=FMuted)
                }
                FCard{FText("Rincian ulasan publik belum tersedia dari layanan toko.",11f,color=FMuted,line=15f)}
            }
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(!valid)FText("Masukkan kelipatan 0,01 kg sesuai stok tersedia.",11f,color=FMuted)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(Modifier.width(104.dp).height(36.dp).background(Color.White,CircleShape).border(1.dp,FBorder,CircleShape),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={amount=((amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ONE)-java.math.BigDecimal.ONE).max(java.math.BigDecimal("0.01")).stripTrailingZeros().toPlainString()},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(28.dp)){FText("−",18f,600)}
                        androidx.compose.foundation.text.BasicTextField(amount,{amount=it},Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(fontFamily=BuyerFont,fontSize=11.sp,color=FInk,textAlign=androidx.compose.ui.text.style.TextAlign.Center),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Decimal))
                        FText("kg",10f)
                        TextButton(onClick={amount=((amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)+java.math.BigDecimal.ONE).stripTrailingZeros().toPlainString()},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(28.dp)){FText("+",18f,600)}
                    }
                    FButton("Tambah · ${money(buyerLineTotal(p.long("pricePerKg"),amount))}",{vm.add(p.text("id"),amount)},modifier=Modifier.weight(1f),enabled=valid && !s.busy,height=36.dp,font=12f)
                }
            }
        } else if(!s.busy)Box(Modifier.padding(16.dp)){BNotice("Produk belum tersedia","Kembali dan pilih produk lain.")}
    }
}
