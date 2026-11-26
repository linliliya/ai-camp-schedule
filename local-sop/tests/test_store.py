import concurrent.futures
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

spec = importlib.util.spec_from_file_location('local_sop_server', Path(__file__).parents[1] / 'server.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
Store, StoreError = module.Store, module.StoreError


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='local-sop-tests-')
        self.base = Path(self.directory.name).resolve()
        self.root = self.base / 'sop'
        self.store = Store(self.root, self.base / 'state')
        self.raw = b'\xef\xbb\xbf# Original\r\n\r\nExisting line\r\n'
        (self.root / '原文.md').write_bytes(self.raw)

    def tearDown(self):
        self.directory.cleanup()

    def test_read_and_index_never_rewrite_original_bytes(self):
        doc = self.store.document('原文.md')
        self.assertEqual(doc['title'], 'Original')
        self.store.documents()
        self.store.search('Existing')
        self.assertEqual((self.root / '原文.md').read_bytes(), self.raw)

    def test_save_reloads_and_keeps_original_history_bytes(self):
        before = self.store.document('原文.md')
        changed = self.store.save('原文.md', '# 更新\n\n周三发布老带新话术', before['version'])
        self.assertEqual(self.store.document('原文.md')['content'], changed['content'])
        self.assertNotEqual(before['version'], changed['version'])
        versions = self.store.history('原文.md')
        self.assertEqual(len(versions), 1)
        self.assertEqual((self.store.history_dir('原文.md') / (versions[0]['id'] + '.md')).read_bytes(), self.raw)

    def test_external_ai_change_rejects_stale_editor_save(self):
        before = self.store.document('原文.md')
        (self.root / '原文.md').write_text('# AI 修改\n\n新的规则', encoding='utf-8')
        with self.assertRaises(StoreError) as raised:
            self.store.save('原文.md', '# 旧草稿', before['version'])
        self.assertEqual(raised.exception.status, 409)
        self.assertEqual(raised.exception.extra['current']['title'], 'AI 修改')
        self.assertIn('新的规则', self.store.document('原文.md')['content'])

    def test_two_editors_cannot_overwrite_the_first_saved_version(self):
        version = self.store.document('原文.md')['version']
        def save(content):
            try:
                self.store.save('原文.md', content, version)
                return 200
            except StoreError as error:
                return error.status
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(save, ['# 草稿一', '# 草稿二']))
        self.assertEqual(sorted(results), [200, 409])
        self.assertEqual(len(self.store.history('原文.md')), 1)

    def test_failed_atomic_replacement_preserves_the_original_and_cleans_temp(self):
        version = self.store.document('原文.md')['version']
        replace = os.replace
        def blocked(source, destination):
            if Path(destination) == self.root / '原文.md':
                raise PermissionError('fixture locked document')
            return replace(source, destination)
        with patch.object(module.os, 'replace', side_effect=blocked):
            with self.assertRaises(PermissionError):
                self.store.save('原文.md', '# cannot save', version)
        self.assertEqual((self.root / '原文.md').read_bytes(), self.raw)
        self.assertEqual(list(self.root.glob('.sop-write-*')), [])

    def test_chinese_search_refreshes_after_external_edits(self):
        self.store.create('课程提醒')
        doc = self.store.document('课程提醒.md')
        self.store.save(doc['path'], '# 课程提醒\n\n第3周 周三 发布老带新话术', doc['version'])
        found = self.store.search('周三 老带新')
        self.assertEqual([d['path'] for d in found], ['课程提醒.md'])
        self.assertEqual(found[0]['hits'][0]['line'], 3)
        (self.root / '课程提醒.md').write_text('# 新话术\n\n结营直播后', encoding='utf-8')
        self.assertEqual(self.store.search('老带新'), [])
        self.assertEqual(self.store.search('直播后')[0]['title'], '新话术')

    def test_invalid_and_outside_paths_are_rejected(self):
        for name in ['../secret.md', '.hidden.md', 'folder/../../secret.md', str(self.base / 'outside.md')]:
            with self.subTest(name=name), self.assertRaises(StoreError):
                self.store.document(name)
        for title in ['', '../escape', 'bad/name', 'bad:name']:
            with self.subTest(title=title), self.assertRaises(StoreError):
                self.store.create(title)

    def test_duplicate_creation_and_invalid_history_are_rejected(self):
        self.store.create('待整理', '收件箱')
        with self.assertRaises(StoreError) as duplicate:
            self.store.create('待整理', '收件箱')
        self.assertEqual(duplicate.exception.status, 409)
        with self.assertRaises(StoreError):
            self.store.revision('原文.md', '../../secret')


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='local-sop-api-')
        self.base = Path(self.directory.name)
        self.server = module.make_server(self.base / 'sop', self.base / 'state', 0, self.base / 'dist')
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.directory.cleanup()

    def request(self, path, body=None, origin=None):
        headers = {'Content-Type': 'application/json'} if body is not None else {}
        if origin:
            headers['Origin'] = origin
        request = Request(self.url + path, data=json.dumps(body).encode('utf-8') if body is not None else None, headers=headers)
        try:
            with urlopen(request, timeout=5) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.load(error)

    def test_http_create_save_read_and_stale_conflict(self):
        status, created = self.request('/api/create', {'title': '新 SOP', 'folder': '收件箱'})
        self.assertEqual(status, 201)
        status, saved = self.request('/api/save', {'path': created['path'], 'version': created['version'], 'content': '# New\n\nSaved text'})
        self.assertEqual(status, 200)
        self.assertEqual(self.server.store.document(created['path'])['content'], saved['content'])
        status, conflict = self.request('/api/save', {'path': created['path'], 'version': created['version'], 'content': '# stale'})
        self.assertEqual(status, 409)
        self.assertEqual(conflict['current']['version'], saved['version'])

    def test_cross_origin_write_and_outside_attachment_are_rejected(self):
        status, _ = self.request('/api/create', {'title': 'Cross origin'}, 'https://another-site.example')
        self.assertEqual(status, 403)
        status, _ = self.request('/api/asset?path=..%2Fsecret.png')
        self.assertEqual(status, 403)
        self.assertEqual(self.server.store.documents(), [])

    def test_restore_preserves_original_bom_and_newlines_and_backs_up_current(self):
        original = b'\xef\xbb\xbf# Title\r\n\r\nOriginal\r\n'
        (self.base / 'sop' / 'original.md').write_bytes(original)
        before = self.server.store.document('original.md')
        saved = self.server.store.save('original.md', '# Modified\n\nNew', before['version'])
        version = self.server.store.history('original.md')[0]['id']
        status, restored = self.request('/api/restore', {'path': 'original.md', 'version': saved['version'], 'revision': version})
        self.assertEqual(status, 200)
        self.assertEqual((self.base / 'sop' / 'original.md').read_bytes(), original)
        self.assertEqual(len(self.server.store.history('original.md')), 2)
        self.assertEqual(restored['version'], before['version'])


if __name__ == '__main__':
    unittest.main()
