'use strict';
const pageUrl = '__BASE_URL__/';
if (/MicroMessenger/i.test(navigator.userAgent)) document.getElementById('wechat').hidden = false;
if (/iPhone|iPad|iPod/i.test(navigator.userAgent)) {
  document.getElementById('platform-note').textContent = '目前仅提供 Android 版本，APK 无法安装到 iPhone 或 iPad。';
}
document.querySelectorAll('.copy').forEach(function(button) {
  button.addEventListener('click', async function() {
    const status = document.querySelector('.copy-status');
    try {
      await navigator.clipboard.writeText(pageUrl);
      status.textContent = '已复制，可粘贴到手机浏览器打开。';
      button.textContent = '链接已复制 ✓';
    } catch (_) {
      status.textContent = '下载页：' + pageUrl;
      window.prompt('复制此链接，到手机浏览器打开', pageUrl);
    }
  });
});
