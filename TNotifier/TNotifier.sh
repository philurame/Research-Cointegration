_python=/home/philurame/ph_notebookenv/bin/python
_runfile=TNotifier.py
_workdir=/home/philurame/TNotifier
_nohupsdir=nohups

write_to=${_workdir}/TNotifier.out
cd ${_workdir}
nohup $_python $_runfile >> $write_to 2>&1 & echo $! | tee $write_to