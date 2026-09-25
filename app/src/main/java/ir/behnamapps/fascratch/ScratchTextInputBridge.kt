package ir.behnamapps.fascratch

import android.webkit.JavascriptInterface

internal data class ScratchTextInputSession(
    val id: String,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val multiline: Boolean
)

internal class ScratchTextInputBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun open(
        id: String,
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        multiline: Boolean
    ) {
        activity.runOnUiThread {
            activity.openScratchTextInput(
                id = id,
                text = text,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                multiline = multiline
            )
        }
    }

    @JavascriptInterface
    fun changed(id: String, text: String, selectionStart: Int, selectionEnd: Int) {
        activity.runOnUiThread {
            activity.updateScratchTextInputFromWeb(id, text, selectionStart, selectionEnd)
        }
    }
}

internal const val SCRATCH_TEXT_INPUT_SCRIPT = """
    (function () {
        if (window.__farsiScratchTextInputInstalled) return;
        window.__farsiScratchTextInputInstalled = true;

        var active = null;
        var syncingFromAndroid = false;
        var serial = 0;
        var unsupportedTypes = {
            button: true, checkbox: true, color: true, file: true, hidden: true,
            image: true, password: true, radio: true, range: true, reset: true,
            submit: true
        };

        function editableElement(target) {
            if (!target || target.nodeType !== 1) return null;
            var element = target.closest
                ? target.closest('input, textarea, [contenteditable="true"]')
                : target;
            if (!element || element.disabled || element.readOnly) return null;
            if (element.tagName === 'INPUT' &&
                unsupportedTypes[(element.type || 'text').toLowerCase()]) return null;
            return element;
        }

        function valueOf(element) {
            return element.isContentEditable ? (element.textContent || '') : (element.value || '');
        }

        function selectionOf(element, fallback) {
            if (element.isContentEditable) return fallback;
            return typeof element.selectionStart === 'number' ? element.selectionStart : fallback;
        }

        function setNativeValue(element, value) {
            if (element.isContentEditable) {
                element.textContent = value;
                return;
            }
            var prototype = element.tagName === 'TEXTAREA'
                ? window.HTMLTextAreaElement.prototype
                : window.HTMLInputElement.prototype;
            var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
            if (descriptor && descriptor.set) descriptor.set.call(element, value);
            else element.value = value;
        }

        document.addEventListener('focusin', function (event) {
            var element = editableElement(event.target);
            if (!element) return;
            var value = valueOf(element);
            var id = String(Date.now()) + '-' + String(++serial);
            var start = selectionOf(element, value.length);
            var end = element.isContentEditable
                ? start
                : (typeof element.selectionEnd === 'number' ? element.selectionEnd : start);
            active = {id: id, element: element};
            try {
                AndroidScratchTextInput.open(
                    id,
                    value,
                    start,
                    end,
                    element.tagName === 'TEXTAREA' || element.isContentEditable
                );
            } catch (_) {}
        }, true);

        document.addEventListener('input', function (event) {
            if (syncingFromAndroid || !active || event.target !== active.element) return;
            var value = valueOf(active.element);
            var start = selectionOf(active.element, value.length);
            var end = active.element.isContentEditable
                ? start
                : (typeof active.element.selectionEnd === 'number'
                    ? active.element.selectionEnd
                    : start);
            try {
                AndroidScratchTextInput.changed(active.id, value, start, end);
            } catch (_) {}
        }, true);

        window.__farsiScratchNativeInput = {
            setValue: function (id, value, selectionStart, selectionEnd) {
                if (!active || active.id !== id || !active.element) return;
                syncingFromAndroid = true;
                try {
                    setNativeValue(active.element, value);
                    active.element.dispatchEvent(new Event('input', {
                        bubbles: true,
                        composed: true
                    }));
                    if (!active.element.isContentEditable && active.element.setSelectionRange) {
                        active.element.setSelectionRange(selectionStart, selectionEnd);
                    }
                } finally {
                    syncingFromAndroid = false;
                }
            },
            finish: function (id) {
                if (!active || active.id !== id || !active.element) return;
                var element = active.element;
                active = null;
                element.dispatchEvent(new Event('change', {
                    bubbles: true,
                    composed: true
                }));
                if (element.blur) element.blur();
            }
        };
    })();
"""
