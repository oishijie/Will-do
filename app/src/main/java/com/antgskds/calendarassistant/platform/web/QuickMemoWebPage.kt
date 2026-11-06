package com.antgskds.calendarassistant.platform.web

/**
 * 电脑访问服务的网页端（单页）。
 *
 * 与声物记的 diary_web_page 同样把 HTML/CSS/JS 作为常量产出：整个页面对浏览器
 * 是一个 GET / 就够，不需要再处理静态资源路由。最小版只做文本闪记的
 * 查看、编辑、删除，交互靠 EventSource 接 SSE，数据一变重新拉列表。
 */
object QuickMemoWebPage {

    val HTML: String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>闪记 · 电脑访问</title>
<style>
  :root {
    --bg: #f5f5f7;
    --card: #ffffff;
    --text: #1c1c1e;
    --muted: #8e8e93;
    --accent: #2e9f5c;
    --danger: #e0524e;
    --line: rgba(0,0,0,0.08);
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #1c1c1e;
      --card: #2c2c2e;
      --text: #f2f2f7;
      --muted: #98989d;
      --line: rgba(255,255,255,0.1);
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 32px 16px 96px;
    background: var(--bg);
    color: var(--text);
    font: 15px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  }
  .wrap { max-width: 720px; margin: 0 auto; }
  header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 24px; }
  h1 { font-size: 22px; margin: 0; font-weight: 600; }
  .status { font-size: 13px; color: var(--muted); }
  .status.live::before {
    content: "";
    display: inline-block;
    width: 7px; height: 7px;
    border-radius: 50%;
    background: var(--accent);
    margin-right: 6px;
    vertical-align: middle;
  }
  .note {
    background: var(--card);
    border-radius: 14px;
    padding: 16px 18px;
    margin-bottom: 12px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.06);
    transition: box-shadow .2s;
  }
  .note:hover { box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
  .note-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    font-size: 12px;
    color: var(--muted);
  }
  .badges { display: flex; gap: 6px; }
  .badge {
    padding: 1px 7px;
    border-radius: 6px;
    background: rgba(46,159,92,0.12);
    color: var(--accent);
    font-size: 11px;
  }
  .badge.done { background: rgba(142,142,147,0.15); color: var(--muted); }
  .body { white-space: pre-wrap; word-break: break-word; }
  .body.empty { color: var(--muted); font-style: italic; }
  .actions { display: flex; gap: 8px; margin-top: 12px; }
  button {
    font: inherit;
    font-size: 13px;
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid var(--line);
    background: transparent;
    color: var(--text);
    cursor: pointer;
  }
  button:hover { background: rgba(128,128,128,0.1); }
  button.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
  button.danger { color: var(--danger); border-color: rgba(224,82,78,0.4); }
  textarea {
    width: 100%;
    min-height: 120px;
    font: inherit;
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: var(--bg);
    color: var(--text);
    resize: vertical;
  }
  textarea:focus { outline: 2px solid var(--accent); outline-offset: -1px; }
  .empty-state { text-align: center; color: var(--muted); padding: 64px 0; }
  .toast {
    position: fixed;
    left: 50%; bottom: 32px;
    transform: translateX(-50%) translateY(12px);
    background: rgba(28,28,30,0.92);
    color: #fff;
    padding: 10px 18px;
    border-radius: 10px;
    font-size: 13px;
    opacity: 0;
    pointer-events: none;
    transition: opacity .2s, transform .2s;
  }
  .toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>闪记</h1>
    <span class="status" id="status">连接中…</span>
  </header>
  <div id="list"></div>
</div>
<div class="toast" id="toast"></div>

<script>
(function () {
  var listEl = document.getElementById('list');
  var statusEl = document.getElementById('status');
  var toastEl = document.getElementById('toast');
  var notes = [];
  var editingId = null;
  var source = null;

  function toast(msg) {
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { toastEl.classList.remove('show'); }, 2000);
  }

  function fmtTime(ms) {
    var d = new Date(ms);
    var now = new Date();
    var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
    var time = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (d.toDateString() === now.toDateString()) return '今天 ' + time;
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + time;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function typeLabel(n) {
    if (n.type === 'VOICE') return '语音';
    if (n.type === 'IMAGE') return '图片';
    if (n.type === 'LINK') return '链接';
    return null;
  }

  function render() {
    if (notes.length === 0) {
      listEl.innerHTML = '<div class="empty-state">还没有闪记</div>';
      return;
    }
    var html = '';
    notes.forEach(function (n) {
      var badges = '';
      var tl = typeLabel(n);
      if (tl) badges += '<span class="badge">' + tl + '</span>';
      if (n.isTodo) {
        badges += '<span class="badge ' + (n.isTodoCompleted ? 'done' : '') + '">' +
          (n.isTodoCompleted ? '已完成' : '待办') + '</span>';
      }
      var content = n.content && n.content.length ? escapeHtml(n.content) : '';
      var bodyHtml;
      if (editingId === n.id) {
        bodyHtml = '<textarea id="edit-' + n.id + '">' + content + '</textarea>' +
          '<div class="actions">' +
            '<button class="primary" data-save="' + n.id + '">保存</button>' +
            '<button data-cancel="' + n.id + '">取消</button>' +
          '</div>';
      } else {
        bodyHtml = '<div class="body' + (content ? '' : ' empty') + '">' +
          (content || '（空白闪记）') + '</div>' +
          '<div class="actions">' +
            '<button data-edit="' + n.id + '">编辑</button>' +
            '<button class="danger" data-delete="' + n.id + '">删除</button>' +
          '</div>';
      }
      html += '<div class="note">' +
        '<div class="note-head">' +
          '<span>' + fmtTime(n.updatedAt || n.createdAt) + '</span>' +
          '<span class="badges">' + badges + '</span>' +
        '</div>' + bodyHtml +
      '</div>';
    });
    listEl.innerHTML = html;

    // 进入编辑态后自动聚焦并把光标放到末尾
    if (editingId !== null) {
      var ta = document.getElementById('edit-' + editingId);
      if (ta) { ta.focus(); ta.setSelectionRange(ta.value.length, ta.value.length); }
    }
  }

  function load() {
    fetch('/api/notes')
      .then(function (r) { return r.json(); })
      .then(function (data) { notes = data.notes || []; render(); })
      .catch(function () { statusEl.textContent = '连接失败'; });
  }

  listEl.addEventListener('click', function (e) {
    var t = e.target;
    if (!t.dataset) return;

    if (t.dataset.edit) {
      editingId = Number(t.dataset.edit);
      render();
      return;
    }
    if (t.dataset.cancel) {
      editingId = null;
      render();
      return;
    }
    if (t.dataset.save) {
      var id = Number(t.dataset.save);
      var ta = document.getElementById('edit-' + id);
      if (!ta) return;
      t.disabled = true;
      fetch('/api/notes/' + id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: ta.value })
      }).then(function (r) {
        if (!r.ok) throw new Error(r.status);
        editingId = null;
        toast('已保存');
        load();
      }).catch(function () {
        toast('保存失败');
        t.disabled = false;
      });
      return;
    }
    if (t.dataset.delete) {
      var delId = Number(t.dataset.delete);
      if (!confirm('删除这条闪记？将移入手机「已归档」，可在手机端恢复。')) return;
      fetch('/api/notes/' + delId, { method: 'DELETE' })
        .then(function (r) {
          if (!r.ok) throw new Error(r.status);
          toast('已删除');
          load();
        })
        .catch(function () { toast('删除失败'); });
    }
  });

  function connect() {
    if (source) source.close();
    source = new EventSource('/api/events');
    source.onopen = function () {
      statusEl.textContent = '实时同步中';
      statusEl.classList.add('live');
    };
    source.onmessage = function (e) {
      if (e.data === 'changed') load();
    };
    source.onerror = function () {
      statusEl.textContent = '连接断开，重试中…';
      statusEl.classList.remove('live');
    };
  }

  load();
  connect();
})();
</script>
</body>
</html>
""".trimIndent()
}
