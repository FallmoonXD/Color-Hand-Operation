#!/system/bin/sh
# ColorOS 17: additive, package-scoped Athena exemptions. No global policy changes.
# Old/new masks: one-click 1/65536; swipe-away 512/262144.
# Transaction numbers are firmware-specific. Never probe unknown transactions by writing.
MODDIR=${0%/*}
VERIFIED_MODEL='PKR110'
VERIFIED_FINGERPRINT='OnePlus/PKR110/OP60EBL1:17/AP3A.240617.008/V.11afd34_14d00bb_14c7403:user/release-keys'
if [ "$(getprop ro.product.model)" != "$VERIFIED_MODEL" ] || [ "$(getprop ro.build.fingerprint)" != "$VERIFIED_FINGERPRINT" ]; then
  log -t EdgeFlowKeepAlive 'Skipped: Athena transaction is not verified for this model and firmware'
  exit 0
fi
[ "$(id -u)" = "0" ] || exit 1
pm path dev.edgeflow >/dev/null 2>&1 || exit 0
if [ "$1" != "--apply" ]; then
  until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
  sleep 10
fi
[ -e "$MODDIR/disable" ] && exit 0
[ -e "$MODDIR/remove" ] && exit 0
xml='<filter><p k="dev.edgeflow" v="328193"/></filter>'
attempt=0
while [ "$attempt" -lt 6 ]; do
  attempt=$((attempt + 1))
  service call athenaservice 241 s16 sys_custom_athena_appfilter_list s16 "$xml" i32 0 >/dev/null 2>&1
  sleep 1
  if dumpsys athenaservice prockill filter_list sys_bg_clean_strategy | grep -F 'dev.edgeflow - 328193' >/dev/null; then
    log -t EdgeFlowKeepAlive 'Applied package-scoped swipe/one-click exemptions'
    exit 0
  fi
  sleep 5
done
log -t EdgeFlowKeepAlive 'Athena exemption could not be verified'
exit 1
