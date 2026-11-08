"""Sinh cac so do cho bao cao/slide (PNG) bang matplotlib."""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

OUT = os.path.join(os.path.dirname(__file__), "figures")
os.makedirs(OUT, exist_ok=True)
plt.rcParams["font.family"] = "Segoe UI"

INK = "#13222b"
MUTED = "#5b6b75"
C = {"client": "#e6eef2", "app": "#dfe9ee", "db": "#e3ebf3", "s3": "#f4ecdf", "ops": "#e2efe8", "edge": "#f0e8da", "gray": "#eef1f3"}
B = {"client": "#16384a", "app": "#16384a", "db": "#3a6a96", "s3": "#a07a42", "ops": "#3f8263", "edge": "#8f6c3e", "gray": "#8c9ba4"}


def box(ax, x, y, w, h, title, sub="", kind="app", fs=11):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.12",
                                fc=C[kind], ec=B[kind], lw=1.8))
    ax.text(x + w / 2, y + h / 2 + (0.13 if sub else 0), title, ha="center", va="center", fontsize=fs, weight="bold", color=INK)
    if sub:
        ax.text(x + w / 2, y + h / 2 - 0.2, sub, ha="center", va="center", fontsize=fs - 2.5, color=MUTED)


def arrow(ax, a, b, label="", color=INK, style="-|>", ls="-", off=(0, 0.12), rad=0.0):
    ax.add_patch(FancyArrowPatch(a, b, arrowstyle=style, mutation_scale=14, color=color, lw=1.5, ls=ls,
                                 connectionstyle=f"arc3,rad={rad}"))
    if label:
        ax.text((a[0] + b[0]) / 2 + off[0], (a[1] + b[1]) / 2 + off[1], label, ha="center", va="bottom",
                fontsize=8.5, color=color, bbox=dict(fc="white", ec="none", pad=1.2))


def canvas(w, h):
    fig, ax = plt.subplots(figsize=(w, h), dpi=200)
    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.axis("off")
    return fig, ax


def save(fig, name):
    fig.savefig(os.path.join(OUT, name), bbox_inches="tight", facecolor="white")
    plt.close(fig)


# 1. Kien truc tong the
fig, ax = canvas(12, 6.2)
ax.add_patch(FancyBboxPatch((3.0, 0.35), 8.7, 5.4, boxstyle="round,pad=0.02,rounding_size=0.2", fc="#fafafa", ec="#cbd5e1", lw=1.2, ls="--"))
ax.text(3.2, 5.5, "Docker host / Amazon EC2  (docker compose)", fontsize=9.5, color=MUTED, weight="bold")
box(ax, 0.2, 2.6, 2.2, 1.2, "Trình duyệt", "HTML/CSS/JS SPA", "client")
box(ax, 3.4, 2.6, 1.9, 1.2, "Caddy", "TLS · reverse proxy", "edge")
box(ax, 6.0, 2.3, 2.6, 1.8, "Spring Boot API", "Nimbus Drive · Java 21", "app", 12)
box(ax, 9.3, 4.1, 2.2, 1.1, "PostgreSQL 16", "metadata · Flyway", "db")
box(ax, 9.3, 2.45, 2.2, 1.1, "Object storage", "SeaweedFS | Amazon S3", "s3")
box(ax, 9.3, 0.7, 2.2, 1.1, "Prometheus", "+ Grafana", "ops")
arrow(ax, (2.4, 3.2), (3.4, 3.2), "HTTPS")
arrow(ax, (5.3, 3.2), (6.0, 3.2), "HTTP")
arrow(ax, (8.6, 3.75), (9.3, 4.55), "JDBC / JPA", off=(-0.1, 0.05))
arrow(ax, (8.6, 3.0), (9.3, 3.0), "S3 API (SigV4)", off=(0, 0.05))
arrow(ax, (9.3, 1.25), (8.6, 2.3), color=B["ops"])
ax.text(8.75, 1.15, "scrape metrics", ha="right", fontsize=8.5, color=B["ops"])
arrow(ax, (1.3, 2.6), (9.6, 2.45), "presigned URL (tải trực tiếp, 15 phút)", color=B["s3"], ls="--", rad=0.3, off=(-0.6, -1.2))
ax.text(7.3, 1.95, "controller → service → repository / ObjectStorage", ha="center", fontsize=8.5, color=MUTED)
save(fig, "architecture.png")

# 2. Phan lop + port/adapter
fig, ax = canvas(11, 5.2)
layers = [("REST Controllers", "Auth · Me · File · Folder · Share  |  JWT filter · CSP · RFC 7807", "client"),
          ("Services (nghiệp vụ)", "FileService · FolderService · ShareService · LoginAttemptService · quota", "app"),
          ("Persistence", "Spring Data JPA repositories  →  PostgreSQL / H2", "db")]
for i, (t, s, k) in enumerate(layers):
    box(ax, 0.3, 3.8 - i * 1.5, 6.4, 1.1, t, s, k)
for i in range(2):
    arrow(ax, (3.5, 3.8 - i * 1.5), (3.5, 3.4 - i * 1.5))
box(ax, 7.4, 2.3, 3.2, 1.1, "«port» ObjectStorage", "put · get · delete · presign", "edge")
box(ax, 7.4, 0.3, 1.5, 1.2, "S3Object\nStorage", "", "s3", 10)
box(ax, 9.1, 0.3, 1.5, 1.2, "LocalObject\nStorage", "", "gray", 10)
arrow(ax, (6.7, 2.85), (7.4, 2.85))
arrow(ax, (8.15, 1.5), (8.6, 2.3), style="-|>", ls="--")
arrow(ax, (9.85, 1.5), (9.4, 2.3), style="-|>", ls="--")
ax.text(9.0, 3.65, "app.storage.type = s3 | local", ha="center", fontsize=9, color=MUTED)
save(fig, "layers.png")

# 3. ERD
fig, ax = canvas(11, 5.6)
ax.set_ylim(-0.5, 5.6)
def table(x, y, name, cols, kind):
    h = 0.42 * (len(cols) + 1)
    ax.add_patch(FancyBboxPatch((x, y - h), 3.0, h, boxstyle="round,pad=0.01,rounding_size=0.08", fc="white", ec=B[kind], lw=1.6))
    ax.add_patch(FancyBboxPatch((x, y - 0.42), 3.0, 0.42, boxstyle="round,pad=0.01,rounding_size=0.08", fc=C[kind], ec=B[kind], lw=1.6))
    ax.text(x + 1.5, y - 0.21, name, ha="center", va="center", weight="bold", fontsize=11, color=INK)
    for i, c in enumerate(cols):
        ax.text(x + 0.15, y - 0.42 * (i + 1.5), c, va="center", fontsize=8.8, color=INK if ("PK" in c or "FK" in c) else MUTED,
                weight="bold" if "PK" in c else "normal")
    return h
table(0.2, 5.3, "users", ["PK id BIGINT", "username VARCHAR(50) UNIQUE", "password_hash (BCrypt)", "quota_bytes BIGINT", "created_at TIMESTAMPTZ"], "client")
table(4.0, 5.3, "folders", ["PK id UUID", "name VARCHAR(255)", "FK parent_id → folders (CASCADE)", "owner → users.username", "created_at"], "edge")
table(4.0, 2.6, "files", ["PK id UUID", "object_key UNIQUE (owner/uuid)", "original_name, content_type", "size BIGINT, starred BOOL", "FK folder_id → folders", "owner, uploaded_at"], "s3")
table(7.8, 2.6, "share_links", ["PK id UUID", "token VARCHAR(64) UNIQUE", "FK file_id → files (CASCADE)", "owner, created_at", "expires_at (NULL = vô hạn)", "download_count INT"], "ops")
arrow(ax, (3.2, 4.2), (4.0, 4.2), "1 — n", off=(0, 0.02))
arrow(ax, (3.2, 3.9), (4.0, 1.9), "1 — n", off=(-0.1, 0))
arrow(ax, (5.5, 3.2), (5.5, 2.6), "1 — n", off=(0.35, -0.1))
arrow(ax, (7.0, 1.6), (7.8, 1.6), "1 — n", off=(0, 0.02))
save(fig, "erd.png")

# 4. CI/CD pipeline
fig, ax = canvas(13, 4.2)
steps = [("git push / PR", "GitHub", "gray"), ("Build & Test", "mvn verify · JUnit 5\nTestcontainers · JaCoCo", "client"),
         ("Docker image", "multi-stage · layered\nBuildx cache", "app"), ("Trivy scan", "CVE HIGH/CRITICAL", "edge"),
         ("Push GHCR", "latest · sha-<commit>", "s3"), ("Deploy", "SSH → compose pull/up\nsmoke test /health", "ops")]
for i, (t, s, k) in enumerate(steps):
    x = 0.2 + i * 2.15
    box(ax, x, 1.5, 1.85, 1.5, t, "", k, 11)
    ax.text(x + 0.925, 1.95, s, ha="center", va="center", fontsize=7.6, color=MUTED)
    if i:
        arrow(ax, (x - 0.3, 2.25), (x, 2.25))
ax.text(0.2, 3.5, "GitHub Actions  ·  .github/workflows/ci-cd.yml", fontsize=11, weight="bold", color=INK)
ax.text(2.35, 0.95, "mọi push & pull request", fontsize=8.5, color=MUTED)
ax.text(6.65, 0.95, "PR: build không push  ·  main: push", fontsize=8.5, color=MUTED)
ax.text(10.95, 0.95, "chỉ main + DEPLOY_ENABLED", fontsize=8.5, color=MUTED)
ax.plot([0.2, 13], [0.75, 0.75], color="#e2e8f0", lw=1)
ax.text(0.2, 0.3, "Fail ở bất kỳ bước nào → dừng pipeline; production luôn chạy image đã qua test, gắn tag theo commit (rollback = deploy lại sha cũ).", fontsize=8.8, color=INK)
save(fig, "cicd.png")

# 5. AWS deployment
fig, ax = canvas(12, 5.6)
ax.add_patch(FancyBboxPatch((0.2, 0.3), 11.6, 5.0, boxstyle="round,pad=0.02,rounding_size=0.2", fc="#f8f5ef", ec="#a07a42", lw=1.4))
ax.text(0.45, 4.95, "AWS  ·  ap-southeast-2 (Sydney)  ·  Terraform", fontsize=10.5, weight="bold", color="#8f6c3e")
ax.add_patch(FancyBboxPatch((2.6, 0.7), 5.6, 3.9, boxstyle="round,pad=0.02,rounding_size=0.15", fc="white", ec="#94a3b8", lw=1.2, ls="--"))
ax.text(2.8, 4.3, "EC2 t3.small · Ubuntu 24.04 · IMDSv2 · EBS mã hóa", fontsize=9, color=MUTED)
box(ax, 0.5, 2.4, 1.7, 1.1, "Elastic IP", "SG: 80/443/22", "edge", 10)
box(ax, 2.9, 2.4, 1.5, 1.1, "Caddy", "HTTPS", "edge", 10)
box(ax, 4.7, 2.4, 1.6, 1.1, "App", "container", "app", 10)
box(ax, 6.5, 2.4, 1.5, 1.1, "Postgres", "container + volume", "db", 10)
box(ax, 4.4, 0.95, 2.3, 0.9, "IAM instance profile", "", "ops", 9.5)
box(ax, 9.0, 2.6, 2.5, 1.5, "Amazon S3", "SSE · versioning\nchặn public · lifecycle", "s3", 11)
box(ax, 9.0, 0.7, 2.5, 1.2, "GHCR", "image registry", "gray", 10)
arrow(ax, (2.2, 2.95), (2.9, 2.95))
arrow(ax, (4.4, 2.95), (4.7, 2.95))
arrow(ax, (6.3, 2.95), (6.5, 2.95))
arrow(ax, (5.5, 3.5), (9.0, 3.5), "s3:Get/Put/DeleteObject (least privilege)", color=B["s3"], off=(0.2, 0.25), rad=-0.25)
arrow(ax, (5.55, 1.85), (5.5, 2.4), color=B["ops"])
arrow(ax, (9.0, 1.3), (6.7, 1.4), "docker pull", color=MUTED, off=(0, 0.02))
box(ax, 9.6, 4.35, 1.9, 0.6, "GitHub Actions", "", "client", 9)
arrow(ax, (9.6, 4.65), (8.2, 4.2), "SSH deploy", color=B["client"], off=(0, 0.05))
save(fig, "aws.png")

# 6. Sequence: upload + presigned
fig, ax = canvas(12, 6.4)
actors = [("Trình duyệt", 1.0, "client"), ("Spring Boot", 4.2, "app"), ("PostgreSQL", 7.4, "db"), ("S3 storage", 10.6, "s3")]
for n, x, k in actors:
    box(ax, x - 0.9, 5.6, 1.8, 0.6, n, "", k, 10)
    ax.plot([x, x], [0.2, 5.6], color="#cbd5e1", lw=1.2, ls="--")
msgs = [(1.0, 4.2, "POST /api/files (multipart, Bearer JWT)", B["client"]), (4.2, 4.2, "xác thực JWT · kiểm tra quyền thư mục · quota", MUTED),
        (4.2, 10.6, "PutObject  key = owner/uuid", B["s3"]), (4.2, 7.4, "INSERT files (metadata)", B["db"]),
        (4.2, 1.0, "201 Created  {id, name, size, ...}", B["app"]), (1.0, 4.2, "GET /api/files/{id}/presign", B["client"]),
        (4.2, 1.0, "URL ký SigV4, hết hạn 15 phút", B["app"]), (1.0, 10.6, "GET object trực tiếp (không qua app)", B["s3"])]
y = 5.1
for a, b, t, col in msgs:
    if a == b:
        ax.text(a + 0.15, y, "» " + t, fontsize=8.6, color=col, va="center")
    else:
        arrow(ax, (a, y), (b, y), t, color=col, off=(0, 0.03))
    y -= 0.62
ax.text(6.0, 0.15, "Nếu ghi DB thất bại → xóa object vừa upload (không để rác). Xóa file: commit DB trước, xóa object sau (afterCommit).",
        ha="center", fontsize=8.6, color=INK)
save(fig, "sequence.png")

# 7. Use case
fig, ax = canvas(11, 5.4)
ax.add_patch(FancyBboxPatch((2.6, 0.2), 6.0, 5.0, boxstyle="round,pad=0.02,rounding_size=0.2", fc="#fafafa", ec="#94a3b8", lw=1.2))
ax.text(5.6, 4.95, "Nimbus Drive", ha="center", fontsize=11, weight="bold")
def actor(x, y, name):
    ax.add_patch(plt.Circle((x, y + 0.55), 0.18, fc="white", ec=INK, lw=1.4))
    ax.plot([x, x], [y + 0.37, y - 0.1], color=INK, lw=1.4)
    ax.plot([x - 0.3, x + 0.3], [y + 0.2, y + 0.2], color=INK, lw=1.4)
    ax.plot([x, x - 0.25], [y - 0.1, y - 0.45], color=INK, lw=1.4)
    ax.plot([x, x + 0.25], [y - 0.1, y - 0.45], color=INK, lw=1.4)
    ax.text(x, y - 0.75, name, ha="center", fontsize=9.5, weight="bold")
actor(1.2, 3.0, "Người dùng")
actor(9.9, 3.4, "Khách (có link)")
actor(9.9, 1.1, "DevOps")
ucs = ["Đăng ký / đăng nhập", "Upload / tải / xem trước file", "Quản lý thư mục, đổi tên, di chuyển", "Tìm kiếm, gắn sao, gần đây",
       "Tạo / thu hồi link chia sẻ", "Xem dung lượng & quota"]
from matplotlib.patches import Ellipse
for i, u in enumerate(ucs):
    yy = 4.4 - i * 0.68
    ax.add_patch(Ellipse((4.6, yy), 3.3, 0.52, fc=C["client"], ec=B["client"], lw=1.2))
    ax.text(4.6, yy, u, ha="center", va="center", fontsize=8.8)
    ax.plot([1.5, 2.95], [3.2, yy], color="#94a3b8", lw=0.9)
ax.add_patch(Ellipse((7.5, 3.4), 1.9, 0.52, fc=C["s3"], ec=B["s3"], lw=1.2))
ax.text(7.5, 3.4, "Tải file qua link", ha="center", va="center", fontsize=8.8)
ax.plot([8.45, 9.6], [3.4, 3.6], color="#94a3b8", lw=0.9)
ax.add_patch(Ellipse((7.5, 0.9), 1.9, 0.52, fc=C["ops"], ec=B["ops"], lw=1.2))
ax.text(7.5, 0.9, "Deploy · giám sát", ha="center", va="center", fontsize=8.8)
ax.plot([8.45, 9.6], [0.9, 1.3], color="#94a3b8", lw=0.9)
save(fig, "usecase.png")

# 8. Luong xac thuc JWT
fig, ax = canvas(12, 4.6)
actors = [("Trình duyệt", 1.2, "client"), ("API · Security + JWT", 4.6, "app"), ("Controller / Service", 8.0, "db"), ("PostgreSQL", 10.9, "gray")]
for n, x, k in actors:
    box(ax, x - 1.0, 3.8, 2.0, 0.55, n, "", k, 9.5)
    ax.plot([x, x], [0.35, 3.8], color="#cbd5e1", lw=1.2, ls="--")
rows = [(1.2, 4.6, "POST /api/auth/login {username, password}", B["client"]),
        (4.6, 10.9, "tìm user · BCrypt.matches · LoginAttemptService", B["db"]),
        (4.6, 1.2, "200 {token HS256, hạn 24 giờ}", B["app"]),
        (1.2, 4.6, "GET /api/files  Authorization: Bearer <token>", B["client"]),
        (4.6, 8.0, "verify chữ ký + hạn → principal = username", B["app"]),
        (8.0, 10.9, "truy vấn luôn lọc owner = username", B["db"])]
y = 3.35
for a, b, t, col in rows:
    arrow(ax, (a, y), (b, y), t, color=col, off=(0, 0.03))
    y -= 0.52
ax.text(6.0, 0.05, "Stateless: không có session phía server → scale ngang dễ; token sai/hết hạn → 401.", ha="center", fontsize=8.8, color=INK)
save(fig, "auth_flow.png")

# 9. Vong doi link chia se
fig, ax = canvas(11, 3.8)
box(ax, 0.2, 1.7, 1.9, 1.1, "Tạo link", "token 192 bit", "client", 10)
box(ax, 3.2, 1.7, 2.2, 1.1, "Còn hiệu lực", "ai có link đều tải", "ops", 10)
box(ax, 6.6, 2.5, 2.3, 0.95, "Hết hạn", "HTTP 410 Gone", "edge", 10)
box(ax, 6.6, 0.8, 2.3, 0.95, "Đã thu hồi", "HTTP 404", "gray", 10)
box(ax, 3.2, 0.0, 2.2, 0.8, "File bị xóa", "link xóa theo (CASCADE)", "s3", 9)
arrow(ax, (2.1, 2.25), (3.2, 2.25), "POST /shares", off=(0, 0.05))
arrow(ax, (5.4, 2.5), (6.6, 2.9), "quá expires_at", off=(-0.2, 0.15))
arrow(ax, (5.4, 1.95), (6.6, 1.3), "DELETE /shares/{id}", off=(-0.15, -0.5))
arrow(ax, (4.3, 0.8), (4.3, 1.7), "", off=(0, 0))
ax.text(4.4, 3.05, "mỗi lượt tải: download_count + 1", ha="center", fontsize=8.5, color=B["ops"])
ax.text(5.5, -0.35, "Trang công khai chỉ trả tên, kích thước, loại file; không lộ object key hay tài khoản khác.", ha="center", fontsize=8.5, color=INK)
save(fig, "share_state.png")

# 10. Phong thu nhieu lop
fig, ax = canvas(11, 5.4)
bands = [("1. Mạng & hạ tầng AWS", "Security Group · IMDSv2 · IAM quyền tối thiểu · S3 chặn public · EBS/S3 mã hóa", "s3"),
         ("2. Biên (Caddy)", "HTTPS Let's Encrypt · giới hạn body 110 MB · chặn /actuator/prometheus", "edge"),
         ("3. Ứng dụng (Spring)", "CSP · JWT stateless · rate-limit đăng nhập · validate DTO · RFC 7807", "app"),
         ("4. Dữ liệu", "truy vấn theo owner · 404 thay 403 · BCrypt · token chia sẻ 192 bit", "db")]
for i, (t, sub, k) in enumerate(bands):
    box(ax, 0.3, 4.1 - i * 1.3, 10.2, 1.0, t, sub, k, 11)
    if i:
        arrow(ax, (5.4, 4.1 - (i - 1) * 1.3), (5.4, 4.1 - i * 1.3 + 1.0))
ax.text(5.4, 0.0, "Kẻ tấn công phải vượt qua cả bốn lớp; một lớp hỏng không làm lộ toàn bộ hệ thống.", ha="center", fontsize=9, color=INK)
save(fig, "defense.png")
print("ok", sorted(os.listdir(OUT)))
