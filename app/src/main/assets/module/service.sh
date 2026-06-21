#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/handheld_remapperd.log
(
  sleep 5
  exec "$MODDIR/handheld_remapperd" "$MODDIR/config" >> "$LOG" 2>&1
) &
