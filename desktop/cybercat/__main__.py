import argparse
import getpass
import hashlib
import os
import sys
from pathlib import Path
from PySide6.QtCore import QTimer, QLockFile, QStandardPaths
from PySide6.QtNetwork import QLocalServer, QLocalSocket
from PySide6.QtWidgets import QApplication, QMessageBox
from .model import Store, data_directory
from .app import Controller, apply_theme


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--command', choices=list('RSPF'))
    parser.add_argument('--smoke-test', action='store_true', help=argparse.SUPPRESS)
    args = parser.parse_args()
    app = QApplication(sys.argv)
    app.setApplicationName('CyberCatDesktop'); app.setOrganizationName('CyberCat')
    app.setQuitOnLastWindowClosed(False); apply_theme(app)
    name = 'cybercat-' + hashlib.sha256((getpass.getuser() + str(data_directory().resolve())).encode()).hexdigest()[:20]
    socket = QLocalSocket(); socket.connectToServer(name)
    if socket.waitForConnected(500):
        socket.write((args.command or 'SHOW').encode() + b'\n'); socket.flush(); socket.waitForBytesWritten(1000)
        return 0
    if args.command and args.command != 'R':
        return 0
    # Prevent duplicates and distinguish a stale socket from a live startup race.
    data_directory().mkdir(parents=True, exist_ok=True)
    lock = QLockFile(str(data_directory() / 'instance.lock'))
    if not lock.tryLock(0): return 0
    QLocalServer.removeServer(name)
    server = QLocalServer(); server.setSocketOptions(QLocalServer.SocketOption.UserAccessOption)
    if not server.listen(name):
        QMessageBox.critical(None, 'Cyber Cat', server.errorString()); return 1
    try:
        store = Store()
    except Exception as error:
        QMessageBox.critical(None, 'Cyber Cat storage', 'Could not load your data. It has not been overwritten.\n' + str(error))
        return 1
    controller = Controller(app, store)
    connections = []
    def connect():
        while server.hasPendingConnections():
            incoming = server.nextPendingConnection(); connections.append(incoming)
            incoming.buffer = bytearray()
            def read(client=incoming):
                client.buffer.extend(bytes(client.readAll()))
                if b'\n' in client.buffer:
                    command = bytes(client.buffer).split(b'\n',1)[0].decode('ascii',errors='ignore')
                    if command == 'SHOW': controller.show_pet()
                    elif command in 'RSPF' and len(command) == 1: controller.command(command)
                    client.disconnectFromServer()
            def cleanup(client=incoming):
                if client in connections: connections.remove(client)
                client.deleteLater()
            incoming.readyRead.connect(read); incoming.disconnected.connect(cleanup)
            read()
    server.newConnection.connect(connect)
    if args.smoke_test:
        def smoke():
            try:
                controller.start(); controller.store.pause(); controller.store.finish()
                controller.show_history(); controller.options()
                print('PASS: native pet, tray, calendar, options and lifecycle constructed', flush=True)
                controller.shortcuts.close(); app.exit(0)
            except Exception as error:
                print('FAIL:', repr(error), file=sys.stderr, flush=True); app.exit(1)
        QTimer.singleShot(250,smoke)
    app.aboutToQuit.connect(lambda: controller.safe(store.checkpoint))
    status = app.exec()
    server.close(); lock.unlock()
    return status


if __name__ == '__main__':
    raise SystemExit(main())
