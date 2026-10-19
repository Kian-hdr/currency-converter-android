package dev.kiankonrad.currencycalculator

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class ConverterJourneyTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun key(label:String) { compose.onNodeWithContentDescription(label).performClick() }
 private fun digits(text:String) {text.forEach {key(it.toString())}}
 private fun amount(value:String):String = NumberFormat.getNumberInstance(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]).apply {minimumFractionDigits=2;maximumFractionDigits=2;roundingMode=RoundingMode.HALF_UP}.format(BigDecimal(value))
 @Test fun moneyMathLiveRatesHistoryAndRestore() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  context.getSharedPreferences("converter_state",0).edit().clear().commit()
  context.getSharedPreferences("exchange_rates",0).edit().clear().commit()
  val scenario=ActivityScenario.launch(MainActivity::class.java)
  try {
   key("All clear");digits("200");key("Subtract");digits("15");key("Percent")
   compose.onNodeWithText("€ "+amount("170")).assertIsDisplayed()
   compose.waitUntil(30_000) {context.getSharedPreferences("exchange_rates",0).contains("last_good")}
   val snapshot=RatesCodec.decodeCache(context.getSharedPreferences("exchange_rates",0).getString("last_good",null)!!,System.currentTimeMillis())
   assertTrue("Provider timestamp must be recent",!snapshot.isStale())
   val converted=BigDecimal("170").multiply(snapshot.crossRate("EUR","USD"))
   compose.onNodeWithText("$ "+amount(converted.toPlainString())).assertIsDisplayed()
   key("Equals");compose.onNodeWithContentDescription("Calculation history").performClick()
   compose.onNodeWithText("200−15% = 170").assertIsDisplayed()
   compose.onNodeWithText("Close").performClick()
   key("All clear");digits("1000");key("Add");digits("20");key("Percent")
   compose.onNodeWithText("€ "+amount("1200")).assertIsDisplayed()
   key("All clear");digits("200");key("Multiply");digits("15");key("Percent")
   compose.onNodeWithText("€ "+amount("30")).assertIsDisplayed()
   key("All clear");digits("120");key("Divide");digits("3")
   compose.onNodeWithText("€ "+amount("40")).assertIsDisplayed()
   key("All clear");digits("1");key("Divide");digits("0")
   compose.onNodeWithText("Cannot divide by zero").assertIsDisplayed()
   key("All clear");digits("200");key("Add");digits("15");key("Percent");key("Change sign")
   compose.onNodeWithText("€ "+amount("170")).assertIsDisplayed()
   key("Equals");digits("5")
   compose.onNodeWithText("€ "+amount("5")).assertIsDisplayed()
   scenario.recreate()
   compose.onNodeWithText("€ "+amount("5")).assertIsDisplayed()
   compose.onNodeWithText("FXAPI").assertIsDisplayed()
   compose.onNodeWithText("USD ▾").performClick()
   compose.onNodeWithText("Search currencies").performTextInput("AED")
   compose.onNodeWithContentDescription("Select AED").performClick()
   compose.onNodeWithContentDescription("UAE dirham symbol",useUnmergedTree=true).assertExists()
   val before=compose.onNodeWithContentDescription("Swap source and target currencies").fetchSemanticsNode().boundsInRoot
   compose.onNodeWithContentDescription("Swap source and target currencies").performClick()
   val after=compose.onNodeWithContentDescription("Swap source and target currencies").fetchSemanticsNode().boundsInRoot
   assertEquals(before.center.y,after.center.y,0.5f)
   compose.onNodeWithText("AED ▾").assertIsDisplayed()
   compose.onNodeWithContentDescription("Delete last character").assertIsDisplayed()
   compose.onNodeWithContentDescription("About Currency Converter and exchange rates").performClick()
   compose.onNodeWithText("Created by Kian Konrad Tajbakhsh").assertIsDisplayed()
   compose.onNodeWithText("Close").performClick()
   val selection=LauncherIconManager.sync(context)
   val expected=if(LauncherIconManager.isWhiconsInstalled(context.packageManager)) LauncherIconManager.Mode.WHICONS else LauncherIconManager.Mode.NORMAL
   assertEquals(expected,selection.mode)
   assertNull(selection.error)
  } finally {scenario.close()}
 }
}
