from IB_init import IndexBacktest

import polars as pl
from tqdm import tqdm
import os, shutil
from tardis_dev import datasets as tardis_datasets
import datetime
import pandas as pd
import gc
import re
import numpy as np
import sys
import nest_asyncio
nest_asyncio.apply()

# =========================================================================================================================
# Get data
# =========================================================================================================================
def aggregate_data(self):
  exchange = self.params['exchange']
  tardis_api_key = self.params['tardis_api_key']
  date_from = self.params['date_from']
  date_to = self.params['date_to']
  symbols = self.params['symbols_to_aggregate']
  save_to = self.params['save_to']
  save_periods = self.params.get('save_periods', 0)
  temp_dirname = self.params.get('temp_dirname', '_temp')

  date_to_p1 = (
    datetime.datetime.strptime(date_to, '%Y-%m-%d') + 
    datetime.timedelta(days=1)
  ).strftime('%Y-%m-%d')

  self.df = pl.DataFrame({
    'timestamp': pl.datetime_range(datetime.datetime.strptime(date_from, "%Y-%m-%d"), datetime.datetime.strptime(date_to_p1, "%Y-%m-%d"), interval=f'5S', eager=True)
    })
  for n, coin in tqdm(enumerate(symbols), total=len(symbols)):
    try:
      if os.path.exists(temp_dirname): shutil.rmtree(temp_dirname)
      os.mkdir(temp_dirname)
      tardis_datasets.download(
        exchange=exchange,
        data_types=["quotes"],
        from_date=date_from,
        to_date=date_to_p1,
        symbols=[coin],
        api_key=tardis_api_key,
        download_dir=temp_dirname,
        )
      
      df_coin_long = pl.DataFrame()
      for date in pd.date_range(date_from, date_to).strftime("%Y-%m-%d"):
        next_date = (datetime.datetime.strptime(date, '%Y-%m-%d') + datetime.timedelta(days=1)).strftime('%Y-%m-%d')
        path = [os.path.join(temp_dirname, p) for p in os.listdir(temp_dirname) if date in p and coin in p][0]
        df_coin = pl.read_csv(path, 
          columns=['timestamp', 'bid_price', 'ask_price'], 
          dtypes={'timestamp': pl.UInt64, 'bid_price': pl.Float64, 'ask_price': pl.Float64},
          ).with_columns(pl.from_epoch("timestamp", time_unit="us")).sort('timestamp')
        df_coin = df_coin.with_columns(
          ((pl.col('ask_price')+pl.col('bid_price'))/2).alias(f'{coin}_mid')
        ).set_sorted('timestamp').group_by_dynamic(
          index_column='timestamp',
          every='5s',
          period='5s',
          label='right'
        ).agg(
          pl.col(f'{coin}_mid').last(),
          pl.col('bid_price').last().alias(f'{coin}_bid'),
          pl.col('ask_price').last().alias(f'{coin}_ask')
        )

        date = datetime.datetime.strptime(date, '%Y-%m-%d')
        next_date = date + datetime.timedelta(days=1)
        if df_coin[0,'timestamp']<=date or df_coin[-1,'timestamp']>next_date:
          df_coin = df_coin.filter(pl.col('timestamp')>date).filter(pl.col('timestamp')<=next_date)

        # okex-swap perp futures has veird format!
        if exchange == 'okex-swap':
          df_coin = df_coin.rename({i:i.split('-')[0]+'USDT_'+i.split('_')[-1] for i in df_coin.columns if i!='timestamp'})
      
        df_coin_long = pl.concat([df_coin_long, df_coin], how='vertical')

      self.df = self.df.join(df_coin_long, on='timestamp', how='outer_coalesce').sort('timestamp')

      if n and save_periods and (n%save_periods==0) and save_to is not None:
        self.df.write_parquet(save_to)
        gc.collect()

    except Exception as e:
      print(e)
      if str(e) == 'KeyboardInterrupt': raise e
    finally: 
      if os.path.exists(temp_dirname): shutil.rmtree(temp_dirname)
  
  if save_to is not None: 
    self.df.write_parquet(save_to)




# =========================================================================================================================
# Get slippages
# =========================================================================================================================
def get_slippages(self):
  fast = self.params.get('fast', 1)
  exchange = self.params['exchange']
  tardis_api_key = self.params['tardis_api_key']
  date_from = self.params['date_from']
  date_to = self.params['date_to']
  symbols = self.params['symbols_to_aggregate']
  save_to = self.params['save_to']
  temp_dirname = self.params.get('temp_dirname', '_temp')
  infer_schema_length = self.params.get('infer_schema_length', 10_000)

  df_slippages = pd.DataFrame()
  for n, coin in tqdm(enumerate(symbols), total=len(symbols)):
    try:
      if os.path.exists(temp_dirname): shutil.rmtree(temp_dirname)
      os.mkdir(temp_dirname)
      df_slippage_coin = []
      for d, date in enumerate(pd.date_range(date_from, date_to).strftime("%Y-%m-%d")):
        if fast is not None and d%fast!=0: continue
        next_date = (datetime.datetime.strptime(date, '%Y-%m-%d') + datetime.timedelta(days=1)).strftime('%Y-%m-%d')
        tardis_datasets.download(
          exchange=exchange,
          data_types=["book_snapshot_25"],
          from_date=date,
          to_date=next_date,
          symbols=[coin],
          api_key=tardis_api_key,
          download_dir=temp_dirname,
          format="csv",
          )
        path = [os.path.join(temp_dirname, p) for p in os.listdir(temp_dirname) if date in p and coin in p][0]
        columns = [f'{side}[{i}].{what}' for side in ['bids', 'asks'] for i in range(25) for what in ['price', 'amount']]
        df = _pipe_vols_quantiles(pl.read_csv(path, columns=columns, infer_schema_length=infer_schema_length))
        df = df.rename(columns={col: coin+'_'+col for col in df.columns})
        df_slippage_coin.append(df)

      df_slippages = pd.concat([df_slippages, sum(df_slippage_coin)/len(df_slippage_coin)], axis=1)
      gc.collect()
      if save_to is not None: df_slippages.to_csv(save_to)

    except Exception as e:
      print(e)
      sys.stdout.flush()
      if str(e) == 'KeyboardInterrupt': raise e
    finally: 
      if os.path.exists(temp_dirname): shutil.rmtree(temp_dirname)

  df_slippages = _to_normal_format(df_slippages)
  if save_to is not None: df_slippages.to_csv(save_to)
  return df_slippages

def _generate_vwap_expr():
  res = []
  for side in ['bids', 'asks']:
    cum_vols = [pl.col(f'{side}[0].amount')]
    for i in range(25):
      cum_vols.append(cum_vols[-1]+pl.col(f'{side}[{i+1}].amount'))
    res += [(pl.col(f'{side}[{i}].usd_cum_vol')/cum_vols[i]).alias(f'{side}[{i}].vwap') for i in range(25)]
  return res
def _generate_usd_cum_vol_expr():
  res = []
  for side in ['bids', 'asks']:
    cum_prods = [pl.col(f'{side}[0].price')*pl.col(f'{side}[0].amount')]
    for i in range(25):
      cum_prods.append(cum_prods[-1]+pl.col(f'{side}[{i+1}].price')*pl.col(f'{side}[{i+1}].amount'))
    res += [cum_prods[i].alias(f'{side}[{i}].usd_cum_vol') for i in range(25)]
  return res
def _generate_slipp_price_expr(i, target_vol=10000, side='bids'):
  if i == 24:
    return pl.when(pl.col(f'{side}[{i}].usd_cum_vol')>target_vol).then(pl.col(f'{side}[{i}].vwap')).otherwise(pl.col(f'{side}[24].price'))
  else:
    return pl.when(pl.col(f'{side}[{i}].usd_cum_vol')>target_vol).then(pl.col(f'{side}[{i}].vwap')).otherwise(_generate_slipp_price_expr(i+1, target_vol=target_vol, side=side)).alias(f'{side}[{target_vol}].slipp_price')
def _generate_slipp_expr(target_volumes):
  res = []
  mid = ( pl.col('asks[0].price')+pl.col('bids[0].price') )/2
  for target_vol in target_volumes:
    res += [(
      ( mid-pl.col(f'bids[{target_vol}].slipp_price') )/mid/2 + ( pl.col(f'asks[{target_vol}].slipp_price')-mid )/mid/2
    ).alias(f'slipp_{target_vol}') ]
  return res

def _to_normal_format(df):
  df = df.reset_index()
  df = df.melt(id_vars=["quantile"], var_name="type", value_name="value")
  df["quantile"] = (df["quantile"] * 100).astype(int).astype(str) + "_quantile_" + df["type"].str.extract(r"(\d+)$")[0]
  # match all digits and letters:
  df["type"] = df["type"].apply(lambda x: re.findall(r"([A-Z0-9]+).*USDT", x.upper())[0] + 'USDT')
  df = df.pivot(index="quantile", columns="type", values="value")
  return df
def _pipe_vols_quantiles(df, target_volumes=[10000, 100000, 500000], quantiles=list(np.arange(0.5, 1, 0.05))+[0.99]):
  vwaps_expr = _generate_vwap_expr()
  usd_cum_vol_expr = _generate_usd_cum_vol_expr()
  slipp_side_exprs = [_generate_slipp_price_expr(0, target_vol=i, side=j) for i in target_volumes for j in ['bids', 'asks']]
  slipp_exprs = _generate_slipp_expr(target_volumes)
  q_df = df.with_columns(usd_cum_vol_expr).with_columns(vwaps_expr).with_columns(slipp_side_exprs).select(slipp_exprs).to_pandas().quantile(quantiles)
  q_df.index.name = 'quantile'
  return q_df


IndexBacktest.aggregate_data = aggregate_data
IndexBacktest.get_slippages = get_slippages