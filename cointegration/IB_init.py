import numpy as np
import polars as pl
from IB_config import PARAMS

class IndexBacktest:
  def __init__(self, path=None, df=None, **kwargs):
    self.params = PARAMS()
    self.df = None 
    self.df_weeks = None
    if df is not None or path is not None:
      self._load_df(df, path, **kwargs)
      self.params.symbols = [sym.split('_')[0] for sym in self.df.columns if sym.endswith('_mid')]
  
  def _load_df(self, df, path, **kwargs):
    '''
    loads parquet file into self.df and cuts it to first monday-extra_days and last sunday
    '''
    if df is not None:
      self.df = df
      return
    extra_days = self.params['extra_days']
    ts = pl.read_parquet(path, columns=['timestamp'], low_memory=True)['timestamp']
    first_weekday = np.where(ts.dt.weekday()==1+(-extra_days)%7)[0][0]
    last_weekday  = np.where(ts.dt.weekday()==7)[0][-1]
    
    self.df = (pl.read_parquet(path, low_memory=True, **kwargs)
                 .slice(first_weekday, last_weekday+1)
                 .with_columns(pl.exclude('timestamp').cast(pl.Float32))
                 .set_sorted('timestamp')
                 .select(pl.all().forward_fill().backward_fill())
                )
  
  def aggregate_data(self)->None:
    '''
    for each coin downloads data from tardis, resamples by 5S and writes to self.df
    '''
    pass
  def normalize_symbols(self, aggregated:bool)->None:
    '''
    0) if aggregated=True, applies same preprocess as in self._load_df(...)
    1) divide df into weeks adding extra params_index['extra_days'] days before monday
    2) adds slippage into bid and ask prices
    3) creates sym_norm columns with normalized prices: column_norm = column/column[monday_start]
    4) writes into self.df_weeks
    '''
    pass
  def make_index(self, weights=None)->None:
    '''
    0) weights must be a np.array of shape (n_weeks, n_symbols) with positive values for leads and negative for lags
      weights of each sign should modulo sum up to 1
    1) calculates index_spread, index_buy, index_sell based on weights
      or leads and lags if weights is None
    2) writes index_spread, index_buy, index_sell into self.df_weeks
    '''
    pass
  def backtest_week(self, i_week:int)->pl.DataFrame:
    '''
    1) takes first max(extra_days, window_MA, window_V) as a warm up period to calculate MA and STD
    2) runs _backtest for corresponding i_week
    3) calculates pnls (including non-realized one)
    4) returns pl.DataFrame with columns ['index_spread', 'ma', 'std', 'poses', 'stops', 'raw_pnls', 'pnls']
    '''
    pass
  def run_backtest_weeks(self)->pl.DataFrame: 
    '''
    1) runs backtest_week for each i_week in params_backtest['weeks_to_backtest']
    2) returns concatenated pl.DataFrame for each i_week
    '''
    pass
  def run_mc(self, identifier='test'):
    '''
    1) sampling random leads and lags from params.symbols
    2) runs run_backtest_weeks on this group using params from params.mc_Ma_V_Enter
    3) each params.save_iter saves to params.mc_save_to using identifier
    '''
    pass
  def plot_backtest_weeks(self, df_backtest_weeks:pl.DataFrame)->None:
    '''
    plots everything from df_backtest_weeks using params_plot
    '''
    pass
  def _backtest(Spread:np.array, MA:np.array, STD:np.array, params:dict)->tuple[np.array, np.array, np.array]:
    '''
    actual backtest
    '''
    pass

import IB_aggregate, IB_index, IB_backtest, IB_groups, IB_plot, IB_config
