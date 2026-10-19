package dev.kiankonrad.currencycalculator

import android.os.Bundle
import android.animation.ValueAnimator
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle

private val Blue=Color(0xFF79B8FF)
private val Muted=Color(0xFFBDC1C6)
private val Divider=Color(0xFF303238)

class MainActivity:ComponentActivity() {
 override fun onResume() {super.onResume();LauncherIconManager.sync(this)}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.BLACK),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.BLACK))
  setContent { ConverterApp() }
 }
}

@Composable
fun ConverterApp() {
 val context=LocalContext.current
 val locale=LocalConfiguration.current.locales[0]
 val state=remember { ConverterState(context) }
 val scope=rememberCoroutineScope()
 val lifecycle=LocalLifecycleOwner.current
 val haptic=LocalHapticFeedback.current
 val swapProgress=remember {Animatable(0f)}
 var swapping by remember {mutableStateOf(false)}
 fun animatedSwap() {
  if(swapping) return
  state.swap()
  if(!ValueAnimator.areAnimatorsEnabled()) return
  swapping=true
  scope.launch {
   try {swapProgress.animateTo(1f,tween(300,easing=FastOutSlowInEasing))}
   finally {swapProgress.snapTo(0f);swapping=false}
  }
 }
 var picker by rememberSaveable { mutableIntStateOf(-1) }
 var showHistory by rememberSaveable { mutableStateOf(false) }
 var showInfo by rememberSaveable { mutableStateOf(false) }
 LaunchedEffect(lifecycle,state) {
  lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   while(isActive) { state.refresh(state.networkError!=null);delay(60_000) }
  }
 }
 DisposableEffect(state,lifecycle) {
  val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  var reconnect:Job?=null
  fun updateConnection() {
   scope.launch {
    val connected=manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    val wasOnline=state.online
    state.updateConnectivity(connected)
    if(connected && (!wasOnline || state.networkError!=null)) {
     reconnect?.cancel()
     reconnect=scope.launch {
      delay(10_000)
      if(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) state.refresh(true)
     }
    }
   }
  }
  val callback=object:ConnectivityManager.NetworkCallback() {
   override fun onAvailable(network:Network) {updateConnection()}
   override fun onLost(network:Network) {updateConnection()}
   override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities) {updateConnection()}
  }
  manager.registerDefaultNetworkCallback(callback)
  updateConnection()
  onDispose {manager.unregisterNetworkCallback(callback);reconnect?.cancel()}
 }
 val evaluation=state.evaluation
 val source=state.currency(state.from)
 val target=state.currency(state.to)
 val sourceAmount=evaluation.value?.let { formatMoney(it,source.fractionDigits,locale) } ?: "—"
 val targetAmount=state.converted?.let { formatMoney(it,target.fractionDigits,locale) } ?: "—"
 val targetExpression=state.rate?.let { "≈ "+MoneyEngine.convertedExpression(compactExpression(state.expressionForDisplay),DisplayFormat.rate(it)) } ?: "Waiting for exchange rate"
 val status=when {
  !state.online && state.snapshot!=null -> "Offline · saved "+SimpleDateFormat("MMM d, HH:mm",locale).format(Date(state.snapshot!!.providerAsOfMs ?: state.snapshot!!.fetchedAtMs))
  !state.online -> "Offline · connect once to download rates"
  state.loading && state.snapshot==null -> "Getting current rates…"
  state.networkError!=null && state.snapshot!=null -> "Using saved rates · refresh failed"
  state.snapshot==null -> "Rates unavailable · tap Refresh"
  state.snapshot?.isStale()==true -> "Saved rates · update delayed"
  else -> "Updated "+SimpleDateFormat("MMM d, HH:mm",locale).format(Date(state.snapshot!!.providerAsOfMs ?: state.snapshot!!.fetchedAtMs))
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Blue,onPrimary=Color.Black,background=Color.Black,onBackground=Color.White,surface=Color(0xFF17191D),onSurface=Color.White)) {
  Surface(Modifier.fillMaxSize(),color=Color.Black) {
   BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize()) {
    val fontScale=LocalDensity.current.fontScale
    val landscape=maxWidth>maxHeight
    val sidePadding=if(landscape) 16.dp else 20.dp
    val availableWidth=minOf(maxWidth,520.dp)-sidePadding*2
    val preferredKey=(availableWidth-24.dp)/4
    val keySize=minOf(preferredKey,((maxHeight-405.dp*fontScale-32.dp)/5).coerceAtLeast(48.dp))
    val scrollNeeded=landscape || maxHeight<650.dp || fontScale>1.5f
    val columnModifier=if(scrollNeeded) Modifier.widthIn(max=520.dp).fillMaxWidth().verticalScroll(rememberScrollState()) else Modifier.widthIn(max=520.dp).fillMaxWidth().fillMaxHeight()
    Column(columnModifier.align(Alignment.Center).padding(horizontal=sidePadding)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Text("Converter",fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
      TextButton(onClick={showHistory=true},modifier=Modifier.semantics {contentDescription="Calculation history"}) {Text("History",color=Blue)}
      TextButton(onClick={showInfo=true},modifier=Modifier.semantics {contentDescription="About Currency Converter and exchange rates"}) {Text("ⓘ",fontSize=23.sp,color=Blue)}
     }
     if(scrollNeeded) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
     CurrencyDisplay(source,sourceAmount,compactExpression(state.expressionForDisplay),Color.White,onSelect={picker=0})
     if(evaluation.error!=null) Text(evaluation.error!!,color=Color(0xFFFFB4AB),fontSize=13.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.End)
     Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      HorizontalDivider(Modifier.weight(1f),color=Divider)
      FilledTonalIconButton(onClick={animatedSwap()},enabled=!swapping,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=Color(0xFF142D4D),contentColor=Blue),modifier=Modifier.size(48.dp).semantics {contentDescription="Swap source and target currencies"}) {
       Icon(painterResource(R.drawable.ic_swap_vertical),contentDescription=null,modifier=Modifier.size(26.dp).graphicsLayer {rotationZ=swapProgress.value*180f})
      }
      HorizontalDivider(Modifier.weight(1f),color=Divider)
     }
     CurrencyDisplay(target,targetAmount,targetExpression,Blue,onSelect={picker=1},onUseAsInput={animatedSwap()})
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text(status,color=Muted,fontSize=11.sp)
       Text(state.snapshot?.source ?: "Current exchange rates",color=Muted,fontSize=10.sp)
      }
      TextButton(onClick={scope.launch {state.refresh(true)}},enabled=!state.loading,modifier=Modifier.semantics {contentDescription="Refresh exchange rates"}) {
       if(state.loading) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=Blue) else Text("Refresh",color=Blue,fontSize=12.sp)
      }
     }
     Spacer(Modifier.height(12.dp))
     val rows=listOf(listOf("AC","+/−","%","÷"),listOf("7","8","9","×"),listOf("4","5","6","−"),listOf("1","2","3","+"),listOf(".","0","⌫","="))
     val actualKey=if(scrollNeeded) minOf(preferredKey,80.dp) else keySize
     Column(Modifier.width(actualKey*4+24.dp).height(actualKey*5+32.dp).align(Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      rows.forEach { row ->
       Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        row.forEach { key ->
         val operator=key in listOf("÷","×","−","+","%","+/−")
         val fill=when(key){"="->Blue;"AC"->Color(0xFF1B406B);else->if(operator) Color(0xFF142D4D) else Color(0xFF202124)}
         val ink=if(key=="=") Color.Black else if(operator||key=="AC") Blue else Color.White
         Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
          Button(onClick={haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);state.press(key)},shape=CircleShape,contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=fill,contentColor=ink),modifier=Modifier.aspectRatio(1f).semantics {contentDescription=keyDescription(key)}) {
           Text(key,fontSize=if(key=="+/−") 23.sp else 30.sp,maxLines=1)
          }
         }
        }
       }
      }
     }
     if(scrollNeeded) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(1f))
    }
   }
  }
  if(picker>=0) CurrencyPicker(state.currencies,picker==1,if(picker==1) state.to else state.from,onDismiss={picker=-1}) {code->state.chooseCurrency(picker==1,code);picker=-1}
  if(showHistory) AlertDialog(onDismissRequest={showHistory=false},title={Text("Recent calculations")},text={
   if(state.history.isEmpty()) Text("Your calculations appear here after you press equals.") else LazyColumn(Modifier.heightIn(max=440.dp)) {
    items(state.history) { record ->TextButton(onClick={state.restore(record);showHistory=false},modifier=Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth()) {Text(record.from+" → "+record.to,color=Blue);Text(record.expression+" = "+record.result,color=Color.White)}} }
   }
  },confirmButton={TextButton(onClick={showHistory=false}){Text("Close")}},dismissButton={if(state.history.isNotEmpty()) TextButton(onClick={state.clearHistory()}){Text("Clear history")}})
  if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("Currency Converter")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
   Text("Created by Kian Konrad Tajbakhsh",fontWeight=FontWeight.Medium)
   Spacer(Modifier.height(12.dp))
   Text("Everyday money math with current currency conversions.")
   Spacer(Modifier.height(12.dp))
   Text("Rate data: "+(state.snapshot?.source ?: "FXAPI (fxapi.app)")+". The provider updates approximately every five minutes. Rates refresh while the app is open; the provider's update time is shown. Saved rates remain available offline and are labeled accordingly.")
   Spacer(Modifier.height(12.dp))
   Text("Indicative conversions. Your bank or card may use a different rate and add fees.")
   Spacer(Modifier.height(12.dp))
   Text("No accounts, ads, or analytics. Calculations and history stay on your device. The rate provider receives network requests, not your amounts or calculations.")
   Spacer(Modifier.height(12.dp))
   Text("Percent examples: 200 − 15% = 170; 200 + 15% = 230; 200 × 15% = 30. The second calculation applies the exchange rate to the whole result, keeping percentages and divisors intact.")
   Text("Version 1.0.1",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
  }},confirmButton={TextButton(onClick={showInfo=false}){Text("Close")}})
 }
}

@Composable
private fun CurrencyPicker(currencies:List<FiatCurrency>,target:Boolean,selected:String,onDismiss:()->Unit,onSelect:(String)->Unit) {
 var query by remember {mutableStateOf("")}
 val filtered=currencies.filter {query.isBlank()||it.code.contains(query,true)||it.name.contains(query,true)}.sortedWith(compareBy<FiatCurrency>{if(it.code==selected)0 else 1}.thenBy{it.name})
 AlertDialog(onDismissRequest=onDismiss,title={Text(if(target) "Convert to" else "Convert from")},text={Column {
  OutlinedTextField(query,{query=it},singleLine=true,label={Text("Search currencies")},modifier=Modifier.fillMaxWidth())
  if(filtered.isEmpty()) Text("No matching currencies",modifier=Modifier.padding(16.dp))
  LazyColumn(Modifier.heightIn(max=360.dp)) {items(filtered,key={it.code}) {currency->TextButton(onClick={onSelect(currency.code)},modifier=Modifier.fillMaxWidth().semantics {contentDescription="Select "+currency.code}) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
 Text(currency.flag+"  "+currency.code+" · "+currency.name,modifier=Modifier.weight(1f),color=if(currency.code==selected) Blue else Color.White)
 if(currency.code=="AED") Icon(painterResource(R.drawable.ic_dirham),contentDescription="UAE dirham symbol",tint=Color.White,modifier=Modifier.size(22.dp)) else Text(currency.symbol,color=Muted)
}}}}
 }},confirmButton={TextButton(onClick=onDismiss){Text("Close")}})
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CurrencyDisplay(currency:FiatCurrency,amount:String,calculation:String,color:Color,onSelect:()->Unit,onUseAsInput:(()->Unit)?=null,modifier:Modifier=Modifier) {
 val density=LocalDensity.current
 val amountHeight=with(density){48.sp.toDp()}
 val calculationHeight=with(density){20.sp.toDp()}
 Row(modifier.fillMaxWidth().height(amountHeight+calculationHeight+8.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
  TextButton(onClick=onSelect,contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.width(88.dp).semantics {contentDescription="Choose "+currency.name+" currency"}) {
   Column(horizontalAlignment=Alignment.CenterHorizontally) {Text(currency.flag,fontSize=30.sp);Text(currency.code+" ▾",color=color,fontSize=14.sp,fontWeight=FontWeight.Medium)}
  }
  Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
   val amountModifier=Modifier.height(amountHeight).horizontalScroll(rememberScrollState()).then(if(onUseAsInput!=null) Modifier.combinedClickable(onClick=onUseAsInput,onClickLabel="Use "+currency.code+" as input") else Modifier)
   SelectionContainer {
    if(currency.code=="AED") Row(amountModifier.semantics(mergeDescendants=true){contentDescription="AED "+amount},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     val glyphHeight=with(density){30.sp.toDp()}
     Icon(painterResource(R.drawable.ic_dirham),contentDescription="UAE dirham symbol",tint=Color.White,modifier=Modifier.height(glyphHeight).width(glyphHeight*1.149795f))
     Text(amount,color=color,fontSize=40.sp,lineHeight=48.sp,fontWeight=FontWeight.Light,maxLines=1)
    } else Text(currency.symbol+" "+amount,color=color,fontSize=40.sp,lineHeight=48.sp,fontWeight=FontWeight.Light,maxLines=1,modifier=amountModifier)
   }
   Text(calculation,color=Muted,fontSize=15.sp,lineHeight=20.sp,maxLines=1,modifier=Modifier.height(calculationHeight).horizontalScroll(rememberScrollState()))
  }
 }
}

private fun formatMoney(value:BigDecimal,digits:Int,locale:Locale):String = NumberFormat.getNumberInstance(locale).apply {minimumFractionDigits=digits.coerceIn(0,3);maximumFractionDigits=digits.coerceIn(0,3);roundingMode=RoundingMode.HALF_UP}.format(value)
private fun keyDescription(key:String)=when(key){"AC"->"All clear";"⌫"->"Delete last character";"÷"->"Divide";"×"->"Multiply";"−"->"Subtract";"+"->"Add";"%"->"Percent";"="->"Equals";"+/−"->"Change sign";"( )"->"Parentheses";"."->"Decimal point";else->key}

private fun compactExpression(text:String):String = Regex("[0-9]+\\.[0-9]{9,}").replace(text) { match-> match.value.toBigDecimal().setScale(8,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }
