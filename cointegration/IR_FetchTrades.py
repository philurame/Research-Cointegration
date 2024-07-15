import pandas as pd
from tqdm import tqdm
import numpy as np
import json
from clickhouse_driver import Client
import plotly.graph_objects as go
import plotly.offline as pyo
pyo.init_notebook_mode()
from plotly_resampler import FigureResampler
Colors = dict(
  index='#0e1111',
  ma='#428bca',
  std='#743a43',
  pnl='#fdb108',
  enter='#5cb85c',
  close='#d9534f',
  week_sep='#1E4276',
  enter_lines='#00755e',
  stop_lines='#922B21',
)

class RealIndex:
  def __init__(self):
    self.client = Client(host='10.0.0.23', database='algo', compression=True)
    self.all_strategies = None
    self.params_strategy_id = None
    self.df = None


  def get_spreads(self, strategy_ids=[]):
    if not strategy_ids:
      if self.all_strategies is None: self._load_strategy_ids()
      strategy_ids = self.all_strategies
    df_res = pd.DataFrame()
    for strategy_id in tqdm(strategy_ids):
      df = self.client.query_dataframe(f"SELECT timestamp, esrName, message, visitParamExtractString(message, 't') as message_type, visitParamExtractInt(message, 'id') as message_id FROM statistic WHERE esrName LIKE 'esrf-{strategy_id}%' AND message_type IN ['S', 'I'] ORDER BY timestamp")
      params = self._norm_parameters(df.query('message_type == "I"')['message'].map(lambda x:json.loads(x)).to_list())
      df = df.query('message_type == "S"')
      df1 = pd.DataFrame.from_records(df['message'].map(lambda x:json.loads(x)).to_list())
      if df1.empty: continue
      df1['timestamp'] = pd.to_datetime(df['timestamp'], utc=True)
      df1 = df1.resample('5S', on='timestamp', label='right').last()
      df1.index = df1.index.strftime('%Y-%m-%d %H:%M:%S')
      df1['strategy_id'] = strategy_id
      for _id in df1['id'].unique():
        if _id not in params: continue
        mask = df1['id'] == _id
        for key in params[_id]:
          df1.loc[mask, key] = params[_id][key]
      df1.rename({'V':'index_spread', 
                  'E':'ma',
                  'S':'std',
                  'T':'targetPosition',
                  'P':'position'}, axis=1, inplace=True)
      df1 = df1[['strategy_id', 'id', 'index_spread', 'ma', 'std', 'targetPosition', 'position'] + list(params[_id].keys())]
      df_res = pd.concat([df_res, df1], axis=0)
    self.df = df_res.dropna(subset=['id'])
    self._df_dtypes()


  def get_df(self, strategy_id, indexName=None, only_with_pos=False):
    q = ''
    if indexName is not None:
      id_index = [i['id'] for i in self.params_strategy_id[strategy_id] if i['indexName'] == indexName][0]
      q = f' and id == {id_index}'
    df = self.df.query(f'strategy_id == {strategy_id}{q}')
    if indexName is None and only_with_pos:
      ids = df.query('targetPosition!=0 or position!=0')['id'].unique()
      df = df.query(f'id in @ids')
    return df
  

  def get_strategy_ids_with_pos(self, pos_col = 'both'):
    q = 'targetPosition!=0 or position!=0' if pos_col == 'both' else f'{pos_col}!=0'
    return self.df.query(q)['strategy_id'].unique()


  def plot_df(self, df, poses_col = 'position'):
    assert all(df.indexName==df.indexName.iloc[0])
    fig = FigureResampler(go.Figure()) 

    fig.add_trace(go.Scattergl(name='index_spread', mode="lines", line=dict(color=Colors['index'])), hf_x=df.index, hf_y=df['index_spread'])
    fig.add_trace(go.Scattergl(name='ma', mode="lines", line=dict(color=Colors['ma'])), hf_x=df.index, hf_y=df['ma'])
    fig.add_trace(go.Scattergl(name='std', mode="lines", line=dict(color=Colors['std']), visible='legendonly'), hf_x=df.index, hf_y=df['std'])

    c_enter = df.c_enter.iloc[0]
    fig.add_trace(go.Scattergl(name='enter1', mode="lines", line=dict(color=Colors['enter_lines']), visible='legendonly', legendgroup='enter'),
                   hf_x=df.index, hf_y=df['ma']-c_enter*df['std'])
    fig.add_trace(go.Scattergl(name='enter2', mode="lines", line=dict(color=Colors['enter_lines']), visible='legendonly', legendgroup='enter'),
                   hf_x=df.index, hf_y=df['ma']+c_enter*df['std'])
    poses = df[poses_col]
    is_long, is_short = poses > 0, poses < 0
    enter_long_mask  = is_long & (~is_long).shift()
    enter_short_mask = is_short & (~is_short).shift()
    close_short_mask = (~is_short) & (is_short).shift()
    close_long_mask  = (~is_long)  & (is_long).shift()
    enter_mask, close_mask = enter_long_mask | enter_short_mask, close_short_mask | close_long_mask
    delta = (max(df.index_spread) - min(df.index_spread))*0.03
    y = df.index_spread-delta
    y[close_long_mask|enter_short_mask] += 2*delta
    angles = np.zeros(len(df.index))
    angles[close_long_mask|enter_short_mask] = 180
    colors = np.array([Colors['enter']]*len(df.index))
    colors[close_long_mask|close_short_mask] = Colors['close']
    enter_marker = dict(size=12,symbol='arrow',angle=angles[enter_mask],color=colors[enter_mask],)
    close_marker = dict(size=12,symbol='arrow',angle=angles[close_mask],color=colors[close_mask],)
    fig.add_trace(go.Scatter(x=df.index[close_mask], y=y[close_mask], mode='markers', name='closes', marker=close_marker))
    fig.add_trace(go.Scatter(x=df.index[enter_mask], y=y[enter_mask], mode='markers', name='enters', marker=enter_marker))
    fig.show()


  def _load_strategy_ids(self):
    df = self.client.query_dataframe("SELECT esrName FROM statistic WHERE esrName LIKE 'esrf-%-uniquetechnique-zhukov-index-%' AND visitParamExtractString(message, 't') ='I'")
    self.all_strategies = sorted(np.unique(df['esrName'].map(lambda x:int(x.split('-')[1]))))
  def _norm_parameters(self, params):
    new_params = {}
    for i in params:
      new_params[i['id']] = dict(
        c_enter = i['cEnter'],
        c_exit = i['cExit'],
        c_stop = i['cStop'],
        window_MA = i['windowMA'],
        window_V = i['windowMA'],
        leads = i['leads'],
        lags = i['lags'],
        indexName = i['indexName'],
        normPrices = i['normPrices'],
        weight = i['weight'],
        quoteSize = i['quoteSize']
        )
    return new_params
  def _df_dtypes(self):
    self.df = self.df.astype({
      'strategy_id':int,
      'id':float,
      'index_spread':float,
      'ma':float,
      'std':float,
      'targetPosition':float,
      'position':float,
      'c_enter':float,
      'c_exit':float,
      'c_stop':float,
      'window_MA':float,
      'window_V':float,
      'leads':str,
      'lags':str,
      'indexName':str,
      'quoteSize':float
    })