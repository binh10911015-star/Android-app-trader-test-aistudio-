package com.example.ai

import com.example.model.ActiveBasket
import com.example.model.BacktestConfig
import com.example.model.BasketBacktestReport
import org.json.JSONArray
import org.json.JSONObject

object ScreenContextBuilder {
    fun buildDualBasketContext(
        currentScreen: String,
        selectedSymbol: String?,
        primaryBasket: ActiveBasket?,
        secondaryBasket: ActiveBasket?,
        backtestReport: BasketBacktestReport?,
        currentBacktestConfig: BacktestConfig? = null,
        backtestDays: Int = 180,
        backtestHistory: List<com.example.model.BacktestHistoryRecord> = emptyList()
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
                json.put("floorLockRoi", basket.floorLockRoi)
                json.put("currentTrailingStopRoi", basket.currentTrailingStopRoi)
                json.put("peakRoi", basket.peakRoi)
                val hours = (System.currentTimeMillis() - basket.openTime) / 3600000.0
                json.put("holdingHours", hours)
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

        if (currentBacktestConfig != null) {
            val cJson = JSONObject()
            cJson.put("testDays", backtestDays)
            cJson.put("slotScanMode", currentBacktestConfig.slotScanMode)
            cJson.put("initialCapital", currentBacktestConfig.initialCapital)
            cJson.put("perTradePct", currentBacktestConfig.perTradePct)
            cJson.put("maxPortfolioAllocation", currentBacktestConfig.maxPortfolioAllocation)
            cJson.put("minQuoteVolume24h", currentBacktestConfig.minQuoteVolume24h)
            cJson.put("minVolRatio", currentBacktestConfig.minVolRatio)
            cJson.put("maxD1ChangePct", currentBacktestConfig.maxD1ChangePct)
            cJson.put("maxAtrPercent", currentBacktestConfig.maxAtrPercent)
            cJson.put("kTrail", currentBacktestConfig.kTrail)
            cJson.put("enableTrailingStop", currentBacktestConfig.enableTrailingStop)
            cJson.put("enableFloorLock", currentBacktestConfig.enableFloorLock)
            cJson.put("floorLockMinRoi", currentBacktestConfig.floorLockMinRoi)
            root.put("backtestConfig", cJson)
        }

        if (backtestReport != null) {
            val bJson = JSONObject()
            bJson.put("totalCycles", backtestReport.totalCycles)
            bJson.put("winrate", backtestReport.winratePercent)
            bJson.put("netProfitUsdt", backtestReport.netProfitUsdt)
            bJson.put("netProfitPercent", backtestReport.netProfitPercent)
            bJson.put("maxDrawdown", backtestReport.maxDrawdownPercent)
            bJson.put("avgHoldingDays", backtestReport.averageHoldingDays)
            bJson.put("finalEquity", backtestReport.finalEquity)

            val trailingHits = backtestReport.cycles.count { it.exitReason == "TRAILING_STOP_HIT" }
            bJson.put("trailingHits", trailingHits)

            root.put("backtestReport", bJson)
        }

        if (backtestHistory.isNotEmpty()) {
            val hArr = JSONArray()
            backtestHistory.forEach { rec ->
                val rJson = JSONObject()
                rJson.put("id", rec.id)
                rJson.put("timestamp", rec.timestamp)
                rJson.put("days", rec.days)
                rJson.put("netProfitUsdt", rec.netProfitUsdt)
                rJson.put("winratePercent", rec.winratePercent)
                rJson.put("maxDrawdownPercent", rec.maxDrawdownPercent)
                rJson.put("totalCycles", rec.totalCycles)
                rJson.put("trailingHits", rec.trailingHits)

                val cfg = JSONObject()
                cfg.put("kTrail", rec.config.kTrail)
                cfg.put("perTradePct", rec.config.perTradePct)
                cfg.put("maxPortfolioAllocation", rec.config.maxPortfolioAllocation)
                cfg.put("minQuoteVolume24h", rec.config.minQuoteVolume24h)
                cfg.put("minVolRatio", rec.config.minVolRatio)
                cfg.put("maxD1ChangePct", rec.config.maxD1ChangePct)
                cfg.put("enableTrailingStop", rec.config.enableTrailingStop)
                cfg.put("enableFloorLock", rec.config.enableFloorLock)
                rJson.put("config", cfg)

                hArr.put(rJson)
            }
            root.put("backtestHistory", hArr)
        }

        return root.toString()
    }
}
