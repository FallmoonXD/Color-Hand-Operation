"""Exercise the real widget resize editor and restore the initial provider layout."""
import device as d,time,json,xml.etree.ElementTree as E
def layout():
 root=E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/replica_widgets.xml'))
 return json.loads(next(n.text for n in root if n.get('name')=='layout_v3'))
before=layout();widget=before[0];wid=widget['id'];r={}
def resize(edge,dx,dy):
 a,b,c,e=d.bounds(d.node('组件 '+str(wid)))
 x=a+8 if edge=='left' else (a+c)//2
 y=e-8 if edge=='bottom' else (b+e)//2
 d.sh('input','swipe',str(x),str(y),str(x+dx),str(y+dy),'500');time.sleep(.5)
d.main_link('小组件');d.click('第 1 页，共 1 页；页面菜单');d.click('编辑组件')
try:
 resize('left',290,0);assert layout()[0]['x']==1 and layout()[0]['w']==3
 d.shot('widget-left-resize');resize('left',-290,0);assert layout()==before
 resize('bottom',0,-300);assert layout()[0]['h']==1
 resize('bottom',0,300);assert layout()==before
 r['left_bottom_resize_and_restore']=True
 # A cancelled move changes neither persistent layout nor the eventual normal view.
 a,b,c,e=d.bounds(d.node('组件 '+str(wid)));x=(a+c)//2;y=e-8
 d.sh('input','motionevent','DOWN',str(x),str(y));d.sh('input','motionevent','MOVE',str(x),str(y-300));d.sh('input','motionevent','CANCEL',str(x),str(y-300));time.sleep(.4)
 assert layout()==before;r['cancelled_resize_preserves_layout']=True
finally:
 d.sh('input','motionevent','CANCEL','630','1400')
 d.click('完成');time.sleep(.5);d.shot('widgets-final');d.sh('input','tap','630','240');time.sleep(.4)
 (d.B/'widget-edit-validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(r,ensure_ascii=False))
