"""Build the active CHO design, including the user's layered color artwork.
The earlier native-vector design builder remains available for its recorded mode.
"""
from pathlib import Path
import copy, hashlib, json, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]; ART=ROOT/'design'; RES=ROOT/'app/src/main/res'
ANDROID='http://schemas.android.com/apk/res/android'; AAPT='http://schemas.android.com/aapt'; A='{'+ANDROID+'}'
ET.register_namespace('android',ANDROID); ET.register_namespace('aapt',AAPT)
def attrs(**values):return {A+k:str(v) for k,v in values.items()}
def vector(size=432):return ET.Element('vector',attrs(width=f'{size}dp',height=f'{size}dp',viewportWidth=432,viewportHeight=432))
def group(parent,clip=None,**transform):
 child=ET.SubElement(parent,'group',attrs(**transform))
 if clip:ET.SubElement(child,'clip-path',attrs(pathData=clip))
 return child
def linear(x1,y1,x2,y2,stops):return dict(type='linear',startX=x1,startY=y1,endX=x2,endY=y2,stops=stops)
def radial(x,y,radius,stops):return dict(type='radial',centerX=x,centerY=y,gradientRadius=radius,stops=stops)
def paint(node,role,value):
 if isinstance(value,str):node.set(A+role,value)
 else:
  wrapper=ET.SubElement(node,'{'+AAPT+'}attr',{'name':'android:'+role})
  grad=ET.SubElement(wrapper,'gradient',attrs(**{k:v for k,v in value.items() if k!='stops'}))
  for offset,color in value['stops']:ET.SubElement(grad,'item',attrs(offset=offset,color=color))
def path(parent,shape,fill=None,stroke=None,width=1,name=None,**extra):
 node=ET.SubElement(parent,'path',attrs(pathData=shape,**extra))
 if name:node.set(A+'name',name)
 if fill is not None:paint(node,'fillColor',fill)
 if stroke is not None:
  paint(node,'strokeColor',stroke)
  node.attrib.update(attrs(strokeWidth=width,strokeLineJoin='round',strokeLineCap='round'))
 return node
def source_vectors(spec):
 out={}
 for role,source in spec['source']['source_files'].items():
  filename=ART/source['file']; assert hashlib.sha256(filename.read_bytes()).hexdigest()==source['sha256'],filename
  out[role]=ET.parse(filename).getroot()
 return out
def write_xml(name,node):
 ET.indent(node,' '); target=RES/name
 target.write_text('<?xml version="1.0" encoding="utf-8"?>\n'+ET.tostring(node,encoding='unicode')+'\n',encoding='utf-8');ET.parse(target)
 return target
def background():
 root=vector();plane='M0,0H432V432H0Z'
 path(root,plane,fill=linear(135,65,335,410,[(0,'#29215F'),(.38,'#5846A4'),(.71,'#BB83CA'),(1,'#E6A2A3')]),name='violet_base')
 path(root,plane,fill=radial(96,354,275,[(0,'#FFFFAE51'),(.22,'#FFF97936'),(.5,'#EDEF7C75'),(.79,'#35C292DE'),(1,'#00C292DE')]),name='orange_light')
 path(root,plane,fill=radial(349,107,238,[(0,'#82B7A2F6'),(.55,'#307D70D0'),(1,'#007D70D0')]),name='violet_light')
 return root
ARROW_HEAD='M219,204L207,216L219,228'
ARROW_TAIL='M209,216H254'
def foreground(original):
 from icon_geometry import visible_shapes
 shapes=visible_shapes([n.get(A+'pathData') for n in original.iter('path')])
 root=vector();root.set(A+'alpha','.94')
 scene=group(root,clip=original.find('.//clip-path').get(A+'pathData'))
 # White and gray acrylic, with one continuous hand and no sleeve/palm cuts.
 # The lighting direction is global; the rim follows existing outer contours.
 def body(name,shape,stops,rim):
  path(scene,shape,fill=linear(180,132,275,360,stops),name=name+'_body')
  inside=group(scene,clip=shape)
  diffuse=linear(162,137,300,350,[(0,'#3AFFFFFF'),(.42,'#18FFFFFF'),(.7,'#00FFFFFF'),(1,'#10FFFFFF')])
  path(inside,shape,stroke=diffuse,width=rim*2.15,name=name+'_soft_edge')
  edge=linear(162,137,300,350,[(0,'#EFFFFFFF'),(.36,'#A8FFFFFF'),(.68,'#20FFFFFF'),(1,'#52FFFFFF')])
  path(inside,shape,stroke=edge,width=rim,name=name+'_edge')
 body('phone',shapes['phone'],[(0,'#E5E7EA'),(.55,'#CED1D6'),(1,'#B7BCC4')],2.2)
 body('screen',shapes['screen'],[(0,'#ADB3BD'),(.55,'#969EA9'),(1,'#B1B6BF')],1.1)
 arrow=linear(207,216,255,216,[(0,'#FFFFFFFF'),(.3,'#FFFFFFFF'),(.66,'#B8FFFFFF'),(1,'#00FFFFFF')])
 for name,shape in [('gesture_arrow_head',ARROW_HEAD),('gesture_arrow_tail',ARROW_TAIL)]:path(scene,shape,stroke=arrow,width=5.4,name=name)
 body('hand',shapes['hand'],[(0,'#F5F6F7'),(.55,'#EBEEF1'),(1,'#D8DDE3')],2.2)
 return root

def monochrome(original):
 from icon_geometry import visible_shapes
 shapes=visible_shapes([n.get(A+'pathData') for n in original.iter('path')])
 root=vector();scene=group(root,clip=original.find('.//clip-path').get(A+'pathData'))
 # Three disjoint tone regions. The entire hand/arm has one tone and outline.
 for name,shape,alpha in [('phone',shapes['frame'],.64),('screen',shapes['visible_screen'],.34),('hand',shapes['hand'],1)]:
  path(scene,shape,fill='#FFFFFFFF',name=name+'_tone',fillAlpha=alpha)
 for name,shape in [('gesture_arrow_head',ARROW_HEAD),('gesture_arrow_tail',ARROW_TAIL)]:path(scene,shape,stroke='#FFFFFFFF',width=5.4,name=name)
 return root

def notification_symbol(original):
 # Original occlusion-resolved paths; omit screen with its three old dot holes.
 paths=list(original.iter('path'));root=vector();scene=group(root,clip=original.find('.//clip-path').get(A+'pathData'))
 # Rasterize the touching outlines together. Separate opaque paths still leave
 # partially covered antialiased pixels at the phone/hand and wrist seams.
 # The original contours share their winding; the phone hole is reversed.
 silhouette=''.join(paths[index].get(A+'pathData') for index in (0,5,6))
 path(scene,silhouette,fill='#FFFFFFFF',name='phone_hand_silhouette',fillType='nonZero')
 for name,shape in [('gesture_arrow_head',ARROW_HEAD),('gesture_arrow_tail',ARROW_TAIL)]:path(scene,shape,stroke='#FFFFFFFF',width=5.4,name=name)
 return root
def main():
 spec=json.loads((ART/'color-hand-operation-icon.json').read_text(encoding='utf-8'))
 if spec['mode']=='user-layered-overlay-17':
  from build_layered_icon import main as build_layered
  build_layered();return
 if spec['mode']!='oho-coloros-neutral-glass-17':raise ValueError('Unexpected active design mode')
 originals=source_vectors(spec);bg=background();fg=foreground(originals['foreground']);mono=monochrome(originals['foreground']);resources=[]
 for role,tree in [('background',bg),('foreground',fg),('monochrome',mono)]:resources.append(write_xml(f'drawable/app_icon_{role}.xml',tree))
 n=spec['notification'];notification=vector(24);holder=group(notification,scaleX=n['scale'],scaleY=n['scale'],pivotX=n['pivot'][0],pivotY=n['pivot'][1],translateX=n['translate'][0],translateY=n['translate'][1])
 for child in notification_symbol(originals['monochrome']):holder.append(copy.deepcopy(child))
 resources.append(write_xml('drawable/ic_stat_hand_operation.xml',notification))
 legacy=ET.Element('layer-list')
 for role in ['background','foreground']:ET.SubElement(legacy,'item',attrs(drawable='@drawable/app_icon_'+role))
 resources.append(write_xml('drawable/app_icon.xml',legacy));adaptive=ET.Element('adaptive-icon')
 for role in ['background','foreground','monochrome']:ET.SubElement(adaptive,role,attrs(drawable='@drawable/app_icon_'+role))
 for name in ['ic_launcher','ic_launcher_round']:resources.append(write_xml(f'mipmap-anydpi-v33/{name}.xml',adaptive))
 from icon_preview import adaptive_svg,vector_svg,render_adaptive,render_vector,mask_preview
 view_box=tuple(spec['adaptive_view_box'])
 (ART/'color-hand-operation.svg').write_text(adaptive_svg(bg,fg,size=512,shape='rounded',view_box=view_box),encoding='utf-8')
 (ART/'color-hand-operation-monochrome.svg').write_text(vector_svg(mono,size=512,view_box=view_box),encoding='utf-8')
 render_adaptive(bg,fg,size=512,shape='rounded',view_box=view_box).save(ART/'color-hand-operation.png')
 mask_preview(bg,fg,mono).save(ART/'icon-mask-preview.png')
 from PIL import Image
 board=Image.new('RGBA',(288,132),'#353D55')
 for x,y,size in [(18,54,24),(74,42,48),(160,18,96)]:board.alpha_composite(render_vector(notification,size=size),(x,y))
 board.convert('RGB').save(ART/'notification-icon-preview.png')
 original_paths=list(originals['foreground'].iter('path'));checks={}
 for name,index in [('phone',1),('screen',2)]:
  native=next(n for n in fg.iter('path') if n.get(A+'name')==name+'_body');checks[name]=native.get(A+'pathData')==original_paths[index].get(A+'pathData')
 assert all(checks.values());dot_shapes={original_paths[i].get(A+'pathData') for i in [4,5,6]}
 assert not any(n.get(A+'pathData') in dot_shapes for tree in [fg,mono] for n in tree.iter('path'))
 assert [n.get(A+'name') for n in mono.iter('path') if n.get(A+'fillColor')]==['phone_tone','screen_tone','hand_tone']
 report={'mode':spec['mode'],'native_xml_count':len(resources),'original_phone_screen_geometry':checks,'hand_geometry':'Boolean union of archived visible rear/front contours, no added anatomy or cuff division','three_dots_removed':True,'arrow_direction':'left / inward','monochrome':'Three disjoint tones: phone, filled screen, continuous hand and arm','material':'Neutral white/gray diffuse acrylic with thin edges following original contours; no palm shading','foreground_composite_alpha':.94,'resources_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in resources},'preview_note':'SVG comes from native XML. Installed VectorDrawable rendering is checked separately.'}
 (ART/'icon-generation-checks.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
