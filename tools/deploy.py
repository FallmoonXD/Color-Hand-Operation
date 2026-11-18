"""Install a CHO development build and provision its device-test permissions."""
from pathlib import Path
import argparse,subprocess

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--migrate-visualtest',action='store_true',help='Remove the retired visual-test service from the enabled list during migration.')
 args=parser.parse_args()
 import device as d
 apk=Path(__file__).resolve().parents[1]/'app/build/outputs/apk/debug/app-debug.apk'
 subprocess.run(d.A+['install','--no-incremental','-r',str(apk)],check=True)
 for permission in ['CAMERA','BLUETOOTH_CONNECT']:
  d.sh('pm','grant',d.P,'android.permission.'+permission)
 d.sh('appwidget','grantbind','--package',d.P,'--user','0')
 d.sh('cmd','appops','set',d.P,'WRITE_SETTINGS','allow')
 d.sh('cmd','appops','set',d.P,'SYSTEM_ALERT_WINDOW','allow')
 d.sh('dumpsys','deviceidle','whitelist','+'+d.P)
 if args.migrate_visualtest:
  services=d.sh('settings','get','secure','enabled_accessibility_services').strip().split(':')
  remaining=[item for item in services if not item.startswith('gulu.gulugulu.visualtest/')]
  if remaining!=services:
   d.sh('settings','put','secure','enabled_accessibility_services',':'.join(remaining))
 d.sh('am','start','-n',d.P+'/.MainActivity')
 print('Installed Color Hand Operation; application data preserved')

if __name__=='__main__':main()
