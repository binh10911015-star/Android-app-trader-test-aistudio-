package com.example

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.ui.MainViewModel

/**
 * Application giữ MainViewModel (bộ máy giao dịch) ở phạm vi TIẾN TRÌNH thay vì phạm vi Activity.
 *
 * Trước đây MainViewModel gắn với MainActivity: khi người dùng vuốt tắt app hoặc hệ thống huỷ
 * Activity, onCleared() chạy và dừng toàn bộ vòng lặp quét lệnh / theo dõi Trailing Stop, dù
 * Foreground Service vẫn hiện thông báo "Bot đang chạy ngầm".
 *
 * Giờ MainViewModel sống cùng tiến trình. TradingForegroundService giữ tiến trình sống 24/7,
 * còn MainActivity chỉ "mượn" lại đúng instance này để hiển thị.
 */
class TradingApp : Application(), ViewModelStoreOwner {

    private val appViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    /** Instance duy nhất của bộ máy giao dịch trong tiến trình (tạo lười ở lần gọi đầu). */
    val tradingViewModel: MainViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[MainViewModel::class.java]
    }
}
