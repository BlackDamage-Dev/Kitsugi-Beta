/* Kitsugi original-page translator. Authored for this app; no remote script or native bridge.
 * Changes Text.nodeValue only. Never replaces elements, attributes, links or event handlers.
 *
 * Performance notes (behaviour unchanged):
 *  - Per-element skip/visibility/language results are cached in a WeakMap and only
 *    invalidated for the mutated subtree; previously every text node paid a
 *    getComputedStyle() walk over all of its ancestors on every scan.
 *  - Visibility uses el.checkVisibility() when available (single native check),
 *    falling back to the computed-style ancestor walk.
 *  - Nodes that are already translated/pending/exhausted are skipped during a
 *    rescan without re-running the full eligibility test, so re-scanning a long
 *    already-translated prefix is nearly free.
 *  - status() counts a flag recorded at apply time instead of trimming every
 *    stored string on every call.
 */
(function () {
    'use strict';
    if (window.__kitsugiWikiDomV1) return;
    var docId = '', target = '', origin = '', enabled = false, root = null, walker = null;
    var rescan = true, serial = 0, storedChars = 0, limited = false, lastInput = -1000;
    var records = new Map(), byNode = new WeakMap(), observer = null;
    var elementInfo = new WeakMap();
    var MAX_NODES = 6000, MAX_CHARS = 1000000;
    /* Batch limits; must stay in sync with WikiDomTranslationBatch.MAX_ROWS (Kotlin)
     * and stay below the translator's single-request chunk size so one batch is
     * one network request. */
    var BATCH_ITEMS = 16, BATCH_CHARS = 3200;
    var letters;
    try { letters = new RegExp('\\p{L}', 'u'); } catch (_) { letters = /[A-Za-z\u00C0-\u02AF\u0400-\u052F\u0600-\u06FF\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF]/; }
    var sensitive = 'form,textarea,input,select,option,button,[role="textbox"],[contenteditable]:not([contenteditable="false"])';
    var skip = 'script,style,noscript,textarea,input,select,option,button,form,pre,code,kbd,samp,svg,math,iframe,' +
        '[contenteditable]:not([contenteditable="false"]),[role="textbox"],[translate="no"],.notranslate,[hidden],[aria-hidden="true"],' +
        '.mw-editsection,.reference,.mw-cite-backlink,.countdown,.countdown-timer,[role="timer"],[aria-live],.wikia-ad,.gpt-ad,.ad-slot';
    // Fandom masaüstü ve mobil ciltleri farklı sarmalayıcılar kullanır; makale kökü
    // sırayla aranır. Hiçbiri yoksa sayfa gövdesine kadar inilir (çeviri tamamen durmasın).
    function findRoot() {
        return document.querySelector('#mw-content-text .mw-parser-output') ||
            document.querySelector('.mw-parser-output') || document.querySelector('#mw-content-text') ||
            document.querySelector('.page-content') || document.querySelector('article') ||
            document.querySelector('main') || document.querySelector('#content') || document.body;
    }
    function input() { lastInput = performance.now(); rescan = true; }
    window.addEventListener('wheel', input, { passive: true });
    window.addEventListener('touchmove', input, { passive: true });
    window.addEventListener('keydown', input, { passive: true });
    window.addEventListener('scroll', function () { rescan = true; }, { passive: true });
    /* A resize can flip media-query visibility without any DOM mutation. */
    window.addEventListener('resize', function () { elementInfo = new WeakMap(); rescan = true; }, { passive: true });
    function anchor() {
        var x = Math.min(window.innerWidth - 1, Math.max(1, window.innerWidth / 2));
        var y = Math.min(window.innerHeight - 1, Math.max(1, window.innerHeight / 3));
        var el = document.elementFromPoint ? document.elementFromPoint(x, y) : null;
        el = el && el.closest('p,li,td,th,h1,h2,h3,h4,h5,h6,figcaption,section,div');
        if (!el || !root || !root.contains(el)) return null;
        return { el: el, top: el.getBoundingClientRect().top, input: lastInput, x: window.scrollX, y: window.scrollY };
    }
    function mutate(work) {
        var saved = anchor();
        work();
        if (saved) requestAnimationFrame(function () {
            // Never fight a gesture or a scroll that occurred after this transaction.
            if (saved.input !== lastInput || !saved.el.isConnected ||
                Math.abs(window.scrollY - saved.y) > window.innerHeight) return;
            var delta = saved.el.getBoundingClientRect().top - saved.top;
            if (Math.abs(delta) > 1) window.scrollBy(0, delta);
        });
    }
    function changed(r) {
        if (r.node.nodeValue === r.rendered) return false;
        storedChars += r.node.nodeValue.length - r.original.length;
        r.original = r.node.nodeValue;
        r.rendered = r.original;
        r.translated = null;
        r.counted = false;
        r.pending = false;
        r.attempts = 0;
        r.retryAt = 0;
        r.rev++;
        return true;
    }
    function write(r, text) {
        if (!r.node.isConnected || !root || !root.contains(r.node) ||
            !r.node.parentElement || r.node.parentElement.closest(sensitive) || changed(r)) return;
        r.rendered = text;
        r.node.nodeValue = text;
    }
    function restore() {
        mutate(function () {
            records.forEach(function (r) { write(r, r.original); r.pending = false; });
        });
    }
    function forgetInfoFor(el) {
        if (!el || el === root || el.tagName === 'STYLE') { elementInfo = new WeakMap(); return; }
        elementInfo.delete(el);
        var descendants = el.querySelectorAll ? el.querySelectorAll('*') : [];
        for (var i = 0; i < descendants.length; i++) elementInfo.delete(descendants[i]);
    }
    function observe() {
        var next = findRoot();
        if (next === root) return;
        if (observer) observer.disconnect();
        root = next;
        walker = null;
        elementInfo = new WeakMap();
        rescan = true;
        if (!root) return;
        observer = new MutationObserver(function (mutations) {
            mutations.forEach(function (m) {
                if (m.type === 'characterData') {
                    var r = byNode.get(m.target);
                    if (r && m.target.nodeValue === r.rendered) return; // our own write
                    if (r) changed(r);
                } else if (m.target && m.target.nodeType === 1) {
                    forgetInfoFor(m.target);
                }
                rescan = true;
            });
        });
        observer.observe(root, { childList: true, characterData: true, subtree: true, attributes: true,
            attributeFilter: ['class', 'style', 'hidden', 'aria-hidden', 'translate', 'lang', 'open'] });
    }
    function language(el) {
        var tagged = el.closest('[lang]');
        return (tagged ? tagged.getAttribute('lang') : document.documentElement.lang || '').toLowerCase().split('-')[0];
    }
    function legacyVisible(el) {
        // Ancestor opacity/visibility and CSS-hidden alternate templates must not be sent.
        for (var parent = el; parent; parent = parent.parentElement) {
            var css = getComputedStyle(parent);
            if (css.display === 'none' || css.visibility === 'hidden' || css.opacity === '0') return false;
            if (parent === root) break;
        }
        return true;
    }
    function infoFor(el) {
        var info = elementInfo.get(el);
        if (info) return info;
        var skipped = !!el.closest(skip);
        var visible = false, hasBox = false;
        if (!skipped) {
            if (typeof el.checkVisibility === 'function') {
                try {
                    visible = el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
                } catch (_) {
                    visible = legacyVisible(el);
                }
            } else {
                visible = legacyVisible(el);
            }
            hasBox = el.getClientRects().length !== 0;
        }
        info = { skip: skipped, visible: visible, hasBox: hasBox, lang: skipped ? '' : language(el) };
        elementInfo.set(el, info);
        return info;
    }
    function eligible(node) {
        var el = node.parentElement;
        if (!el || !node.isConnected) return false;
        var text = node.nodeValue.trim();
        // Preserve letter-tile headings, counters, URLs and very large single nodes intact.
        if (text.length < 2 || text.length > 8000 || !letters.test(text) || /^(?:https?:\/\/|www\.)\S+$/i.test(text)) return false;
        var info = infoFor(el);
        if (info.skip || !info.visible || !info.hasBox) return false;
        if (info.lang === target.toLowerCase().split('-')[0]) return false;
        var box = el.getBoundingClientRect();
        return box.bottom >= -window.innerHeight && box.top <= window.innerHeight * 3;
    }
    function prune() {
        var removed = false;
        records.forEach(function (r, id) {
            if (!r.node.isConnected || !root || !root.contains(r.node) || r.original.length > 8000 || storedChars > MAX_CHARS) {
                // Sites may recycle detached nodes. Do not let our translated string
                // become the new source text when that same node is mounted again.
                if (r.node.nodeValue === r.rendered) r.node.nodeValue = r.original;
                storedChars -= r.original.length;
                removed = true;
                records.delete(id);
                byNode.delete(r.node);
            }
        });
        if (removed && records.size < MAX_NODES && storedChars < MAX_CHARS) limited = false;
    }
    function status() {
        var translated = 0, failed = 0;
        records.forEach(function (r) {
            if (r.counted) translated++;
            if (r.attempts >= 2 && r.translated === null) failed++;
        });
        return { translated: translated, failed: failed, limited: limited, available: !!root, scanning: !!walker || rescan };
    }
    window.__kitsugiWikiDomV1 = {
        configure: function (id, lang, active, retry, source) {
            observe();
            if (id !== docId || lang !== target || origin !== source) {
                restore(); records.clear(); byNode = new WeakMap(); storedChars = 0;
                docId = id; target = lang; origin = source; walker = null; rescan = true; limited = false;
            }
            if (retry) records.forEach(function (r) { r.attempts = 0; r.pending = false; r.retryAt = 0; });
            if (!active && enabled) restore();
            if (active && !enabled) mutate(function () {
                records.forEach(function (r) { if (r.translated !== null) write(r, r.translated); });
            });
            enabled = active;
            if (retry || active) rescan = true;
            return status();
        },
        take: function (id) {
            if (id !== docId || !enabled) return { items: [], status: status() };
            observe(); prune();
            if (!root) return { items: [], status: status() };
            if (!walker && rescan) {
                walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT); rescan = false;
                /* Fresh element info per scan pass: catches visibility changes that
                 * produce no attribute mutation (content-visibility, media queries). */
                elementInfo = new WeakMap();
            }
            var items = [], chars = 0, steps = 0, node, started = performance.now();
            while (walker && steps++ < 1500 && items.length < BATCH_ITEMS && chars < BATCH_CHARS && performance.now() - started < 8) {
                node = walker.nextNode();
                if (!node) { walker = null; break; }
                var r = byNode.get(node);
                if (r && !changed(r)) {
                    // Already settled: a rescan must not pay full eligibility again.
                    if (r.translated !== null || r.pending || r.attempts >= 2) continue;
                    if (Date.now() < r.retryAt) { rescan = true; continue; }
                }
                if (!eligible(node)) continue;
                if (!r) {
                    if (records.size >= MAX_NODES || storedChars + node.nodeValue.length > MAX_CHARS) { limited = true; continue; }
                    r = { id: ++serial, node: node, original: node.nodeValue, rendered: node.nodeValue,
                        translated: null, counted: false, pending: false, attempts: 0, retryAt: 0, rev: 0 };
                    records.set(r.id, r); byNode.set(node, r); storedChars += r.original.length;
                }
                if (r.translated !== null || r.pending || r.attempts >= 2) continue;
                if (Date.now() < r.retryAt) { rescan = true; continue; }
                r.pending = true;
                items.push({ id: r.id, rev: r.rev, text: r.original.trim() });
                chars += r.original.length;
            }
            return { items: items, status: status() };
        },
        apply: function (id, values) {
            if (id !== docId || !enabled) return { stale: true };
            if (performance.now() - lastInput < 250) return { deferred: true };
            var wrote = false;
            mutate(function () {
                values.forEach(function (v) {
                    var r = records.get(v.id);
                    if (!r || !r.pending) return;
                    // Düğüm biz istekte bulunurken değiştiyse sonucu yazmayız; ama "bekliyor"
                    // işaretini MUTLAKA temizleriz, aksi hâlde o metin bir daha hiç istenmez.
                    if (r.rev !== v.rev || r.original.trim() !== v.source || changed(r)) {
                        r.pending = false;
                        rescan = true;
                        return;
                    }
                    r.pending = false;
                    if (!r.node.isConnected || !root || !root.contains(r.node) ||
                        !r.node.parentElement || r.node.parentElement.closest(skip)) { rescan = true; return; }
                    if (typeof v.text !== 'string' || !v.text.trim() || v.text.length > Math.min(40000, r.original.length * 6 + 256)) {
                        r.attempts++; r.retryAt = Date.now() + 5000; rescan = true; return;
                    }
                    var clean = v.text.trim();
                    r.translated = (r.original.match(/^\s*/)[0]) + clean + (r.original.match(/\s*$/)[0]);
                    r.counted = clean !== r.original.trim();
                    write(r, r.translated);
                    wrote = true;
                });
            });
            // Çeviri metni uzunluğu değiştirebilir: "ekrana sığdır" katmanı yeniden ölçsün.
            if (wrote) {
                try { document.dispatchEvent(new Event('kitsugi:content-changed')); } catch (_) {}
            }
            return { status: status() };
        },
        release: function (id) {
            if (id === docId) records.forEach(function (r) { r.pending = false; });
        },
        status: status
    };
})();
