package com.example.data

import com.example.model.BinanceTicker

object DefaultCoinData {

    private val SYMBOLS = listOf(
        "BTCUSDT" to 81933.0,
        "ETHUSDT" to 2401.0,
        "SOLUSDT" to 104.0,
        "BNBUSDT" to 727.5,
        "DOGEUSDT" to 0.0792,
        "1000PEPEUSDT" to 0.0041,
        "SUIUSDT" to 0.987,
        "AVAXUSDT" to 9.54,
        "NEARUSDT" to 4.94,
        "WIFUSDT" to 0.199,
        "AAVEUSDT" to 148.0,
        "ENAUSDT" to 0.186,
        "XRPUSDT" to 1.303,
        "ADAUSDT" to 0.217,
        "LINKUSDT" to 11.9,
        "1000SHIBUSDT" to 0.0050,
        "APTUSDT" to 0.762,
        "ARBUSDT" to 0.194,
        "OPUSDT" to 0.124,
        "TIAUSDT" to 0.605,
        "SEIUSDT" to 0.0598,
        "INJUSDT" to 8.54,
        "RUNEUSDT" to 0.64,
        "RENDERUSDT" to 1.79,
        "FETUSDT" to 0.188,
        "TAOUSDT" to 266.0,
        "KASUSDT" to 0.035,
        "STXUSDT" to 0.36,
        "PENDLEUSDT" to 2.05,
        "JUPUSDT" to 0.291,
        "PYTHUSDT" to 0.0726,
        "1000BONKUSDT" to 0.0029,
        "1000FLOKIUSDT" to 0.0234,
        "NOTUSDT" to 0.00038,
        "TONUSDT" to 5.2,
        "DOTUSDT" to 1.14,
        "UNIUSDT" to 7.24,
        "ATOMUSDT" to 1.75,
        "LTCUSDT" to 59.8,
        "BCHUSDT" to 277.4,
        "ETCUSDT" to 7.7,
        "FILUSDT" to 1.01,
        "TRXUSDT" to 0.331,
        "HBARUSDT" to 0.082,
        "KAVAUSDT" to 0.070,
        "GALAUSDT" to 0.0019
    )

    fun getSeedTickers(): List<BinanceTicker> {
        return SYMBOLS.mapIndexed { index, (sym, basePrice) ->
            val change = ((index % 11) - 4) * 0.85
            val volume = 2000000.0 + (index * 450000.0)
            val sma20 = basePrice * (0.985 + (index % 5) * 0.005)
            val volRatio = 1.4 + ((index % 8) * 0.15)
            BinanceTicker(
                symbol = sym,
                lastPrice = basePrice,
                priceChangePercent = change,
                volume = volume,
                highPrice = basePrice * 1.03,
                lowPrice = basePrice * 0.97,
                sma20OpenD1 = sma20,
                volumeRatioD1 = volRatio
            )
        }
    }
}
