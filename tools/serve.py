#!/usr/bin/env python3
"""本地静态服务（多线程）。

为什么不用 `python3 -m http.server`：它是单线程的，浏览器并发拉 ES module
时会互相等待，偶尔直接加载失败 —— 看着像「页面空白」的 bug，其实是服务端排队。

用法：python3 tools/serve.py [端口]，默认 8779，根目录是本仓库根。
"""
import os
import sys
import http.server
import socketserver

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8779


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *a):
        pass

    def end_headers(self):
        # 开发期别缓存，改完刷新就能看到
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    with Server(("127.0.0.1", PORT), Handler) as httpd:
        print(f"serving {ROOT} on http://127.0.0.1:{PORT}", flush=True)
        httpd.serve_forever()
