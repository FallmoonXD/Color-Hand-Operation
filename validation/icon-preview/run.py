"""Real resvg pixel checks for the production native-vector preview converter."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import icon_preview as preview

OUT = ROOT / ".checks/icon-preview"
OUT.mkdir(parents=True, exist_ok=True)
checks = []


def check(value, label):
    if not value:
        raise AssertionError(label)
    checks.append(label)


def vector(body, **attrs):
    text = " ".join(f'android:{key}="{value}"' for key, value in attrs.items())
    return (f'<vector xmlns:android="{preview.ANDROID}" xmlns:aapt="{preview.AAPT}" '
            f'android:viewportWidth="100" android:viewportHeight="100" {text}>{body}</vector>')


def image(body, **attrs):
    return preview.render_vector(vector(body, **attrs), 100)


def rect(color, alpha="1", data="M0,0H100V100H0Z"):
    return f'<path android:fillColor="{color}" android:fillAlpha="{alpha}" android:pathData="{data}"/>'


def rejects(xml, fragment):
    try:
        preview.vector_svg(xml)
    except ValueError as error:
        check(fragment in str(error), f"reject {fragment}")
    else:
        raise AssertionError(f"Expected rejection: {fragment}")


solid = image(rect("#80ff0000", ".5"))
check(solid.getpixel((50, 50)) == (255, 0, 0, 64), "ARGB and path alpha multiply")
check(image(rect("#4f00")).getpixel((50, 50)) == (255, 0, 0, 68), "short Android ARGB, not CSS RGBA")
check(image(rect("#ff0000"), alpha=".5").getpixel((50, 50))[3] == 128, "root alpha composites vector")
check(image(rect("#00000000")).getbbox() is None, "transparent paint remains transparent")
tinted = preview.render_vector(vector(rect("#80ff0000", ".5")), 100, tint="#8000ff00")
check(tinted.getpixel((50, 50)) == (0, 255, 0, 32), "monochrome tint retains source and tint alpha")

linear = '''<path android:pathData="M0,0H100V100H0Z"><aapt:attr name="android:fillColor">
<gradient android:type="linear" android:startX="0" android:startY="0" android:endX="100" android:endY="0">
<item android:offset="0" android:color="#ffff0000"/><item android:offset="1" android:color="#ff0000ff"/>
</gradient></aapt:attr></path>'''
im = image(linear)
check(im.getpixel((10, 50))[0] > 220 and im.getpixel((10, 50))[2] < 35, "linear gradient starts red")
check(im.getpixel((90, 50))[2] > 220 and im.getpixel((90, 50))[0] < 35, "linear gradient ends blue")
check(abs(im.getpixel((50, 50))[0] - im.getpixel((50, 50))[2]) < 5, "linear gradient midpoint interpolation")
radial = '''<path android:pathData="M0,0H100V100H0Z"><aapt:attr name="android:fillColor">
<gradient android:type="radial" android:centerX="50" android:centerY="50" android:gradientRadius="40">
<item android:offset="0" android:color="#ffffffff"/><item android:offset="1" android:color="#00ffffff"/>
</gradient></aapt:attr></path>'''
im = image(radial)
check(im.getpixel((50, 50))[3] > 245, "radial center remains opaque")
check(im.getpixel((91, 50))[3] == 0, "radial outer ARGB stop becomes transparent")
check(115 < im.getpixel((70, 50))[3] < 140, "radial alpha interpolates")
defaults = linear.replace('android:type="linear"', 'android:type="linear" android:startColor="#ffff0000" android:centerColor="#ff00ff00" android:endColor="#ff0000ff"')
defaults = defaults.replace('<item android:offset="0" android:color="#ffff0000"/><item android:offset="1" android:color="#ff0000ff"/>', '')
check(image(defaults).getpixel((50, 50))[1] > 245, "start/center/end color form is equivalent")
repeat = linear.replace('android:endX="100"', 'android:endX="20" android:tileMode="repeat"')
a, b = image(repeat).getpixel((5, 50)), image(repeat).getpixel((25, 50))
check(max(abs(x-y) for x, y in zip(a, b)) <= 1, "repeat tile mode repeats spatially")
check('spreadMethod="reflect"' in preview.vector_svg(vector(linear.replace('android:type="linear"', 'android:type="linear" android:tileMode="mirror"'))), "mirror tile mode emitted as reflect")

clip = '<clip-path android:pathData="M50,0H100V100H50Z"/>'
im = image(rect("#ff0000") + clip + rect("#0000ff"))
check(im.getpixel((25, 50)) == (255, 0, 0, 255), "clip does not affect preceding sibling")
check(im.getpixel((75, 50)) == (0, 0, 255, 255), "clip affects following sibling")
im = image(rect("#ff0000") + clip + rect("#0000ff") + '<clip-path android:pathData="M0,0H100V50H0Z"/>' + rect("#ffffff"))
check(im.getpixel((75, 25)) == (255, 255, 255, 255), "successive clips intersect")
check(im.getpixel((75, 75)) == (0, 0, 255, 255), "second clip preserves earlier draw")
im = image('<group>' + clip + rect("#ff0000") + '</group>' + rect("#0000ff", data="M0,0H25V100H0Z"))
check(im.getpixel((10, 50)) == (0, 0, 255, 255), "nested group clip does not leak outside group")
ring = '<path android:fillColor="#ffffff" android:fillType="evenOdd" android:pathData="M10,10H90V90H10Z M30,30H70V70H30Z"/>'
check(image(ring).getpixel((50, 50))[3] == 0, "evenOdd compound path hole preserved")
check(image(ring.replace('android:fillType="evenOdd"', '')).getpixel((50, 50))[3] == 255, "nonzero remains distinct from parity")

shape = rect("#ffffff", data="M10,10H20V20H10Z")
im = image('<group android:pivotX="10" android:pivotY="10" android:scaleX="2" android:scaleY="2" android:translateX="5">' + shape + '</group>')
check(im.getbbox() == (15, 10, 35, 30), "group pivot scale translate ordering")
check(image('<group android:pivotX="20" android:pivotY="20" android:rotation="90">' + shape + '</group>').getbbox() == (20, 10, 30, 20), "group rotation about pivot")
check(image('<group android:scaleX="2" android:scaleY="3">' + shape + '</group>').getbbox() == (20, 30, 40, 60), "nonuniform transformed fill geometry")
check(image('<group android:translateX="7"><group android:translateY="9">' + shape + '</group></group>').getbbox() == (17, 19, 27, 29), "nested group transform accumulation")

stroke = '<path android:strokeColor="#ffffffff" android:strokeWidth="10" android:strokeAlpha=".5" android:strokeLineCap="round" android:strokeLineJoin="bevel" android:pathData="M10,50H90"/>'
im = image(stroke)
check(im.getbbox() == (5, 45, 95, 55), "round cap and stroke width rendered")
check(im.getpixel((50, 50))[3] == 128, "stroke alpha preserved")
check('stroke-linejoin="bevel"' in preview.vector_svg(vector(stroke)), "stroke join preserved")
check(image(stroke.replace('android:strokeLineCap="round"', '')).getbbox() == (10, 45, 90, 55), "default stroke cap is butt")
gradient_stroke = linear.replace('android:pathData="M0,0H100V100H0Z"', 'android:strokeWidth="10" android:pathData="M10,50H90"').replace('name="android:fillColor"', 'name="android:strokeColor"')
im = image(gradient_stroke)
check(im.getpixel((20, 50))[0] > im.getpixel((20, 50))[2], "stroke linear gradient paints left")
check(im.getpixel((80, 50))[2] > im.getpixel((80, 50))[0], "stroke linear gradient paints right")
check(image('<group android:scaleX=".5" android:scaleY=".5">'+stroke+'</group>').getbbox() == (2, 22, 48, 28), "uniform transformed stroke scales consistently")

rejects(vector('<group android:scaleX="2">'+stroke+'</group>'), "Non-uniform group stroke")
rejects(vector(radial.replace('gradientRadius="40"', 'gradientRadius="0"')), "positive gradientRadius")
rejects(vector(linear.replace('type="linear"', 'type="sweep"')), "gradient type")
rejects(vector(rect("@color/unresolved")), "Resolve Android color resource")
rejects(vector(stroke.replace('android:strokeWidth="10"', 'android:trimPathStart=".3" android:strokeWidth="10"')), "trimPath")

originals = ROOT / "design/oho-original-9.3.13.0"
fg, bg, mono = [originals / name for name in ("oho__fg.xml", "oho__bg.xml", "oho__mono.xml")]
before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in (fg, bg, mono)}
for size in (44, 72):
    for shape in ("rounded", "circle"):
        im = preview.render_adaptive(bg, fg, size, shape)
        check(im.size == (size, size) and im.getpixel((0, 0))[3] == 0 and im.getpixel((size//2, size//2))[3] == 255,
              f"actual {size}px {shape} adaptive mask")
        im.save(OUT / f"native-original-{shape}-{size}.png")
check(before == {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in (fg, bg, mono)}, "renderer never changes source assets")
node = ET.parse(fg).getroot();encoded = ET.tostring(node)
preview.vector_svg(node, 72)
check(ET.tostring(node) == encoded, "Element inputs are not mutated")
board = preview.mask_preview(bg, fg, mono);board.save(OUT / "original-mask-board.png")
check(board.size == (1080, 500), "mask board includes actual 72 and 44px cases")

import resvg_py
report = {"passed": len(checks), "renderer": "resvg_py", "version": resvg_py.__version__,
          "resvg_version": resvg_py.__resvg_version__, "checks": checks,
          "limits": "No Android device renderer comparison; nonuniform strokes, sweep gradients, trimPath and unresolved resource colors explicitly reject."}
(OUT / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print(f"PASS {len(checks)} native-vector/SVG/resvg assertions")
