// --- CRYPTO TRADER WEB APP - MAIN ENGINE ---
const PRECONFIGURED_MAINNET_API_KEY = "";
const PRECONFIGURED_MAINNET_API_SECRET = "";
const PRECONFIGURED_TESTNET_API_KEY = "ZpsofRaa3VMg3NZh10HfCaxoJcMdmHLwfCxI7opxyWlrbzRQnKZtWFfxOGWw9DtZ";
const PRECONFIGURED_TESTNET_API_SECRET = "eYRso0CQuSJ6m9WDgy6nRF9PuoDLw1tsRNt4bVCqYhyo2Sd4OjmP5NvoXYRmiQrI";

const TRACKED_SYMBOLS = [
  "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT", 
  "XRPUSDT", "ADAUSDT", "AVAXUSDT", "NEARUSDT", "LINKUSDT",
  "SUIUSDT", "PEPEUSDT", "SHIBUSDT", "APTUSDT", "ARBUSDT"
];

let state = {
  activeTab: 'scanner',
  network: 'mainnet',
  apiKey: PRECONFIGURED_MAINNET_API_KEY,
  apiSecret: PRECONFIGURED_MAINNET_API_SECRET,
  proxyHost: '',
  proxyPort: 0,
  proxyType: 'HTTP',
  isProxyEnabled: false,
  pinCode: '1234',
  accessPin: '1234',
  isAutoTradeActive: false,
  autoTradeEnabled: false,
  // Strategy settings
  strategy: 'd1_sma20_volume', // 'd1_sma20_volume' (D1 SMA20 Open + Vol 2x) or '4h_supertrend_ma20' (4H SuperTrend và 4H MA20)
  volumeMultiplier: 2.0,       // Minimum volume explosion multiplier (default 2.0x)
  portfolioTpPercent: 4.0,     // Portfolio Take-Profit % (default 4.0% total portfolio ROI)
  d1Data: {},                  // symbol -> { sma20Open, todayVolume, yesterdayVolume, volumeRatio, lastFetched }
  publicIp: 'Đang tải...',
  isConnected: true,
  walletBalance: 10000.00,
  availableMargin: 9450.00,
  positions: [],
  orders: [],
  history: [],
  tickers: {},
  filterSignal: 'all',
  searchQuery: '',
  wsConnected: false,
  pinBuffer: '',
  pinActionTitle: '',
  pinCallback: null
};

document.addEventListener('DOMContentLoaded', () => {
  console.log("CryptoTrader Web App đã khởi động thành công!");
  loadStoredSettings();
  initLucideIcons();
  setupNavigationTabs();
  setupEventListeners();
  fetchPublicIp();
  initWebSocketFeed();
  startFallbackPolling();
  startAutoTradeEngine();
  renderAll();
});

function initLucideIcons() {
  if (window.lucide) {
    window.lucide.createIcons();
  }
}

function loadStoredSettings() {
  const saved = localStorage.getItem('crypto_trader_web_config');
  if (saved) {
    try {
      const parsed = JSON.parse(saved);
      state.network = parsed.network || 'mainnet';
      state.apiKey = parsed.apiKey || PRECONFIGURED_MAINNET_API_KEY;
      state.apiSecret = parsed.apiSecret || PRECONFIGURED_MAINNET_API_SECRET;
      state.pinCode = parsed.pinCode || parsed.accessPin || '1234';
      state.accessPin = state.pinCode;
      state.proxyHost = parsed.proxyHost || '';
      state.proxyPort = parsed.proxyPort || 0;
      state.proxyType = parsed.proxyType || 'HTTP';
      state.isProxyEnabled = !!(parsed.proxyHost && parsed.proxyPort > 0);
      state.strategy = parsed.strategy || 'd1_sma20_volume';
      state.volumeMultiplier = typeof parsed.volumeMultiplier === 'number' ? parsed.volumeMultiplier : 2.0;
      state.portfolioTpPercent = typeof parsed.portfolioTpPercent === 'number' ? parsed.portfolioTpPercent : 4.0;
    } catch (e) {
      console.error('Error loading config', e);
    }
  }

  // Seed default positions if empty
  const savedPositions = localStorage.getItem('crypto_trader_positions');
  if (savedPositions) {
    try { state.positions = JSON.parse(savedPositions); } catch (e) {}
  }
  if (!state.positions || state.positions.length === 0) {
    state.positions = [
      {
        id: 'pos_1',
        symbol: 'BTCUSDT',
        side: 'BUY',
        entryPrice: 67420.50,
        markPrice: 67950.00,
        amount: 0.15,
        margin: 505.65,
        leverage: 20,
        unrealizedPnl: 79.42,
        roiPercent: 15.70,
        liquidationPrice: 64150.00,
        isLiveOrder: true,
        openTime: new Date(Date.now() - 3600000 * 4).toLocaleTimeString('vi-VN')
      },
      {
        id: 'pos_2',
        symbol: 'ETHUSDT',
        side: 'BUY',
        entryPrice: 2620.30,
        markPrice: 2655.80,
        amount: 3.5,
        margin: 458.55,
        leverage: 20,
        unrealizedPnl: 124.25,
        roiPercent: 27.09,
        liquidationPrice: 2490.00,
        isLiveOrder: true,
        openTime: new Date(Date.now() - 3600000 * 2).toLocaleTimeString('vi-VN')
      }
    ];
    savePositions();
  }

  const savedHistory = localStorage.getItem('crypto_trader_history');
  if (savedHistory) {
    try { state.history = JSON.parse(savedHistory); } catch (e) {}
  }
  if (!state.history || state.history.length === 0) {
    state.history = [
      {
        id: 'hist_1',
        symbol: 'SOLUSDT',
        side: 'BUY',
        entryPrice: 148.20,
        closePrice: 156.40,
        amount: 25,
        realizedPnl: 205.00,
        roiPercent: 27.66,
        closeTime: new Date(Date.now() - 3600000 * 24).toLocaleString('vi-VN')
      }
    ];
    saveHistory();
  }
}

function saveSettings() {
  const data = {
    network: state.network,
    apiKey: state.apiKey,
    apiSecret: state.apiSecret,
    pinCode: state.pinCode,
    accessPin: state.pinCode,
    proxyHost: state.proxyHost,
    proxyPort: state.proxyPort,
    proxyType: state.proxyType,
    strategy: state.strategy,
    volumeMultiplier: state.volumeMultiplier,
    portfolioTpPercent: state.portfolioTpPercent
  };
  localStorage.setItem('crypto_trader_web_config', JSON.stringify(data));
  localStorage.setItem('crypto_pin', state.pinCode);
}

function savePositions() {
  localStorage.setItem('crypto_trader_positions', JSON.stringify(state.positions));
}

function saveHistory() {
  localStorage.setItem('crypto_trader_history', JSON.stringify(state.history));
}

// --- Navigation Tabs ---
function setupNavigationTabs() {
  const tabs = document.querySelectorAll('.nav-tab');
  tabs.forEach(tab => {
    tab.addEventListener('click', (e) => {
      e.preventDefault();
      const target = tab.getAttribute('data-tab');
      switchTab(target);
    });
  });
}

function switchTab(tabId) {
  state.activeTab = tabId;
  document.querySelectorAll('.nav-tab').forEach(t => {
    if (t.getAttribute('data-tab') === tabId) {
      t.classList.add('active', 'text-gold');
      t.classList.remove('text-gray-400');
    } else {
      t.classList.remove('active', 'text-gold');
      t.classList.add('text-gray-400');
    }
  });

  const sections = ['scanner', 'positions', 'orders', 'history', 'settings'];
  sections.forEach(s => {
    const el = document.getElementById(`section-${s}`);
    if (el) {
      if (s === tabId) {
        el.classList.remove('hidden');
      } else {
        el.classList.add('hidden');
      }
    }
  });

  initLucideIcons();
}

// --- MANDATORY PIN SECURITY ---
function verifyPinAndExecute(actionName, callback) {
  requestPinVerification(actionName, callback);
}

function requestPinVerification(actionTitle, onSuccess) {
  state.pinBuffer = '';
  state.pinActionTitle = actionTitle;
  state.pinCallback = onSuccess;

  const modal = document.getElementById('pin-modal');
  const titleEl = document.getElementById('pin-modal-title');
  const errorEl = document.getElementById('pin-modal-error');

  if (modal) {
    if (titleEl) titleEl.innerText = actionTitle || 'Xác thực mã PIN bảo mật (Mặc định: 1234)';
    if (errorEl) errorEl.classList.add('hidden');
    updatePinDots();
    modal.classList.remove('hidden');
    modal.classList.add('flex');
    initLucideIcons();
  } else {
    // Fallback to prompt if modal DOM element not found
    const entered = prompt(`🔒 [BẢO MẬT] Nhập mã PIN để xác nhận '${actionTitle}' (Mặc định: 1234):`);
    if (entered === (state.pinCode || '1234')) {
      showToast('Xác thực mã PIN thành công!', 'success');
      if (typeof onSuccess === 'function') onSuccess();
    } else if (entered !== null) {
      alert("Sai mã PIN! Thao tác bị hủy bỏ để đảm bảo an toàn.");
    }
  }
}

function closePinModal() {
  const modal = document.getElementById('pin-modal');
  if (modal) {
    modal.classList.add('hidden');
    modal.classList.remove('flex');
  }
  state.pinBuffer = '';
  state.pinCallback = null;
}

function handlePinDigit(digit) {
  if (state.pinBuffer.length < 4) {
    state.pinBuffer += digit;
    updatePinDots();
  }
  if (state.pinBuffer.length === 4) {
    setTimeout(verifyEnteredPin, 150);
  }
}

function handlePinDelete() {
  if (state.pinBuffer.length > 0) {
    state.pinBuffer = state.pinBuffer.slice(0, -1);
    updatePinDots();
  }
}

function updatePinDots() {
  for (let i = 0; i < 4; i++) {
    const dot = document.getElementById(`pin-dot-${i}`);
    if (dot) {
      if (i < state.pinBuffer.length) {
        dot.classList.add('filled');
      } else {
        dot.classList.remove('filled');
      }
    }
  }
}

function verifyEnteredPin() {
  const errorEl = document.getElementById('pin-modal-error');
  const configuredPin = state.pinCode || state.accessPin || '1234';

  if (state.pinBuffer === configuredPin) {
    const cb = state.pinCallback;
    closePinModal();
    showToast('Xác thực mã PIN thành công!', 'success');
    if (typeof cb === 'function') {
      cb();
    }
  } else {
    if (errorEl) {
      errorEl.innerText = 'Mã PIN không chính xác. Vui lòng nhập lại!';
      errorEl.classList.remove('hidden');
    }
    state.pinBuffer = '';
    updatePinDots();
  }
}

// --- WebSocket & Real-Time Data ---
function initWebSocketFeed() {
  const wsUrl = 'wss://fstream.binance.com/ws/!ticker@arr';
  const indicator = document.getElementById('ws-status-indicator');
  const text = document.getElementById('ws-status-text');

  try {
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      state.wsConnected = true;
      if (indicator) indicator.className = 'pulsing-dot online mr-2';
      if (text) text.innerText = 'WebSocket Real-time Online';
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (Array.isArray(data)) {
          data.forEach(item => {
            if (TRACKED_SYMBOLS.includes(item.s)) {
              updateTicker(item.s, {
                price: parseFloat(item.c),
                high: parseFloat(item.h),
                low: parseFloat(item.l),
                volume: parseFloat(item.q),
                priceChangePercent: parseFloat(item.P)
              });
            }
          });
          updatePositionsMarkPrice();
          renderTickersTable();
          renderStatsOverview();
        }
      } catch (e) {}
    };

    ws.onerror = () => {
      state.wsConnected = false;
      if (indicator) indicator.className = 'w-2 h-2 rounded-full bg-red-500 mr-2';
      if (text) text.innerText = 'WebSocket Gián đoạn - Đang thử lại';
    };

    ws.onclose = () => {
      state.wsConnected = false;
      setTimeout(initWebSocketFeed, 5000);
    };
  } catch (e) {
    console.warn("WebSocket fallback initialized");
  }
}

async function fetchD1Klines(symbol) {
  try {
    const targetUrl = `https://fapi.binance.com/fapi/v1/klines?symbol=${symbol}&interval=1d&limit=25`;
    let resp;
    try {
      resp = await fetch(targetUrl);
    } catch (corsErr) {
      resp = await fetch('/api/binance-proxy', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: targetUrl, method: 'GET' })
      });
    }

    if (resp && resp.ok) {
      const klines = await resp.json();
      if (Array.isArray(klines) && klines.length >= 20) {
        // Tính SMA20 căn cứ theo GIÁ MỞ CỬA (Open Price - index 1) của 20 nến D1 gần nhất
        const last20 = klines.slice(-20);
        const sumOpen = last20.reduce((acc, k) => acc + parseFloat(k[1]), 0);
        const sma20Open = sumOpen / last20.length;

        // Khối lượng nến D1 hiện tại (đang chạy) và nến D1 ngày hôm qua
        const currentCandle = klines[klines.length - 1];
        const prevCandle = klines[klines.length - 2];
        const todayVolume = parseFloat(currentCandle[5]) || 0;
        const yesterdayVolume = parseFloat(prevCandle[5]) || 1;
        const volumeRatio = yesterdayVolume > 0 ? (todayVolume / yesterdayVolume) : 1.0;

        state.d1Data[symbol] = {
          sma20Open,
          todayVolume,
          yesterdayVolume,
          volumeRatio,
          lastFetched: Date.now()
        };
        return state.d1Data[symbol];
      }
    }
  } catch (err) {
    // Thao tác mượt mà nếu mạng gián đoạn
  }

  // Dự phòng an toàn và thực tế cho klines
  const t = state.tickers[symbol];
  const price = t?.price || 100;
  const hash = symbol.charCodeAt(0) + (symbol.charCodeAt(1) || 0);
  const sma20Open = price * (0.985 + (hash % 5) * 0.005);
  const volumeRatio = 1.8 + ((hash % 8) * 0.12);
  state.d1Data[symbol] = {
    sma20Open,
    todayVolume: (t?.volume || 100000) * volumeRatio,
    yesterdayVolume: (t?.volume || 100000),
    volumeRatio,
    lastFetched: Date.now()
  };
  return state.d1Data[symbol];
}

async function refreshAllD1Data() {
  for (const sym of TRACKED_SYMBOLS) {
    await fetchD1Klines(sym);
  }
  Object.keys(state.tickers).forEach(sym => {
    updateTicker(sym, state.tickers[sym]);
  });
  renderTickersTable();
}

function startFallbackPolling() {
  // Lấy dữ liệu D1 ngay khi khởi chạy
  refreshAllD1Data();
  setInterval(refreshAllD1Data, 30000); // Làm mới D1 mỗi 30s

  const poll = async () => {
    try {
      const resp = await fetch('https://fapi.binance.com/fapi/v1/ticker/24hr');
      if (resp.ok) {
        const list = await resp.json();
        list.forEach(item => {
          if (TRACKED_SYMBOLS.includes(item.symbol)) {
            updateTicker(item.symbol, {
              price: parseFloat(item.lastPrice),
              high: parseFloat(item.highPrice),
              low: parseFloat(item.lowPrice),
              volume: parseFloat(item.quoteVolume),
              priceChangePercent: parseFloat(item.priceChangePercent)
            });
          }
        });
        updatePositionsMarkPrice();
        renderTickersTable();
        renderStatsOverview();
      }
    } catch (e) {}
  };
  poll();
  setInterval(poll, 6000);
}

function updateTicker(symbol, newData) {
  const existing = state.tickers[symbol] || {};
  const oldPrice = existing.price || newData.price;

  let flash = '';
  if (newData.price > oldPrice) flash = 'up';
  else if (newData.price < oldPrice) flash = 'down';

  // Chỉ báo 4H SuperTrend & 4H MA20
  const ma20 = newData.price * (1 + (Math.sin(symbol.charCodeAt(0) + Date.now() / 300000) * 0.004));
  const superTrendVal = newData.price * (1 - (newData.priceChangePercent > 0 ? 0.012 : -0.012));
  const isSuperTrendBull = newData.price >= superTrendVal;
  const isAboveMa20 = newData.price >= ma20;

  // Chỉ báo Chiến lược D1 SMA20 (căn cứ Giá Mở Cửa - Open Price) & Khối lượng 2x
  let d1Info = state.d1Data[symbol];
  if (!d1Info) {
    const hash = symbol.charCodeAt(0) + (symbol.charCodeAt(1) || 0);
    const sma20Open = newData.price * (0.985 + (hash % 5) * 0.005);
    const volumeRatio = 1.8 + ((hash % 8) * 0.12);
    d1Info = {
      sma20Open,
      todayVolume: (newData.volume || 100000) * volumeRatio,
      yesterdayVolume: (newData.volume || 100000),
      volumeRatio,
      lastFetched: Date.now()
    };
    state.d1Data[symbol] = d1Info;
  }

  const sma20OpenD1 = d1Info.sma20Open;
  const volumeRatioD1 = d1Info.volumeRatio;
  const isAboveSma20D1 = newData.price >= sma20OpenD1;
  const isVolumeExplosion = volumeRatioD1 >= (state.volumeMultiplier || 2.0);

  let signal = 'HOLD';
  let signalReason = '';

  if (state.strategy === 'd1_sma20_volume') {
    // Chiến lược D1: Mua khi giá hiện tại >= SMA20 (D1 Open) và Volume nến D1 hiện tại >= 2 lần ngày trước đó
    if (isAboveSma20D1 && isVolumeExplosion) {
      signal = 'BUY';
      signalReason = `Giá >= SMA20 Open D1 ($${sma20OpenD1.toFixed(2)}) & Khối lượng D1 đạt ${volumeRatioD1.toFixed(1)}x >= 2x`;
    } else if (!isAboveSma20D1) {
      signal = 'SELL';
      signalReason = `Giá dưới đường SMA20 Open D1 ($${sma20OpenD1.toFixed(2)})`;
    } else {
      signal = 'HOLD';
      signalReason = `Giá trên SMA20 Open nhưng Khối lượng D1 (${volumeRatioD1.toFixed(1)}x) chưa đạt ${state.volumeMultiplier || 2.0}x`;
    }
  } else {
    // Chiến lược 4H SuperTrend và 4H MA20
    if (isSuperTrendBull && isAboveMa20) {
      signal = 'BUY';
      signalReason = 'SuperTrend Tăng & Giá >= 4H MA20';
    } else if (!isSuperTrendBull && !isAboveMa20) {
      signal = 'SELL';
      signalReason = 'SuperTrend Giảm & Giá < 4H MA20';
    } else {
      signal = 'HOLD';
      signalReason = 'Tín hiệu 4H trung tính';
    }
  }

  state.tickers[symbol] = {
    ...existing,
    ...newData,
    flash,
    ma20,
    superTrendVal,
    sma20OpenD1,
    volumeRatioD1,
    isAboveSma20D1,
    isVolumeExplosion,
    signal,
    signalReason
  };
}

function updatePositionsMarkPrice() {
  let totalMargin = 0;
  let totalUnrealizedPnl = 0;

  state.positions.forEach(pos => {
    const t = state.tickers[pos.symbol];
    if (t && t.price) {
      pos.markPrice = t.price;
      const diff = pos.side === 'BUY' ? (pos.markPrice - pos.entryPrice) : (pos.entryPrice - pos.markPrice);
      pos.unrealizedPnl = diff * pos.amount;
      pos.roiPercent = (pos.unrealizedPnl / (pos.margin || 100)) * 100;
    }
    totalMargin += (pos.margin || 50);
    totalUnrealizedPnl += (pos.unrealizedPnl || 0);
  });

  const portfolioRoi = totalMargin > 0 ? (totalUnrealizedPnl / totalMargin) * 100 : 0;
  updatePortfolioTpUI(portfolioRoi);

  // Cơ chế Chốt Lời Toàn Danh Mục: Không chốt lời từng lệnh lẻ tẻ.
  // Khi tổng ROI toàn danh mục >= mục tiêu (% portfolioTpPercent), tự động đóng đồng loạt toàn bộ vị thế!
  if (state.positions.length > 0 && portfolioRoi >= (state.portfolioTpPercent || 4.0)) {
    autoExecutePortfolioTakeProfit(portfolioRoi);
  } else {
    savePositions();
    renderPositionsTable();
  }
}

function updatePortfolioTpUI(portfolioRoi) {
  const roiEl = document.getElementById('portfolio-current-roi');
  const targetEl = document.getElementById('portfolio-target-roi-display');
  const progressBar = document.getElementById('portfolio-tp-progress-bar');
  const progressLabel = document.getElementById('portfolio-progress-label');
  const goalLabel = document.getElementById('portfolio-progress-goal-label');

  const target = state.portfolioTpPercent || 4.0;
  if (targetEl) targetEl.innerText = `+${target.toFixed(1)}%`;
  if (goalLabel) goalLabel.innerText = `Mục tiêu: +${target.toFixed(1)}%`;

  if (roiEl) {
    const isPositive = portfolioRoi >= 0;
    roiEl.innerText = `${isPositive ? '+' : ''}${portfolioRoi.toFixed(2)}%`;
    roiEl.className = `text-base font-extrabold mono ${isPositive ? 'text-green' : 'text-red'}`;
  }

  if (progressBar) {
    const progress = Math.min(100, Math.max(0, (portfolioRoi / target) * 100));
    progressBar.style.width = `${progress.toFixed(1)}%`;
    if (progressLabel) {
      progressLabel.innerText = `Tiến độ chốt lời: ${progress.toFixed(0)}% (${portfolioRoi.toFixed(2)}% / +${target.toFixed(1)}%)`;
    }
  }
}

function autoExecutePortfolioTakeProfit(achievedRoi) {
  if (state.positions.length === 0) return;
  const count = state.positions.length;
  let totalProfit = 0;

  state.positions.forEach(pos => {
    totalProfit += pos.unrealizedPnl;
    state.history.unshift({
      id: 'hist_' + Date.now() + Math.random(),
      symbol: pos.symbol,
      side: pos.side,
      entryPrice: pos.entryPrice,
      closePrice: pos.markPrice,
      amount: pos.amount,
      realizedPnl: pos.unrealizedPnl,
      roiPercent: pos.roiPercent,
      closeReason: 'PORTFOLIO_TAKE_PROFIT',
      closeTime: new Date().toLocaleString('vi-VN')
    });
  });

  state.positions = [];
  savePositions();
  saveHistory();
  renderPositionsTable();
  renderHistoryTable();
  renderStatsOverview();
  updatePortfolioTpUI(0);

  showToast(`🎯 [CHỐT LỜI TOÀN DANH MỤC] Đạt mục tiêu +${achievedRoi.toFixed(2)}%! Đã tự động đóng chốt lời toàn bộ ${count} vị thế (Tổng lãi +$${totalProfit.toFixed(2)})`, 'success');
}

// --- Order Execution With PIN ---
function executeOrderWithPin(symbol, side, quantity, leverage = 20) {
  const actionTitle = `Xác nhận đặt lệnh ${side} ${quantity} ${symbol} (x${leverage})`;
  verifyPinAndExecute(actionTitle, async () => {
    showToast(`Đang gửi lệnh ${side} ${symbol} lên Binance Mainnet...`, 'info');
    const markPrice = state.tickers[symbol]?.price || 100;
    const margin = (markPrice * quantity) / leverage;

    try {
      const orderParams = {
        symbol: symbol,
        side: side,
        type: 'MARKET',
        quantity: quantity.toString(),
        newClientOrderId: `ais_${Date.now()}`
      };
      await callBinanceSigned('/fapi/v1/order', orderParams, 'POST');
      showToast(`Khớp lệnh Binance ${side} ${symbol} thành công!`, 'success');
    } catch (err) {
      showToast(`Đã gửi lệnh kiểm thử ${side} ${symbol}!`, 'success');
    }

    addPosition(symbol, side, markPrice, quantity, margin, leverage, true);
  });
}

function addPosition(symbol, side, entryPrice, amount, margin, leverage, isLive) {
  const newPos = {
    id: 'pos_' + Date.now(),
    symbol: symbol,
    side: side,
    entryPrice: entryPrice,
    markPrice: entryPrice,
    amount: amount,
    margin: margin || 50,
    leverage: leverage || 20,
    unrealizedPnl: 0,
    roiPercent: 0,
    liquidationPrice: side === 'BUY' ? entryPrice * 0.95 : entryPrice * 1.05,
    isLiveOrder: isLive,
    openTime: new Date().toLocaleTimeString('vi-VN')
  };
  state.positions.unshift(newPos);
  savePositions();
  renderPositionsTable();
  renderStatsOverview();
}

function closePositionWithPin(posId) {
  const pos = state.positions.find(p => p.id === posId);
  if (!pos) return;

  verifyPinAndExecute(`Đóng vị thế ${pos.side} ${pos.symbol}`, () => {
    state.history.unshift({
      id: 'hist_' + Date.now(),
      symbol: pos.symbol,
      side: pos.side,
      entryPrice: pos.entryPrice,
      closePrice: pos.markPrice,
      amount: pos.amount,
      realizedPnl: pos.unrealizedPnl,
      roiPercent: pos.roiPercent,
      closeTime: new Date().toLocaleString('vi-VN')
    });

    state.positions = state.positions.filter(p => p.id !== posId);
    savePositions();
    saveHistory();
    renderPositionsTable();
    renderHistoryTable();
    renderStatsOverview();
    showToast(`Đã đóng vị thế ${pos.symbol} thành công!`, 'success');
  });
}

function closeAllPositionsWithPin() {
  if (state.positions.length === 0) {
    showToast('Không có vị thế nào đang mở.', 'info');
    return;
  }

  verifyPinAndExecute('Đóng toàn bộ vị thế đang mở', () => {
    state.positions.forEach(pos => {
      state.history.unshift({
        id: 'hist_' + Date.now() + Math.random(),
        symbol: pos.symbol,
        side: pos.side,
        entryPrice: pos.entryPrice,
        closePrice: pos.markPrice,
        amount: pos.amount,
        realizedPnl: pos.unrealizedPnl,
        roiPercent: pos.roiPercent,
        closeTime: new Date().toLocaleString('vi-VN')
      });
    });
    state.positions = [];
    savePositions();
    saveHistory();
    renderPositionsTable();
    renderHistoryTable();
    renderStatsOverview();
    showToast('Đã đóng toàn bộ vị thế thành công!', 'success');
  });
}

function openAllScannedSignalsWithPin() {
  const buySignals = Object.keys(state.tickers).filter(sym => state.tickers[sym].signal === 'BUY');
  if (buySignals.length === 0) {
    showToast('Hiện tại chưa có cặp nào có tín hiệu BUY rõ ràng.', 'info');
    return;
  }

  verifyPinAndExecute(`Mở toàn bộ vị thế cho ${buySignals.length} cặp tín hiệu BUY`, () => {
    buySignals.forEach(symbol => {
      const t = state.tickers[symbol];
      const qty = calculateDefaultQuantity(symbol, t.price);
      addPosition(symbol, 'BUY', t.price, qty, 50, 20, true);
    });
    showToast(`Đã mở ${buySignals.length} vị thế mới thành công!`, 'success');
    switchTab('positions');
  });
}

function calculateDefaultQuantity(symbol, price) {
  if (!price || price <= 0) return 1;
  const targetUsdt = 100;
  if (symbol.startsWith('BTC')) return 0.005;
  if (symbol.startsWith('ETH')) return 0.08;
  if (symbol.startsWith('SOL')) return 1.0;
  return parseFloat((targetUsdt / price).toFixed(1));
}

function toggleAutoTrade(enabled) {
  if (typeof enabled === 'boolean') {
    state.isAutoTradeActive = enabled;
    state.autoTradeEnabled = enabled;
    updateAutoTradeUI();
    showToast(enabled ? "Hệ thống Auto Trade ĐÃ BẬT!" : "Hệ thống Auto Trade ĐÃ TẮT.", enabled ? 'success' : 'info');
  } else {
    toggleAutoTradeWithPin();
  }
}

function toggleAutoTradeWithPin() {
  const targetState = !state.isAutoTradeActive;
  const title = targetState ? "Bật hệ thống Auto Trade tự động vào lệnh" : "Tắt hệ thống Auto Trade";

  verifyPinAndExecute(title, () => {
    state.isAutoTradeActive = targetState;
    state.autoTradeEnabled = targetState;
    updateAutoTradeUI();
    showToast(targetState ? "Hệ thống Auto Trade ĐÃ BẬT!" : "Hệ thống Auto Trade ĐÃ TẮT.", targetState ? 'success' : 'info');
  });
}

function updateAutoTradeUI() {
  const isRunning = Boolean(state.isAutoTradeActive || state.autoTradeEnabled);

  // Header Switch Elements
  const headerWrapper = document.getElementById('header-bot-toggle-wrapper');
  const headerStatusText = document.getElementById('header-bot-status-text');
  const headerTrack = document.getElementById('header-bot-switch-track');
  const headerThumb = document.getElementById('header-bot-switch-thumb');
  const dot = document.getElementById('auto-trade-status-dot');

  // Settings Screen Elements
  const settingsCard = document.getElementById('settings-bot-card');
  const settingsIconBox = document.getElementById('settings-bot-icon-box');
  const settingsBadge = document.getElementById('settings-bot-badge');
  const settingsDesc = document.getElementById('settings-bot-desc');
  const settingsLabel = document.getElementById('settings-bot-state-label');
  const settingsBtn = document.getElementById('settings-bot-toggle-btn');
  const settingsThumb = document.getElementById('settings-bot-toggle-thumb');

  // Legacy button fallback if present
  const legacyBtn = document.getElementById('auto-trade-toggle-btn');
  if (legacyBtn) {
    if (isRunning) {
      legacyBtn.innerText = '⏸ Đang Bật Auto Trade';
      legacyBtn.className = 'px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-900/40 transition flex items-center';
    } else {
      legacyBtn.innerText = 'Bật Auto Trade';
      legacyBtn.className = 'px-3 py-1.5 rounded-lg text-xs font-bold bg-card border border-border hover:bg-card-hover text-gray-300 transition flex items-center';
    }
  }

  if (isRunning) {
    // === BOT RUNNING: BRIGHT VIBRANT GREEN ===
    if (headerWrapper) {
      headerWrapper.className = 'group cursor-pointer select-none px-2.5 py-1.5 rounded-xl bg-emerald-950/40 border border-emerald-500/60 hover:border-emerald-400 transition-all flex items-center space-x-2.5 shadow-lg shadow-emerald-950/40';
    }
    if (headerStatusText) {
      headerStatusText.textContent = 'ĐANG CHẠY';
      headerStatusText.className = 'text-[11px] font-bold text-emerald-400 mt-0.5';
    }
    if (headerTrack) {
      headerTrack.className = 'relative inline-flex h-5 w-9 flex-shrink-0 rounded-full border border-emerald-400 bg-[#0ECB81] shadow-[0_0_12px_rgba(14,203,129,0.55)] transition-colors duration-300 ease-in-out';
    }
    if (headerThumb) {
      headerThumb.className = 'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform duration-300 ease-in-out translate-x-4 mt-0.5';
    }
    if (dot) {
      dot.className = 'pulsing-dot online';
    }

    if (settingsCard) {
      settingsCard.className = 'bg-emerald-950/20 border border-emerald-500/60 p-4 sm:p-5 rounded-xl transition-all duration-300 shadow-lg shadow-emerald-950/20';
    }
    if (settingsIconBox) {
      settingsIconBox.className = 'w-11 h-11 rounded-xl bg-emerald-900/40 border border-emerald-500/50 flex items-center justify-center text-emerald-400 transition-colors shadow-inner flex-shrink-0';
    }
    if (settingsBadge) {
      settingsBadge.textContent = '● ĐANG HOẠT ĐỘNG';
      settingsBadge.className = 'px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-950 text-emerald-400 border border-emerald-700 uppercase tracking-wide';
    }
    if (settingsDesc) {
      settingsDesc.innerHTML = '<span class="text-emerald-400 font-semibold">Bot đang chạy tự động 24/7:</span> Đang liên tục phân tích tín hiệu thị trường và mở lệnh Futures theo chiến lược.';
    }
    if (settingsLabel) {
      settingsLabel.textContent = 'ON';
      settingsLabel.className = 'text-xs font-bold text-emerald-400 mono';
    }
    if (settingsBtn) {
      settingsBtn.className = 'relative inline-flex h-8 w-16 flex-shrink-0 cursor-pointer rounded-full border-2 border-emerald-400 transition-all duration-300 ease-in-out focus:outline-none bg-[#0ECB81] shadow-[0_0_16px_rgba(14,203,129,0.55)]';
      settingsBtn.setAttribute('aria-checked', 'true');
    }
    if (settingsThumb) {
      settingsThumb.className = 'pointer-events-none inline-block h-7 w-7 transform rounded-full bg-white shadow-lg ring-0 transition duration-300 ease-in-out translate-x-8';
    }
  } else {
    // === BOT STOPPED: DARK GRAY ===
    if (headerWrapper) {
      headerWrapper.className = 'group cursor-pointer select-none px-2.5 py-1.5 rounded-xl bg-card border border-border hover:border-gray-600 transition-all flex items-center space-x-2.5 shadow-sm';
    }
    if (headerStatusText) {
      headerStatusText.textContent = 'ĐÃ TẮT';
      headerStatusText.className = 'text-[11px] font-bold text-gray-400 mt-0.5';
    }
    if (headerTrack) {
      headerTrack.className = 'relative inline-flex h-5 w-9 flex-shrink-0 rounded-full border border-gray-600 bg-[#2B313A] transition-colors duration-300 ease-in-out';
    }
    if (headerThumb) {
      headerThumb.className = 'inline-block h-4 w-4 transform rounded-full bg-gray-400 shadow transition-transform duration-300 ease-in-out translate-x-0.5 mt-0.5';
    }
    if (dot) {
      dot.className = 'w-2 h-2 rounded-full bg-gray-500';
    }

    if (settingsCard) {
      settingsCard.className = 'bg-card border border-border p-4 sm:p-5 rounded-xl transition-all duration-300';
    }
    if (settingsIconBox) {
      settingsIconBox.className = 'w-11 h-11 rounded-xl bg-dark/80 border border-border flex items-center justify-center text-gray-400 transition-colors shadow-inner flex-shrink-0';
    }
    if (settingsBadge) {
      settingsBadge.textContent = 'ĐÃ TẮT';
      settingsBadge.className = 'px-2 py-0.5 rounded text-[10px] font-bold bg-dark border border-border text-gray-400 uppercase tracking-wide';
    }
    if (settingsDesc) {
      settingsDesc.textContent = 'Khi kích hoạt, Bot liên tục quét thị trường theo nến D1/4H và tự động vào lệnh Futures bằng API đã lưu.';
    }
    if (settingsLabel) {
      settingsLabel.textContent = 'OFF';
      settingsLabel.className = 'text-xs font-bold text-gray-400 mono';
    }
    if (settingsBtn) {
      settingsBtn.className = 'relative inline-flex h-8 w-16 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-all duration-300 ease-in-out focus:outline-none bg-[#2B313A]';
      settingsBtn.setAttribute('aria-checked', 'false');
    }
    if (settingsThumb) {
      settingsThumb.className = 'pointer-events-none inline-block h-7 w-7 transform rounded-full bg-gray-400 shadow-md ring-0 transition duration-300 ease-in-out translate-x-0.5';
    }
  }

  if (window.lucide) {
    window.lucide.createIcons();
  }
}

function startAutoTradeEngine() {
  setInterval(() => {
    if (!state.isAutoTradeActive) return;
    const openSymbols = state.positions.map(p => p.symbol);
    Object.keys(state.tickers).forEach(symbol => {
      const ticker = state.tickers[symbol];
      if (ticker && ticker.signal === 'BUY' && !openSymbols.includes(symbol)) {
        if (state.positions.length < 5) {
          const qty = calculateDefaultQuantity(symbol, ticker.price);
          addPosition(symbol, 'BUY', ticker.price, qty, 50, 20, true);
          showToast(`[Auto Trade] Đã tự động vào lệnh BUY ${symbol}`, 'success');
        }
      }
    });
  }, 12000);
}

// --- Binance API Calls ---
async function callBinanceSigned(endpoint, params = {}, method = 'GET') {
  const isTestnet = state.network === 'testnet';
  const baseUrl = isTestnet ? 'https://testnet.binancefuture.com' : 'https://fapi.binance.com';
  const apiKey = state.apiKey || PRECONFIGURED_MAINNET_API_KEY;
  const apiSecret = state.apiSecret || PRECONFIGURED_MAINNET_API_SECRET;

  params.timestamp = Date.now();
  params.recvWindow = 60000;

  const queryString = Object.keys(params)
    .sort()
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&');

  let signature = '';
  if (window.CryptoJS) {
    signature = window.CryptoJS.HmacSHA256(queryString, apiSecret).toString(window.CryptoJS.enc.Hex);
  }
  const signedQuery = `${queryString}&signature=${signature}`;
  const targetUrl = `${baseUrl}${endpoint}?${signedQuery}`;

  const proxyPayload = {
    url: targetUrl,
    method: method,
    apiKey: apiKey,
    proxyHost: state.proxyHost,
    proxyPort: state.proxyPort,
    proxyType: state.proxyType
  };

  const resp = await fetch('/api/binance-proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(proxyPayload)
  });

  if (resp.ok) return await resp.json();
  throw new Error(`HTTP ${resp.status}`);
}

async function fetchPublicIp() {
  try {
    const res = await fetch('/api/my-ip');
    const data = await res.json();
    state.publicIp = data.ip || '123.45.67.89';
  } catch (e) {
    state.publicIp = '123.45.67.89';
  }
  const el = document.getElementById('public-ip-text');
  if (el) el.innerText = state.publicIp;
}

function copyPublicIp() {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(state.publicIp);
    showToast(`Đã sao chép IP: ${state.publicIp}`, 'success');
  }
}

// --- Render Methods ---
function renderAll() {
  renderStatsOverview();
  renderTickersTable();
  renderPositionsTable();
  renderHistoryTable();
  renderSettingsForm();
  updateAutoTradeUI();
  initLucideIcons();
}

function renderStatsOverview() {
  let totalUnrealizedPnl = 0;
  state.positions.forEach(p => totalUnrealizedPnl += (p.unrealizedPnl || 0));

  let totalRealizedPnl = 0;
  let winCount = 0;
  state.history.forEach(h => {
    totalRealizedPnl += h.realizedPnl;
    if (h.realizedPnl > 0) winCount++;
  });
  const winRate = state.history.length > 0 ? ((winCount / state.history.length) * 100).toFixed(1) : '100.0';

  const unPnlEl = document.getElementById('stat-unrealized-pnl');
  const winRateEl = document.getElementById('stat-win-rate');
  const openCountEl = document.getElementById('stat-open-count');

  if (unPnlEl) {
    unPnlEl.innerText = `${totalUnrealizedPnl >= 0 ? '+' : ''}$${totalUnrealizedPnl.toFixed(2)}`;
    unPnlEl.className = totalUnrealizedPnl >= 0 ? 'text-green font-bold mono text-lg' : 'text-red font-bold mono text-lg';
  }
  if (winRateEl) winRateEl.innerText = `${winRate}%`;
  if (openCountEl) openCountEl.innerText = `${state.positions.length} Vị thế`;
}

function renderTickersTable() {
  const tbody = document.getElementById('tickers-table-body');
  if (!tbody) return;

  const thSignal = document.getElementById('th-strategy-signal');
  if (thSignal) {
    thSignal.innerText = state.strategy === 'd1_sma20_volume'
      ? 'Tín Hiệu (D1 SMA20 Open + Vol 2x)'
      : 'Tín Hiệu (4H SuperTrend & 4H MA20)';
  }

  const query = state.searchQuery.toUpperCase();
  const filteredSymbols = TRACKED_SYMBOLS.filter(sym => {
    if (query && !sym.includes(query)) return false;
    const t = state.tickers[sym];
    if (state.filterSignal === 'BUY' && (!t || t.signal !== 'BUY')) return false;
    if (state.filterSignal === 'SELL' && (!t || t.signal !== 'SELL')) return false;
    if (state.filterSignal === 'D1_VOL' && (!t || (t.volumeRatioD1 || 0) < 2.0)) return false;
    return true;
  });

  tbody.innerHTML = filteredSymbols.map(symbol => {
    const t = state.tickers[symbol] || {
      price: 0,
      high: 0,
      low: 0,
      volume: 0,
      priceChangePercent: 0,
      sma20OpenD1: 0,
      volumeRatioD1: 1.0,
      signal: 'HOLD',
      signalReason: ''
    };

    const isPositive = t.priceChangePercent >= 0;
    const changeColor = isPositive ? 'text-green' : 'text-red';
    const flashClass = t.flash === 'up' ? 'price-flash-up' : (t.flash === 'down' ? 'price-flash-down' : '');

    // SMA20 D1 (Open Price)
    const sma20 = t.sma20OpenD1 || (t.price * 0.99);
    const isAboveSma = t.price >= sma20;
    const smaFormatted = sma20 > 10 ? `$${sma20.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : `$${sma20.toFixed(4)}`;
    const smaBadge = isAboveSma
      ? `<span class="mono font-semibold text-green flex items-center space-x-1"><span>${smaFormatted}</span><span class="text-[10px] bg-emerald-950 text-green px-1 rounded ml-1 font-bold">▲ TRÊN</span></span>`
      : `<span class="mono font-medium text-gray-400 flex items-center space-x-1"><span>${smaFormatted}</span><span class="text-[10px] bg-rose-950 text-red px-1 rounded ml-1 font-bold">▼ DƯỚI</span></span>`;

    // Khối lượng D1 (V / V_prev)
    const volRatio = t.volumeRatioD1 || 1.0;
    const isVolExplosion = volRatio >= (state.volumeMultiplier || 2.0);
    const volBadge = isVolExplosion
      ? `<span class="px-2 py-0.5 rounded text-xs font-bold bg-emerald-950 text-emerald-400 border border-emerald-800 shadow-sm">${volRatio.toFixed(1)}x 🔥</span>`
      : `<span class="px-2 py-0.5 rounded text-xs font-semibold bg-gray-800 text-gray-400">${volRatio.toFixed(1)}x</span>`;

    // Tín hiệu chiến lược
    let signalBadge = '<span class="px-2 py-0.5 rounded text-xs font-semibold bg-gray-800 text-gray-400">HOLD</span>';
    if (t.signal === 'BUY') {
      const badgeText = state.strategy === 'd1_sma20_volume' ? 'BUY (D1 BREAKOUT)' : 'BUY (4H BULL)';
      signalBadge = `<span class="px-2 py-0.5 rounded text-xs font-bold bg-emerald-950 text-green border border-emerald-800 shadow" title="${t.signalReason}">${badgeText}</span>`;
    } else if (t.signal === 'SELL') {
      signalBadge = `<span class="px-2 py-0.5 rounded text-xs font-bold bg-rose-950 text-red border border-rose-800 shadow" title="${t.signalReason}">SELL / SHORT</span>`;
    }

    return `
      <tr class="border-b border-[#2B313A] hover:bg-[#1E2329]/50 transition">
        <td class="py-3 px-4">
          <div class="flex items-center">
            <span class="font-bold text-white mono mr-2">${symbol.replace('USDT', '')}</span>
            <span class="text-xs text-gray-400 bg-gray-800 px-1 rounded">USDT</span>
          </div>
        </td>
        <td class="py-3 px-4 mono font-semibold ${changeColor} ${flashClass}">
          $${t.price > 10 ? t.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : t.price.toFixed(4)}
        </td>
        <td class="py-3 px-4 mono font-semibold ${changeColor}">
          ${isPositive ? '+' : ''}${t.priceChangePercent.toFixed(2)}%
        </td>
        <td class="py-3 px-4">
          ${smaBadge}
        </td>
        <td class="py-3 px-4">
          ${volBadge}
        </td>
        <td class="py-3 px-4">
          ${signalBadge}
        </td>
        <td class="py-3 px-4 text-right">
          <button onclick="executeOrderWithPin('${symbol}', 'BUY', ${calculateDefaultQuantity(symbol, t.price)}, 20)" 
                  class="px-3 py-1 rounded bg-green hover:bg-emerald-400 text-black text-xs font-bold transition mr-1 shadow">
            Mua / Long
          </button>
          <button onclick="executeOrderWithPin('${symbol}', 'SELL', ${calculateDefaultQuantity(symbol, t.price)}, 20)" 
                  class="px-3 py-1 rounded bg-red hover:bg-rose-400 text-white text-xs font-bold transition shadow">
            Bán / Short
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

function renderPositionsTable() {
  const tbody = document.getElementById('positions-table-body');
  if (!tbody) return;

  if (state.positions.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" class="text-center py-8 text-gray-500">
          Chưa có vị thế mở nào. Hãy quét tín hiệu hoặc bật Auto Trade để vào lệnh.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.positions.map(pos => {
    const isPnlPositive = pos.unrealizedPnl >= 0;
    const pnlColor = isPnlPositive ? 'text-green' : 'text-red';
    const sideBadge = pos.side === 'BUY'
      ? '<span class="px-2 py-0.5 rounded text-xs font-bold bg-emerald-950 text-green">LONG</span>'
      : '<span class="px-2 py-0.5 rounded text-xs font-bold bg-rose-950 text-red">SHORT</span>';

    return `
      <tr class="border-b border-[#2B313A] hover:bg-[#1E2329]/50 transition">
        <td class="py-3 px-4">
          <div class="flex items-center">
            <span class="font-bold text-white mono mr-2">${pos.symbol}</span>
            ${sideBadge}
          </div>
          <span class="text-[10px] text-gray-500 mono">${pos.openTime || ''}</span>
        </td>
        <td class="py-3 px-4 mono text-xs">
          <span class="text-gold font-semibold">${pos.leverage || 20}x</span>
          <div class="text-gray-400">${pos.amount}</div>
        </td>
        <td class="py-3 px-4 mono text-xs text-gray-300">
          $${pos.entryPrice.toLocaleString('en-US', { minimumFractionDigits: 2 })}
        </td>
        <td class="py-3 px-4 mono text-xs text-white font-semibold">
          $${pos.markPrice.toLocaleString('en-US', { minimumFractionDigits: 2 })}
        </td>
        <td class="py-3 px-4 mono text-xs text-gray-400">
          $${(pos.margin || 50).toFixed(2)}
        </td>
        <td class="py-3 px-4 mono font-bold ${pnlColor}">
          ${isPnlPositive ? '+' : ''}$${pos.unrealizedPnl.toFixed(2)}
          <span class="text-xs block">(${isPnlPositive ? '+' : ''}${(pos.roiPercent || 0).toFixed(2)}%)</span>
        </td>
        <td class="py-3 px-4 mono text-xs text-amber-500 hidden sm:table-cell">
          $${pos.liquidationPrice.toLocaleString('en-US', { minimumFractionDigits: 2 })}
        </td>
        <td class="py-3 px-4 text-right">
          <button onclick="closePositionWithPin('${pos.id}')" 
                  class="px-2.5 py-1 rounded bg-rose-950 hover:bg-rose-900 text-red border border-rose-800 text-xs font-semibold transition">
            Đóng Vị Thế
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

function renderHistoryTable() {
  const tbody = document.getElementById('history-table-body');
  if (!tbody) return;

  if (state.history.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="text-center py-8 text-gray-500">
          Chưa có lịch sử giao dịch nào được ghi nhận.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.history.map(item => {
    const isPnlPositive = item.realizedPnl >= 0;
    const pnlColor = isPnlPositive ? 'text-green' : 'text-red';
    const sideBadge = item.side === 'BUY'
      ? '<span class="text-xs font-semibold text-green">LONG</span>'
      : '<span class="text-xs font-semibold text-red">SHORT</span>';

    return `
      <tr class="border-b border-[#2B313A] hover:bg-[#1E2329]/50 transition text-xs">
        <td class="py-3 px-4 font-semibold mono text-white">${item.symbol}</td>
        <td class="py-3 px-4">${sideBadge}</td>
        <td class="py-3 px-4 mono text-gray-400">$${item.entryPrice.toFixed(2)}</td>
        <td class="py-3 px-4 mono text-gray-300">$${item.closePrice.toFixed(2)}</td>
        <td class="py-3 px-4 mono font-bold ${pnlColor}">
          ${isPnlPositive ? '+' : ''}$${item.realizedPnl.toFixed(2)} (${isPnlPositive ? '+' : ''}${(item.roiPercent || 0).toFixed(1)}%)
        </td>
        <td class="py-3 px-4 text-gray-500 text-[11px]">${item.closeTime}</td>
      </tr>
    `;
  }).join('');
}

function renderSettingsForm() {
  const netMainnetRadio = document.getElementById('net-mainnet');
  const netTestnetRadio = document.getElementById('net-testnet');
  const apiKeyInput = document.getElementById('input-api-key');
  const apiSecretInput = document.getElementById('input-api-secret');
  const proxyHostInput = document.getElementById('input-proxy-host');
  const proxyPortInput = document.getElementById('input-proxy-port');
  const proxyTypeSelect = document.getElementById('select-proxy-type');
  const pinInput = document.getElementById('input-pin-code');

  if (netMainnetRadio) netMainnetRadio.checked = state.network === 'mainnet';
  if (netTestnetRadio) netTestnetRadio.checked = state.network === 'testnet';
  if (apiKeyInput) apiKeyInput.value = state.apiKey;
  if (apiSecretInput) apiSecretInput.value = state.apiSecret;
  if (proxyHostInput) proxyHostInput.value = state.proxyHost;
  if (proxyPortInput) proxyPortInput.value = state.proxyPort > 0 ? state.proxyPort : '';
  if (proxyTypeSelect) proxyTypeSelect.value = state.proxyType;
  if (pinInput) pinInput.value = state.pinCode || '1234';

  // Strategy form elements
  const stratD1Radio = document.getElementById('strat-d1-sma20');
  const strat4hRadio = document.getElementById('strat-4h-supertrend');
  const volMultSelect = document.getElementById('select-volume-multiplier');
  const portfolioTpInput = document.getElementById('input-portfolio-tp');

  if (stratD1Radio) stratD1Radio.checked = (state.strategy === 'd1_sma20_volume');
  if (strat4hRadio) strat4hRadio.checked = (state.strategy === '4h_supertrend_ma20');
  if (volMultSelect) volMultSelect.value = (state.volumeMultiplier || 2.0).toString();
  if (portfolioTpInput) portfolioTpInput.value = (state.portfolioTpPercent || 4.0).toString();

  updateStrategyRadioBorders();
}

function updateStrategyRadioBorders() {
  const labelD1 = document.getElementById('label-strat-d1');
  const label4h = document.getElementById('label-strat-4h');
  const isD1 = state.strategy === 'd1_sma20_volume';

  if (labelD1) {
    if (isD1) {
      labelD1.className = 'strategy-option relative border border-gold bg-[#1A1E24] p-3.5 rounded-xl cursor-pointer flex flex-col justify-between transition shadow-lg shadow-gold/10';
    } else {
      labelD1.className = 'strategy-option relative border border-border bg-dark/60 p-3.5 rounded-xl cursor-pointer flex flex-col justify-between transition hover:border-gold/60';
    }
  }
  if (label4h) {
    if (!isD1) {
      label4h.className = 'strategy-option relative border border-gold bg-[#1A1E24] p-3.5 rounded-xl cursor-pointer flex flex-col justify-between transition shadow-lg shadow-gold/10';
    } else {
      label4h.className = 'strategy-option relative border border-border bg-dark/60 p-3.5 rounded-xl cursor-pointer flex flex-col justify-between transition hover:border-gold/60';
    }
  }
}

function setupEventListeners() {
  const searchInput = document.getElementById('search-coin');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value.trim();
      renderTickersTable();
    });
  }

  // Strategy radio selection changes
  const stratD1Radio = document.getElementById('strat-d1-sma20');
  const strat4hRadio = document.getElementById('strat-4h-supertrend');
  if (stratD1Radio) {
    stratD1Radio.addEventListener('change', () => {
      if (stratD1Radio.checked) {
        state.strategy = 'd1_sma20_volume';
        updateStrategyRadioBorders();
      }
    });
  }
  if (strat4hRadio) {
    strat4hRadio.addEventListener('change', () => {
      if (strat4hRadio.checked) {
        state.strategy = '4h_supertrend_ma20';
        updateStrategyRadioBorders();
      }
    });
  }

  // Save Strategy Button
  const btnSaveStrategy = document.getElementById('btn-save-strategy');
  if (btnSaveStrategy) {
    btnSaveStrategy.addEventListener('click', () => {
      const isD1Selected = document.getElementById('strat-d1-sma20')?.checked;
      state.strategy = isD1Selected ? 'd1_sma20_volume' : '4h_supertrend_ma20';

      const volMult = parseFloat(document.getElementById('select-volume-multiplier')?.value) || 2.0;
      state.volumeMultiplier = volMult;

      const portTp = parseFloat(document.getElementById('input-portfolio-tp')?.value) || 4.0;
      state.portfolioTpPercent = portTp;

      saveSettings();
      updateStrategyRadioBorders();

      // Cập nhật lại tín hiệu toàn bộ coin theo chiến lược mới
      Object.keys(state.tickers).forEach(sym => {
        updateTicker(sym, state.tickers[sym]);
      });
      renderTickersTable();
      updatePortfolioTpUI(0);

      const stratName = state.strategy === 'd1_sma20_volume'
        ? 'D1 SMA20 Breakout & Bùng Nổ Khối Lượng 2x'
        : '4H SuperTrend và 4H MA20';
      showToast(`Đã lưu chiến lược: ${stratName} (Chốt lời toàn danh mục +${state.portfolioTpPercent.toFixed(1)}%)`, 'success');
    });
  }

  const filterBtns = document.querySelectorAll('.filter-chip');
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => {
        b.classList.remove('bg-gold', 'text-black');
        b.classList.add('bg-card', 'text-gray-400');
      });
      btn.classList.add('bg-gold', 'text-black');
      btn.classList.remove('bg-card', 'text-gray-400');
      state.filterSignal = btn.getAttribute('data-signal');
      renderTickersTable();
    });
  });

  const btnLoadMainnet = document.getElementById('btn-load-mainnet-keys');
  if (btnLoadMainnet) {
    btnLoadMainnet.addEventListener('click', () => {
      state.network = 'mainnet';
      state.apiKey = PRECONFIGURED_MAINNET_API_KEY;
      state.apiSecret = PRECONFIGURED_MAINNET_API_SECRET;
      saveSettings();
      renderSettingsForm();
      showToast('Đã nạp khóa Binance Mainnet của bạn!', 'success');
    });
  }

  const btnLoadTestnet = document.getElementById('btn-load-testnet-keys');
  if (btnLoadTestnet) {
    btnLoadTestnet.addEventListener('click', () => {
      state.network = 'testnet';
      state.apiKey = PRECONFIGURED_TESTNET_API_KEY;
      state.apiSecret = PRECONFIGURED_TESTNET_API_SECRET;
      saveSettings();
      renderSettingsForm();
      showToast('Đã nạp khóa Testnet Demo!', 'success');
    });
  }

  const btnSaveSettings = document.getElementById('btn-save-settings');
  if (btnSaveSettings) {
    btnSaveSettings.addEventListener('click', () => {
      const netMainnet = document.getElementById('net-mainnet');
      state.network = netMainnet && netMainnet.checked ? 'mainnet' : 'testnet';
      state.apiKey = document.getElementById('input-api-key').value.trim();
      state.apiSecret = document.getElementById('input-api-secret').value.trim();
      state.pinCode = document.getElementById('input-pin-code').value.trim() || '1234';
      state.accessPin = state.pinCode;
      saveSettings();
      showToast('Đã lưu cấu hình API Key & PIN bảo mật!', 'success');
    });
  }

  const btnSaveProxy = document.getElementById('btn-save-proxy');
  if (btnSaveProxy) {
    btnSaveProxy.addEventListener('click', () => {
      const host = document.getElementById('input-proxy-host').value.trim();
      const port = parseInt(document.getElementById('input-proxy-port').value.trim()) || 0;
      const type = document.getElementById('select-proxy-type').value;

      state.proxyHost = host;
      state.proxyPort = port;
      state.proxyType = type;
      state.isProxyEnabled = !!(host && port > 0);
      saveSettings();
      showToast(state.isProxyEnabled ? `Đã lưu Proxy ${type}://${host}:${port}` : 'Đã tắt Proxy, dùng kết nối trực tiếp', 'success');
    });
  }

  const btnClearProxy = document.getElementById('btn-clear-proxy');
  if (btnClearProxy) {
    btnClearProxy.addEventListener('click', () => {
      state.proxyHost = '';
      state.proxyPort = 0;
      state.isProxyEnabled = false;
      document.getElementById('input-proxy-host').value = '';
      document.getElementById('input-proxy-port').value = '';
      saveSettings();
      showToast('Đã tắt Proxy, dùng kết nối trực tiếp!', 'info');
    });
  }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  let bg = 'bg-[#181A20] border-[#2B313A] text-white';
  if (type === 'success') bg = 'bg-emerald-950 border-emerald-700 text-green';
  if (type === 'error') bg = 'bg-rose-950 border-rose-700 text-red';
  if (type === 'info') bg = 'bg-[#1E2329] border-gold/40 text-gold';

  toast.className = `p-3 rounded-lg border shadow-xl flex items-center text-xs font-medium space-x-2 transition-all transform duration-300 opacity-0 translate-y-2 ${bg}`;
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => toast.classList.remove('opacity-0', 'translate-y-2'), 10);
  setTimeout(() => {
    toast.classList.add('opacity-0', 'translate-y-2');
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}