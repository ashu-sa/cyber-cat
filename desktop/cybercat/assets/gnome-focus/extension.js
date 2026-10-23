import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import Shell from 'gi://Shell';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';

export default class FocanopyFocus extends Extension {
    enable() {
        this._policy = {}; this._lastNotice = 0;
        const path = GLib.build_filenamev([GLib.get_user_data_dir(), 'cybercat']);
        GLib.mkdir_with_parents(path, 0o700);
        this._file = Gio.File.new_for_path(GLib.build_filenamev([path, 'focus-policy.json']));
        this._monitor = Gio.File.new_for_path(path).monitor_directory(Gio.FileMonitorFlags.NONE, null);
        this._monitor.connect('changed', (_monitor, file) => { if(file.get_basename() === 'focus-policy.json') this._load(); });
        this._focus = global.display.connect('notify::focus-window', () => this._check());
        this._load();
    }
    _load() {
        try { const [ok, bytes] = this._file.load_contents(null); this._policy = ok ? JSON.parse(new TextDecoder().decode(bytes)) : {}; }
        catch (_) { this._policy = {}; }
        this._check();
    }
    _check() {
        const now = Date.now()/1000;
        if(!this._policy.active || now >= this._policy.expires || !Array.isArray(this._policy.apps)) return;
        const window = global.display.focus_window;
        if(!window || !window.can_minimize()) return;
        const app = Shell.WindowTracker.get_default().get_window_app(window);
        const id = app?.get_id();
        if(!id || ['org.gnome.Settings.desktop','org.gnome.Extensions.desktop','com.mattjakeman.ExtensionManager.desktop'].includes(id) || !this._policy.apps.includes(id)) return;
        window.minimize();
        if(now-this._lastNotice > 10) { this._lastNotice = now; Main.notify('Focanopy · Flower focus', 'Let your flower grow. Pause Pomodoro or turn app blocking off in Focanopy to allow this app.'); }
    }
    disable() {
        if(this._focus) global.display.disconnect(this._focus);
        this._monitor?.cancel(); this._monitor=null; this._focus=null; this._policy={}; this._file=null;
    }
}
