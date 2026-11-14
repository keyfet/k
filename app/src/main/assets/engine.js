(function () {
  'use strict';
  if (window.__MiniTMEngine) return;

  const runIds = new Set();
  const commands = [];
  const escapeRE = s => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&');

  function globMatch(pattern, url, isInclude) {
    if (!pattern) return false;
    try {
      if (isInclude && /^\/.+\/$/.test(pattern)) {
        return new RegExp(pattern.slice(1, -1)).test(url);
      }
      if (!isInclude) {
        const pieces = /^(\*|https?):\/\/([^/]+)(\/.*)$/.exec(pattern);
        if (!pieces) return false;
        const scheme = pieces[1] === '*' ? 'https?' : escapeRE(pieces[1]);
        let host = pieces[2];
        const optionalSubdomain = host.startsWith('*.');
        if (optionalSubdomain) host = host.slice(2);
        let hostRE = escapeRE(host).replace(/\*/g, '[^/]*');
        if (optionalSubdomain) hostRE = '(?:[^/]+\\.)?' + hostRE;
        const pathRE = escapeRE(pieces[3]).replace(/\*/g, '.*');
        return new RegExp('^' + scheme + '://' + hostRE + pathRE + '$', 'i').test(url);
      }
      return new RegExp('^' + escapeRE(pattern).replace(/\*/g, '.*') + '$').test(url);
    } catch (e) { return false; }
  }

  function isMatch(meta) {
    const url = window.location.href.split('#')[0];
    const include = (meta.matches || []).some(p => globMatch(p, url, false)) ||
      (meta.includes || []).some(p => globMatch(p, url, true));
    if (!include && (meta.matches || []).length + (meta.includes || []).length > 0) return false;
    if ((meta.excludes || []).some(p => globMatch(p, url, true))) return false;
    if ((meta.excludeMatches || []).some(p => globMatch(p, url, false))) return false;
    if (meta.noframes && window.self !== window.top) return false;
    return true;
  }

  function onReady(fn, idle) {
    const call = () => idle ? setTimeout(fn, 0) : fn();
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', call, {once: true});
    } else call();
  }

  function install(meta, fn) {
    if (!meta.enabled || !isMatch(meta) || runIds.has(meta.id)) return;
    runIds.add(meta.id);
    const prefix = '__minitm_v1_' + meta.id + '_';
    const key = k => prefix + String(k);
    const storageGet = (k, otherwise) => {
      try { const result = localStorage.getItem(key(k)); return result === null ? otherwise : JSON.parse(result); }
      catch (e) { return otherwise; }
    };
    const storageSet = (k, value) => { try { localStorage.setItem(key(k), JSON.stringify(value)); } catch (e) { console.warn('MiniTM storage:', e); } };
    const storageDelete = k => { try { localStorage.removeItem(key(k)); } catch (e) {} };
    const storageKeys = () => {
      try { return Object.keys(localStorage).filter(k => k.startsWith(prefix)).map(k => k.slice(prefix.length)); }
      catch (e) { return []; }
    };
    const addStyle = css => {
      const node = document.createElement('style');
      node.textContent = css;
      const add = () => (document.head || document.documentElement).appendChild(node);
      if (document.head || document.documentElement) add(); else onReady(add, false);
      return node;
    };
    const xhr = details => {
      const ctrl = new AbortController();
      const opts = {
        method: details.method || 'GET',
        headers: details.headers || {},
        body: details.data,
        credentials: details.anonymous ? 'omit' : 'include',
        signal: ctrl.signal
      };
      fetch(details.url, opts).then(async response => {
        const responseText = await response.text();
        const reply = {
          readyState: 4, status: response.status, statusText: response.statusText,
          responseText, response: responseText, finalUrl: response.url,
          responseHeaders: Array.from(response.headers.entries()).map(x => x.join(': ')).join('\r\n')
        };
        try { if (details.onload) details.onload(reply); } catch (err) { console.error(err); }
      }).catch(error => {
        if (ctrl.signal.aborted) { if (details.onabort) details.onabort({error}); }
        else if (details.onerror) details.onerror({error, status: 0});
      });
      return {abort: () => ctrl.abort()};
    };
    const download = (arg, filename) => {
      const url = typeof arg === 'string' ? arg : arg.url;
      const name = filename || arg.name || 'download';
      const link = document.createElement('a');
      link.href = url; link.download = name; link.rel = 'noopener';
      (document.body || document.documentElement).appendChild(link);
      link.click(); link.remove();
    };
    const menuCommand = (name, handler) => {
      if (typeof handler === 'function') commands.push({name: meta.name + ': ' + name, handler});
      return commands.length;
    };
    const GM = {
      info: {script: {name: meta.name}, scriptHandler: 'MiniTM', version: '1.0'},
      getValue: storageGet, setValue: storageSet, deleteValue: storageDelete,
      listValues: storageKeys, addStyle, xmlHttpRequest: xhr,
      openInTab: url => window.open(url, '_blank'),
      download, registerMenuCommand: menuCommand,
      setClipboard: value => navigator.clipboard?.writeText(String(value)),
      log: (...args) => console.log('[MiniTM][' + meta.name + ']', ...args)
    };
    const run = () => {
      try {
        fn(storageGet, storageSet, storageDelete, storageKeys,
           addStyle, xhr, GM.openInTab, download, menuCommand,
           GM.setClipboard, GM.log, GM.info, window, GM);
      } catch (e) { console.error('MiniTM userscript failed: ' + meta.name, e); }
    };
    if (meta.runAt === 'document-start') run();
    else onReady(run, meta.runAt === 'document-idle');
  }

  window.__MiniTMEngine = {register: install};
  window.__MiniTMCommands = () => commands.map((x, i) => ({index: i, name: x.name}));
  window.__MiniTMRunCommand = index => {
    if (commands[index]) { commands[index].handler(); return true; }
    return false;
  };
})();
