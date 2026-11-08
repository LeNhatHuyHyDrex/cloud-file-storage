"""Sinh slide thuyet trinh (.pptx) bang python-pptx."""
import os
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Emu, Inches, Pt
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
FIG = os.path.join(HERE, "figures")
SHOT = os.path.join(ROOT, "docs", "screenshots")
OUT = os.path.join(HERE, "Slide_NimbusDrive.pptx")

NAVY = RGBColor(0x10, 0x30, 0x3F)
INDIGO = RGBColor(0x16, 0x38, 0x4A)
VIOLET = RGBColor(0x3A, 0x6A, 0x96)
PINK = RGBColor(0xA0, 0x7A, 0x42)
GREEN = RGBColor(0x3F, 0x82, 0x63)
AMBER = RGBColor(0x8F, 0x6C, 0x3E)
INK = RGBColor(0x10, 0x30, 0x3F)
MUTED = RGBColor(0x4A, 0x5A, 0x64)
SOFT = RGBColor(0xE2, 0xEB, 0xEF)
CARD = RGBColor(0xF2, 0xF4, 0xF5)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
FONT = "Calibri"

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(13.333), Inches(7.5)
BLANK = prs.slide_layouts[6]
W, H = 13.333, 7.5
num = [0]


def bg(slide, color):
    f = slide.background.fill
    f.solid()
    f.fore_color.rgb = color


def text(slide, x, y, w, h, s, size=16, color=INK, bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, italic=False, name=None):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = anchor
    lines = s if isinstance(s, list) else [s]
    for i, line in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        _runs(p, line, size, color, bold, italic)
    if name:
        tb.name = name
    return tb


def _runs(p, line, size, color, bold, italic=False):
    parts = line.split("**")
    for k, part in enumerate(parts):
        if not part:
            continue
        r = p.add_run()
        r.text = part
        r.font.name = FONT
        r.font.size = Pt(size)
        r.font.bold = bold or k % 2 == 1
        r.font.italic = italic
        r.font.color.rgb = color


def bullets(slide, x, y, w, h, items, size=16, color=INK, gap=8):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    for i, it in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(gap)
        pPr = p._p.get_or_add_pPr()
        pPr.set("marL", str(Emu(Inches(0.28))))
        pPr.set("indent", str(-Emu(Inches(0.28))))
        from pptx.oxml.ns import qn
        from lxml import etree
        bu = etree.SubElement(pPr, qn("a:buClr"))
        clr = etree.SubElement(bu, qn("a:srgbClr")); clr.set("val", "A07A42")
        ch = etree.SubElement(pPr, qn("a:buChar")); ch.set("char", "•")
        _runs(p, it, size, color, False)
    return tb


def rect(slide, x, y, w, h, fill, radius=True, line=None):
    shp = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
                                 Inches(x), Inches(y), Inches(w), Inches(h))
    if radius:
        shp.adjustments[0] = 0.08
    shp.fill.solid()
    shp.fill.fore_color.rgb = fill
    if line:
        shp.line.color.rgb = line
        shp.line.width = Pt(1)
    else:
        shp.line.fill.background()
    shp.shadow.inherit = False
    return shp


def circle(slide, x, y, d, fill, label, size=16):
    c = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(d), Inches(d))
    c.fill.solid()
    c.fill.fore_color.rgb = fill
    c.line.fill.background()
    c.shadow.inherit = False
    tf = c.text_frame
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    _runs(p, label, size, WHITE, True)
    return c


def picture(slide, path, x, y, w=None, h=None, border=False):
    im = Image.open(path)
    ar = im.width / im.height
    if w and h:
        if w / h > ar:
            w2 = h * ar
            x += (w - w2) / 2
            w = w2
        else:
            h2 = w / ar
            y += (h - h2) / 2
            h = h2
    elif w:
        h = w / ar
    else:
        w = h * ar
    pic = slide.shapes.add_picture(path, Inches(x), Inches(y), Inches(w), Inches(h))
    if border:
        pic.line.color.rgb = RGBColor(0xDC, 0xE2, 0xE6)
        pic.line.width = Pt(1)
    return pic


def footer(slide, dark=False):
    num[0] += 1
    c = RGBColor(0x8C, 0x9B, 0xA4)
    text(slide, 0.6, 7.0, 6, 0.3, "Nimbus Drive · Đồ án Điện toán đám mây", 10, c)
    text(slide, W - 1.6, 7.0, 1.0, 0.3, str(num[0]), 10, c, align=PP_ALIGN.RIGHT)


def content(title, kicker=None):
    s = prs.slides.add_slide(BLANK)
    bg(s, WHITE)
    if kicker:
        text(s, 0.6, 0.42, 10, 0.3, kicker.upper(), 12, VIOLET, True)
    text(s, 0.6, 0.72, 12.1, 0.8, title, 32, INK, True, name="Title")
    footer(s)
    return s


def grid(slide, x, y, widths, header, rows, size=13, row_h=0.62, head_h=0.52, first_bold=True):
    """Bang dung hinh khoi: dong tieu de + cac dong xen ke mau."""
    total = sum(widths)
    rect(slide, x, y, total, head_h, INDIGO, radius=False)
    cx = x
    for w, h in zip(widths, header):
        text(slide, cx + 0.15, y, w - 0.25, head_h, h, size, WHITE, True, anchor=MSO_ANCHOR.MIDDLE)
        cx += w
    for i, row in enumerate(rows):
        ry = y + head_h + i * row_h
        rect(slide, x, ry, total, row_h, SOFT if i % 2 == 0 else CARD, radius=False)
        cx = x
        for j, (w, cell) in enumerate(zip(widths, row)):
            text(slide, cx + 0.15, ry, w - 0.25, row_h, cell, size - 1 if j else size, INK if (j == 0 and first_bold) else MUTED,
                 bold=(j == 0 and first_bold), anchor=MSO_ANCHOR.MIDDLE)
            cx += w


def card_grid(slide, items, cols, x0=0.6, y0=1.85, cw=2.85, ch=2.15, gx=0.22, gy=0.3, palette=None, tsize=17, dsize=13.5):
    palette = palette or [INDIGO, VIOLET, PINK, GREEN]
    for i, (t, d) in enumerate(items):
        col, row = i % cols, i // cols
        x, y = x0 + col * (cw + gx), y0 + row * (ch + gy)
        rect(slide, x, y, cw, ch, CARD)
        circle(slide, x + 0.25, y + 0.25, 0.55, palette[i % len(palette)], str(i + 1), 15)
        text(slide, x + 0.25, y + 0.95, cw - 0.45, 0.4, t, tsize, INK, True)
        text(slide, x + 0.25, y + 1.38, cw - 0.45, ch - 1.45, d, dsize, MUTED)


def notes(slide, s):
    slide.notes_slide.notes_text_frame.text = s


# ---------------------------------------------------------------- 1. Title
s = prs.slides.add_slide(BLANK)
bg(s, NAVY)
picture(s, os.path.join(SHOT, "02-dashboard-grid.png"), 7.3, 1.25, w=5.6, border=True)
rect(s, 0.7, 0.75, 0.62, 0.62, INDIGO)
text(s, 0.7, 0.75, 0.62, 0.62, "☁", 22, WHITE, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE)
text(s, 1.5, 0.82, 5, 0.5, "Nimbus Drive", 22, WHITE, True)
text(s, 0.7, 1.9, 6.3, 2.6, "Nền tảng lưu trữ file đám mây trên kiến trúc tương thích Amazon S3", 36, WHITE, True)
text(s, 0.7, 4.25, 6.3, 1.2, "Design and Deployment of a Spring Boot Containerized Cloud File Storage Platform "
     "based on AWS S3-Compatible Architecture with an Automated CI/CD Pipeline", 14, RGBColor(0xD6, 0xB7, 0x84), italic=True)
text(s, 0.7, 5.75, 6.3, 1.0, ["Sinh viên: [Họ tên] · MSSV: [MSSV]", "GVHD: [Họ tên giảng viên] · Môn Điện toán đám mây"], 14, RGBColor(0xD5, 0xDD, 0xE1))
text(s, 7.3, 5.0, 5.6, 0.4, "Spring Boot 3.5 · PostgreSQL · S3 · Docker · Terraform · GitHub Actions", 12, RGBColor(0x8C, 0x9B, 0xA4), align=PP_ALIGN.CENTER)
num[0] += 1
notes(s, "Chào thầy cô và các bạn. Em xin trình bày đồ án Nimbus Drive – một nền tảng lưu trữ file trên cloud, "
         "xây dựng bằng Spring Boot, lưu file trên object storage chuẩn Amazon S3, đóng gói bằng Docker và triển khai tự động bằng CI/CD.")

# ---------------------------------------------------------------- 2. Agenda
s = content("Nội dung trình bày")
items = [("Bài toán & mục tiêu", "Vì sao cần, làm gì"), ("Kiến trúc hệ thống", "S3-compatible, phân lớp, CSDL"),
         ("Sản phẩm & demo", "Tính năng, giao diện"), ("Bảo mật", "Các mối đe dọa và biện pháp"),
         ("Container & CI/CD", "Docker, Compose, GitHub Actions"), ("Triển khai AWS & kết quả", "Terraform, kiểm thử, hướng phát triển")]
for i, (t, d) in enumerate(items):
    col, row = i % 3, i // 3
    x, y = 0.6 + col * 4.1, 1.85 + row * 2.45
    rect(s, x, y, 3.8, 2.05, CARD)
    circle(s, x + 0.3, y + 0.3, 0.62, [INDIGO, VIOLET, PINK][col], f"{i + 1:02d}", 15)
    text(s, x + 0.3, y + 1.08, 3.3, 0.45, t, 19, INK, True)
    text(s, x + 0.3, y + 1.5, 3.3, 0.4, d, 14, MUTED)
notes(s, "Bài trình bày gồm 6 phần: bài toán, kiến trúc, sản phẩm, bảo mật, container và CI/CD, cuối cùng là triển khai AWS và kết quả.")

# ---------------------------------------------------------------- 3. Problem & goals
s = content("Bài toán: lưu trữ dữ liệu linh hoạt, an toàn, tự động", "Bài toán & mục tiêu")
rect(s, 0.6, 1.8, 5.2, 4.9, SOFT)
text(s, 0.95, 2.1, 4.6, 0.4, "Thực trạng", 20, INDIGO, True)
bullets(s, 0.95, 2.65, 4.6, 3.9, [
    "Nhu cầu lưu trữ & chia sẻ file trực tuyến tăng mạnh",
    "Object storage chuẩn **S3** là nền tảng của mọi cloud drive",
    "Triển khai thủ công dễ lỗi, khó tái tạo môi trường",
    "Phụ thuộc một nhà cung cấp gây rủi ro (vendor lock-in)"], 16)
goals = [("Ứng dụng web hoàn chỉnh", "Upload, thư mục, xem trước, chia sẻ, quota", INDIGO),
         ("Kiến trúc tương thích S3", "Chạy trên AWS S3 hoặc S3 tự host, không đổi mã", PINK),
         ("Container hóa + IaC", "Docker Compose & Terraform cho AWS", VIOLET),
         ("CI/CD tự động", "Test → image → scan → deploy → smoke test", GREEN)]
for i, (t, d, c) in enumerate(goals):
    y = 1.8 + i * 1.25
    circle(s, 6.3, y + 0.1, 0.62, c, str(i + 1), 16)
    text(s, 7.15, y + 0.05, 5.6, 0.4, t, 19, INK, True)
    text(s, 7.15, y + 0.48, 5.6, 0.4, d, 14, MUTED)
notes(s, "Mục tiêu gồm bốn phần: một ứng dụng hoàn chỉnh; kiến trúc tương thích S3 để không bị khóa vào một nhà cung cấp; "
         "đóng gói container và hạ tầng dưới dạng mã; và pipeline CI/CD tự động hoàn toàn.")

s = content("Khảo sát: khoảng trống mà đề tài lấp đầy", "Bài toán & mục tiêu")
grid(s, 0.6, 1.75, [2.75, 2.45, 2.15, 2.0, 2.8],
     ["Tiêu chí", "Google Drive / Dropbox", "Nextcloud", "S3 Console", "Nimbus Drive"],
     [["Mô hình", "SaaS đóng", "Tự host (PHP)", "Công cụ quản trị", "Tự host hoặc AWS"],
      ["Kiểm soát dữ liệu", "Thấp", "Cao", "Cao", "Cao (S3 / SeaweedFS)"],
      ["User, quota, thư mục", "Có", "Có", "Không", "Có"],
      ["Chia sẻ link có hạn", "Có", "Có", "Không", "Có, thu hồi được"],
      ["Presigned URL tải trực tiếp", "Không công khai", "Tùy cấu hình", "Có", "Có (15 phút)"],
      ["Container + IaC + CI/CD", "Không áp dụng", "Cộng đồng", "Không áp dụng", "Có sẵn, đã chạy thật"]],
     size=14, row_h=0.64)
rect(s, 0.6, 6.15, 12.15, 0.6, SOFT)
text(s, 0.9, 6.15, 11.6, 0.6, "Định vị: hệ thống tham chiếu gọn (~2.000 dòng Java) để học và áp dụng kỹ thuật cloud, không phải bản sao Google Drive", 14, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Trước khi làm em khảo sát bốn nhóm giải pháp. Dịch vụ thương mại thì tiện nhưng dữ liệu nằm trong hệ sinh thái của họ. "
         "Nextcloud tự host được nhưng là khối PHP lớn, khó dùng để học kiến trúc cloud. S3 Console chỉ là công cụ quản trị bucket, không có user, thư mục hay chia sẻ. "
         "Nimbus Drive lấp khoảng trống ở giữa: đủ tính năng để dùng thật, đủ nhỏ để đọc hiểu toàn bộ mã nguồn, và có sẵn container, hạ tầng dưới dạng mã và CI/CD.")

s = content("Nền tảng: tám khái niệm của Amazon S3", "Kiến thức nền")
card_grid(s, [("Bucket", "Thùng chứa tên duy nhất toàn cầu, gắn với một region"),
          ("Object key", "Tên phẳng của object. Ở đây: owner/<uuid>, không dùng tên file"),
          ("Presigned URL", "URL ký sẵn SigV4, tự hết hạn: ủy quyền tạm thời cho trình duyệt"),
          ("Versioning", "Giữ nhiều phiên bản; lifecycle xóa bản cũ sau 30 ngày"),
          ("SSE-S3", "Mã hóa phía server AES-256, bật mặc định bằng Terraform"),
          ("Block Public Access", "Chặn mọi cấu hình công khai; chỉ IAM hoặc presigned URL"),
          ("Strong consistency", "Đọc ngay sau khi ghi luôn thấy dữ liệu mới (từ 12/2020)"),
          ("CORS + Lifecycle", "Cho phép GET/HEAD từ trình duyệt; hủy multipart dở sau 7 ngày")],
      4, ch=2.2, gy=0.25, dsize=13)
notes(s, "Tám khái niệm S3 mà đồ án dùng. Bucket là thùng chứa. Key là tên phẳng của object, em đặt owner gạch chéo uuid để không trùng và không lộ tên file. "
         "Presigned URL là URL đã ký sẵn có hạn, cho phép trình duyệt tải thẳng từ S3. Versioning, mã hóa SSE và chặn public được bật bằng Terraform. "
         "S3 có strong consistency nên đọc ngay sau khi ghi luôn đúng, giúp luồng upload rồi hiển thị ảnh thu nhỏ đơn giản hơn.")

# ---------------------------------------------------------------- 4. Architecture
s = content("Kiến trúc: tách metadata và nội dung file", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "architecture.png"), 0.6, 1.7, w=8.3, h=5.1)
cards = [("PostgreSQL", "Người dùng, thư mục ảo, metadata, link chia sẻ", RGBColor(0x3A, 0x6A, 0x96)),
         ("Object storage", "Nội dung file, key phẳng owner/uuid", PINK),
         ("Presigned URL", "Trình duyệt tải thẳng từ S3, giảm tải server", VIOLET)]
for i, (t, d, c) in enumerate(cards):
    y = 1.85 + i * 1.62
    rect(s, 9.2, y, 3.55, 1.4, CARD)
    text(s, 9.45, y + 0.2, 3.1, 0.4, t, 17, c, True)
    text(s, 9.45, y + 0.6, 3.1, 0.7, d, 14, MUTED)
notes(s, "Điểm mấu chốt của kiến trúc là tách metadata và nội dung. Metadata nằm ở PostgreSQL để truy vấn và phân quyền; "
         "nội dung nằm ở object storage. Thư mục là thư mục ảo nên đổi tên hay di chuyển không phải copy dữ liệu trên S3. "
         "Caddy lo HTTPS, Prometheus thu thập metrics.")

# ---------------------------------------------------------------- 5. S3 compatible
s = content("Ports & Adapters: một mã nguồn, nhiều nhà cung cấp S3", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "layers.png"), 0.6, 1.75, w=7.0, h=4.9)
bullets(s, 8.0, 1.95, 4.75, 3.0, [
    "Interface **ObjectStorage**: put · get · delete · presign",
    "**S3ObjectStorage** dùng AWS SDK v2 – chạy với AWS S3, SeaweedFS, Ceph, MinIO",
    "**LocalObjectStorage** cho dev/test không cần Docker",
    "Đổi backend chỉ bằng biến môi trường (12-factor)"], 16)
rect(s, 8.0, 5.05, 4.75, 1.6, RGBColor(0xF5, 0xEF, 0xE4))
text(s, 8.25, 5.2, 4.3, 0.35, "Bài học thực tế", 15, PINK, True)
text(s, 8.25, 5.58, 4.3, 1.0, "MinIO community ngừng phát hành bản dựng (410 Gone) → chuyển sang SeaweedFS mà không sửa dòng code nghiệp vụ nào.", 14, INK)
notes(s, "Tầng lưu trữ được trừu tượng hóa bằng interface ObjectStorage. Trong quá trình làm, MinIO bản cộng đồng đã ngừng phát hành, "
         "em chuyển sang SeaweedFS chỉ bằng cấu hình – đây là minh chứng rõ nhất cho lợi ích của kiến trúc tương thích S3.")

s = content("Mã nguồn gọn: ~2.000 dòng Java, ~1.400 dòng giao diện", "Kiến trúc hệ thống")
rect(s, 0.6, 1.75, 6.0, 5.0, NAVY)
tree = ["src/main/java/com/cloudfs/", "  controller/  Auth Me File Folder Share", "  service/     File Folder Share LoginAttempt",
        "  storage/     ObjectStorage S3 Local", "  security/    JwtService JwtAuthFilter", "  config/      StorageConfig OpenApi",
        "  web/         ApiExceptionHandler", "src/main/resources/", "  db/migration/V1__init.sql", "  static/  index app share .html  css/ js/",
        "src/test/  ApiFlowTest UnitTests *IT", "deploy/  seaweedfs caddy monitoring terraform", "Dockerfile  docker-compose(.aws).yml", ".github/workflows/ci-cd.yml"]
tb = text(s, 0.85, 2.0, 5.6, 4.6, tree, 12.5, RGBColor(0xDC, 0xE2, 0xE6))
for pp in tb.text_frame.paragraphs:
    pp.space_after = Pt(4)
    for r in pp.runs:
        r.font.name = "Consolas"
grid(s, 6.95, 1.75, [1.95, 3.85], ["Lớp", "Công nghệ"],
     [["Ngôn ngữ", "Java 21 · Spring Boot 3.5"], ["Bảo mật", "Spring Security · JJWT · BCrypt"],
      ["Dữ liệu", "JPA/Hibernate 6 · PostgreSQL 16 · Flyway"], ["Lưu trữ", "AWS SDK v2 (S3Client, S3Presigner)"],
      ["API docs", "springdoc-openapi 2.10"], ["Giám sát", "Actuator · Micrometer · Prometheus · Grafana"],
      ["Kiểm thử", "JUnit 5 · MockMvc · Testcontainers · JaCoCo"], ["Frontend", "HTML/CSS/JS thuần, không bước build"]],
     size=13, row_h=0.56)
notes(s, "Mã nguồn chia lớp rõ ràng: controller, service, storage, security, config. Công nghệ chọn theo hướng ít phụ thuộc: frontend là JavaScript thuần "
         "nên không có bước build và tuân thủ CSP chặt. Toàn bộ backend khoảng hai nghìn dòng Java nên có thể đọc hiểu hết trong một buổi.")

# ---------------------------------------------------------------- 6. ERD
s = content("Cơ sở dữ liệu: 4 bảng, quản lý bằng Flyway", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "erd.png"), 0.6, 1.7, w=8.2, h=5.1)
bullets(s, 9.1, 1.95, 3.7, 4.6, [
    "Thư mục dạng cây, parent_id **ON DELETE CASCADE**",
    "object_key duy nhất ánh xạ tới S3",
    "Token chia sẻ 192 bit, có hạn dùng",
    "Migration có phiên bản, chạy trên PostgreSQL & H2",
    "Index theo (owner, folder) cho truy vấn nhanh"], 15)
notes(s, "Cơ sở dữ liệu gồm users, folders, files và share_links. Schema được quản lý bằng Flyway thay vì để Hibernate tự sinh.")

# ---------------------------------------------------------------- 7. Features
s = content("Tính năng chính của Nimbus Drive", "Sản phẩm")
feats = [("Tài khoản JWT", "BCrypt, khóa tạm sau 5 lần sai"), ("Upload kéo-thả", "Nhiều file, thanh tiến độ, 100 MB/file"),
         ("Thư mục lồng nhau", "Breadcrumb, kéo file vào thư mục"), ("Xem trước", "Ảnh, PDF, video, audio, code"),
         ("Chia sẻ link", "Hết hạn 1h–30 ngày, đếm lượt, thu hồi"), ("Presigned URL", "Tải trực tiếp từ S3 15 phút"),
         ("Quota & thống kê", "1 GB/user, phân bố theo loại file"), ("Trải nghiệm", "Tìm kiếm, gắn sao, dark mode, mobile")]
cols = [INDIGO, VIOLET, PINK, GREEN]
for i, (t, d) in enumerate(feats):
    col, row = i % 4, i // 4
    x, y = 0.6 + col * 3.07, 1.85 + row * 2.45
    rect(s, x, y, 2.85, 2.15, CARD)
    circle(s, x + 0.25, y + 0.25, 0.55, cols[col], str(i + 1), 15)
    text(s, x + 0.25, y + 0.98, 2.4, 0.4, t, 17, INK, True)
    text(s, x + 0.25, y + 1.38, 2.4, 0.7, d, 14, MUTED)
notes(s, "Đây là tám nhóm tính năng chính. Tất cả đều được kiểm thử tự động qua API.")

# ---------------------------------------------------------------- 8. Demo 1
s = content("Giao diện quản lý file", "Demo")
picture(s, os.path.join(SHOT, "03-folder-images.png"), 0.6, 1.7, w=7.9, border=True)
picture(s, os.path.join(SHOT, "09-dark-mode.png"), 9.3, 1.7, w=3.45, border=True)
picture(s, os.path.join(SHOT, "12-mobile.png"), 10.3, 3.95, h=2.75, border=True)
text(s, 0.6, 6.5, 8.4, 0.35, "Lưới / danh sách · ảnh thu nhỏ · thống kê dung lượng · sáng/tối · responsive", 13, MUTED)
notes(s, "Giao diện viết bằng JavaScript thuần, không cần build. Có chế độ lưới, danh sách, ảnh thu nhỏ, dark mode và hiển thị tốt trên điện thoại.")

# ---------------------------------------------------------------- 9. Demo 2
s = content("Xem trước và chia sẻ file", "Demo")
picture(s, os.path.join(SHOT, "07-share-dialog.png"), 0.6, 1.7, w=6.0, border=True)
picture(s, os.path.join(SHOT, "10-public-share.png"), 6.75, 1.7, w=6.0, border=True)
text(s, 0.6, 5.45, 6.0, 0.35, "Tạo link có thời hạn, sao chép, thu hồi", 14, MUTED)
text(s, 6.75, 5.45, 6.0, 0.35, "Khách tải file không cần đăng nhập", 14, MUTED)
rect(s, 0.6, 5.95, 12.15, 0.8, SOFT)
text(s, 0.9, 5.95, 11.6, 0.8, "File HTML/SVG do người dùng tải lên luôn bị ép tải về (không hiển thị inline) để chống XSS", 15, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Người dùng tạo link chia sẻ có thời hạn; khách mở link sẽ thấy trang tải công khai, có xem trước ảnh.")

s = content("Danh sách, menu thao tác, link đã chia sẻ và Swagger", "Demo")
shots = [("05-list-view.png", "Chế độ danh sách + breadcrumb thư mục lồng nhau"), ("06-context-menu.png", "Menu: xem, tải, đổi tên, sao, chia sẻ, Link S3"),
         ("08-shared-links.png", "Quản lý link đã chia sẻ: lượt tải, hạn dùng, thu hồi"), ("11-swagger.png", "Swagger UI: tài liệu API tương tác")]
for i, (f, cap_) in enumerate(shots):
    col, row = i % 2, i // 2
    x, y = 0.6 + col * 6.2, 1.65 + row * 2.62
    picture(s, os.path.join(SHOT, f), x, y, w=5.95, h=2.15, border=True)
    text(s, x, y + 2.2, 5.95, 0.3, cap_, 12.5, MUTED)
notes(s, "Thêm bốn màn hình: chế độ danh sách với breadcrumb, menu ngữ cảnh có mục Link S3 tạm thời, trang quản lý các link đã chia sẻ cho biết số lượt tải và hạn dùng, "
         "và Swagger UI để thử API trực tiếp.")

# ---------------------------------------------------------------- 10. Sequence
s = content("Luồng upload và tải qua presigned URL", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "sequence.png"), 0.6, 1.65, w=8.0, h=5.2)
steps = [("1", "Xác thực & kiểm tra quota", INDIGO), ("2", "PutObject lên S3 trước", PINK),
         ("3", "Ghi metadata; lỗi → xóa object", RGBColor(0x3A, 0x6A, 0x96)), ("4", "Xóa: commit DB rồi mới xóa object", GREEN)]
for i, (n, t, c) in enumerate(steps):
    y = 1.9 + i * 1.2
    circle(s, 8.95, y, 0.55, c, n, 15)
    text(s, 9.7, y, 3.1, 0.55, t, 15, INK, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Thứ tự ghi được thiết kế để không mất dữ liệu: khi upload ghi S3 trước rồi mới ghi DB, nếu DB lỗi thì xóa object; "
         "khi xóa thì commit DB xong mới xóa object.")

s = content("Hai cách phục vụ file: qua server và presigned URL", "Kiến trúc hệ thống")
grid(s, 0.6, 1.75, [3.0, 4.6, 4.55], ["Tiêu chí", "Tải qua máy chủ ứng dụng", "Presigned URL tới S3"],
     [["Đường đi dữ liệu", "S3 → app → trình duyệt", "S3 → trình duyệt (trực tiếp)"],
      ["Tải cho app", "Cao: app stream toàn bộ file", "Gần như bằng 0"],
      ["Kiểm soát quyền", "Mỗi request qua JWT filter", "Quyền nằm trong URL, hết hạn 15 phút"],
      ["Yêu cầu hạ tầng", "Chỉ mở cổng app", "Trình duyệt thấy S3 + cấu hình CORS"],
      ["Dùng trong đề tài", "Tải xuống, xem trước, link chia sẻ", "Menu “Link S3 tạm thời”, API /presign"]],
     size=14, row_h=0.72)
rect(s, 0.6, 5.95, 12.15, 0.8, SOFT)
text(s, 0.9, 5.95, 11.6, 0.8, "Backend local (dev) không có S3 → /presign trả 501; có test kiểm tra hành vi này", 15, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Hệ thống hỗ trợ hai cách phục vụ nội dung. Đi qua ứng dụng thì mọi truy cập đều được xác thực và đếm lượt nhưng tốn băng thông của server. "
         "Presigned URL cho trình duyệt tải thẳng từ S3, server gần như không tốn gì, quyền được ký vào URL và tự hết hạn sau 15 phút. "
         "Khi chạy chế độ dev với ổ đĩa cục bộ, API presign trả 501 và có test cho trường hợp này.")

s = content("Xác thực JWT: stateless, mọi truy vấn lọc theo owner", "Bảo mật")
picture(s, os.path.join(FIG, "auth_flow.png"), 0.6, 1.75, w=8.4, h=4.9)
bullets(s, 9.3, 1.95, 3.5, 4.7, [
    "Token **HMAC-SHA**, hạn **24 giờ**, khóa ≥ 32 ký tự từ biến môi trường",
    "Không lưu session → chạy nhiều instance dễ",
    "Sai **5 lần** → khóa **5 phút** (HTTP 429)",
    "Tài nguyên người khác → **404**, không lộ sự tồn tại",
    "BCrypt: có muối, cố ý chậm"], 14.5)
notes(s, "Đăng nhập xong server ký một JWT. Mỗi request sau đó mang token, filter xác minh chữ ký và hạn rồi lấy tên người dùng làm principal; "
         "mọi service nhận tên này làm tham số owner nên không thể đọc dữ liệu của người khác. Sai mật khẩu năm lần thì khóa năm phút. "
         "Nhược điểm của JWT là khó thu hồi trước hạn, nên refresh token nằm trong hướng phát triển.")

s = content("Vòng đời của link chia sẻ công khai", "Bảo mật")
picture(s, os.path.join(FIG, "share_state.png"), 0.6, 1.75, w=8.2, h=4.6)
bullets(s, 9.1, 1.95, 3.7, 4.7, [
    "Token **192 bit** (SecureRandom, Base64URL): không thể đoán",
    "Hạn: 1 giờ · 1 ngày · 7 ngày · 30 ngày · vô hạn",
    "Thu hồi bất cứ lúc nào; xóa file thì link xóa theo (CASCADE)",
    "Trang công khai chỉ trả tên, kích thước, loại file",
    "Hết hạn → **410**, thu hồi → **404**"], 14.5)
notes(s, "Link công khai là ngoại lệ duy nhất của nguyên tắc mọi thứ phải đăng nhập, nên được thiết kế chặt: token 192 bit, có hạn, thu hồi được, "
         "và trang công khai chỉ lộ thông tin tối thiểu. Hết hạn trả 410, đã thu hồi trả 404.")

# ---------------------------------------------------------------- 11. Security
s = content("Bảo mật nhiều lớp", "Bảo mật")
sec = [("Xác thực", "JWT HS256 · BCrypt · khóa 5 phút sau 5 lần sai (429)"),
       ("Phân quyền", "Mọi truy vấn lọc theo owner; tài nguyên người khác → 404"),
       ("Chống XSS", "CSP script-src 'self'; file HTML/SVG không hiển thị inline"),
       ("Đầu vào", "Làm sạch tên file, chặn path traversal, validate DTO"),
       ("Hạ tầng AWS", "IAM role quyền tối thiểu, IMDSv2, không lưu access key"),
       ("Dữ liệu", "S3 chặn public, mã hóa SSE, versioning; HTTPS qua Caddy")]
for i, (t, d) in enumerate(sec):
    col, row = i % 2, i // 2
    x, y = 0.6 + col * 6.15, 1.8 + row * 1.65
    rect(s, x, y, 5.95, 1.45, CARD)
    circle(s, x + 0.25, y + 0.42, 0.6, [INDIGO, PINK][col], "✓", 16)
    text(s, x + 1.1, y + 0.25, 4.6, 0.4, t, 18, INK, True)
    text(s, x + 1.1, y + 0.7, 4.6, 0.65, d, 14, MUTED)
notes(s, "Bảo mật được áp dụng ở cả tầng ứng dụng lẫn hạ tầng: xác thực, phân quyền, chống XSS, kiểm soát đầu vào, IAM quyền tối thiểu và mã hóa dữ liệu.")

s = content("Phòng thủ nhiều lớp: bốn vòng bảo vệ", "Bảo mật")
picture(s, os.path.join(FIG, "defense.png"), 0.6, 1.7, w=7.6, h=5.0)
rect(s, 8.5, 1.85, 4.25, 4.8, CARD)
text(s, 8.8, 2.05, 3.7, 0.4, "Chống XSS ở hai tầng", 17, INDIGO, True)
bullets(s, 8.8, 2.6, 3.7, 4.0, [
    "Giao diện: CSP **script-src 'self'**, không inline script, escape dữ liệu khi render",
    "File người dùng: chỉ ảnh/PDF/text/video/audio được xem inline; **HTML, SVG ép tải về**",
    "Response file kèm CSP **sandbox** + default-src 'none'",
    "Tên file bị làm sạch: bỏ đường dẫn, ký tự điều khiển"], 13.5)
notes(s, "Bốn lớp bảo vệ từ ngoài vào trong: hạ tầng AWS, biên Caddy, ứng dụng Spring và dữ liệu. Riêng XSS được chặn ở hai tầng: "
         "CSP chặt cho giao diện, và với file người dùng tải lên thì HTML, SVG luôn bị ép tải về, response còn kèm CSP sandbox.")

# ---------------------------------------------------------------- 12. Containers
s = content("Container hóa với Docker & Docker Compose", "Container & CI/CD")
rect(s, 0.6, 1.8, 5.6, 4.9, NAVY)
code = ["# Stage 1: build (JDK + Maven, cache .m2)", "FROM maven:3.9-eclipse-temurin-21 AS build", "RUN mvn package && java -Djarmode=tools \\",
        "    -jar app.jar extract --layers", "", "# Stage 2: runtime (chỉ JRE)", "FROM eclipse-temurin:21-jre",
        "COPY dependencies/ application/ ./", "USER appuser        # không chạy root", "HEALTHCHECK curl /actuator/health/liveness"]
tb = text(s, 0.9, 2.05, 5.1, 4.5, code, 13, RGBColor(0xDC, 0xE2, 0xE6))
for p in tb.text_frame.paragraphs:
    for r in p.runs:
        r.font.name = "Consolas"
svc = [("app", "Spring Boot · chờ DB & S3 healthy", VIOLET), ("postgres", "PostgreSQL 16 · volume", RGBColor(0x3A, 0x6A, 0x96)),
       ("seaweedfs", "S3 API :8333 tự host", PINK), ("caddy", "HTTPS tự động (profile proxy)", AMBER),
       ("prometheus + grafana", "Giám sát (profile monitoring)", GREEN)]
for i, (t, d, c) in enumerate(svc):
    y = 1.8 + i * 0.99
    rect(s, 6.6, y, 6.15, 0.85, CARD)
    circle(s, 6.8, y + 0.17, 0.5, c, "", 10)
    text(s, 7.5, y + 0.1, 5.0, 0.35, t, 16, INK, True)
    text(s, 7.5, y + 0.46, 5.0, 0.35, d, 13, MUTED)
notes(s, "Dockerfile nhiều giai đoạn, tách layer để build nhanh, chạy bằng user thường và có healthcheck. "
         "Docker Compose dựng cả hệ thống bằng một lệnh, có profile tùy chọn cho HTTPS và giám sát.")

s = content("Cấu hình 12-factor: đổi môi trường chỉ bằng biến", "Container & CI/CD")
grid(s, 0.6, 1.75, [3.4, 3.0, 5.75], ["Biến", "Mặc định", "Ý nghĩa"],
     [["S3_ENDPOINT", "localhost:8333", "Trống = Amazon S3 thật; có giá trị = S3 tự host"],
      ["S3_ACCESS_KEY / SECRET", "nimbus…", "Trống trên AWS → dùng IAM instance profile"],
      ["S3_PATH_STYLE", "true", "true cho SeaweedFS/MinIO, false cho Amazon S3"],
      ["S3_PUBLIC_ENDPOINT", "= endpoint", "Địa chỉ trình duyệt thấy, để ký presigned URL"],
      ["STORAGE_TYPE", "s3", "s3 hoặc local (thư mục, chỉ dev)"],
      ["JWT_SECRET", "phải đổi", "Khóa ký token ≥ 32 ký tự; Terraform sinh ngẫu nhiên"],
      ["DEFAULT_QUOTA_BYTES", "1 GB", "Hạn mức mỗi người dùng"],
      ["MAX_FILE_SIZE", "100 MB", "Kích thước tối đa mỗi file"]],
     size=13.5, row_h=0.55)
notes(s, "Toàn bộ cấu hình đi qua biến môi trường theo nguyên tắc 12-factor. Chuyển từ SeaweedFS sang Amazon S3 chỉ là để trống endpoint và key, "
         "đặt path-style về false; ứng dụng tự dùng IAM role. Khóa JWT được Terraform sinh ngẫu nhiên khi dựng hạ tầng.")

# ---------------------------------------------------------------- 13. CI/CD
s = content("Pipeline CI/CD tự động với GitHub Actions", "Container & CI/CD")
picture(s, os.path.join(FIG, "cicd.png"), 0.6, 1.65, w=12.15)
stats = [("3", "job: test → image → deploy"), ("2", "tag: latest + sha-<commit>"), ("90s", "smoke test sau deploy")]
for i, (n, d) in enumerate(stats):
    x = 0.6 + i * 4.1
    rect(s, x, 5.35, 3.85, 1.35, SOFT)
    text(s, x + 0.3, 5.42, 1.4, 1.2, n, 40, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
    text(s, x + 1.6, 5.42, 2.1, 1.2, d, 14, INK, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Mỗi lần push, pipeline chạy test kể cả integration test bằng Testcontainers, build image, quét lỗ hổng bằng Trivy, "
         "đẩy lên GHCR và tự deploy lên server, sau đó smoke test endpoint health. Image gắn tag theo commit nên rollback dễ dàng.")

s = content("CI/CD chi tiết: cấu hình và thực hành an toàn", "Container & CI/CD")
rect(s, 0.6, 1.8, 5.9, 4.9, SOFT)
text(s, 0.9, 2.0, 5.3, 0.4, "Cấu hình GitHub cho deploy", 19, INDIGO, True)
grid(s, 0.85, 2.55, [2.35, 3.2], ["Tên", "Mục đích"],
     [["DEPLOY_ENABLED", "true = bật job deploy"], ["APP_URL", "URL môi trường production"], ["DEPLOY_HOST", "Elastic IP của EC2"],
      ["DEPLOY_USER", "ubuntu"], ["DEPLOY_SSH_KEY", "Khóa riêng, không commit"], ["GITHUB_TOKEN", "pull/push GHCR"]],
     size=12, row_h=0.5, head_h=0.45)
text(s, 6.9, 2.0, 5.8, 0.4, "Thực hành an toàn trong pipeline", 19, INDIGO, True)
bullets(s, 6.9, 2.6, 5.85, 4.1, [
    "**concurrency** hủy lần chạy cũ khi có push mới",
    "Action Trivy **ghim theo SHA**, không dùng tag",
    "Bước kiểm tra **skipped = 0**: integration test bị bỏ qua thì pipeline đỏ",
    "Quyền tối thiểu: job image ghi packages, job deploy chỉ đọc",
    "Environment **production** có thể bật duyệt thủ công",
    "Cache Buildx (type=gha) rút ngắn thời gian build",
    "Báo cáo test + JaCoCo lưu 14 ngày"], 14.5)
notes(s, "Chi tiết pipeline: bên trái là các biến và secret cần cấu hình, đặc biệt DEPLOY_ENABLED dùng như công tắc. Bên phải là các thực hành an toàn: "
         "ghim action theo SHA, cấp quyền tối thiểu cho từng job, và đặc biệt là bước kiểm tra integration test không bị bỏ qua - "
         "đây là bài học từ lần CI đầu tiên báo xanh giả.")

# ---------------------------------------------------------------- 14. AWS
s = content("Triển khai trên AWS bằng Terraform", "Triển khai AWS")
picture(s, os.path.join(FIG, "aws.png"), 0.6, 1.65, w=7.9, h=5.2)
bullets(s, 8.85, 1.9, 3.9, 4.8, [
    "**S3**: chặn public, SSE-AES256, versioning, lifecycle, CORS",
    "**IAM role** chỉ Get/Put/Delete trên 1 bucket",
    "**EC2** Ubuntu 24.04, IMDSv2, EBS mã hóa, Elastic IP",
    "user_data cài Docker, sinh secret ngẫu nhiên",
    "Đã chạy thật ở Sydney: upload, S3, presigned URL, chia sẻ đều đạt"], 15)
notes(s, "Toàn bộ hạ tầng AWS được mô tả bằng Terraform. Trên AWS ứng dụng dùng Amazon S3 thật, lấy credential tạm thời từ IAM role nên không cần lưu access key. "
         "Hệ thống đã chạy thật ở vùng Sydney và được kiểm thử đầu cuối qua Internet.")

s = content("Quy trình triển khai từ đầu đến cuối: sáu bước", "Triển khai AWS")
steps6 = [("Đăng nhập AWS", "aws login, tạo key pair EC2"), ("Khai báo biến", "terraform.tfvars: key_name, app_image, region"),
          ("terraform apply", "S3, IAM, Security Group, EC2, Elastic IP"), ("Cấu hình GitHub", "Secrets + DEPLOY_ENABLED=true"),
          ("git push main", "Test → image → Trivy → GHCR → deploy"), ("Kiểm tra", "/actuator/health = UP, đăng ký và upload thử")]
card_grid(s, steps6, 3, ch=2.0, gy=0.25, cw=3.9, gx=0.225, tsize=18, dsize=14)
rect(s, 0.6, 6.3, 12.15, 0.55, SOFT)
text(s, 0.9, 6.3, 11.6, 0.55, "Rollback: chạy lại workflow ở commit cũ (tag sha-<commit>) · Dọn dẹp: terraform destroy", 14, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Sáu bước từ đầu đến cuối. Đăng nhập AWS, khai báo biến, terraform apply dựng hạ tầng, cấu hình GitHub, push lên main để pipeline tự deploy, "
         "rồi kiểm tra health. Rollback chỉ là deploy lại tag commit cũ vì dữ liệu nằm ở volume PostgreSQL và bucket S3. Khi không dùng thì terraform destroy để không tốn chi phí.")

s = content("Giám sát: Prometheus + Grafana, 8 chỉ số chính", "Triển khai AWS")
card_grid(s, [("Uptime", "up của job nimbus-drive"), ("Request / giây", "http_server_requests"), ("Độ trễ p95", "histogram phân vị 95"),
          ("Lỗi 5xx (5 phút)", "tỷ lệ lỗi phía server"), ("File đã upload", "nimbus.files.uploaded"), ("Bytes upload", "nimbus.files.upload.bytes"),
          ("JVM heap", "jvm_memory_used_bytes"), ("Kết nối DB", "hikaricp_connections_active")],
      4, ch=2.05, gy=0.25, dsize=13)
text(s, 0.6, 6.55, 12.1, 0.35, "Scrape 15 giây qua /actuator/prometheus (Caddy chặn từ Internet) · Docker healthcheck: /actuator/health/liveness", 12.5, MUTED)
notes(s, "Dashboard Grafana có tám chỉ số: tình trạng sống, lưu lượng, độ trễ p95, tỷ lệ lỗi 5xx, hai metric nghiệp vụ do em tự đăng ký là số file và số byte upload, "
         "heap JVM và số kết nối cơ sở dữ liệu. Prometheus scrape mỗi 15 giây và endpoint metrics bị Caddy chặn từ Internet.")

# ---------------------------------------------------------------- 15. Results
s = content("Kết quả kiểm thử", "Kết quả")
big = [("22/22", "ca test đạt trên CI", INDIGO), ("~80%", "độ phủ dòng (JaCoCo)", VIOLET), ("22", "REST endpoint", PINK), ("5", "service container", GREEN)]
for i, (n, d, c) in enumerate(big):
    x = 0.6 + i * 3.07
    rect(s, x, 1.85, 2.85, 2.0, CARD)
    text(s, x, 2.0, 2.85, 1.1, n, 46, c, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE)
    text(s, x, 3.1, 2.85, 0.5, d, 15, MUTED, align=PP_ALIGN.CENTER)
rows = [("Unit test", "Tên file, phân loại, token, khóa đăng nhập, JWT"),
        ("API test (MockMvc + H2)", "Luồng đầy đủ: cô lập user, quota, thư mục đệ quy, chia sẻ, chống XSS"),
        ("Integration (Testcontainers)", "PostgreSQL 16 và SeaweedFS S3 thật – đạt trên GitHub Actions")]
for i, (t, d) in enumerate(rows):
    y = 4.25 + i * 0.82
    rect(s, 0.6, y, 12.15, 0.68, SOFT if i % 2 == 0 else WHITE)
    text(s, 0.9, y, 3.8, 0.68, t, 15, INK, True, anchor=MSO_ANCHOR.MIDDLE)
    text(s, 4.8, y, 7.8, 0.68, d, 14, MUTED, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Cả 22 ca kiểm thử đều đạt trên GitHub Actions, gồm 4 ca integration với PostgreSQL và S3 thật; độ phủ dòng khoảng 80%.")

s = content("22 ca kiểm thử: từ unit đến hệ thống thật", "Kết quả")
grid(s, 0.6, 1.75, [3.5, 0.9, 7.75], ["Lớp test", "Ca", "Điều được kiểm chứng"],
     [["JwtServiceTest", "3", "Token đúng / sai khóa / hết hạn"],
      ["UnitTests", "4", "Làm sạch tên file, phân loại, token chia sẻ, khóa đăng nhập"],
      ["ApiFlowTest (MockMvc + H2)", "11", "401, validate, 409, khóa 429, vòng đời file, cô lập user, quota 413, thư mục đệ quy, chia sẻ, chống XSS, presign 501, health"],
      ["PostgresMigrationIT", "1", "Flyway + Hibernate trên PostgreSQL 16 thật"],
      ["S3ObjectStorageIT", "3", "Put/Get/Delete, presigned URL không cần credential, tạo bucket idempotent (SeaweedFS thật)"]],
     size=13.5, row_h=0.68)
for i, (n, d, c) in enumerate([("22/22", "đạt trên GitHub Actions", INDIGO), ("18", "chạy được mọi máy", VIOLET), ("4", "cần Docker (Testcontainers)", PINK)]):
    x = 0.6 + i * 4.1
    rect(s, x, 6.0, 3.85, 0.85, SOFT)
    text(s, x + 0.2, 6.0, 1.6, 0.85, n, 26, c, True, anchor=MSO_ANCHOR.MIDDLE)
    text(s, x + 1.8, 6.0, 1.95, 0.85, d, 12.5, INK, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Chi tiết hai mươi hai ca: ba ca JWT, bốn ca unit, mười một ca API chạy bằng MockMvc, một ca migration trên PostgreSQL thật và ba ca S3 trên SeaweedFS thật. "
         "Mười tám ca đầu chạy được trên mọi máy; bốn ca integration cần Docker và đã chạy thật trên GitHub Actions.")

s = content("Sự cố thật và bài học rút ra", "Kết quả")
grid(s, 0.6, 1.7, [2.85, 4.45, 4.85], ["Sự cố", "Nguyên nhân gốc", "Khắc phục / bài học"],
     [["MinIO không tải được", "Bản community ngừng phát hành (410 Gone)", "Chuyển SeaweedFS chỉ bằng cấu hình"],
      ["CI xanh giả", "Testcontainers 1.19 bỏ qua 4 ca integration", "Nâng 1.21.4 + bước kiểm tra skipped = 0"],
      ["Không lấy được credential IAM", "IMDSv2 hop limit = 1, container cần 2", "http_put_response_hop_limit = 2"],
      ["Không pull được image GHCR", "Job deploy thiếu quyền packages: read", "Thêm quyền + docker login bằng GITHUB_TOKEN"],
      ["Trivy: 65 lỗ hổng HIGH/CRITICAL", "Spring Boot 3.3 + thư viện cũ", "Lên Boot 3.5.16, override phiên bản, apt upgrade → còn 2"],
      ["Giao diện thiếu nhất quán", "Bộ màu indigo – hồng, thiếu phân cấp", "Design token: mực – sâm panh, serif, sáng/tối"]],
     size=13.5, row_h=0.74)
notes(s, "Sáu sự cố thật và bài học. MinIO ngừng phát hành bản dựng nên chuyển SeaweedFS. CI báo xanh giả vì Testcontainers bỏ qua test nên em thêm bước chặn. "
         "IMDSv2 hop limit làm container không lấy được credential. Thiếu quyền đọc package làm server không pull được image. "
         "Trivy ban đầu báo 65 lỗ hổng, sau khi nâng phiên bản còn 2 lỗi CRITICAL của spring-webmvc chỉ vá ở Spring 7. Cuối cùng là thiết kế lại giao diện bằng design token.")

s = content("Sáu quyết định thiết kế và đánh đổi", "Kết luận")
grid(s, 0.6, 1.7, [2.6, 4.4, 5.15], ["Vấn đề", "Chọn", "Đánh đổi"],
     [["Nơi lưu file", "Object storage chuẩn S3", "Độ trễ cao hơn ổ đĩa cục bộ"],
      ["Thư mục", "Thư mục ảo trong PostgreSQL", "Xóa đệ quy xử lý ở tầng service"],
      ["Xác thực", "JWT stateless", "Khó thu hồi token trước hạn"],
      ["Thứ tự ghi", "S3 trước DB; xóa DB trước S3", "Có thể sót object mồ côi nếu app sập"],
      ["Frontend", "JavaScript thuần, không build", "Mã giao diện dài hơn framework"],
      ["Hạ tầng AWS", "1 EC2 + S3 qua Terraform", "Không Multi-AZ; DB là điểm lỗi đơn"]],
     size=13.5, row_h=0.74)
notes(s, "Sáu quyết định chính và cái giá phải trả. Điểm đáng nhấn mạnh là thứ tự ghi: vì không có giao dịch phân tán giữa S3 và PostgreSQL, "
         "em chọn thứ tự sao cho lỗi ở bước nào cũng không để lại metadata trỏ tới object không tồn tại.")

s = content("Chi phí vận hành trên AWS và cách tối ưu", "Kết luận")
grid(s, 0.6, 1.75, [2.9, 4.6, 4.65], ["Thành phần", "Cách tính", "Tối ưu"],
     [["EC2 t3.small", "Theo giờ chạy", "Dừng máy khi không demo"],
      ["EBS gp3 20 GB", "GB-tháng, tính cả khi máy dừng", "Xóa khi terraform destroy"],
      ["IPv4 công khai / EIP", "Tính phí theo giờ từ 02/2024", "Giải phóng khi không dùng"],
      ["Amazon S3", "GB lưu + request + dữ liệu ra", "Lifecycle; presigned URL giảm lưu lượng"],
      ["GitHub Actions + GHCR", "Trong hạn mức repo công khai", "Cache Buildx"]],
     size=14, row_h=0.66)
rect(s, 0.6, 5.85, 12.15, 0.9, SOFT)
text(s, 0.9, 5.85, 11.6, 0.9, "Không nêu đơn giá vì thay đổi theo vùng và thời điểm: tính bằng AWS Pricing Calculator. Biện pháp hiệu quả nhất: hạ tầng dưới dạng mã → dựng và hủy bằng một lệnh.", 14, INDIGO, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Chi phí chủ yếu đến từ EC2 và EBS, ngoài ra từ năm 2024 AWS tính phí cả địa chỉ IPv4 công khai. Em không nêu con số vì giá thay đổi theo vùng và thời điểm. "
         "Nhờ Terraform, hạ tầng dựng và hủy bằng một lệnh nên rất phù hợp bài toán học tập có ngân sách hạn chế.")

# ---------------------------------------------------------------- 16. Future
s = content("Hạn chế và hướng phát triển", "Kết luận")
rect(s, 0.6, 1.8, 5.2, 4.9, RGBColor(0xF9, 0xEC, 0xEA))
text(s, 0.95, 2.05, 4.6, 0.4, "Hạn chế", 20, RGBColor(0xB3, 0x36, 0x2F), True)
bullets(s, 0.95, 2.6, 4.6, 4.0, [
    "Upload qua server, giới hạn 100 MB/file",
    "Rate-limit lưu RAM, chưa dùng chung nhiều instance",
    "PostgreSQL chung EC2, chưa backup tự động",
    "Còn 2 lỗ hổng cần Spring Boot 4; AWS chưa có HTTPS"], 15)
fut = [("Upload trực tiếp S3", "Presigned PUT + multipart, file nhiều GB"), ("Thùng rác & phiên bản", "Xóa mềm, khôi phục qua S3 versioning"),
       ("Managed services", "RDS Multi-AZ, ECS Fargate, CloudFront"), ("Bảo mật nâng cao", "OAuth2 Google, 2FA, quét virus, Redis")]
for i, (t, d) in enumerate(fut):
    y = 1.8 + i * 1.25
    circle(s, 6.3, y + 0.1, 0.6, [INDIGO, VIOLET, PINK, GREEN][i], "→", 16)
    text(s, 7.15, y + 0.05, 5.6, 0.4, t, 18, INK, True)
    text(s, 7.15, y + 0.48, 5.6, 0.4, d, 14, MUTED)
notes(s, "Em nhìn nhận một số hạn chế và đề xuất hướng phát triển, ưu tiên cao nhất là upload trực tiếp lên S3 và thùng rác/phiên bản file.")

s = content("Kết luận và bài học kinh nghiệm", "Kết luận")
lessons = [("Chuẩn mở là bảo hiểm", "Bám chuẩn S3 + port/adapter → đổi MinIO sang SeaweedFS, local sang AWS không sửa nghiệp vụ", INDIGO),
           ("Thứ tự thao tác > giao dịch phân tán", "Với hai kho dữ liệu, chọn đúng thứ tự ghi/xóa đơn giản và hiệu quả", VIOLET),
           ("Kiểm thử trên môi trường giống thật", "Lỗi hop limit và Testcontainers bị bỏ qua chỉ lộ khi chạy trên AWS / CI", PINK),
           ("Bảo mật là quá trình liên tục", "Trivy: 65 → 2 lỗ hổng sau nâng cấp; cần quy trình cập nhật định kỳ", GREEN),
           ("Hạ tầng dưới dạng mã", "Dựng, kiểm thử, hủy cả môi trường AWS bằng một lệnh", AMBER)]
for i, (t, d, c) in enumerate(lessons):
    y = 1.8 + i * 0.98
    rect(s, 0.6, y, 12.15, 0.86, CARD)
    circle(s, 0.85, y + 0.14, 0.58, c, str(i + 1), 15)
    text(s, 1.7, y, 4.4, 0.86, t, 17, INK, True, anchor=MSO_ANCHOR.MIDDLE)
    text(s, 6.2, y, 6.4, 0.86, d, 13.5, MUTED, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Tổng kết năm bài học: chuẩn mở là bảo hiểm trước rủi ro nhà cung cấp; thứ tự thao tác quan trọng hơn giao dịch phân tán; kiểm thử phải giống môi trường thật; "
         "bảo mật là quá trình liên tục; và hạ tầng dưới dạng mã giúp tái tạo cũng như dọn dẹp dễ dàng.")

# ---------------------------------------------------------------- 17. Thanks
s = prs.slides.add_slide(BLANK)
bg(s, NAVY)
text(s, 0.7, 1.6, 11.9, 1.0, "Cảm ơn thầy cô và các bạn đã lắng nghe", 40, WHITE, True, PP_ALIGN.CENTER)
text(s, 0.7, 2.75, 11.9, 0.6, "Hỏi & đáp · Demo trực tiếp", 20, RGBColor(0xD6, 0xB7, 0x84), align=PP_ALIGN.CENTER)
demo = [("docker compose up -d --build", "Chạy toàn bộ hệ thống"), ("http://localhost:8080", "Giao diện web"),
        ("/swagger-ui.html", "Tài liệu API"), ("./mvnw verify", "Chạy kiểm thử")]
for i, (c, d) in enumerate(demo):
    x = 0.7 + i * 3.0
    rect(s, x, 4.1, 2.8, 1.6, RGBColor(0x1A, 0x3C, 0x4E))
    tb = text(s, x + 0.1, 4.3, 2.6, 0.7, c, 11.5, RGBColor(0xD6, 0xB7, 0x84), True, PP_ALIGN.CENTER)
    for r in tb.text_frame.paragraphs[0].runs:
        r.font.name = "Consolas"
    text(s, x + 0.2, 5.05, 2.4, 0.5, d, 14, RGBColor(0xD5, 0xDD, 0xE1), align=PP_ALIGN.CENTER)
num[0] += 1
notes(s, "Em xin cảm ơn. Em sẵn sàng demo trực tiếp và trả lời câu hỏi.")

prs.save(OUT)
print("saved", OUT, len(prs.slides), "slides")
