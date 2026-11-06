"""Package the user's native CSP exports as adaptive color layers; preserve MD3 bytes.

The source foreground uses Overlay. Its effective RGB was baked against the authored
backdrop while retaining alpha, then checked against CSP's own full-size PNG. Normal
Android alpha compositing can therefore reproduce the design without redrawing it.
"""
from pathlib import Path
import base64
import hashlib
import json
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'design'
RES=ROOT/'app/src/main/res'
ANDROID='http://schemas.android.com/apk/res/android'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def padded(image, background):
    """The authored square is the visible 72..360 region of the 432-unit layer."""
    side=image.width;pad=side//4;full=side+2*pad
    result=Image.new('RGBA',(full,full),(0,0,0,0));result.paste(image,(pad,pad))
    if background:
        # Only extend the background into launcher overscan; don't invent new art.
        result.paste(image.crop((0,0,1,side)).resize((pad,side)),(0,pad))
        result.paste(image.crop((side-1,0,side,side)).resize((pad,side)),(pad+side,pad))
        result.paste(result.crop((0,pad,full,pad+1)).resize((full,pad)),(0,0))
        result.paste(result.crop((0,pad+side-1,full,pad+side)).resize((full,pad)),(0,pad+side))
    return result

def color_svg(background,foreground,size=512,shape='rounded'):
    if shape not in ('rounded','circle',None):raise ValueError(shape)
    clip=''
    if shape:
        geometry='<circle cx="216" cy="216" r="144"/>' if shape=='circle' else '<rect x="72" y="72" width="288" height="288" rx="72"/>'
        clip='<defs><clipPath id="mask">'+geometry+'</clipPath></defs>'
    layers=''.join('<image x="0" y="0" width="432" height="432" href="data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode('ascii')+'"/>' for path in (background,foreground))
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="72 72 288 288">{clip}<g'+(' clip-path="url(#mask)"' if shape else '')+'>'+layers+'</g></svg>'

def main():
    spec=json.loads((ART/'color-hand-operation-icon.json').read_text(encoding='utf-8'))
    if spec['mode']!='user-layered-overlay-17':raise ValueError('Not the active layered icon design')
    source=spec['user_design']
    for item in source['source_files'].values():
        path=ART/item['file']
        if sha(path)!=item['sha256']:raise ValueError('Design source changed; export and review it first: '+str(path))
    for filename,expected in source['preserved_sha256'].items():
        if sha(ROOT/filename)!=expected:raise ValueError('MD3/notification asset changed unexpectedly: '+filename)
    folder=ART/'user-layered'
    background=Image.open(folder/'color-background.png').convert('RGBA')
    foreground=Image.open(folder/'color-foreground.png').convert('RGBA')
    reference=Image.open(folder/'color-composite.png').convert('RGBA')
    if any(image.size!=tuple(source['canvas']) for image in (background,foreground,reference)):raise ValueError('Source canvas sizes differ')
    diff=ImageChops.difference(Image.alpha_composite(background,foreground),reference)
    if any(maximum for minimum,maximum in diff.getextrema()):raise ValueError('Color layers no longer reproduce the native CSP export')
    target=RES/'drawable-nodpi';target.mkdir(exist_ok=True)
    bitmaps=[]
    for role,image in [('background',background),('foreground',foreground)]:
        bitmap=target/f'app_icon_color_{role}.png'
        padded(image,role=='background').resize((source['runtime_layer_pixels'],)*2,Image.Resampling.LANCZOS).save(bitmap,optimize=True)
        bitmaps.append(bitmap)
        (RES/'drawable'/f'app_icon_{role}.xml').write_text(
            f'<?xml version="1.0" encoding="utf-8"?>\n<bitmap xmlns:android="{ANDROID}" android:src="@drawable/app_icon_color_{role}" android:gravity="fill" android:filter="true" android:antialias="true" android:dither="true" />\n',encoding='utf-8',newline='\n')
    from icon_preview import render_svg,render_adaptive
    svg=color_svg(*bitmaps)
    (ART/'color-hand-operation.svg').write_text(svg+'\n',encoding='utf-8',newline='\n')
    render_svg(svg).save(ART/'color-hand-operation.png')
    board=Image.new('RGB',(1080,500),'#f3f5fa')
    cases=[(30,48,300,'rounded',False),(365,70,250,'circle',False),(660,60,180,'rounded',True),(875,80,140,'circle',True),(675,300,72,'rounded',False),(785,315,44,'rounded',False),(880,300,72,'rounded',True),(990,315,44,'rounded',True)]
    for x,y,size,shape,themed in cases:
        icon=render_adaptive('#e7ebff',RES/'drawable/app_icon_monochrome.xml',size,shape,tint='#354583') if themed else render_svg(color_svg(*bitmaps,size,shape))
        board.paste(icon,(x,y),icon)
    draw=ImageDraw.Draw(board)
    for x,label in [(675,'72 px'),(785,'44 px'),(880,'72 px mono'),(990,'44 px')]:draw.text((x,390),label,fill='#4f5667')
    board.save(ART/'icon-mask-preview.png')
    resources=[RES/'drawable'/f'app_icon_{role}.xml' for role in ('background','foreground','monochrome')]
    resources += bitmaps+[RES/'drawable/app_icon.xml',RES/'drawable/ic_stat_hand_operation.xml']+[RES/'mipmap-anydpi-v33'/f'{name}.xml' for name in ('ic_launcher','ic_launcher_round')]
    for filename,expected in source['preserved_sha256'].items():assert sha(ROOT/filename)==expected,filename
    report={'mode':spec['mode'],'native_resources':len(resources),'source_composite_max_channel_error':0,'runtime_layer_pixels':source['runtime_layer_pixels'],'visible_region':'72,72,288,288 of 432x432; original icon size/position retained','material':'User CSP Overlay composite preserved in effective foreground RGB; no additional foreground alpha','monochrome':'Existing production XML and SVG preserved byte for byte','notification':'Existing notification icon and preview preserved byte for byte','resources_sha256':{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in resources},'preserved_sha256':source['preserved_sha256'],'preview_note':'Color previews use the packaged bitmap layers. MD3 uses the original vector; installed Android rendering is checked separately.'}
    (ART/'icon-generation-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
