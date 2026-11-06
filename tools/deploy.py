"""Install this source build, preserve settings and add its accessibility service."""
from pathlib import Path
import argparse,subprocess

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--migrate-visualtest',action='store_true',help='Explicitly disable the old gulu.gulugulu.visualtest accessibility service when adding CHO; no other service is removed.')
 args=parser.parse_args()
 import device as d
 apk=Path(__file__).resolve().parents[1]/'app/build/outputs/apk/debug/app-debug.apk'
 subprocess.run(d.A+['install','--no-incremental','-r',str(apk)],check=True)
 for permission in ['CAMERA','POST_NOTIFICATIONS','BLUETOOTH_CONNECT']:
  d.sh('pm','grant',d.P,'android.permission.'+permission)
 d.sh('appwidget','grantbind','--package',d.P,'--user','0')
 d.sh('cmd','appops','set',d.P,'WRITE_SETTINGS','allow')
 d.sh('cmd','appops','set',d.P,'SYSTEM_ALERT_WINDOW','allow')
 d.sh('dumpsys','deviceidle','whitelist','+'+d.P)
 d.activate(migrate_visualtest=args.migrate_visualtest)
 d.sh('am','start','-n',d.P+'/.MainActivity')
 print('Installed Color Hand Operation; application data preserved')

if __name__=='__main__':main()
