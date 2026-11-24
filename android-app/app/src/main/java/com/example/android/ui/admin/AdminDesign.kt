package id.panenku.app.ui.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import id.panenku.app.R
import id.panenku.app.ui.connected.buyer.*

@Composable internal fun AdminTheme(content:@Composable ()->Unit)=BuyerTransactionTheme(FPaper,content)
@Composable internal fun APage(title:String,subtitle:String="",back:()->Unit,brand:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().background(FPaper)){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(36.dp).background(Color.White,CircleShape).clickable(role=Role.Button,onClick=back),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer_shared_back,20.dp,"Kembali")}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                if(brand)FText("ADMIN · PANENKU",10f,600,Color(0xFF2C6F26),line=13f)
                FText(title,18f,700,line=27f)
                if(subtitle.isNotBlank())FText(subtitle,12f,color=FMuted,line=15f)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=6.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
@Composable internal fun AMenu(icon:Int,title:String,body:String,click:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=click),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        FIcon(icon,22.dp,tint=FInk)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){FText(title,14f,600,line=18f);if(body.isNotBlank())FText(body,12f,color=FMuted,line=15f)}
        FIcon(R.drawable.buyer_shared_chevron,16.dp,tint=FInk)
    }
}
@Composable internal fun ASearch(value:String,change:(String)->Unit,label:String,submit:()->Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(Color.White,CircleShape).border(1.dp,FBorder,CircleShape).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(24.dp).clickable(role=Role.Button,onClick=submit),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer15_search,20.dp,"Cari")}
        BasicTextField(value,change,Modifier.weight(1f),singleLine=true,textStyle=TextStyle(fontFamily=BuyerFont,fontSize=12.sp,color=FInk),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}),decorationBox={inner->Box{if(value.isEmpty())FText(label,12f,color=FMuted);inner()}})
        if(value.isNotEmpty())FText("×",14f,600,FGreen,Modifier.clickable(role=Role.Button){change("");submit()})
    }
}
@Composable internal fun AChips(choices:List<Pair<String,String>>,selected:String,change:(String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){choices.forEach{(value,label)->BChoice(selected==value,label){change(value)}}}
}
@Composable internal fun AEmpty(body:String="Belum ada data pada filter ini.")=FCard{FText(body,12f,color=FMuted)}
@Composable internal fun AConfirm(title:String,body:String,confirm:String="Lanjutkan",cancel:()->Unit,accept:()->Unit) {
    AlertDialog(onDismissRequest=cancel,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=accept){Text(confirm)}},dismissButton={TextButton(onClick=cancel){Text("Kembali")}})
}
@Composable internal fun AHistory(rows:List<com.google.gson.JsonObject>) {
    if(rows.isEmpty())FText("Belum ada riwayat dari server.",12f,color=FMuted)
    rows.forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(24.dp).background(Color(0xFFE3F7E1),CircleShape),contentAlignment=Alignment.Center){FText("✓",12f,600,FGreen)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            FText(row.get("note")?.takeUnless{it.isJsonNull}?.asString?.ifBlank{null} ?: row.get("action")?.asString?.replace('_',' ') ?: row.get("toStatus")?.asString?.replace('_',' ').orEmpty(),13f,500,line=17f)
            val whenText=row.get("changedAt")?.asString ?: row.get("createdAt")?.asString.orEmpty()
            val actor=row.get("actorName")?.takeUnless{it.isJsonNull}?.asString?.ifBlank{null} ?: row.get("actorEmail")?.takeUnless{it.isJsonNull}?.asString?.ifBlank{null} ?: row.get("actorRole")?.takeUnless{it.isJsonNull}?.asString.orEmpty()
            val reason=row.get("reason")?.takeUnless{it.isJsonNull}?.asString.orEmpty()
            FText(listOf(dateTimeLabel(whenText),actor,reason).filter{it.isNotBlank()}.joinToString(" · "),11f,color=FMuted,line=15f)
        }
    }}
}
/** Display rupiah grouping without changing the integer text sent to the API. */
@Composable internal fun AdminCurrencyField(value:String,change:(String)->Unit,enabled:Boolean) {
    val label="Batas total per pesanan"
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
        FText(label,12f,color=Color(0xFF3F3F46),line=16f)
        val format=VisualTransformation { input ->
            val raw=input.text
            if(raw.isEmpty() || raw.any{!it.isDigit()}) TransformedText(input,OffsetMapping.Identity)
            else {
                val shown=StringBuilder("Rp");val positions=IntArray(raw.length+1)
                raw.forEachIndexed{i,c->if(i>0 && (raw.length-i)%3==0)shown.append('.');positions[i]=shown.length;shown.append(c)}
                positions[raw.length]=shown.length
                val offsets=object:OffsetMapping {
                    override fun originalToTransformed(offset:Int)=positions[offset.coerceIn(0,raw.length)]
                    override fun transformedToOriginal(offset:Int)=positions.indexOfLast{it<=offset}.coerceAtLeast(0)
                }
                TransformedText(AnnotatedString(shown.toString()),offsets)
            }
        }
        BasicTextField(value,change,Modifier.fillMaxWidth().testTag("buyer-input-$label"),enabled=enabled,singleLine=true,textStyle=TextStyle(fontFamily=BuyerFont,fontSize=13.sp,lineHeight=20.sp,color=FInk),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),visualTransformation=format,decorationBox={inner->Box(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).border(1.dp,Color(0xFFE4E4E7),RoundedCornerShape(12.dp)).heightIn(min=40.dp).padding(horizontal=14.dp,vertical=10.dp),contentAlignment=Alignment.CenterStart){inner()}})
    }
}
@Composable fun AdminLoginScreen(busy:Boolean,onBack:()->Unit,onLogin:(String,String)->Unit) {
    var email by rememberSaveable{mutableStateOf("")};var password by remember{mutableStateOf("")};var visible by rememberSaveable{mutableStateOf(false)};var help by remember{mutableStateOf(false)}
    APage("Akses administrator","Khusus pengelola Panenku",onBack){
        Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
            Box(Modifier.size(80.dp).background(Color(0xFFE3F7E1),RoundedCornerShape(24.dp)),contentAlignment=Alignment.Center){FIcon(R.drawable.admin_shield,22.dp,tint=FGreen)}
            FText("Panenku Admin",20f,700,FForest,line=30f)
            FText("Jaga kepercayaan petani dan pembeli melalui keputusan yang tercatat.",14f,color=FMuted,align=androidx.compose.ui.text.style.TextAlign.Center)
        }
        FCard("Masuk ke ruang administrasi"){
            FField("Email administrator",email,{email=it},"Email akun administrator",keyboard=KeyboardType.Email,enabled=!busy)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                FText("Kata sandi",12f,color=Color(0xFF3F3F46),line=16f)
                BasicTextField(password,{password=it},Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).border(1.dp,Color(0xFFE4E4E7),RoundedCornerShape(12.dp)).heightIn(min=40.dp).padding(horizontal=14.dp,vertical=10.dp),enabled=!busy,singleLine=true,textStyle=TextStyle(fontFamily=BuyerFont,fontSize=13.sp,color=FInk),visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            }
            Row(Modifier.clickable(enabled=!busy,role=Role.Checkbox){visible=!visible},verticalAlignment=Alignment.CenterVertically){Checkbox(visible,{visible=it},Modifier.size(18.dp),enabled=!busy,colors=CheckboxDefaults.colors(checkedColor=FGreen));FText("Tampilkan kata sandi",12f,color=FMuted,modifier=Modifier.padding(start=6.dp))}
            FButton("Masuk sebagai admin",{onLogin(email.trim(),password)},enabled=!busy && email.contains('@') && password.length>=8)
            FButton("Bantuan akses administrator",{help=true},primary=false,enabled=!busy)
        }
        BNotice("Tidak ada pendaftaran admin publik","Akun hanya diberikan oleh pengelola berwenang. Jika belum memiliki akses atau lupa kata sandi, hubungi pengelola sistem.")
        FText("Gunakan perangkat tepercaya. Aktivitas administratif dicatat pada log audit; jangan membagikan kredensial.",10f,color=FMuted)
    }
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("Bantuan akses administrator")},text={Text("Hubungi pengelola Panenku untuk pemberian atau pemulihan akses admin. Tidak ada pendaftaran admin publik.")},confirmButton={TextButton(onClick={help=false}){Text("OK")}})
}
