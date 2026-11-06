"""VectorDrawable -> SVG -> resvg PNG previews, without changing native design assets.

Private renderer install (no global packages/environment changes):
  python -m pip install --target .toolchains/icon-preview --only-binary=:all: resvg_py==0.5.0

The SVG keeps path data, sequential group clipping, even-odd fills, ARGB opacity,
linear/radial gradients and normal Android stroke settings. Unsupported Android
features fail explicitly. In particular, non-uniform transformed strokes differ
from SVG strokes and are rejected rather than silently approximated.
"""
from __future__ import annotations

import argparse
import io
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageColor, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
SVG = "http://www.w3.org/2000/svg"
A = "{" + ANDROID + "}"
ET.register_namespace("", SVG)
VectorSource = str | Path | ET.Element


def _tag(name: str) -> str:
    return "{" + SVG + "}" + name


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _number(value: float) -> str:
    return format(float(value), ".10g")


def _float(node: ET.Element, key: str, default: float = 0) -> float:
    value = float(node.get(A + key, default))
    if not math.isfinite(value):
        raise ValueError(f"Non-finite android:{key}")
    return value


def _alpha(node: ET.Element, key: str, default: float = 1) -> float:
    return min(1.0, max(0.0, _float(node, key, default)))


def _read(source: VectorSource) -> ET.Element:
    if isinstance(source, ET.Element):
        node = source
    elif isinstance(source, str) and source.lstrip().startswith("<"):
        node = ET.fromstring(source)
    else:
        node = ET.parse(Path(source)).getroot()
    if _local(node.tag) != "vector":
        raise ValueError("Expected a VectorDrawable <vector> root")
    if node.get(A + "tint"):
        raise ValueError("Resolve android:tint explicitly with the tint= argument")
    return node


def _color(value: str) -> tuple[str, float]:
    if value == "@android:color/transparent":
        return "#000000", 0.0
    if value.startswith(("@", "?")):
        raise ValueError(f"Resolve Android color resource before previewing: {value}")
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) in (3, 4):
            digits = "".join(c * 2 for c in digits)
        if len(digits) == 6:
            digits = "ff" + digits
        if len(digits) != 8:
            raise ValueError(f"Invalid Android color: {value}")
        number = int(digits, 16)
        return f"#{number & 0xffffff:06x}", (number >> 24) / 255
    r, g, b, a = ImageColor.getcolor(value, "RGBA")
    return f"#{r:02x}{g:02x}{b:02x}", a / 255


def _matrix_multiply(a, b):
    # SVG affine matrices (a,b,c,d,e,f), multiplying parent by local.
    return (a[0]*b[0]+a[2]*b[1], a[1]*b[0]+a[3]*b[1],
            a[0]*b[2]+a[2]*b[3], a[1]*b[2]+a[3]*b[3],
            a[0]*b[4]+a[2]*b[5]+a[4], a[1]*b[4]+a[3]*b[5]+a[5])


IDENTITY = (1., 0., 0., 1., 0., 0.)


def _group_matrix(node: ET.Element):
    px, py = _float(node, "pivotX"), _float(node, "pivotY")
    sx, sy = _float(node, "scaleX", 1), _float(node, "scaleY", 1)
    tx, ty = _float(node, "translateX"), _float(node, "translateY")
    angle = math.radians(_float(node, "rotation"))
    cos, sin = math.cos(angle), math.sin(angle)
    a, b, c, d = cos*sx, sin*sx, -sin*sy, cos*sy
    return a, b, c, d, px+tx-a*px-c*py, py+ty-b*px-d*py


def _uniform(matrix) -> bool:
    a, b, c, d, _, _ = matrix
    x, y, dot = a*a+b*b, c*c+d*d, a*c+b*d
    tolerance = max(1., x, y) * 1e-7
    return abs(x-y) <= tolerance and abs(dot) <= tolerance


class _Converter:
    def __init__(self, root: ET.Element, prefix: str, tint: str | None = None):
        self.defs = ET.SubElement(root, _tag("defs"))
        self.prefix, self.serial = prefix, 0
        self.tint = _color(tint) if tint else None
        self.has_stroke = False

    def _id(self, kind: str) -> str:
        self.serial += 1
        return f"{self.prefix}-{kind}-{self.serial}"

    def _paint_color(self, value: str) -> tuple[str, float]:
        color, alpha = _color(value)
        return (self.tint[0], alpha*self.tint[1]) if self.tint else (color, alpha)

    def _gradient(self, gradient: ET.Element) -> str:
        kind = gradient.get(A + "type", "linear")
        kind = {"0": "linear", "1": "radial", "2": "sweep"}.get(kind, kind)
        attrs = {"id": self._id("gradient"), "gradientUnits": "userSpaceOnUse"}
        tile = gradient.get(A + "tileMode", "clamp")
        tile = {"0": "clamp", "1": "repeat", "2": "mirror"}.get(tile, tile)
        if tile not in ("clamp", "repeat", "mirror"):
            raise ValueError(f"Unsupported gradient tile mode {tile}")
        attrs["spreadMethod"] = {"clamp": "pad", "repeat": "repeat", "mirror": "reflect"}[tile]
        if kind == "linear":
            attrs.update({"x1": _number(_float(gradient, "startX")), "y1": _number(_float(gradient, "startY")),
                          "x2": _number(_float(gradient, "endX")), "y2": _number(_float(gradient, "endY"))})
            node = ET.SubElement(self.defs, _tag("linearGradient"), attrs)
        elif kind == "radial":
            radius = _float(gradient, "gradientRadius")
            if radius <= 0:
                raise ValueError("Radial gradient requires a positive gradientRadius")
            attrs.update({"cx": _number(_float(gradient, "centerX")), "cy": _number(_float(gradient, "centerY")), "r": _number(radius)})
            node = ET.SubElement(self.defs, _tag("radialGradient"), attrs)
        else:
            raise ValueError(f"Unsupported Android gradient type: {kind}")
        stops = [(float(item.get(A+"offset")), item.get(A+"color"))
                 for item in gradient if _local(item.tag) == "item"]
        if not stops:
            stops = [(0., gradient.get(A+"startColor", "#00000000"))]
            if gradient.get(A+"centerColor") is not None:
                stops.append((.5, gradient.get(A+"centerColor")))
            stops.append((1., gradient.get(A+"endColor", "#00000000")))
        if len(stops) < 2 or any(not math.isfinite(offset) or not 0 <= offset <= 1 or color is None for offset, color in stops):
            raise ValueError("Gradient requires at least two valid color stops")
        if any(stops[i][0] > stops[i+1][0] for i in range(len(stops)-1)):
            raise ValueError("Gradient stops must be ordered")
        for offset, value in stops:
            color, alpha = self._paint_color(value)
            ET.SubElement(node, _tag("stop"), {"offset": _number(offset), "stop-color": color, "stop-opacity": _number(alpha)})
        return f"url(#{attrs['id']})"

    def _paint(self, original: ET.Element, target: ET.Element, role: str):
        alpha = _alpha(original, role + "Alpha")
        resource = next((child for child in original if child.tag == "{"+AAPT+"}attr"
                         and child.get("name") in ("android:"+role+"Color", role+"Color")), None)
        if resource is not None:
            gradients = [child for child in resource if _local(child.tag) == "gradient"]
            if len(gradients) != 1:
                raise ValueError(f"Only gradient aapt:{role}Color is supported")
            target.set(role, self._gradient(gradients[0]))
        else:
            value = original.get(A + role + "Color")
            if value is None:
                target.set(role, "none")
                return
            color, opacity = self._paint_color(value)
            target.set(role, color)
            alpha *= opacity
        target.set(role + "-opacity", _number(alpha))

    def children(self, original: ET.Element, target: ET.Element, matrix=IDENTITY):
        current = target
        for node in original:
            kind = _local(node.tag)
            if kind == "group":
                local = _group_matrix(node)
                group = ET.SubElement(current, _tag("g"), {"transform": "matrix("+" ".join(map(_number, local))+")"})
                self.children(node, group, _matrix_multiply(matrix, local))
            elif kind == "clip-path":
                ident = self._id("clip")
                clip = ET.SubElement(self.defs, _tag("clipPath"), {"id": ident, "clipPathUnits": "userSpaceOnUse"})
                rule = "evenodd" if node.get(A+"fillType") in ("evenOdd", "1") else "nonzero"
                ET.SubElement(clip, _tag("path"), {"d": node.get(A+"pathData", ""), "clip-rule": rule, "fill-rule": rule})
                # VectorDrawable clipping affects only following siblings in this group.
                current = ET.SubElement(current, _tag("g"), {"clip-path": f"url(#{ident})"})
            elif kind == "path":
                if (_float(node, "trimPathStart") != 0 or _float(node, "trimPathEnd", 1) != 1 or _float(node, "trimPathOffset") != 0):
                    raise ValueError("trimPath requires path-length processing; unsupported in icon previews")
                attrs = {"d": node.get(A+"pathData", ""), "fill-rule": "evenodd" if node.get(A+"fillType") in ("evenOdd", "1") else "nonzero"}
                path = ET.SubElement(current, _tag("path"), attrs)
                self._paint(node, path, "fill")
                self._paint(node, path, "stroke")
                if path.get("stroke") != "none":
                    self.has_stroke = True
                    if not _uniform(matrix):
                        raise ValueError("Non-uniform group stroke differs between Android and SVG; use uniform groups for icon strokes")
                    cap = node.get(A+"strokeLineCap", "butt")
                    join = node.get(A+"strokeLineJoin", "miter")
                    path.set("stroke-linecap", {"0": "butt", "1": "round", "2": "square"}.get(cap, cap))
                    path.set("stroke-linejoin", {"0": "miter", "1": "round", "2": "bevel"}.get(join, join))
                    path.set("stroke-width", _number(_float(node, "strokeWidth")))
                    path.set("stroke-miterlimit", _number(_float(node, "strokeMiterLimit", 4)))
            else:
                raise ValueError(f"Unsupported VectorDrawable element: {kind}")


def _size(size):
    width, height = (size, size) if isinstance(size, int) else size
    if not isinstance(width, int) or not isinstance(height, int) or width <= 0 or height <= 0:
        raise ValueError("Preview size must be positive integer pixels")
    return width, height


def _viewport(node):
    width, height = _float(node, "viewportWidth"), _float(node, "viewportHeight")
    if width <= 0 or height <= 0:
        raise ValueError("Vector viewport must be positive")
    return 0., 0., width, height


def _svg_root(size, view_box):
    width, height = _size(size)
    if len(view_box) != 4 or view_box[2] <= 0 or view_box[3] <= 0:
        raise ValueError("view_box must be (x,y,width,height)")
    return ET.Element(_tag("svg"), {"width": str(width), "height": str(height),
                     "viewBox": " ".join(map(_number, view_box)), "preserveAspectRatio": "none"})


def _add_vector(svg, source, prefix, tint=None):
    node = _read(source)
    group = ET.SubElement(svg, _tag("g"), {"opacity": _number(_alpha(node, "alpha"))})
    converter = _Converter(svg, prefix, tint)
    converter.children(node, group)
    return converter


def vector_svg(source: VectorSource, size=512, view_box=None, tint: str | None = None) -> str:
    """Convert one native vector. view_box optionally crops to the adaptive viewport."""
    node = _read(source)
    view_box = _viewport(node) if view_box is None else view_box
    svg = _svg_root(size, view_box)
    converter = _add_vector(svg, node, "vector", tint)
    width, height = _size(size)
    if converter.has_stroke and not math.isclose(width/view_box[2], height/view_box[3], rel_tol=1e-7):
        raise ValueError("Non-uniform output scaling is not supported for Android strokes")
    return ET.tostring(svg, encoding="unicode")


def adaptive_svg(background: VectorSource, foreground: VectorSource, size=512,
                 shape: str | None = "rounded", view_box=(72, 72, 288, 288),
                 tint: str | None = None) -> str:
    """Compose native layers and apply only the requested launcher preview mask.

    background may also be a hex color, useful for themed monochrome previews.
    tint affects the foreground's RGB while retaining all source alpha.
    """
    svg = _svg_root(size, view_box)
    defs = ET.SubElement(svg, _tag("defs"))
    x, y, width, height = view_box
    if shape not in (None, "rounded", "circle"):
        raise ValueError("shape must be rounded, circle or None")
    host = ET.SubElement(svg, _tag("g"))
    if shape:
        clip = ET.SubElement(defs, _tag("clipPath"), {"id": "launcher-mask", "clipPathUnits": "userSpaceOnUse"})
        if shape == "circle":
            ET.SubElement(clip, _tag("ellipse"), {"cx": _number(x+width/2), "cy": _number(y+height/2), "rx": _number(width/2), "ry": _number(height/2)})
        else:
            ET.SubElement(clip, _tag("rect"), {"x": _number(x), "y": _number(y), "width": _number(width), "height": _number(height), "rx": _number(min(width, height)/4)})
        host.set("clip-path", "url(#launcher-mask)")
    if isinstance(background, str) and background.startswith("#"):
        color, alpha = _color(background)
        ET.SubElement(host, _tag("rect"), {"x": _number(x), "y": _number(y), "width": _number(width), "height": _number(height), "fill": color, "fill-opacity": _number(alpha)})
    else:
        _add_vector(host, background, "background")
    converter = _add_vector(host, foreground, "foreground", tint)
    out_width, out_height = _size(size)
    if converter.has_stroke and not math.isclose(out_width/width, out_height/height, rel_tol=1e-7):
        raise ValueError("Non-uniform output scaling is not supported for Android strokes")
    return ET.tostring(svg, encoding="unicode")


def render_svg(svg: str) -> Image.Image:
    """Rasterize with project-private resvg. This function never writes design files."""
    private = ROOT / ".toolchains/icon-preview"
    if private.is_dir() and str(private) not in sys.path:
        sys.path.insert(0, str(private))
    try:
        import resvg_py
    except ImportError as error:
        raise RuntimeError("Install the private renderer: python -m pip install --target .toolchains/icon-preview --only-binary=:all: resvg_py==0.5.0") from error
    data = resvg_py.svg_to_bytes(svg_string=svg, skip_system_fonts=True, shape_rendering="geometric_precision")
    with Image.open(io.BytesIO(data)) as image:
        return image.convert("RGBA")


def render_vector(source: VectorSource, size=512, view_box=None, tint: str | None = None) -> Image.Image:
    return render_svg(vector_svg(source, size, view_box, tint))


def render_adaptive(background: VectorSource, foreground: VectorSource, size=512,
                    shape="rounded", view_box=(72, 72, 288, 288), tint=None) -> Image.Image:
    return render_svg(adaptive_svg(background, foreground, size, shape, view_box, tint))


def mask_preview(background: VectorSource, foreground: VectorSource, monochrome: VectorSource,
                 view_box=(72, 72, 288, 288)) -> Image.Image:
    """Round/rounded and themed/native 72/44px board; icons are rendered at actual pixel sizes."""
    board = Image.new("RGB", (1080, 500), "#f3f5fa")
    cases = [(30,48,300,"rounded",False), (365,70,250,"circle",False),
             (660,60,180,"rounded",True), (875,80,140,"circle",True),
             (675,300,72,"rounded",False), (785,315,44,"rounded",False),
             (880,300,72,"rounded",True), (990,315,44,"rounded",True)]
    for x, y, size, shape, themed in cases:
        icon = render_adaptive("#e7ebff" if themed else background,
                               monochrome if themed else foreground, size, shape, view_box,
                               "#354583" if themed else None)
        board.paste(icon, (x, y), icon)
    draw = ImageDraw.Draw(board)
    for x, label in [(675,"72 px"), (785,"44 px"), (880,"72 px mono"), (990,"44 px")]:
        draw.text((x,390), label, fill="#4f5667")
    return board


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("vector", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--view-box", type=float, nargs=4)
    parser.add_argument("--tint")
    parser.add_argument("--svg", type=Path, help="Optionally save the exact intermediate SVG")
    args = parser.parse_args()
    svg = vector_svg(args.vector, args.size, args.view_box, args.tint)
    render_svg(svg).save(args.output)
    if args.svg:
        args.svg.write_text(svg, encoding="utf-8")


if __name__ == "__main__":
    main()
