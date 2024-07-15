from IB_init import IndexBacktest
import polars as pl
import numpy as np
import datetime
import gc

def normalize_symbols(self, aggregated=False):
  extra_days = self.params['extra_days']
  if aggregated: 
    self.params['symbols'] = [i.split('_mid')[0] for i in self.df.columns if '_mid' in i]
    first_weekday = np.where(self.df['timestamp'].dt.weekday()==1+(-extra_days)%7)[0][0]
    last_weekday  = np.where(self.df['timestamp'].dt.weekday()==7)[0][-1]
    self.df = (self.df
                 .slice(first_weekday, last_weekday+1)
                 .with_columns(pl.exclude('timestamp').cast(pl.Float32))
                 .set_sorted('timestamp')
                 .select(pl.all().forward_fill().backward_fill())
                )

  symbols    = self.params['symbols']
  slippage   = self.params['slippage'][symbols]
  fee        = self.params['fee']
  
  norm_idx = extra_days * 24 * 3600 // 5
  agg_dict = {}
  for sym in symbols:
    pl_mid = pl.col(f'{sym}_mid')
    pl_bid_corrected = (pl.col(f'{sym}_bid') - pl_mid * slippage[sym]) * (1 - fee)
    pl_ask_corrected = (pl.col(f'{sym}_ask') + pl_mid * slippage[sym]) * (1 + fee)
    agg_dict[f'{sym}_mid_norm'] = pl_mid / pl_mid.shift(-norm_idx).get(0)
    agg_dict[f'{sym}_bid_norm'] = pl_bid_corrected / pl_mid.shift(-norm_idx).get(0)
    agg_dict[f'{sym}_ask_norm'] = pl_ask_corrected / pl_mid.shift(-norm_idx).get(0)
  
  cols = [i+j for i in symbols for j in ['_bid', '_ask', '_mid']]
  self.df_weeks = (self.df[["timestamp"]+cols].group_by_dynamic(
    index_column="timestamp", 
    every=f"7d", 
    closed="left", 
    include_boundaries=True,
    start_by='datapoint',
    period=f'{7+extra_days}d'
    )
    .agg(**agg_dict)
    )

  while self.df_weeks[-1,'_upper_boundary'] > self.df[-1, 'timestamp'] + datetime.timedelta(seconds=5):
    self.df_weeks = self.df_weeks[:-1]

  
def make_index(self, weights=None):
  weights = self._make_default_weights(weights)
  symbols = list(self.params['leads']) + list(self.params['lags'])

  mid_norms = [f'{s}_mid_norm' for s in symbols]
  bid_norms = [f'{s}_bid_norm' for s in symbols]
  ask_norms = [f'{s}_ask_norm' for s in symbols]
  
  # we buy using ask prices and sell using bid prices
  # we buy iff weight > 0, sell iff weight < 0
  buy_cols  = np.where(weights>0, ask_norms, bid_norms)
  sell_cols = np.where(weights>0, bid_norms, ask_norms)

  index_spread = np.vstack((weights*self.df_weeks[mid_norms]).sum(axis=1))
  index_buy, index_sell = [], []
  for i in range(len(self.df_weeks)):
    index_buy.append((weights[i]*self.df_weeks[i, buy_cols[i]]).sum(axis=1)[0])
    index_sell.append((weights[i]*self.df_weeks[i, sell_cols[i]]).sum(axis=1)[0])
  
  self.df_weeks = self.df_weeks.with_columns([
    pl.Series('index_spread', index_spread),
    pl.Series('index_buy', index_buy),
    pl.Series('index_sell', index_sell)
    ] 
  )

def _make_default_weights(self, weights_weeks):
  if weights_weeks: return weights_weeks
  n_weeks = len(self.df_weeks)
  n_leads, n_lags = len(self.params['leads']), len(self.params['lags'])
  weights_weeks = np.array([[1/n_leads]*n_leads + [-1/n_lags]*n_lags]*n_weeks)

  if self.params.weights_type == 'mean':
    return weights_weeks
    
  
  elif self.params.weights_type == 'std':
    leads_mids = [f'{s}_mid_norm' for s in self.params['leads']]
    lags_mids  = [f'{s}_mid_norm' for s in self.params['lags']]
    for i in range(1, n_weeks):
      stds = self.df_weeks[i-1].select(pl.col(c).explode().std() for c in leads_mids+lags_mids)
      # stds = self.df_weeks[i-1].select(pl.col(c).explode().ewm_std(span=len(self.df_weeks[i-1]), ignore_nulls=True).last() for c in leads_mids+lags_mids)
      w_leads, w_lags = 1/stds[leads_mids].to_numpy().reshape(-1), 1/stds[lags_mids].to_numpy().reshape(-1)
      w_leads, w_lags = w_leads/w_leads.sum(), w_lags/w_lags.sum()
      weights_weeks[i] = w_leads.tolist() + (-w_lags).tolist()
  return weights_weeks



IndexBacktest.normalize_symbols = normalize_symbols
IndexBacktest.make_index = make_index
IndexBacktest._make_default_weights = _make_default_weights