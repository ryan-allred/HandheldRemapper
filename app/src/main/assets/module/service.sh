#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/rg505_mapperd.log
(
  sleep 5
  exec "$MODDIR/rg505_mapperd" "$MODDIR/config" >> "$LOG" 2>&1
) &
