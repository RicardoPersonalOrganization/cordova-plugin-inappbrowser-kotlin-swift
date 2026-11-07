const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

describe('InAppBrowser JS API', () => {
    test('inappbrowser.js exports a function', () => {
        const file = path.resolve(__dirname, '../www/inappbrowser.js');
        const code = fs.readFileSync(file, 'utf8');
        const dom = new JSDOM('<!doctype html><html><body></body></html>', { runScripts: 'outside-only' });
        const window = dom.window;

        // minimal cordova stubs required by the script
        window.cordova = { exec: function () { /* noop */ } };
        const requireStub = function (name) {
            if (name === 'cordova/exec') return window.cordova.exec;
            if (name === 'cordova/channel') return { create: function() { return { subscribe: function(){}, unsubscribe: function(){}, fire: function(){} }; } };
            if (name === 'cordova/modulemapper') return { getOriginalSymbol: function(w, _n) { return w.open; } };
            if (name === 'cordova/urlutil') return { makeAbsolute: function(s){ return s; } };
            throw new Error('unmocked require: ' + name);
        };

        const wrapper = '(function(){ var exports = {}; var module = { exports: exports }; var require = ' + requireStub.toString() + '; ' + code + '; return module.exports; })()';
        const script = new vm.Script(wrapper, { filename: 'inappbrowser.js' });

        const sandbox = { window, document: window.document, console, setTimeout, setInterval, clearInterval, clearTimeout };
        const context = vm.createContext(sandbox);
        const exported = script.runInContext(context);
        expect(typeof exported).toBe('function');
    });
});
