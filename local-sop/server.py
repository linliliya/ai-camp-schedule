"""A local-only Markdown knowledge base. No cloud services or credentials."""
from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import tempfile
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

APP_DIR = Path(__file__).resolve().parent
PROJECT = APP_DIR.parent
MAX_DOCUMENT = 2 * 1024 * 1024


class StoreError(Exception):
    def __init__(self, message, status=400, **extra):
        super().__init__(message)
        self.status, self.extra = status, extra


class Store:
    def __init__(self, root: Path, state: Path):
        self.root, self.state = root.resolve(), state.resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.state.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()

    def path(self, relative: str, *, markdown=True):
        if not isinstance(relative, str) or not relative or '\x00' in relative:
            raise StoreError('文档路径无效。')
        path = (self.root / relative).resolve()
        if not path.is_relative_to(self.root) or path == self.root:
            raise StoreError('只能访问本地 SOP 文件夹内的文件。', 403)
        if any(part.startswith('.') for part in path.relative_to(self.root).parts):
            raise StoreError('不能访问隐藏文件。', 403)
        if markdown and path.suffix.lower() != '.md':
            raise StoreError('文档需要使用 .md 格式。')
        return path

    @staticmethod
    def version(raw: bytes):
        return hashlib.sha256(raw).hexdigest()

    def document(self, relative):
        with self.lock:
            path = self.path(relative)
            if not path.is_file():
                raise StoreError('文档不存在，可能已被移动。', 404)
            raw = path.read_bytes()
            content = raw.decode('utf-8-sig')
            heading = re.search(r'^#\s+(.+)', content, re.M)
            return dict(path=path.relative_to(self.root).as_posix(),
                        title=heading.group(1).strip() if heading else path.stem,
                        content=content, version=self.version(raw),
                        modified=path.stat().st_mtime, size=len(content))

    def documents(self):
        docs = []
        for path in sorted(self.root.rglob('*.md')):
            try:
                doc = self.document(path.relative_to(self.root).as_posix())
                docs.append({k: v for k, v in doc.items() if k != 'content'})
            except (StoreError, UnicodeError, FileNotFoundError):
                continue
        return docs

    def search(self, query):
        terms = query.casefold().split()
        if not terms:
            return []
        results = []
        for item in self.documents():
            try:
                doc = self.document(item['path'])
            except StoreError:
                continue
            haystack = (doc['title'] + '\n' + doc['content']).casefold()
            if not all(term in haystack for term in terms):
                continue
            lines = doc['content'].splitlines()
            hits = []
            for line_no, line in enumerate(lines, 1):
                lower = line.casefold()
                positions = [lower.find(term) for term in terms if term in lower]
                if positions:
                    start = max(0, min(positions) - 35)
                    hits.append(dict(line=line_no, text=line[start:start + 190].strip()))
                if len(hits) == 3:
                    break
            results.append(dict(**item, hits=hits, score=sum(doc['title'].casefold().count(t) * 10 + haystack.count(t) for t in terms)))
        return sorted(results, key=lambda item: (-item['score'], item['title']))[:50]

    def history_dir(self, relative):
        return self.state / 'history' / hashlib.sha256(relative.encode('utf-8')).hexdigest()

    def backup(self, relative, raw):
        folder = self.history_dir(relative)
        folder.mkdir(parents=True, exist_ok=True)
        key = f'{time.time_ns()}-{self.version(raw)[:12]}'
        self.atomic(folder / (key + '.md'), raw)
        return key

    @staticmethod
    def atomic(path: Path, raw: bytes):
        path.parent.mkdir(parents=True, exist_ok=True)
        descriptor, temporary = tempfile.mkstemp(prefix='.sop-write-', dir=path.parent)
        try:
            with os.fdopen(descriptor, 'wb') as stream:
                stream.write(raw)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)

    def save(self, relative, content, expected, *, create=False):
        if not isinstance(content, str) or len(content.encode('utf-8')) > MAX_DOCUMENT:
            raise StoreError('文档过大或内容无效。')
        return self.save_bytes(relative, content.encode('utf-8'), expected, create=create)

    def save_bytes(self, relative, encoded, expected, *, create=False):
        with self.lock:
            path = self.path(relative)
            exists = path.is_file()
            if create and exists:
                raise StoreError('已有同名文档，请换一个标题。', 409)
            if not create and not exists:
                raise StoreError('文档已不存在，草稿仍保留在当前页面。', 409)
            raw = path.read_bytes() if exists else b''
            if exists and self.version(raw) != expected:
                raise StoreError('文件已被其他窗口或 AI 修改。你的草稿已保留，请先核对最新版本。', 409,
                                 current=self.document(relative))
            if exists and raw == encoded:
                return self.document(relative)
            if exists:
                self.backup(relative, raw)
                # Detect external writers during history creation as well.
                if path.read_bytes() != raw:
                    raise StoreError('文件刚刚被外部修改，请先重新加载。', 409, current=self.document(relative))
            self.atomic(path, encoded)
            return self.document(relative)

    def create(self, title, folder=''):
        if not isinstance(title, str) or not title.strip() or len(title) > 100 or re.search(r'[<>:"/\\|?*\x00-\x1f]', title):
            raise StoreError('标题需要为 1–100 字，不能包含路径分隔符。')
        if folder not in ('', '收件箱', '维护与入库'):
            raise StoreError('请选择有效目录。')
        title = title.strip().rstrip('.')
        if not title or title.startswith('.'):
            raise StoreError('标题无效。')
        relative = f'{folder + "/" if folder else ""}{title}.md'
        return self.save(relative, '# ' + title + '\n\n', None, create=True)

    def history(self, relative):
        self.path(relative)
        folder = self.history_dir(relative)
        if not folder.exists():
            return []
        return [dict(id=p.stem, modified=p.stat().st_mtime, size=p.stat().st_size)
                for p in sorted(folder.glob('*.md'), reverse=True)]

    def revision(self, relative, revision):
        return self.revision_bytes(relative, revision).decode('utf-8-sig')

    def revision_bytes(self, relative, revision):
        self.path(relative)
        if not isinstance(revision, str) or not re.fullmatch(r'\d+-[0-9a-f]{12}', revision):
            raise StoreError('历史版本无效。')
        path = self.history_dir(relative) / (revision + '.md')
        if not path.is_file():
            raise StoreError('历史版本不存在。', 404)
        return path.read_bytes()

    def restore(self, relative, revision, expected):
        with self.lock:
            return self.save_bytes(relative, self.revision_bytes(relative, revision), expected)


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(20)

    def send_bytes(self, body, content_type, status=200):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; media-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
        self.end_headers()
        self.wfile.write(body)

    def json(self, value, status=200):
        self.send_bytes(json.dumps(value, ensure_ascii=False).encode('utf-8'), 'application/json; charset=utf-8', status)

    def route(self):
        host = self.headers.get('Host', '')
        if host not in (f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}'):
            raise StoreError('仅支持本机访问。', 403)
        parsed = urlsplit(self.path)
        params = {k: v[0] for k, v in parse_qs(parsed.query).items()}
        return unquote(parsed.path), params

    def do_GET(self):
        try:
            route, params = self.route()
            store = self.server.store
            if route == '/api/documents':
                return self.json(dict(documents=store.documents(), root=str(store.root)))
            if route == '/api/document':
                return self.json(store.document(params.get('path', '')))
            if route == '/api/search':
                return self.json(dict(results=store.search(params.get('q', '')[:200])))
            if route == '/api/history':
                return self.json(dict(versions=store.history(params.get('path', ''))))
            if route == '/api/revision':
                return self.json(dict(content=store.revision(params.get('path', ''), params.get('revision', ''))))
            if route == '/api/asset':
                path = store.path(params.get('path', ''), markdown=False)
                if path.suffix.lower() not in {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.mp4', '.pdf'}:
                    raise StoreError('该附件类型暂不支持预览。', 403)
                return self.send_file(path)
            if route == '/fonts/misans.otf':
                return self.send_file(PROJECT / 'android/app/src/main/res/font/misans_regular.otf')
            if route == '/fonts/misans-medium.otf':
                return self.send_file(PROJECT / 'android/app/src/main/res/font/misans_medium.otf')
            if route == '/licenses/misans.pdf':
                return self.send_file(PROJECT / 'android/app/src/main/assets/misans-license.pdf')
            if route.startswith('/api/'):
                raise StoreError('接口不存在。', 404)
            path = (self.server.dist / route.lstrip('/')).resolve()
            if not path.is_relative_to(self.server.dist) or not path.is_file():
                if '.' in Path(route).name:
                    raise StoreError('文件不存在。', 404)
                path = self.server.dist / 'index.html'
            return self.send_file(path)
        except (StoreError, OSError, UnicodeError) as error:
            self.handle_error(error)

    def send_file(self, path):
        if not path.is_file():
            raise StoreError('文件不存在。', 404)
        kind = mimetypes.guess_type(str(path))[0] or 'application/octet-stream'
        self.send_bytes(path.read_bytes(), kind)

    def do_POST(self):
        try:
            route, _ = self.route()
            origin = self.headers.get('Origin')
            if origin and origin not in (f'http://127.0.0.1:{self.server.server_port}', f'http://localhost:{self.server.server_port}'):
                raise StoreError('请在本地知识库页面中保存。', 403)
            if self.headers.get('Content-Type', '').split(';')[0] != 'application/json':
                raise StoreError('请求格式无效。', 415)
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_DOCUMENT * 2:
                raise StoreError('请求内容过大或为空。', 413)
            body = json.loads(self.rfile.read(length))
            store = self.server.store
            if route == '/api/save':
                return self.json(store.save(body.get('path'), body.get('content'), body.get('version')))
            if route == '/api/create':
                return self.json(store.create(body.get('title'), body.get('folder', '')), 201)
            if route == '/api/restore':
                return self.json(store.restore(body.get('path'), body.get('revision'), body.get('version')))
            raise StoreError('接口不存在。', 404)
        except (StoreError, OSError, UnicodeError, ValueError, TypeError) as error:
            self.handle_error(error)

    def handle_error(self, error):
        if isinstance(error, StoreError):
            self.json(dict(error=str(error), **error.extra), error.status)
        else:
            self.json(dict(error='文件未能保存或读取，请检查磁盘与文件权限。当前草稿仍保留。'), 500)

    def log_message(self, fmt, *values):
        print(fmt % values, flush=True)


def make_server(root, state, port=8901, dist=None):
    server = ThreadingHTTPServer(('127.0.0.1', port), Handler)
    server.store = Store(Path(root), Path(state))
    server.dist = (Path(dist) if dist else APP_DIR / 'dist').resolve()
    return server


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=PROJECT / 'AI编程SOP')
    parser.add_argument('--state', type=Path, default=APP_DIR / '.state')
    parser.add_argument('--port', type=int, default=8901)
    options = parser.parse_args()
    app = make_server(options.root, options.state, options.port)
    if not (app.dist / 'index.html').is_file():
        raise SystemExit('请先在 local-sop 文件夹运行 npm install 和 npm run build。')
    print(f'本地 SOP：http://127.0.0.1:{app.server_port}', flush=True)
    try:
        app.serve_forever()
    except KeyboardInterrupt:
        app.server_close()
