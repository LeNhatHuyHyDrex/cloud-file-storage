(function () {
  "use strict";
  const N = window.Nimbus;
  const { icon, esc, bytes, fullDate, fileIcon } = N;

  N.mountThemeToggle();
  const card = document.getElementById("card");
  const token = new URLSearchParams(location.search).get("t") || "";
  const base = "/api/public/shares/" + encodeURIComponent(token);
  const brand = '<div class="brand" style="justify-content:center"><span class="logo">' + icon("cloud") + "</span> Nimbus Drive</div>";

  function fail(title, msg) {
    card.innerHTML = brand + '<div class="ficon" style="background:var(--danger-soft);color:var(--danger)">' + icon("alert") + "</div>" +
      "<h2>" + esc(title) + '</h2><p class="muted">' + esc(msg) + '</p><a class="btn" href="/">Về trang chủ</a>';
  }

  (async function () {
    if (!token) return fail("Link không hợp lệ", "Thiếu mã chia sẻ trong đường dẫn.");
    let info;
    try {
      const res = await fetch(base);
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        return fail(res.status === 410 ? "Link đã hết hạn" : "Không tìm thấy file", body.detail || "Link có thể đã bị thu hồi.");
      }
      info = await res.json();
    } catch (e) {
      return fail("Lỗi kết nối", "Không thể kết nối tới máy chủ.");
    }
    document.title = info.fileName + " — Nimbus Drive";
    card.innerHTML = brand + fileIcon(info.category) + "<h2>" + esc(info.fileName) + "</h2>" +
      '<div class="muted">' + bytes(info.size) + " · chia sẻ bởi <b>" + esc(info.owner) + "</b></div>" +
      '<div class="small muted" style="margin-top:6px">' + (info.expiresAt ? "Link hết hạn lúc " + fullDate(info.expiresAt) : "Link không có thời hạn") + "</div>" +
      '<div class="pv hidden" id="pv"></div>' +
      '<a class="btn primary block" style="margin-top:22px" href="' + base + '/download">' + icon("download") + "Tải xuống</a>";

    const type = info.contentType || "";
    if (/^image\/(png|jpe?g|gif|webp|bmp)/.test(type) && info.size < 15 * 1024 * 1024) {
      const blob = await (await fetch(base + "/download?inline=true")).blob();
      const pv = document.getElementById("pv");
      pv.innerHTML = '<img alt="" src="' + URL.createObjectURL(blob) + '">';
      pv.classList.remove("hidden");
    }
  })();
})();
