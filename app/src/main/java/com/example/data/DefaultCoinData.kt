package com.example.data

import com.example.model.BinanceTicker

object DefaultCoinData {

    private val SYMBOLS = listOf(
        "BTCUSDT" to 67500.0,
        "ETHUSDT" to 2650.0,
        "SOLUSDT" to 155.0,
        "BNBUSDT" to 590.0,
        "DOGEUSDT" to 0.145,
        "1000PEPEUSDT" to 0.0105,
        "SUIUSDT" to 2.15,
        "AVAXUSDT" to 28.5,
        "NEARUSDT" to 5.2,
        "WIFUSDT" to 2.65,
        "AAVEUSDT" to 160.0,
        "ENAUSDT" to 0.38,
        "XRPUSDT" to 0.54,
        "ADAUSDT" to 0.35,
        "LINKUSDT" to 11.8,
        "SHIBUSDT" to 0.000018,
        "APTUSDT" to 9.8,
        "ARBUSDT" to 0.58,
        "OPUSDT" to 1.65,
        "FTMUSDT" to 0.72,
        "TIAUSDT" to 6.2,
        "SEIUSDT" to 0.44,
        "INJUSDT" to 21.5,
        "RUNEUSDT" to 5.1,
        "RENDERUSDT" to 6.2,
        "FETUSDT" to 1.42,
        "TAOUSDT" to 580.0,
        "KASUSDT" to 0.13,
        "STXUSDT" to 1.9,
        "PENDLEUSDT" to 4.7,
        "JUPUSDT" to 0.95,
        "PYTHUSDT" to 0.36,
        "BONKUSDT" to 0.000023,
        "FLOKIUSDT" to 0.00015,
        "NOTUSDT" to 0.008,
        "TONUSDT" to 5.2,
        "DOTUSDT" to 4.3,
        "MATICUSDT" to 0.38,
        "UNIUSDT" to 7.8,
        "ATOMUSDT" to 4.6,
        "LTCUSDT" to 71.0,
        "BCHUSDT" to 345.0,
        "ETCUSDT" to 19.5,
        "XLMUSDT" to 0.095,
        "ALGOUSDT" to 0.125,
        "MANAUSDT" to 0.31,
        "SANDUSDT" to 0.28,
        "AXSUSDT" to 5.1,
        "GALAUSDT" to 0.022,
        "CHZUSDT" to 0.072,
        "DYDXUSDT" to 1.15,
        "CRVUSDT" to 0.29,
        "SNXUSDT" to 1.62
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
