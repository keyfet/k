(() => {
    if (window.__miniTMMove) return;
    let mode = 0, drag = null;
    const listener = { capture: true, passive: false };
    window.__miniTMMove = value => { mode = value; drag = null; };

    function scrollTarget(node) {
        for (let el = node; el && el !== document.body; el = el.parentElement) {
            const css = getComputedStyle(el);
            if ((el.scrollHeight > el.clientHeight && /auto|scroll/.test(css.overflowY)) ||
                (el.scrollWidth > el.clientWidth && /auto|scroll/.test(css.overflowX))) return el;
        }
        return document.scrollingElement;
    }

    window.addEventListener('touchstart', event => {
        drag = null;
        if (!mode || event.touches.length !== 1) return;
        const finger = event.touches[0];
        const hit = event.composedPath().find(el => el instanceof Element);
        if (!hit) return;
        const target = mode === 1 ? scrollTarget(hit) :
            hit.closest('button, a, img, video, canvas, svg, input, select, textarea, [role="button"]') || hit;
        if (!target || (mode === 2 && (target === document.body || target === document.documentElement))) return;
        const transform = getComputedStyle(target).transform;
        drag = {target, x: finger.clientX, y: finger.clientY, left: target.scrollLeft,
            top: target.scrollTop, transform: transform === 'none' ? '' : transform, moved: false};
        event.preventDefault();
        event.stopImmediatePropagation();
    }, listener);

    window.addEventListener('touchmove', event => {
        if (!drag || event.touches.length !== 1) return;
        const finger = event.touches[0];
        const dx = finger.clientX - drag.x, dy = finger.clientY - drag.y;
        event.preventDefault();
        event.stopImmediatePropagation();
        if (!drag.moved && Math.hypot(dx, dy) < 3) return;
        drag.moved = true;
        if (mode === 1) {
            drag.target.scrollLeft = drag.left - dx;
            drag.target.scrollTop = drag.top - dy;
        } else {
            if (getComputedStyle(drag.target).display === 'inline')
                drag.target.style.setProperty('display', 'inline-block');
            drag.target.style.setProperty('transform',
                'translate(' + dx + 'px,' + dy + 'px) ' + drag.transform, 'important');
        }
    }, listener);
    for (const type of ['touchend', 'touchcancel']) {
        window.addEventListener(type, event => {
            if (!drag) return;
            drag = null;
            if (event.cancelable) event.preventDefault();
            event.stopImmediatePropagation();
        }, listener);
    }
})();