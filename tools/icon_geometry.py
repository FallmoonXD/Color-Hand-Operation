"""Boolean operations on the archived icon curves; no hand-authored new anatomy.

Preview-only dependency, not an Android build/runtime dependency:
python -m pip install --target .toolchains/icon-geometry --only-binary=:all: skia-pathops==0.9.2
"""
from pathlib import Path
import re
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'.toolchains/icon-geometry'))
import pathops


def parse(data):
    tokens=re.findall(r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?',data)
    path=pathops.Path();i=0;command=None;x=y=0
    arity={'M':2,'L':2,'H':1,'V':1,'C':6,'Q':4}
    while i<len(tokens):
        if tokens[i].isalpha():
            command=tokens[i];i+=1
            if command=='Z':path.close();command=None;continue
        if command not in arity:raise ValueError('Unsupported source command: '+str(command))
        count=arity[command];values=list(map(float,tokens[i:i+count]));i+=count
        if len(values)!=count:raise ValueError('Incomplete path command')
        if command=='M':x,y=values;path.moveTo(x,y);command='L'
        elif command=='L':x,y=values;path.lineTo(x,y)
        elif command=='H':x=values[0];path.lineTo(x,y)
        elif command=='V':y=values[0];path.lineTo(x,y)
        elif command=='C':path.cubicTo(*values);x,y=values[-2:]
        elif command=='Q':path.quadTo(*values);x,y=values[-2:]
    return path


def svg(path):
    names={pathops.PathVerb.MOVE:'M',pathops.PathVerb.LINE:'L',pathops.PathVerb.QUAD:'Q',pathops.PathVerb.CUBIC:'C',pathops.PathVerb.CLOSE:'Z'}
    out=[]
    for verb,points in path:
        if verb not in names:raise ValueError('Unsupported boolean result: '+str(verb))
        out.append(names[verb]+','.join(format(v,'.7g') for point in points for v in point))
    return ''.join(out)


def combine(left,right,operation):
    return svg(pathops.op(parse(left),parse(right),getattr(pathops.PathOp,operation)))


def visible_shapes(original_paths):
    back,phone,screen,front=[original_paths[i] for i in (0,1,2,3)]
    # The sleeve-colored patch is deliberately not included as a separate part.
    hand=combine(combine(back,phone,'DIFFERENCE'),front,'UNION')
    visible_screen=combine(screen,hand,'DIFFERENCE')
    frame=combine(combine(phone,screen,'DIFFERENCE'),hand,'DIFFERENCE')
    whole=combine(phone,hand,'UNION')
    return {'phone':phone,'screen':screen,'hand':hand,'visible_screen':visible_screen,'frame':frame,'whole':whole}
