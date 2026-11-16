(function () {
  "use strict";
  const { icon, esc, bytes, fileIcon, folderIcon } = window.Nimbus;
  const view = document.getElementById('shared-folder-view');
  const token = new URLSearchParams(location.search).get('t') || '';
  const base = '/api/public/folders/' + encodeURIComponent(token);
  const path = [];
  let folderId = null;
  document.getElementById('logo').innerHTML = icon('cloud');

  async function load() {
    if (!token) { view.textContent = 'Invalid share link.'; return; }
    view.textContent = 'Loading folder...';
    try {
      const res = await fetch(base + (folderId ? '?folderId=' + encodeURIComponent(folderId) : ''));
      if (!res.ok) throw new Error(res.status === 410 ? 'This link has expired.' : 'This folder is unavailable or the link has been revoked.');
      const c = await res.json();
      document.title = c.rootName + ' — Nimbus Drive';
      view.innerHTML = '<div class="shared-folder-head">' + (path.length ? '<button class="btn" data-back>Back</button>' : '') +
        '<div><h1>' + esc(c.folderName) + '</h1><p class="muted">Shared by ' + esc(c.owner) + ' · View and download</p></div><a class="btn" href="' + base + '/zip' + (folderId ? '?folderId=' + encodeURIComponent(folderId) : '') + '">Download folder ZIP</a></div>' +
        '<div class="shared-items">' + c.folders.map((f) => '<button class="shared-item shared-entry" data-folder="' + f.id + '">' + folderIcon() + '<span>' + esc(f.name) + '</span></button>').join('') +
        c.files.map((f) => '<div class="shared-item">' + fileIcon(f.category) + '<div class="shared-item-info"><strong>' + esc(f.name) + '</strong><span class="muted small">' + bytes(f.size) + '</span></div>' +
          (/^(image\/(png|jpeg|gif|webp|bmp)|video\/|audio\/|application\/pdf)/i.test(f.contentType || '') ? '<a class="btn" target="_blank" rel="noopener noreferrer" href="' + base + '/files/' + f.id + '?inline=true">View</a>' : '') +
          '<a class="btn" href="' + base + '/files/' + f.id + '">Download</a></div>').join('') +
        (!c.folders.length && !c.files.length ? '<p class="muted">This folder is empty.</p>' : '') + '</div>';
      view.querySelectorAll('[data-folder]').forEach((b) => b.addEventListener('click', () => { path.push(folderId); folderId = b.dataset.folder; load(); }));
      const back = view.querySelector('[data-back]');
      if (back) back.addEventListener('click', () => { folderId = path.pop(); load(); });
    } catch (e) { view.textContent = e.message || 'Could not load the folder.'; }
  }
  load();
})();
