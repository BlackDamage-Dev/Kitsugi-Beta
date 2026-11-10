/* Kitsugi wiki page fit. Authored for this app; no remote script, no native bridge.
 *
 * Two jobs, both non-destructive:
 *  1) MOBILE (default): keep the site's own responsive viewport. Only when a page
 *     insists on a fixed pixel width wider than the device (desktop-only documents)
 *     does it fall back to width=device-width. DESKTOP mode keeps a 1200 px layout
 *     and asks the engine to scale it down so the whole document fits the screen.
 *  2) FIT: if the rendered document is still wider than the viewport, mark only the
 *     elements that actually overflow (media, tables, <pre>) and let them shrink.
 *     Text nodes, attributes, links and event handlers are never replaced, so the
 *     translator and the site's own scripts keep working.
 */
(function () {
    'use strict';
    if (window.__kitsugiWikiFitV1) return;

    var VIEWPORT_ID = 'kitsugi-wiki-viewport-v1';
    var STYLE_ID = 'kitsugi-wiki-fit-style-v1';
    var WIDE = 'kitsugi-wiki-fit-wide';
    var BLOCK = 'kitsugi-wiki-fit-block';
    var DESKTOP_WIDTH = 1200;
    var MEDIA = 'img,video,iframe,embed,canvas,picture,figure,table,pre';
    var desktop = false;
    var timer = 0, marked = 0;

    var css = '.' + WIDE + '{max-width:100%!important;height:auto!important}' +
        'table.' + WIDE + ',pre.' + WIDE + '{display:block!important;overflow-x:auto!important}' +
        // Geniş bir kapsayıcı kendi içinde kaydırılsın; sayfanın tamamı yatay kaymasın.
        '.' + BLOCK + '{max-width:100%!important;overflow-x:auto!important}';

    function screenWidth() {
        var width = window.screen && window.screen.width ? Math.round(window.screen.width) : 0;
        if (width > 0) return width;
        return Math.max(1, Math.round(document.documentElement.clientWidth || window.innerWidth || 1));
    }

    function findViewport(create) {
        var meta = document.getElementById(VIEWPORT_ID);
        if (!meta) {
            var metas = document.querySelectorAll('meta[name="viewport"]');
            meta = metas.length ? metas[0] : null;
        }
        if (!meta && create) {
            meta = document.createElement('meta');
            meta.name = 'viewport';
            (document.head || document.documentElement).appendChild(meta);
        }
        if (meta) meta.id = VIEWPORT_ID;
        return meta;
    }

    function applyViewport() {
        var screen = screenWidth();
        if (desktop) {
            var meta = findViewport(true);
            // A desktop UA alone still obeys width=device-width; force a desktop layout
            // and scale it down so it is fully visible instead of horizontally scrollable.
            var scale = Math.min(1, screen / DESKTOP_WIDTH);
            var next = 'width=' + DESKTOP_WIDTH + ', initial-scale=' + scale.toFixed(3);
            if (meta.getAttribute('content') !== next) meta.setAttribute('content', next);
            return;
        }
        // Mobile: never invent a viewport for sites that already behave; only correct
        // documents that hard-code a much wider pixel canvas.
        var current = findViewport(false);
        if (!current) return;
        var content = current.getAttribute('content') || '';
        var fixed = /(?:^|[,;\s])width\s*=\s*(\d{3,5})/i.exec(content);
        if (fixed && Number(fixed[1]) > screen + 2) {
            current.setAttribute('content', 'width=device-width, initial-scale=1');
        }
    }

    function style() {
        var el = document.getElementById(STYLE_ID);
        if (el) return el;
        el = document.createElement('style');
        el.id = STYLE_ID;
        el.textContent = css;
        (document.head || document.documentElement).appendChild(el);
        return el;
    }

    function viewportWidth() {
        var root = document.documentElement;
        var width = root.clientWidth || window.innerWidth || 0;
        // 0 = henüz yerleşim yok; o anda hiçbir öğe "geniş" sayılmamalı.
        return width > 0 ? width : 0;
    }

    function markWide(limit) {
        var nodes = document.querySelectorAll(MEDIA);
        for (var i = 0; i < nodes.length && marked < 160; i++) {
            var el = nodes[i];
            if (el.classList.contains(WIDE)) continue;
            if (el.getBoundingClientRect().width > limit + 2) {
                el.classList.add(WIDE);
                marked++;
            }
        }
    }

    // Son çare: görsel/tablo dışındaki geniş bir blok tüm sayfayı kaydırıyorsa,
    // yalnızca o bloğu kendi içinde kaydırılabilir yap. İçerik kırpılmaz.
    function markBlocks(limit) {
        var root = document.querySelector('#mw-content-text .mw-parser-output') ||
            document.querySelector('.mw-parser-output') || document.querySelector('#mw-content-text') ||
            document.querySelector('.page-content') || document.querySelector('article') ||
            document.querySelector('main') || document.querySelector('#content');
        if (!root) return 0;
        var children = root.children, added = 0;
        for (var i = 0; i < children.length && added < 12; i++) {
            var el = children[i];
            if (el.classList.contains(WIDE) || el.classList.contains(BLOCK)) continue;
            if (el.getBoundingClientRect().width > limit + 2) {
                el.classList.add(BLOCK);
                added++;
            }
        }
        return added;
    }

    function applyFit() {
        if (!document.documentElement) return { overflow: false, marked: 0 };
        var limit = viewportWidth();
        if (limit <= 0) return { overflow: false, marked: marked };
        var root = document.documentElement;
        // 1 px yuvarlama normaldir; yalnızca gerçek taşma düzeltilir.
        if (root.scrollWidth <= limit + 2) return { overflow: false, marked: marked };
        style();
        markWide(limit);
        if (root.scrollWidth > limit + 2) markBlocks(limit);
        return { overflow: root.scrollWidth > limit + 2, marked: marked };
    }

    function schedule(delay) {
        if (timer) clearTimeout(timer);
        timer = setTimeout(function () {
            timer = 0;
            applyFit();
        }, typeof delay === 'number' ? delay : 150);
    }

    // Late images, lazy templates and translation writes change the layout after
    // first paint; re-check without touching the scroll position.
    window.addEventListener('resize', function () { applyViewport(); schedule(220); }, { passive: true });
    window.addEventListener('orientationchange', function () { schedule(350); }, { passive: true });
    window.addEventListener('load', function () { schedule(250); }, { passive: true });
    document.addEventListener('load', function () { schedule(300); }, true);
    document.addEventListener('kitsugi:content-changed', function () { schedule(400); }, false);

    window.__kitsugiWikiFitSchedule = schedule;

    window.__kitsugiWikiFitV1 = function (isDesktop) {
        desktop = !!isDesktop;
        try { applyViewport(); } catch (_) {}
        try { applyFit(); } catch (_) {}
        schedule(600);
        return true;
    };
})();
