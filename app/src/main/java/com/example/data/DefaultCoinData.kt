package com.example.data

import com.example.model.BinanceTicker

object DefaultCoinData {
    fun getSeedTickers(): List<BinanceTicker> = listOf(
        BinanceTicker("BTCUSDT", 67250.0, 1.68, 1800000.0, 67900.0, 66400.0),
        BinanceTicker("ETHUSDT", 3550.0, 2.14, 1600000.0, 3610.0, 3450.0),
        BinanceTicker("SOLUSDT", 142.0, 3.75, 980000.0, 148.0, 136.0),
        BinanceTicker("BNBUSDT", 610.0, 1.09, 750000.0, 618.0, 596.0),
        BinanceTicker("XRPUSDT", 0.62, -0.35, 530000.0, 0.64, 0.60),
        BinanceTicker("DOGEUSDT", 0.18, 4.2, 920000.0, 0.19, 0.17),
        BinanceTicker("ADAUSDT", 0.72, 1.9, 440000.0, 0.74, 0.69),
        BinanceTicker("AVAXUSDT", 35.2, 2.1, 760000.0, 36.2, 33.8),
        BinanceTicker("LINKUSDT", 18.1, 0.8, 420000.0, 18.8, 17.4),
        BinanceTicker("SUIUSDT", 1.28, 5.6, 360000.0, 1.34, 1.21)
    )
}
