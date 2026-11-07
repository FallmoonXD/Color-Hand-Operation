"""ADB helpers for the independent app's device verification."""
from pathlib import Path
import os,shutil,subprocess,shlex,re,time,json,xml.etree.ElementTree as E
from contextlib import contextmanager
ADB=shutil.which('adb') or 'adb'

def select_device(adb,requested=None):
 requested=(requested or '').strip()
 try:
  output=subprocess.check_output([adb,'devices'],stderr=subprocess.STDOUT).decode('utf-8',errors='replace')
 except FileNotFoundError as error:
  raise RuntimeError('ADB was not found. Install Android platform-tools and add adb to PATH.') from error
 except subprocess.CalledProcessError as error:
  raise RuntimeError('ADB device discovery failed: '+error.output.decode('utf-8',errors='replace').strip()) from error
 devices={};listed=False
 for line in output.splitlines():
  if line.startswith('List of devices attached'):
   listed=True;continue
  if not listed:continue
  parts=line.split()
  if len(parts)>=2 and not line.startswith('*'):
   devices[parts[0]]=parts[1]
 if requested:
  state=devices.get(requested)
  if state!='device':
   raise RuntimeError('EDGEFLOW_SERIAL '+requested+' is '+(state or 'not connected')+'. Check the cable and USB debugging authorization.')
  return requested
 if not devices:
  raise RuntimeError('No ADB device connected. Connect a phone and authorize USB debugging.')
 if len(devices)!=1:
  raise RuntimeError('Multiple ADB devices are connected. Set EDGEFLOW_SERIAL explicitly: '+', '.join(devices))
 serial,state=next(iter(devices.items()))
 if state!='device':
  raise RuntimeError('ADB device '+serial+' is '+state+'. Check the cable and USB debugging authorization.')
 return serial

A=[ADB,'-s',select_device(ADB,os.environ.get('EDGEFLOW_SERIAL'))]
P='dev.edgeflow';B=Path(__file__).resolve().parents[1]/'verification'
def sh(*args):return subprocess.check_output(A+['shell',shlex.join(args)]).decode('utf-8',errors='replace')
_display_size=None
def current_display_size():
 match=re.search(r'\bcur=(\d+)x(\d+)',sh('dumpsys','window','displays'))
 if not match:raise RuntimeError('WindowManager did not report the current display size')
 return tuple(map(int,match.groups()))
def ui():
 global _display_size
 # The OEM occasionally kills the short-lived read-only accessibility probe (137).
 # Retry only this snapshot, never an input or a state-changing command.
 for attempt in range(3):
  try:
   data=sh('sh','-c','CLASSPATH=/data/local/tmp/edgeflow-probe.jar app_process /system/bin probe.OverlayDump')
   tree=E.fromstring(data[data.index('<hierarchy>'):])
   _display_size=current_display_size()
   return tree
  except subprocess.CalledProcessError as failure:
   if failure.returncode!=137 or attempt==2:raise
   time.sleep(.7)
def node(s):return next(n for n in ui().iter('node') if n.get('text')==s or n.get('content-desc')==s)
def tap(n):
 a,b,c,d=map(int,re.findall(r'-?\d+',n.get('bounds')));sh('input','tap',str((a+c)//2),str((b+d)//2))

def bounds(n):return tuple(map(int,re.findall(r'-?\d+',n.get('bounds'))))

def visible(n):
 a,b,c,e=bounds(n);w,h=_display_size or current_display_size()
 return n.get("visible-to-user","true")=="true" and 0<=a<c<=w and 0<=b<e<=h

def wait_node(s,timeout=6):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  n=next((n for n in ui().iter('node') if (n.get('text')==s or n.get('content-desc')==s) and visible(n)),None)
  if n is not None:return n
  time.sleep(.2)
 raise AssertionError('No visible node: '+s)

def widget_page_menu():
 return next((n for n in ui().iter('node') if n.get('package')==P
              and n.get('content-desc','').endswith('页面菜单') and visible(n)),None)

def wait_widget_panel(shown=True,timeout=8):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  menu=widget_page_menu()
  if (menu is not None)==shown:return menu
  time.sleep(.15)
 raise AssertionError('Unexpected widget panel visibility: '+str(shown))

def click(s):tap(wait_node(s));time.sleep(.25)

def preferences(name='edgeflow'):
 """Read settings without changing process state or writing private files."""
 root=E.fromstring(sh('run-as',P,'cat','shared_prefs/'+name+'.xml'));out={}
 for n in root:
  if n.tag=='string':value=n.text or ''
  elif n.tag=='boolean':value=n.get('value')=='true'
  elif n.tag in ('int','long'):value=int(n.get('value'))
  elif n.tag=='float':value=float(n.get('value'))
  elif n.tag=='set':value={item.text or '' for item in n}
  else:raise AssertionError('Unknown preference type: '+n.tag)
  out[n.get('name')]=value
 return out

def _match(n,s,description=False,action_row=False):
 if action_row:return n.get('class')=='android.widget.RadioButton' and n.get('content-desc','').partition('，')[0]==s
 return n.get('content-desc')==s if description else n.get('text')==s or n.get('content-desc')==s

def _scroll_containers(tree):
 containers=[n for n in tree.iter('node') if n.get('class') in ('android.widget.ScrollView','android.widget.ListView','androidx.recyclerview.widget.RecyclerView') and visible(n)]
 # The embedded action sheet shares its Activity window. The probe includes
 # unimportant background views, so document order no longer identifies the modal scroller.
 close=next((n for n in tree.iter('node') if n.get('content-desc')=='关闭选择操作' and visible(n)),None)
 if close is not None:
  below=[n for n in containers if bounds(n)[1]>=bounds(close)[3]]
  if below:containers=below
 return containers

def _scroll_bounds(tree):
 containers=_scroll_containers(tree)
 assert containers,'No visible scroll container'
 return bounds(containers[-1])

def _scroll(tree,forward=True):
 # Modest, slow travel avoids flinging past a target between accessibility snapshots.
 a,b,c,e=_scroll_bounds(tree);x=(a+c)//2;low=b+round((e-b)*.3);high=b+round((e-b)*.7)
 start,end=(high,low) if forward else (low,high)
 sh('input','swipe',str(x),str(start),str(x),str(end),'450');time.sleep(.25)

def find_item(s,description=False,timeout=3,*,action_row=False):
 """Find a visible item in a page or chooser, including restored scroll positions."""
 deadline=time.monotonic()+timeout
 while True:
  tree=ui();found=next((n for n in tree.iter('node') if _match(n,s,description,action_row) and visible(n)),None)
  if found is not None:return found
  if time.monotonic()>=deadline:break
  time.sleep(.15)
 previous=None;forward=True;stalled_ends=0
 for _ in range(44):
  tree=ui();targets=[n for n in tree.iter('node') if _match(n,s,description,action_row)]
  found=next((n for n in targets if visible(n)),None)
  if found is not None:return found
  try:a,b,c,e=_scroll_bounds(tree)
  except AssertionError:break
  # Infer overshoot from this same tree; zero-size offscreen bounds carry no direction.
  positioned=[bounds(n) for n in targets if bounds(n)[2]>bounds(n)[0] and bounds(n)[3]>bounds(n)[1]]
  if positioned:
   target=min(positioned,key=lambda box:abs((box[1]+box[3])-(b+e)))
   if target[3]>e:forward=True
   elif target[1]<b:forward=False
  # Accessibility connection/node IDs and the status-bar clock can change on
  # every snapshot. Compare the scroll content, so a reached end reverses the
  # search instead of repeatedly swiping an already stationary page.
  containers=_scroll_containers(tree)
  scope=containers[-1] if containers else tree
  signature=tuple((n.get('text'),n.get('content-desc'),n.get('class'),n.get('bounds')) for n in scope.iter('node'))
  if signature==previous:
   stalled_ends+=1
   if stalled_ends>=2:break
   forward=not forward
  previous=signature;_scroll(tree,forward)
 raise AssertionError('No menu item: '+s)

def click_item(s,description=False):tap(find_item(s,description));time.sleep(.35)

def main_home():
 """Return through the actual back stack; never select an action or enable a handle."""
 # Do not start a Home animation just to reopen an Activity which is already
 # foreground. A rapid Home/relaunch can be merged by the OEM transition manager.
 if P+'/.MainActivity' not in resumed():
  sh('am','start','-W','-f','0x14000000','-n',P+'/.MainActivity');time.sleep(.6)
 for _ in range(16):
  if P+'/.MainActivity' not in resumed():
   sh('am','start','-W','-f','0x14000000','-n',P+'/.MainActivity');time.sleep(.35)
  tree=ui()
  # Package updates can show this OEM prompt after the Activity has resumed.
  # Keep only this application's already-authorized accessibility service enabled.
  own=any('Color Hand Operation' in n.get('text','') and '无障碍' in n.get('text','') for n in tree.iter('node'))
  keep=next((n for n in tree.iter('node') if n.get('text')=='保持开启' and n.get('package')=='com.oplus.safecenter' and visible(n)),None)
  if own and keep is not None:tap(keep);time.sleep(.5);continue
  # A dialog may leave the underlying home page in the accessibility tree.
  cancel=next((n for n in tree.iter('node') if n.get('text')=='取消' and visible(n)),None)
  if cancel is not None:tap(cancel);time.sleep(.25);continue
  if any(n.get('content-desc')=='更多选项' and visible(n) for n in tree.iter('node')):
   heading=next((n for n in tree.iter('node') if (n.get('text')=='Color Hand Operation' or n.get('content-desc')=='Color Hand Operation') and visible(n)),None)
   if heading is not None:tap(heading);time.sleep(.3) # Harmless outside touch dismisses an old nonfocusable peer panel.
   return
  sh('input','keyevent','4');time.sleep(.25)
 raise AssertionError('Could not return to Color Hand Operation home through its back stack')

HANDLES={'left':'左侧触钮','right':'右侧触钮','extra_left':'左侧触钮 2','extra_right':'右侧触钮 2','extra2_left':'左侧触钮 3','extra2_right':'右侧触钮 3'}
HANDLE_ALIASES={title:key for key,title in HANDLES.items()}
HANDLE_ALIASES.update({'左侧手势':'left','右侧手势':'right','额外左侧手势':'extra_left','额外右侧手势':'extra_right','额外左侧手势 2':'extra2_left','额外右侧手势 2':'extra2_right'})

def pair_count(state=None):
 state=preferences() if state is None else state
 count=max(1,min(3,state.get('handle_pair_count',1)))
 if state.get('extra_left_enabled',False) or state.get('extra_right_enabled',False):count=max(2,count)
 if state.get('extra2_left_enabled',False) or state.get('extra2_right_enabled',False):count=3
 return count

def open_handle(handle):
 assert handle in HANDLES,'Unknown handle: '+handle
 main_home();state=preferences();assert state.get('enabled',True),'Gesture master is disabled; navigation must not enable it'
 assert state.get(handle+'_enabled',not handle.startswith('extra')),'Handle is disabled; explicitly enable it in the test and restore it: '+handle
 assert pair_count(state)>=(3 if handle.startswith('extra2_') else 2 if handle.startswith('extra_') else 1),'Handle pair is absent: '+handle
 click_item(HANDLES[handle],True);wait_node('短距离滑动')

def open_action_chooser(handle='right',direction='straight',longer=False):
 open_handle(handle)
 if longer:assert preferences().get(handle+'_long_enabled',True),'Long swipe is disabled; test must opt in explicitly'
 name={'straight':'直线向右' if handle.endswith('left') else '直线向左','up':'对角线向上','down':'对角线向下'}[direction]
 click_item(('长滑' if longer else '短滑')+name,True);wait_node('选择操作')

def action_settings(title):
 before=preferences();mapping={k:v for k,v in before.items() if k.startswith('action_')}
 handle=next((key for key in ('right','left','extra_right','extra_left','extra2_right','extra2_left') if before.get(key+'_enabled',not key.startswith('extra'))),None)
 assert handle is not None,'No enabled handle; settings navigation will not implicitly enable one'
 open_action_chooser(handle);click_item('设置'+title,True);wait_node(title)
 assert {k:v for k,v in preferences().items() if k.startswith('action_')}==mapping,'Opening an action gear changed gesture mappings'

def panel_settings(title):
 main_home();click_item('面板与工具',True);click_item(title,True);wait_node(title)

def range_settings(handle='left'):
 open_handle(handle);click_item('触钮范围',True);wait_node('大小')

PREVIEWS={'快速工具':'预览快速工具','小组件':'打开小组件面板','快速启动':'编辑快速启动','任务切换器':'预览任务切换器','虚拟触控板':'预览触控板','导航键面板':'右侧边缘','键盘快捷方式':'预览键盘快捷方式'}
SETTINGS_ALIASES={'快速工具设置':'快速工具','小组件设置':'小组件','快速启动设置':'快速启动','任务切换器设置':'任务切换器','触控板设置':'虚拟触控板','导航键设置':'导航键面板','键盘快捷方式设置':'键盘快捷方式'}

def main_link(s):
 """Compatibility routing preserves callers' old settings-versus-preview intent."""
 if s in HANDLE_ALIASES:return open_handle(HANDLE_ALIASES[s])
 if s in SETTINGS_ALIASES:return panel_settings(SETTINGS_ALIASES[s])
 if s in PREVIEWS:panel_settings(s);click_item(PREVIEWS[s],True);return
 main_home()
 if s in ('高级设置','手势设置','面板与工具','触钮范围'):click_item(s,True);return
 if s=='应用例外':click_item('高级设置',True);click_item('应用例外',True);return
 if s in ('关于','关于 Color Hand Operation','恢复默认设置','备份与恢复','权限与后台运行'):
  click('更多选项');click('关于' if s=='关于 Color Hand Operation' else s);return
 if s in ('导出配置','导入配置','小组件迁移结果'):
  click('更多选项');click('备份与恢复');click_item(s,True);return
 raise AssertionError('No OHO navigation route for old main link: '+s)

def action_titles():
 source=(Path(__file__).resolve().parents[1]/'app/src/main/java/dev/edgeflow/ActionExecutor.java').read_text(encoding='utf-8')
 arrays=[json.loads('['+re.search(r'\b'+name+r'\s*=\s*\{([^}]+)\}',source).group(1)+']') for name in ('IDS','TITLES')]
 assert len(arrays[0])==len(arrays[1]);return dict(zip(*arrays))

def action_key(handle='right',direction='straight',longer=False):return 'action_'+handle+'_'+direction+('_long' if longer else '_short')

def choose_action(title,handle='right',direction='straight',longer=False):
 open_action_chooser(handle,direction,longer)
 tap(find_item(title,True,action_row=True))
 expected=next((action for action,label in action_titles().items() if label==title),None)
 if expected is None:return
 deadline=time.monotonic()+4
 key=action_key(handle,direction,longer)
 while preferences().get(key)!=expected:
  if time.monotonic()>=deadline:raise AssertionError('Action selection did not commit: '+key)
  time.sleep(.1)

@contextmanager
def preserved_action(handle='right',direction='straight',longer=False):
 """Only touch a mapping when its prior selection can be restored through public UI."""
 key=action_key(handle,direction,longer);before=preferences();assert key in before,'Missing explicit mapping; initialize the app before this test'
 original=before[key];titles=action_titles();assert original in titles,'Existing app/shortcut mapping needs a different test slot; refusing to overwrite '+key
 try:yield
 finally:
  if preferences().get(key)!=original:choose_action(titles[original],handle,direction,longer)
  assert preferences().get(key)==original,'Failed to restore '+key

def set_handle_enabled(handle,on):
 main_home();state=preferences();assert state.get('enabled',True),'Master disabled; test must explicitly handle master state'
 title='启用'+HANDLES[handle];switch=find_item(title,True);assert switch.get('class')=='android.widget.Switch','Expected handle switch'
 if (switch.get('checked')=='true')!=on:tap(switch);time.sleep(.35)
 assert preferences().get(handle+'_enabled',not handle.startswith('extra'))==on

def set_toggle(title,on):
 switch=find_item(title,True);assert switch.get('class')=='android.widget.Switch','Expected settings switch: '+title
 if (switch.get('checked')=='true')!=on:tap(switch);time.sleep(.35)
 assert find_item(title,True).get('checked')==str(on).lower(),'Switch did not change: '+title

def set_pair_count(count):
 assert 1<=count<=3
 for _ in range(3):
  main_home();current=pair_count()
  if current==count:return
  if current<count:click_item('添加触钮',True)
  else:click_item('移除触钮',True);click('移除')
 assert pair_count()==count,'Failed to restore handle pair count'
def shot(name):
 from PIL import Image
 p=B/(name+'.png');p.write_bytes(subprocess.check_output(A+['exec-out','screencap','-p']));im=Image.open(p);im.thumbnail((455,1000));im.save(B/(name+'-small.png'))
def resumed():return '\n'.join(s for s in sh('dumpsys','activity','activities').splitlines() if 'ResumedActivity:' in s)
def vol():return int(re.search(r'volume is (\d+)',sh('cmd','media_session','volume','--stream','3','--get'))[1])
def activate(migrate_visualtest=False):
 old=sh('settings','get','secure','enabled_accessibility_services').strip().split(':')
 keep=[s for s in old if s and s!='null' and not (migrate_visualtest and s.startswith('gulu.gulugulu.visualtest/'))]
 if not any(s in (P+'/dev.edgeflow.GestureService',P+'/.GestureService') for s in keep):
  keep.append(P+'/dev.edgeflow.GestureService')
 if keep!=[s for s in old if s and s!='null']:
  sh('settings','put','secure','enabled_accessibility_services',':'.join(keep))
 sh('settings','put','secure','accessibility_enabled','1')
def keep_open():
 n=next((n for n in ui().iter('node') if n.get('text')=='保持开启'),None)
 if n is not None:tap(n)
