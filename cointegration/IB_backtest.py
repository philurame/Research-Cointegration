from IB_init import IndexBacktest
import polars as pl
import numpy as np
from numba import njit
from numba.core import types
from numba.typed import Dict

def backtest_week(self, i_week):
  window_MA = int(self.params['window_MA'])
  window_V  = int(self.params['window_V'])
  c_enter_long  = self.params['c_enter']
  c_enter_short = self.params['c_enter']
  c_exit = self.params['c_exit']
  c_stop = self.params['c_stop']
  stop_cap = self.params['stop_cap']
  wait_candles = self.params['wait_candles']
  is_exit_roll = self.params['is_exit_roll']
  std_cap = self.params['std_cap']

  n_heat_up = max(self.params['extra_days']*24*3600//5, window_MA, window_V)
  index_spread = self.df_weeks[i_week, 'index_spread']
  ma = self.df_weeks[i_week].select(pl.col('index_spread').explode().ewm_mean(span=window_MA, ignore_nulls=True)).to_numpy().reshape(-1)[n_heat_up:]
  if self.params['use_ewm_std']:
    std = self.df_weeks[i_week].select(pl.col('index_spread').explode().ewm_std(span=window_V, ignore_nulls=True).clip(0,std_cap)).to_numpy().reshape(-1)[n_heat_up:]
  else:
    std = self.df_weeks[i_week].select(pl.col('index_spread').explode().rolling_std(window_size=window_V).clip(0,std_cap)).to_numpy().reshape(-1)[n_heat_up:]

  index_spread = index_spread.to_numpy().reshape(-1)[n_heat_up:]

  _params = Dict.empty(types.unicode_type, types.float64)
  _params.update(dict(
    c_enter_long  = c_enter_long,
    c_enter_short = c_enter_short,
    c_exit = c_exit,
    c_stop = c_stop,
    wait_candles = wait_candles,
    is_exit_roll = is_exit_roll,
    stop_cap = stop_cap,
  ))
  
  poses, stops, raw_pnls = self._backtest(index_spread, ma, std, _params)
  
  index_buy  = self.df_weeks[i_week, 'index_buy'][n_heat_up:]
  index_sell = self.df_weeks[i_week, 'index_sell'][n_heat_up:]
  buy_pnl  = index_spread - index_buy
  sell_pnl = index_sell - index_spread

  index_spread_diff = np.diff(index_spread, prepend=0)
  pos_diff = np.diff(poses, prepend=0)

  non_realised_pnl = np.where(poses==1, index_spread_diff, np.where(poses==-1, -index_spread_diff, 0))
  pnls = np.where(pos_diff==1, buy_pnl, np.where(pos_diff==-1, sell_pnl, non_realised_pnl))
  if poses[-1] != 0: 
    pnls[-1] += sell_pnl[-1] if poses[-1] == 1 else buy_pnl[-1]
    poses[-1] = 0
  
  poses, stops = poses.astype(np.int8), stops.astype(np.int8)
  raw_pnls, pnls = raw_pnls.astype(np.float32), pnls.astype(np.float32)
  return pl.DataFrame(
    data   = [[index_spread], [ma], [std], [poses], [stops], [raw_pnls], [pnls]], 
    schema = ['index_spread', 'ma', 'std', 'poses', 'stops', 'raw_pnls', 'pnls']
    )

def run_backtest_weeks(self):
  if self.params.get('weeks_to_backtest', None) is None: 
    self.params['weeks_to_backtest'] = np.arange(len(self.df_weeks))
  df_backtest_weeks = pl.DataFrame()
  for i in self.params['weeks_to_backtest']:
    df_backtest = self.backtest_week(int(i))
    df_backtest_weeks = df_backtest_weeks.vstack(df_backtest)
  return df_backtest_weeks

@staticmethod
@njit
def _backtest(Spread, MA, STD, _params):
  exit_long, exit_short, stop_long, stop_short = np.inf, -np.inf, -np.inf, np.inf
  idx_enter = None
  wait_candles = _params['wait_candles']
  touched_MA = False

  poses = np.zeros(len(Spread))
  stops = np.zeros(len(Spread))
  pnls  = np.zeros(len(Spread))

  c_enter_long  = _params['c_enter_long']
  c_enter_short = _params['c_enter_short']
  c_exit  = _params['c_exit']
  c_stop  = _params['c_stop']
  stop_cap = _params['stop_cap']
  is_exit_roll = _params['is_exit_roll']

  pos = 0

  # ======================== backtest loop ========================
  for idx in range(1, len(Spread)):
    pnls[idx] = 0
    is_stop = 0

    spread = Spread[idx]
    MA_spread = MA[idx]

    pred_spread = Spread[idx-1]
    touched_MA = touched_MA or (min(pred_spread, spread) < MA_spread) and (MA_spread < max(pred_spread, spread))

    volatility = STD[idx]

    enter_long  = MA_spread - c_enter_long*volatility
    enter_short = MA_spread + c_enter_short*volatility
    if is_exit_roll:
      exit_long  = MA_spread - c_exit*volatility
      exit_short = MA_spread + c_exit*volatility
    
  # ======================== if not in position and touched MA, try to enter ========================
    if pos == 0 and touched_MA: # and (idx > heat_up):
      if pred_spread < enter_long and spread >= enter_long:
        pos = 1
        exit_long = MA_spread - c_exit*volatility
        stop_long = MA_spread - c_stop*volatility
        stop_long = max(stop_long, spread-stop_cap)
        idx_enter = idx
        enter_spread = spread
      elif pred_spread > enter_short and spread <= enter_short:
        pos = -1
        exit_short = MA_spread + c_exit*volatility
        stop_short = MA_spread + c_stop*volatility
        stop_short = min(stop_short, spread+stop_cap)
        idx_enter  = idx
        enter_spread = spread

  # ======================== check if it is time to close position ========================
    elif pos==1:
      is_stop = spread < stop_long or idx-idx_enter > wait_candles
      if is_stop or spread > exit_long:
        pos = 0
        touched_MA = not is_stop
        pnls[idx] = spread - enter_spread
    elif pos==-1:
      is_stop = spread > stop_short or idx-idx_enter > wait_candles
      if is_stop or spread < exit_short:
        pos = 0
        touched_MA = not is_stop
        pnls[idx] = enter_spread - spread
    
    poses[idx] = pos
    stops[idx] = is_stop

  if pos!=0: pnls[-1] = spread - enter_spread if pos==1 else enter_spread - spread
  return poses, stops, pnls

IndexBacktest.backtest_week = backtest_week
IndexBacktest._backtest = _backtest
IndexBacktest.run_backtest_weeks = run_backtest_weeks