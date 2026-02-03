from pathlib import Path
import re, ast
from io import BytesIO

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / 'backend' / 'scripts' / 'seed_db.py'
OUT = ROOT / 'backend' / 'context' / 'productos_barcodes.pdf'

CODE128_PATTERNS = [
    "212222","222122","222221","121223","121322","131222","122213","122312","132212",
    "221213","221312","231212","112232","122132","122231","113222","123122","123221",
    "223211","221132","221231","213212","223112","312131","311222","321122","321221",
    "312212","322112","322211","212123","212321","232121","111323","131123","131321",
    "112313","132113","132311","211313","231113","231311","112133","112331","132131",
    "113123","113321","133121","313121","211331","231131","213113","213311","213131",
    "311123","311321","331121","312113","312311","332111","314111","221411","431111",
    "111224","111422","121124","121421","141122","141221","112214","112412","122114",
    "122411","142112","142211","241211","221114","413111","241112","134111","111242",
    "121142","121241","114212","124112","124211","411212","421112","421211","212141",
    "214121","412121","111143","111341","131141","114113","114311","411113","411311",
    "113141","114131","311141","411131","211412","211214","211232","2331112"
]
START_B = 104
STOP = 106

def encode_code128_b(data: str):
    values = [START_B]
    for ch in data:
        code = ord(ch) - 32
        if code < 0 or code > 95:
            raise ValueError(f"Caracter no valido para Code128-B: {ch}")
        values.append(code)
    checksum = START_B
    for i, v in enumerate(values[1:], start=1):
        checksum += v * i
    checksum = checksum % 103
    values.append(checksum)
    values.append(STOP)
    return "".join(CODE128_PATTERNS[v] for v in values)


def pdf_escape(text: str) -> str:
    return text.replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')


def load_products():
    content = SEED.read_text(encoding='utf-8')
    m = re.search(r"for sku, name, barcode, category_name in \[(.*?)]\s*:", content, re.S)
    if not m:
        raise RuntimeError("No se encontro la lista de productos en seed_db.py")
    list_literal = "[" + m.group(1) + "]"
    products = ast.literal_eval(list_literal)
    return [
        {"sku": sku, "name": name, "barcode": barcode, "category": category}
        for (sku, name, barcode, category) in products
    ]


def page_content(prod):
    x_margin = 50
    y_top = 780
    line_gap = 18

    lines = [
        (x_margin, y_top, 16, f"Producto: {prod['name']}"),
        (x_margin, y_top - line_gap, 12, f"SKU: {prod['sku']}"),
        (x_margin, y_top - 2*line_gap, 12, f"Categoria: {prod['category']}"),
        (x_margin, y_top - 3*line_gap, 12, f"Barcode: {prod['barcode']}"),
    ]

    img_x, img_y, img_w, img_h = 400, 620, 140, 140

    pattern = encode_code128_b(prod['barcode'])
    module = 1.2
    quiet = 10 * module
    bar_x = x_margin
    bar_y = 500
    bar_h = 80

    parts = []
    parts.append("q")
    parts.append("0 0 0 rg 0 0 0 RG")
    for x, y, size, text in lines:
        parts.append("BT")
        parts.append(f"/F1 {size} Tf")
        parts.append(f"{x} {y} Td")
        parts.append(f"({pdf_escape(text)}) Tj")
        parts.append("ET")

    parts.append(f"{img_x} {img_y} {img_w} {img_h} re S")
    parts.append("BT")
    parts.append("/F1 10 Tf")
    parts.append(f"{img_x + 40} {img_y + img_h/2} Td")
    parts.append("(Imagen) Tj")
    parts.append("ET")

    x = bar_x + quiet
    is_bar = True
    for d in pattern:
        w = int(d) * module
        if is_bar:
            parts.append(f"{x:.2f} {bar_y} {w:.2f} {bar_h} re f")
        x += w
        is_bar = not is_bar

    parts.append("BT")
    parts.append("/F1 12 Tf")
    parts.append(f"{bar_x} {bar_y - 20} Td")
    parts.append(f"({pdf_escape(prod['barcode'])}) Tj")
    parts.append("ET")

    parts.append("Q")
    stream = "\n".join(parts) + "\n"
    return stream.encode('ascii', errors='ignore')


def build_pdf(products):
    buf = BytesIO()
    buf.write(b"%PDF-1.4\n")
    xref = [0]

    def write_obj(obj_num: int, data: bytes):
        xref.append(buf.tell())
        buf.write(f"{obj_num} 0 obj\n".encode('ascii'))
        buf.write(data)
        if not data.endswith(b"\n"):
            buf.write(b"\n")
        buf.write(b"endobj\n")

    write_obj(1, b"<< /Type /Catalog /Pages 2 0 R >>")
    write_obj(3, b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    pages_kids = []
    page_objs = []
    content_objs = []

    obj_num = 4
    content_base = 200

    for i, prod in enumerate(products):
        page_num = obj_num
        content_num = content_base + i
        pages_kids.append(page_num)

        content = page_content(prod)
        content_obj = b"<< /Length %d >>\nstream\n" % len(content) + content + b"endstream"
        content_objs.append((content_num, content_obj))

        page_obj = (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            + f"/Contents {content_num} 0 R ".encode('ascii')
            + b"/Resources << /Font << /F1 3 0 R >> >> >>"
        )
        page_objs.append((page_num, page_obj))
        obj_num += 1

    kids_ref = " ".join(f"{n} 0 R" for n in pages_kids).encode('ascii')
    pages_obj = b"<< /Type /Pages /Kids [" + kids_ref + b"] /Count %d >>" % len(pages_kids)
    write_obj(2, pages_obj)

    for num, data in page_objs:
        write_obj(num, data)
    for num, data in content_objs:
        write_obj(num, data)

    xref_start = buf.tell()
    buf.write(f"xref\n0 {len(xref)}\n".encode('ascii'))
    buf.write(b"0000000000 65535 f \n")
    for pos in xref[1:]:
        buf.write(f"{pos:010d} 00000 n \n".encode('ascii'))
    buf.write(b"trailer\n")
    buf.write(f"<< /Size {len(xref)} /Root 1 0 R >>\n".encode('ascii'))
    buf.write(b"startxref\n")
    buf.write(f"{xref_start}\n".encode('ascii'))
    buf.write(b"%%EOF\n")

    return buf.getvalue()


def main():
    products = load_products()
    data = build_pdf(products)
    OUT.write_bytes(data)
    print(f"PDF regenerado: {OUT}")

if __name__ == '__main__':
    main()
