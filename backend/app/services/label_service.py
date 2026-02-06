from __future__ import annotations

import base64
from pathlib import Path
from typing import Final

import barcode
from barcode.writer import SVGWriter

LABEL_WIDTH_MM: Final[int] = 80
LABEL_HEIGHT_MM: Final[int] = 40
VIEWBOX_W: Final[int] = 800
VIEWBOX_H: Final[int] = 400

_ROOT_DIR = Path(__file__).resolve().parents[2]
LABEL_DIR = _ROOT_DIR / "storage" / "labels"


def _ensure_dirs() -> None:
    LABEL_DIR.mkdir(parents=True, exist_ok=True)


def _generate_barcode_svg(barcode_value: str) -> str:
    _ensure_dirs()
    code128 = barcode.get("code128", barcode_value, writer=SVGWriter())
    tmp_base = LABEL_DIR / f"_tmp_{barcode_value}"
    filename = code128.save(
        str(tmp_base),
        options={
            "write_text": False,
            "module_width": 0.2,
            "module_height": 15,
            "quiet_zone": 1,
        },
    )
    svg_text = Path(filename).read_text(encoding="utf-8")
    try:
        Path(filename).unlink()
    except OSError:
        pass
    return svg_text


def build_label_svg(*, barcode_value: str, sku: str, company: str = "IoTrack") -> str:
    barcode_svg = _generate_barcode_svg(barcode_value)
    barcode_b64 = base64.b64encode(barcode_svg.encode("utf-8")).decode("ascii")
    barcode_href = f"data:image/svg+xml;base64,{barcode_b64}"

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg"
     width="{LABEL_WIDTH_MM}mm" height="{LABEL_HEIGHT_MM}mm"
     viewBox="0 0 {VIEWBOX_W} {VIEWBOX_H}">
  <rect x="0" y="0" width="{VIEWBOX_W}" height="{VIEWBOX_H}" fill="#FFFFFF" />
  <text x="40" y="60" font-family="Arial, sans-serif" font-size="36" font-weight="700">{company}</text>
  <text x="760" y="60" font-family="Arial, sans-serif" font-size="28" text-anchor="end">SKU {sku}</text>
  <image x="40" y="85" width="720" height="220" href="{barcode_href}" preserveAspectRatio="xMidYMid meet" />
  <text x="400" y="350" font-family="Arial, sans-serif" font-size="32" text-anchor="middle">{barcode_value}</text>
</svg>
"""


def label_path_for(product_id: int) -> Path:
    _ensure_dirs()
    return LABEL_DIR / f"product_{product_id}.svg"


def generate_and_store_label(*, product_id: int, barcode_value: str, sku: str) -> Path:
    svg = build_label_svg(barcode_value=barcode_value, sku=sku)
    path = label_path_for(product_id)
    path.write_text(svg, encoding="utf-8")
    return path
