// ==UserScript==
// @name         MiniTM test betiği
// @namespace    local.minitm.test
// @version      1.0
// @description  example.com üzerinde test etiketi oluşturur
// @match        https://example.com/*
// @grant        GM_addStyle
// @grant        GM_getValue
// @grant        GM_setValue
// @run-at       document-end
// ==/UserScript==
(function () {
  'use strict';
  GM_addStyle('#minitm-test { position: fixed; bottom: 15px; right: 15px; z-index: 2147483647; background: #155e75; color: white; border-radius: 10px; padding: 10px 13px; font: 14px sans-serif; }');
  const count = GM_getValue('count', 0) + 1;
  GM_setValue('count', count);
  const panel = document.createElement('div');
  panel.id = 'minitm-test';
  panel.textContent = 'MiniTM çalışıyor · açılış ' + count;
  document.body.appendChild(panel);
})();
