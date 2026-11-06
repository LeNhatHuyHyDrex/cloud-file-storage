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

NAVY = RGBColor(0x0F, 0x17, 0x2A)
INDIGO = RGBColor(0x4F, 0x46, 0xE5)
VIOLET = RGBColor(0x7C, 0x3A, 0xED)
PINK = RGBColor(0xDB, 0x27, 0x77)
GREEN = RGBColor(0x16, 0xA3, 0x4A)
AMBER = RGBColor(0xD9, 0x77, 0x06)
INK = RGBColor(0x0F, 0x17, 0x2A)
MUTED = RGBColor(0x47, 0x55, 0x69)
SOFT = RGBColor(0xEE, 0xF2, 0xFF)
CARD = RGBColor(0xF5, 0xF7, 0xFB)
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
        clr = etree.SubElement(bu, qn("a:srgbClr")); clr.set("val", "7C3AED")
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
        pic.line.color.rgb = RGBColor(0xE2, 0xE8, 0xF0)
        pic.line.width = Pt(1)
    return pic


def footer(slide, dark=False):
    num[0] += 1
    c = RGBColor(0x94, 0xA3, 0xB8)
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
     "based on AWS S3-Compatible Architecture with an Automated CI/CD Pipeline", 14, RGBColor(0xC7, 0xD2, 0xFE), italic=True)
text(s, 0.7, 5.75, 6.3, 1.0, ["Sinh viên: [Họ tên] · MSSV: [MSSV]", "GVHD: [Họ tên giảng viên] · Môn Điện toán đám mây"], 14, RGBColor(0xCB, 0xD5, 0xE1))
text(s, 7.3, 5.0, 5.6, 0.4, "Spring Boot 3 · PostgreSQL · S3 · Docker · Terraform · GitHub Actions", 12, RGBColor(0x94, 0xA3, 0xB8), align=PP_ALIGN.CENTER)
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

# ---------------------------------------------------------------- 4. Architecture
s = content("Kiến trúc: tách metadata và nội dung file", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "architecture.png"), 0.6, 1.7, w=8.3, h=5.1)
cards = [("PostgreSQL", "Người dùng, thư mục ảo, metadata, link chia sẻ", RGBColor(0x25, 0x63, 0xEB)),
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
rect(s, 8.0, 5.05, 4.75, 1.6, RGBColor(0xFD, 0xF2, 0xF8))
text(s, 8.25, 5.2, 4.3, 0.35, "Bài học thực tế", 15, PINK, True)
text(s, 8.25, 5.58, 4.3, 1.0, "MinIO community ngừng phát hành bản dựng (410 Gone) → chuyển sang SeaweedFS mà không sửa dòng code nghiệp vụ nào.", 14, INK)
notes(s, "Tầng lưu trữ được trừu tượng hóa bằng interface ObjectStorage. Trong quá trình làm, MinIO bản cộng đồng đã ngừng phát hành, "
         "em chuyển sang SeaweedFS chỉ bằng cấu hình – đây là minh chứng rõ nhất cho lợi ích của kiến trúc tương thích S3.")

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

# ---------------------------------------------------------------- 10. Sequence
s = content("Luồng upload và tải qua presigned URL", "Kiến trúc hệ thống")
picture(s, os.path.join(FIG, "sequence.png"), 0.6, 1.65, w=8.0, h=5.2)
steps = [("1", "Xác thực & kiểm tra quota", INDIGO), ("2", "PutObject lên S3 trước", PINK),
         ("3", "Ghi metadata; lỗi → xóa object", RGBColor(0x25, 0x63, 0xEB)), ("4", "Xóa: commit DB rồi mới xóa object", GREEN)]
for i, (n, t, c) in enumerate(steps):
    y = 1.9 + i * 1.2
    circle(s, 8.95, y, 0.55, c, n, 15)
    text(s, 9.7, y, 3.1, 0.55, t, 15, INK, True, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Thứ tự ghi được thiết kế để không mất dữ liệu: khi upload ghi S3 trước rồi mới ghi DB, nếu DB lỗi thì xóa object; "
         "khi xóa thì commit DB xong mới xóa object.")

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

# ---------------------------------------------------------------- 12. Containers
s = content("Container hóa với Docker & Docker Compose", "Container & CI/CD")
rect(s, 0.6, 1.8, 5.6, 4.9, NAVY)
code = ["# Stage 1: build (JDK + Maven, cache .m2)", "FROM maven:3.9-eclipse-temurin-21 AS build", "RUN mvn package && java -Djarmode=tools \\",
        "    -jar app.jar extract --layers", "", "# Stage 2: runtime (chỉ JRE)", "FROM eclipse-temurin:21-jre",
        "COPY dependencies/ application/ ./", "USER appuser        # không chạy root", "HEALTHCHECK curl /actuator/health/liveness"]
tb = text(s, 0.9, 2.05, 5.1, 4.5, code, 13, RGBColor(0xE2, 0xE8, 0xF0))
for p in tb.text_frame.paragraphs:
    for r in p.runs:
        r.font.name = "Consolas"
svc = [("app", "Spring Boot · chờ DB & S3 healthy", VIOLET), ("postgres", "PostgreSQL 16 · volume", RGBColor(0x25, 0x63, 0xEB)),
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

# ---------------------------------------------------------------- 14. AWS
s = content("Triển khai trên AWS bằng Terraform", "Triển khai AWS")
picture(s, os.path.join(FIG, "aws.png"), 0.6, 1.65, w=7.9, h=5.2)
bullets(s, 8.85, 1.9, 3.9, 4.8, [
    "**S3**: chặn public, SSE-AES256, versioning, lifecycle, CORS",
    "**IAM role** chỉ Get/Put/Delete trên 1 bucket",
    "**EC2** Ubuntu 24.04, IMDSv2, EBS mã hóa, Elastic IP",
    "user_data cài Docker, sinh secret ngẫu nhiên",
    "terraform apply → push main → tự deploy"], 15)
notes(s, "Toàn bộ hạ tầng AWS được mô tả bằng Terraform. Trên AWS ứng dụng dùng Amazon S3 thật, lấy credential tạm thời từ IAM role nên không cần lưu access key. "
         "Lưu ý: phần Terraform đã viết và rà soát nhưng chưa apply trên tài khoản AWS thật.")

# ---------------------------------------------------------------- 15. Results
s = content("Kết quả kiểm thử", "Kết quả")
big = [("18/18", "ca test đạt", INDIGO), ("~80%", "độ phủ dòng (JaCoCo)", VIOLET), ("22", "REST endpoint", PINK), ("5", "service container", GREEN)]
for i, (n, d, c) in enumerate(big):
    x = 0.6 + i * 3.07
    rect(s, x, 1.85, 2.85, 2.0, CARD)
    text(s, x, 2.0, 2.85, 1.1, n, 46, c, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE)
    text(s, x, 3.1, 2.85, 0.5, d, 15, MUTED, align=PP_ALIGN.CENTER)
rows = [("Unit test", "Tên file, phân loại, token, khóa đăng nhập, JWT"),
        ("API test (MockMvc + H2)", "Luồng đầy đủ: cô lập user, quota, thư mục đệ quy, chia sẻ, chống XSS"),
        ("Integration (Testcontainers)", "PostgreSQL 16 và SeaweedFS S3 thật – chạy trên CI")]
for i, (t, d) in enumerate(rows):
    y = 4.25 + i * 0.82
    rect(s, 0.6, y, 12.15, 0.68, SOFT if i % 2 == 0 else WHITE)
    text(s, 0.9, y, 3.8, 0.68, t, 15, INK, True, anchor=MSO_ANCHOR.MIDDLE)
    text(s, 4.8, y, 7.8, 0.68, d, 14, MUTED, anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Tất cả 18 ca kiểm thử chạy cục bộ đều đạt, độ phủ dòng khoảng 80%. Các integration test cần Docker được chạy trên GitHub Actions.")

# ---------------------------------------------------------------- 16. Future
s = content("Hạn chế và hướng phát triển", "Kết luận")
rect(s, 0.6, 1.8, 5.2, 4.9, RGBColor(0xFE, 0xF2, 0xF2))
text(s, 0.95, 2.05, 4.6, 0.4, "Hạn chế", 20, RGBColor(0xDC, 0x26, 0x26), True)
bullets(s, 0.95, 2.6, 4.6, 4.0, [
    "Upload qua server, giới hạn 100 MB/file",
    "Rate-limit lưu RAM, chưa dùng chung nhiều instance",
    "PostgreSQL chung EC2, chưa backup tự động",
    "Terraform chưa apply trên AWS thật"], 15)
fut = [("Upload trực tiếp S3", "Presigned PUT + multipart, file nhiều GB"), ("Thùng rác & phiên bản", "Xóa mềm, khôi phục qua S3 versioning"),
       ("Managed services", "RDS Multi-AZ, ECS Fargate, CloudFront"), ("Bảo mật nâng cao", "OAuth2 Google, 2FA, quét virus, Redis")]
for i, (t, d) in enumerate(fut):
    y = 1.8 + i * 1.25
    circle(s, 6.3, y + 0.1, 0.6, [INDIGO, VIOLET, PINK, GREEN][i], "→", 16)
    text(s, 7.15, y + 0.05, 5.6, 0.4, t, 18, INK, True)
    text(s, 7.15, y + 0.48, 5.6, 0.4, d, 14, MUTED)
notes(s, "Em nhìn nhận một số hạn chế và đề xuất hướng phát triển, ưu tiên cao nhất là upload trực tiếp lên S3 và thùng rác/phiên bản file.")

# ---------------------------------------------------------------- 17. Thanks
s = prs.slides.add_slide(BLANK)
bg(s, NAVY)
text(s, 0.7, 1.6, 11.9, 1.0, "Cảm ơn thầy cô và các bạn đã lắng nghe", 40, WHITE, True, PP_ALIGN.CENTER)
text(s, 0.7, 2.75, 11.9, 0.6, "Hỏi & đáp · Demo trực tiếp", 20, RGBColor(0xC7, 0xD2, 0xFE), align=PP_ALIGN.CENTER)
demo = [("docker compose up -d --build", "Chạy toàn bộ hệ thống"), ("http://localhost:8080", "Giao diện web"),
        ("/swagger-ui.html", "Tài liệu API"), ("./mvnw verify", "Chạy kiểm thử")]
for i, (c, d) in enumerate(demo):
    x = 0.7 + i * 3.0
    rect(s, x, 4.1, 2.8, 1.6, RGBColor(0x1E, 0x29, 0x3B))
    tb = text(s, x + 0.1, 4.3, 2.6, 0.7, c, 11.5, RGBColor(0xA5, 0xB4, 0xFC), True, PP_ALIGN.CENTER)
    for r in tb.text_frame.paragraphs[0].runs:
        r.font.name = "Consolas"
    text(s, x + 0.2, 5.05, 2.4, 0.5, d, 14, RGBColor(0xCB, 0xD5, 0xE1), align=PP_ALIGN.CENTER)
num[0] += 1
notes(s, "Em xin cảm ơn. Em sẵn sàng demo trực tiếp và trả lời câu hỏi.")

prs.save(OUT)
print("saved", OUT, len(prs.slides), "slides")
