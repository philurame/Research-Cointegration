from IB_init import IndexBacktest
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import datetime

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

def plot_backtest_weeks(self, df_backtest_weeks):
  if self.params.get('backend', 'matplotlib') == 'matplotlib': 
    plot_backtest_weeks_matplotlib(self, df_backtest_weeks)
  elif self.params['backend'] == 'plotly':
    plot_backtest_weeks_plotly(self, df_backtest_weeks)
  else:
    raise NotImplementedError

# =======================================================================
# plotly
# =======================================================================
def plot_backtest_weeks_plotly(self, df_backtest_weeks):
  import plotly.graph_objects as go
  import plotly.offline as pyo
  pyo.init_notebook_mode()
  from plotly_resampler import FigureResampler
  # all data
  weeks_to_plot = self.params.get('weeks_to_backtest',np.arange(len(self.df_weeks)))
  dates_start = self.df_weeks['_lower_boundary'] + datetime.timedelta(days=self.params['extra_days'])
  dates_start = dates_start[weeks_to_plot]
  index_spread = np.concatenate(df_backtest_weeks['index_spread'])
  ma = np.concatenate(df_backtest_weeks['ma'])
  std = np.concatenate(df_backtest_weeks['std'])
  poses = pd.Series(np.concatenate(df_backtest_weeks['poses']))
  is_long, is_short = poses == 1, poses == -1
  enter_long_mask  = is_long & (~is_long).shift()
  enter_short_mask = is_short & (~is_short).shift()
  close_short_mask = (~is_short) & (is_short).shift()
  close_long_mask  = (~is_long)  & (is_long).shift()
  enter_mask, close_mask = enter_long_mask | enter_short_mask, close_short_mask | close_long_mask
  x = np.arange(len(ma))
  delta = (max(index_spread) - min(index_spread))*0.03
  y = index_spread-delta
  y[close_long_mask|enter_short_mask] += 2*delta

  fig = FigureResampler(go.Figure()) 

  # index, ma, std, pnl
  fig.add_trace(go.Scattergl(mode='lines', name='index_spread', line=dict(color=Colors['index'])), hf_x=x, hf_y=index_spread)
  fig.add_trace(go.Scattergl(name='ma', mode="lines", line=dict(color=Colors['ma'])), hf_x=x, hf_y=ma)
  fig.add_trace(go.Scatter(name='std', mode="lines", line=dict(color=Colors['std']), visible='legendonly'), hf_x=x, hf_y=std)
  fig.add_trace(go.Scattergl(name='pnls_week', line=dict(color=Colors['pnl']), mode="lines", visible='legendonly'), hf_x=x, hf_y=np.concatenate([np.cumsum(i) for i in df_backtest_weeks['pnls']]))
  fig.add_trace(go.Scattergl(name='pnls_cumsum', line=dict(color=Colors['pnl']), mode="lines"), hf_x=x, hf_y=np.cumsum(np.concatenate(df_backtest_weeks['pnls'])))

  # enter_lines
  c_enter  = self.params['c_enter']
  fig.add_trace(go.Scattergl(name='enter_line_up', mode="lines", line=dict(color=Colors['enter_lines']), legendgroup='enter_lines',  visible='legendonly'), hf_x=x, hf_y=ma+c_enter*std,)
  fig.add_trace(go.Scattergl(name='enter_line_down', mode="lines", line=dict(color=Colors['enter_lines']), legendgroup='enter_lines',  visible='legendonly'), hf_x=x, hf_y=ma-c_enter*std)

  # trades
  angles = np.zeros(len(x))
  angles[close_long_mask|enter_short_mask] = 180
  colors = np.array([Colors['enter']]*len(x))
  colors[close_long_mask|close_short_mask] = Colors['close']
  enter_marker = dict(size=12,symbol='arrow',angle=angles[enter_mask],color=colors[enter_mask],)
  close_marker = dict(size=12,symbol='arrow',angle=angles[close_mask],color=colors[close_mask],)
  fig.add_trace(go.Scatter(x=x[close_mask], y=y[close_mask], mode='markers', name='closes', marker=close_marker))
  fig.add_trace(go.Scatter(x=x[enter_mask], y=y[enter_mask], mode='markers', name='enters', marker=enter_marker))

  # week separation vlines
  x_weeks = x[::len(df_backtest_weeks['ma'][0])]
  for i in range(len(x_weeks)):
    fig.add_vline(x_weeks[i], line_width=3, line_dash="dash", line_color=Colors['week_sep'])

  # button for change ticks (weeks <-> trades)
  is_trade = enter_mask | close_mask
  dates = pd.Series(np.concatenate([pd.date_range(i, i+datetime.timedelta(days=7), freq='5S')[:-1] for i in dates_start]))
  button_week = dict(
    label='ticks-weeks',
    method='relayout',
    args=[{'xaxis.tickvals': x_weeks.tolist(), 'xaxis.ticktext': dates_start.dt.strftime('%Y-%m-%d').to_list()}]
    )
  button_trades = dict(
    label='ticks-trades',
    method='relayout',
    args=[{'xaxis.tickvals': np.arange(len(index_spread))[is_trade].tolist(), 'xaxis.ticktext': dates[is_trade].dt.strftime('%d|%H:%M').to_list()}]
  )
  fig.update_layout(
    updatemenus=[dict(
      buttons=[button_week, button_trades],
      direction='down',
      pad={'r': 10, 't': 10},
      showactive=True,
      x=0.0,
      xanchor='left',
      y=1.15,
      yanchor='top'
    )]
  )
  fig.update_layout(hovermode="x")
  fig.show("notebook")



# ===============================================================================
# matplotlib
# ===============================================================================
def plot_backtest_weeks_matplotlib(self, df_backtest_weeks):
  colors = plt.cm.tab10(range(10))
  ax = self.params.get('ax', plt.subplots(figsize=(20, 8))[1])
  weeks_to_plot = self.params.get('weeks_to_backtest',np.arange(len(self.df_weeks)))
  dates_start = self.df_weeks['_lower_boundary'] + datetime.timedelta(days=self.params['extra_days'])
  print(weeks_to_plot)
  dates_start = dates_start[weeks_to_plot]

  ma = np.concatenate(df_backtest_weeks['ma'])
  std = np.concatenate(df_backtest_weeks['std'])
  index_spread = np.concatenate(df_backtest_weeks['index_spread'])
  poses = pd.Series(np.concatenate(df_backtest_weeks['poses']))
  is_long, is_short = poses == 1, poses == -1
  enter_long_mask  = is_long & (~is_long).shift()
  enter_short_mask = is_short & (~is_short).shift()
  close_short_mask = (~is_short) & (is_short).shift()
  close_long_mask  = (~is_long)  & (is_long).shift()

  ax.plot(np.concatenate(df_backtest_weeks['index_spread']), label='index_spread', color='black')

  def plot_weekly(name, color, cumsum=False):
    for i in range(len(df_backtest_weeks)):
      i_from, i_to = i*len(df_backtest_weeks[i,name]), (i+1)*len(df_backtest_weeks[i,name])
      ax.plot(np.arange(i_from, i_to), df_backtest_weeks[i,name] if not cumsum else np.cumsum(df_backtest_weeks[i,name]), label=name if i == 0 else '', color=color)
      
  if self.params.get('ma', False): plot_weekly('ma', colors[0])

  if self.params.get('std', False): plot_weekly('std', colors[-1])

  if self.params.get('pnls', 'week') == 'week': plot_weekly('pnls', colors[1], cumsum=True)
  elif self.params['pnls'] == 'all':
    ax.plot(np.cumsum(np.concatenate(df_backtest_weeks['pnls'])), label='pnls', color=colors[1])

  if self.params.get('stops', False):
    # ma = np.concatenate(dfot['ma'])
    # std = np.concatenate(dfot['std'])
    # c_stop = params['c_stop']
    # ax.plot(ma-std*c_stop, label='stop', color='r')
    # ax.plot(ma+std*c_stop, color='r')
    pass
  if self.params.get('enters', False):
    c_enter_long  = self.params.get('c_enter_long',  self.params['c_enter'])
    c_enter_short = self.params.get('c_enter_short', self.params['c_enter'])
    ax.plot(ma-std*c_enter_long, label='enter', color=colors[4])
    ax.plot(ma+std*c_enter_short, color=colors[4])
  if self.params.get('trades_vlines', True):
    ax.vlines(np.arange(len(index_spread))[enter_long_mask], ax.get_ylim()[0], index_spread[enter_long_mask], color=colors[2], linestyles='dashed', linewidth=1.5)
    ax.vlines(np.arange(len(index_spread))[close_long_mask], index_spread[close_long_mask], ax.get_ylim()[1], color=colors[3], linestyles='dashed', linewidth=1.5)
    ax.vlines(np.arange(len(index_spread))[enter_short_mask], index_spread[enter_short_mask], ax.get_ylim()[1], color=colors[2], linestyles='dashed', linewidth=1.5)
    ax.vlines(np.arange(len(index_spread))[close_short_mask], ax.get_ylim()[0], index_spread[close_short_mask], color=colors[3], linestyles='dashed', linewidth=1.5)
    
    if self.params.get('trades_time', False):
      is_trade = enter_long_mask | close_long_mask | enter_short_mask | close_short_mask
      dates = pd.Series(np.concatenate([pd.date_range(i, i+datetime.timedelta(days=7), freq='5S')[:-1] for i in dates_start]))
      ax.set_xticks(np.arange(len(index_spread))[is_trade], labels=dates[is_trade].dt.strftime('%d|%H:%M'), rotation=-45)
    else:
      ax.set_xticks(np.arange(0,len(index_spread), len(df_backtest_weeks['index_spread'][0])), labels=dates_start.dt.strftime('%Y-%m-%d'))

  xs = np.arange(0,len(index_spread), len(df_backtest_weeks['index_spread'][0]))
  if self.params.get('vlines_weeks', False): ax.vlines(xs, *ax.get_ylim(), color=colors[6], linewidth=2)
  else: ax.scatter(xs, np.ones_like(xs)*ax.get_ylim()[0], marker='v', color=colors[6], s=70, linewidths=2) 
    
  ax.legend()


IndexBacktest.plot_backtest_weeks = plot_backtest_weeks
IndexBacktest.plot_backtest_weeks_plotly = plot_backtest_weeks_plotly
IndexBacktest.plot_backtest_weeks_matplotlib = plot_backtest_weeks_matplotlib