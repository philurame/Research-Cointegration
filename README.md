# cointegration

- usage intro backtest: `framework_backtest.ipynb`
- usage intro production: `framework_production.ipynb`
- must check:  `backtest/IB_init.py` and `backtest/IB_config.py`

$~$
- backtest:
- `IB_init.py`:   all methods with dockstrings
- `IB_config.py`: all parameters
- `IB_aggregate.py`: aggregation of ticker into candles
- `IB_index.py`:     creates index out of lead and lag assets
- `IB_backtest.py`:  runs backtest on index using MA, volatility and other params
- `IB_groups.py`:    runs multiple backtests on random groups
- `IB_plot.py`:      plots results of backtests