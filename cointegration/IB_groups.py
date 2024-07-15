from IB_init import IndexBacktest
import numpy as np
import gc
from tqdm import tqdm
import polars as pl

def run_mc(self, identifier='test'): # len(params.symbols) must be > 3
  n_MC = self.params['mc_n_MC']
  seed = self.params['mc_seed']
  save_to = self.params['mc_save_to'] # must be a folder
  save_iter = self.params['mc_save_iter']
  np.random.seed(seed)

  # params to use in mc-search
  Ma_V_Enter = self.params.get('mc_Ma_V_Enter', np.array([[28000, 28000, 4.2],[28000, 28000, 3.5],[16000, 25000, 3.5],[ 9000, 17000, 3.7]]))

  df_stats  = pl.DataFrame(schema=_get_stats_schema())

  for n in tqdm(range(n_MC)):

    n_leads = np.random.choice([1,2,3])
    max_lags = min(4, len(self.params.symbols)-n_leads)
    n_lags  = np.random.choice(np.arange(1, max_lags+1))

    leads = np.random.choice(self.params.symbols, n_leads, replace=False)
    lags  = np.random.choice(list(set(self.params.symbols)-set(leads)), n_lags, replace=False)

    self.params.leads = leads
    self.params.lags  = lags
    self.make_index()

    for window_MA, window_V, c_enter in Ma_V_Enter:

      window_MA, window_V, c_enter = np.uint16(window_MA), np.uint16(window_V), c_enter.astype(np.float32)
      self.params.window_MA = window_MA
      self.params.window_V  = window_V
      self.params.c_enter   = c_enter
      df_backtest_weeks = self.run_backtest_weeks()

      df_stats = df_stats.vstack(_calc_stats(df_backtest_weeks, self.params))

    if n and n%save_iter==0:
      df_stats.write_parquet(f'{save_to}/mc_stats_{n}_{identifier}.parquet')
      df_stats  = pl.DataFrame(schema=_get_stats_schema())
      gc.collect()

  gc.collect()
  df_stats.write_parquet(f'{save_to}/mc_stats_{n_MC}_{identifier}.parquet')


def _get_stats_schema():
  return dict(
    index = pl.UInt32,
    total_pnl = pl.Float32,
    mean_pnl = pl.Float32,
    std_pnl = pl.Float32,
    max_pnl = pl.Float32,
    min_pnl = pl.Float32,
    win_rate = pl.Float32,
    pnl_skew = pl.Float32,
    pnl_kurt = pl.Float32,
    mean_pnl_raw = pl.Float32,
    std_pnl_raw = pl.Float32,
    max_pnl_raw = pl.Float32,
    min_pnl_raw = pl.Float32,
    win_rate_raw = pl.Float32,
    num_trades = pl.UInt32,
    time_in_position = pl.Float32,
    total_stops = pl.UInt32,
    avg_ma_distance = pl.Float32,
    std_range = pl.Float32,
    max_drawdown = pl.Float32,
    drawdown_duration = pl.Float32,
    buy_duration = pl.Float32,
    sell_duration = pl.Float32,
    downside_deviation = pl.Float32,
    sharpe_ratio_1p = pl.Float32,
    sortino_ratio_1p = pl.Float32,
    symbols_params = pl.Utf8,
    )

def _calc_stats(df_backtest_weeks, params):
  symbols_params = ','.join(params['leads'])+' '+','.join(params['lags'])+' '+str(params['window_MA'])+','+str(params['window_V'])+','+str(params['c_enter'])
  return (
    df_backtest_weeks.with_row_index().explode(pl.exclude('index'))
    .with_columns(
      drawdowns = (pl.col('pnls').cum_sum() - pl.col('pnls').cum_sum().cum_max()).over('index'),
      )
    .group_by('index').agg(
      total_pnl = pl.col('pnls').sum(),
      mean_pnl  = pl.col('pnls').mean(),
      std_pnl   = pl.col('pnls').std(),
      max_pnl   = pl.col('pnls').max(),
      min_pnl   = pl.col('pnls').min(),
      win_rate = (pl.col('pnls') > 0).mean(),
      pnl_skew = pl.col('pnls').skew(),
      pnl_kurt = pl.col('pnls').kurtosis(),

      mean_pnl_raw = pl.col('raw_pnls').mean(),
      std_pnl_raw = pl.col('raw_pnls').std(),
      max_pnl_raw = pl.col('raw_pnls').max(),
      min_pnl_raw = pl.col('raw_pnls').min(),
      win_rate_raw = (pl.col('raw_pnls') > 0).mean(),

      num_trades = pl.col('poses').diff().abs().sum(),
      time_in_position = (pl.col('poses') != 0).mean(),
      total_stops = pl.col('stops').sum(),

      avg_ma_distance = (pl.col('index_spread') - pl.col('ma')).abs().mean(),
      std_range = pl.col('std').max() - pl.col('std').min(),

      max_drawdown = pl.col('drawdowns').min(),
      
      drawdown_duration = (pl.col('drawdowns')<0).sum() / ((pl.col('drawdowns')<0).cast(pl.Int32).diff()>0).sum(),
      buy_duration = (pl.col('poses')>0).sum() / ((pl.col('poses')>0).cast(pl.Int32).diff()>0).sum(),
      sell_duration = (pl.col('poses')<0).sum() / ((pl.col('poses')<0).cast(pl.Int32).diff()>0).sum(),

      downside_deviation = (pl.col('pnls').filter(pl.col('pnls') < 0)**2).mean().sqrt().fill_null(0),
    ).with_columns(
      sharpe_ratio_1p = pl.col('mean_pnl') / (1+pl.col('std_pnl')),
      sortino_ratio_1p = pl.col('mean_pnl') / (1+pl.col('downside_deviation')),
    )
    .with_columns(symbols_params = pl.lit(symbols_params))
    .with_columns([pl.col(col).fill_nan(0).fill_null(0).cast(dtype) for col, dtype in _get_stats_schema().items() if dtype != pl.Utf8])
  )


IndexBacktest.run_mc = run_mc