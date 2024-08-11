import pandas as pd

class PARAMS():
  # =========================================================================================================================
  # AGGREGATION PARAMS
  # =========================================================================================================================
  exchange = 'binance-futures' # okex-swap, bybit
  symbols_to_aggregate = ['ETHUSDT', 'UNIUSDT', 'KAVAUSDT', 'SUSHIUSDT', 'ALPHAUSDT', 'AAVEUSDT', 'BELUSDT']
  candles_sec = 5
  date_from = "2024-04-19"
  date_to   = "2024-05-12"
  save_to   = "data/df_test.parquet"
  tardis_api_key = 'Your_Tardis_API_Key'

  # =========================================================================================================================
  # INDEX PARAMS
  # =========================================================================================================================
  slippage = pd.read_csv('data/binance-futures_slippage.csv', index_col=0).loc['95_quantile_10000']
  fee = 0.00025
  extra_days = 3
  leads = ['ETHUSDT', 'UNIUSDT', 'KAVAUSDT'] 
  lags  = ['SUSHIUSDT', 'ALPHAUSDT', 'AAVEUSDT', 'BELUSDT']
  symbols = leads + lags 
  weights_type = 'mean' # std

  # =========================================================================================================================
  # BACKTEST PARAMS
  # =========================================================================================================================
  weeks_to_backtest = None # all weeks
  window_MA = 28000 # optimal window_MA in [27k,28k] or [19k,20k] with c_enter in [3,4] or in [1k,6k] with c_enter in [5,7]
  window_V  = 28000 
  c_enter = 3.5
  c_exit  = 0
  c_stop  = 10 # [7,10]
  stop_cap = 2 # 0.2 = 20% of index movement
  std_cap = 2 #0.045 
  wait_candles = 1_000_000
  is_exit_roll = False
  use_ewm_std = False

  # =========================================================================================================================
  # MC GROUPS PARAMS
  # =========================================================================================================================
  mc_n_MC = 100 # better is 25000+
  mc_seed = 42
  mc_save_to = 'data'
  mc_save_iter = mc_n_MC # if == mc_n_MC then no batching

  # =========================================================================================================================
  # PLOT PARAMS
  # =========================================================================================================================
  backend = 'plotly' # the rest params only for backend = 'matplotlib', dont use it)))
  pnls = 'week' # week, all or None
  trades_vlines = True
  trades_time = False
  ma = True
  enters = False
  stops = False
  std = False
  vlines_weeks = True


PARAMS.__getitem__ = lambda self, key: getattr(self, key)
PARAMS.__setitem__ = lambda self, key, value: setattr(self, key, value)
PARAMS.get = lambda self, key, default=None: getattr(self, key, default)