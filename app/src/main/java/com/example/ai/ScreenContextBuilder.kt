package com.example.ai

import com.example.model.ActiveBasket
import com.example.model.BasketBacktestReport
import org.json.JSONArray
import org.json.JSONObject

object ScreenContextBuilder {
    fun buildDualBasketContext(
        currentScreen: String,
        selectedSymbol: String?,
        primaryBasket: ActiveBasket?,
        secondaryBasket: ActiveBasket?,
        backtestReport: BasketBacktestReport?
    ): String {
        val root = JSONObject()
        root.put("currentScreen", currentScreen)
        selectedSymbol?.let { root.put("selectedSymbol", it) }

        fun serializeBasket(basket: ActiveBasket?): JSONObject {
            val json = JSONObject()
            if (basket != null) {
                json.put("status", basket.status.name)
                json.put("investedUsdt", basket.totalInvested)
                json.put("coinsCount", basket.positions.size)
                json.put("basketAtrPercent", basket.basketAtrPercent)
                json.put("activationRoi", basket.activationRoi)
                json.put("currentTrailingStopRoi", basket.currentTrailingStopRoi)
                json.put("peakRoi", basket.peakRoi)
                val hours = (System.currentTimeMillis() - basket.openTime) / 3600000.0
                json.put("holdingHours", hours)
                json.put("isTimedOut", hours >= 72.0)
                val arr = JSONArray()
                basket.positions.forEach { arr.put(it.symbol) }
                json.put("symbols", arr)
            } else {
                json.put("status", "EMPTY_SLOT")
            }
            return json
        }

        root.put("primaryBasket", serializeBasket(primaryBasket))
        root.put("secondaryBasket", serializeBasket(secondaryBasket))

        if (currentScreen == "ANALYTICS" && backtestReport != null) {
            val bJson = JSONObject()
            bJson.put("totalCycles", backtestReport.totalCycles)
            bJson.put("winrate", backtestReport.winratePercent)
            bJson.put("netProfitUsdt", backtestReport.netProfitUsdt)
            bJson.put("maxDrawdown", backtestReport.maxDrawdownPercent)
            bJson.put("avgHoldingDays", backtestReport.averageHoldingDays)
            root.put("backtestReport", bJson)
        }
        return root.toString()
    }
}
