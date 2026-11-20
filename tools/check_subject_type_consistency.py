# -*- coding: utf-8 -*-
"""
灵记笔记「类型 - 数据」一致性校验（只读）。

背景：历史上 add_fragment / create_page 未按 subject.type 校验，可能把碎片/页面
写到 TODO 或别的类型笔记上。这些行能被 list_* 查到，但 fullNoteContent() 不读它们，
属于「写成功、正文读不到」的孤立脏数据。

用法：
    python tools/check_subject_type_consistency.py <lingji_database 路径>
    python tools/check_subject_type_consistency.py <包含数据库的 app_data 目录>

配合设备取数（需要 root / 可 debuggable）：
    adb shell "su -c 'tar -czf /sdcard/lingji_data_backup.tar.gz -C /data/user/0/com.lingji.app .'"
    adb pull /sdcard/lingji_data_backup.tar.gz tmp/
    # 解包后把 app_data 目录传给本脚本

脚本在临时副本上打开数据库（连同 -wal / -shm），绝不修改源文件；
发现问题只列出可执行的清理参数，不自动删除任何数据。
"""
import os
import re
import shutil
import sqlite3
import sys
import tempfile

DB_NAME = 'lingji_database'
KNOWN_CHILD_TABLES = {
    'fragments': ('FRAGMENT', 'add_fragment', 'delete_fragment'),
    'notebook_pages': ('NOTEBOOK', 'create_page', 'delete_page'),
    'todo_items': ('TODO', 'add_todo', 'delete_todo'),
}
UUID_RE = re.compile(r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')
SHORT_HEX_RE = re.compile(r'^[0-9a-fA-F]{8}$')


def resolve_db(path):
    if os.path.isdir(path):
        candidate = os.path.join(path, 'databases', DB_NAME)
        if os.path.isfile(candidate):
            return candidate
        candidate = os.path.join(path, DB_NAME)
        if os.path.isfile(candidate):
            return candidate
        raise SystemExit('目录里找不到 %s：%s' % (DB_NAME, path))
    if not os.path.isfile(path):
        raise SystemExit('数据库文件不存在：%s' % path)
    return path


def id_shape(value):
    if UUID_RE.match(value or ''):
        return 'UUID(36)'
    if SHORT_HEX_RE.match(value or ''):
        return 'short-hex(8)'
    return 'other(%d)' % len(value or '')


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    src = resolve_db(sys.argv[1])

    work = tempfile.mkdtemp(prefix='lingji-check-')
    try:
        dst = os.path.join(work, DB_NAME)
        shutil.copy2(src, dst)
        for suffix in ('-wal', '-shm'):
            side = src + suffix
            if os.path.isfile(side):
                shutil.copy2(side, dst + suffix)
        con = sqlite3.connect(dst)
        con.row_factory = sqlite3.Row
        tables = {r[0] for r in con.execute("select name from sqlite_master where type='table'")}
        if 'subjects' not in tables:
            raise SystemExit('不是灵记数据库：缺少 subjects 表')

        subjects = {}
        for r in con.execute('select id, title, type from subjects'):
            subjects[r['id']] = (r['title'], (r['type'] or '').upper())

        print('=' * 68)
        print('灵记「类型 - 数据」一致性报告')
        print('=' * 68)
        print('数据库      :', os.path.abspath(src))
        print('笔记总数    :', len(subjects))
        by_type = {}
        for title, typ in subjects.values():
            by_type[typ] = by_type.get(typ, 0) + 1
        print('类型分布    :', ', '.join('%s=%d' % kv for kv in sorted(by_type.items())) or '(空)')

        shapes = {}
        for sid in subjects:
            shapes[id_shape(sid)] = shapes.get(id_shape(sid), 0) + 1
        print('笔记 id 形态:', ', '.join('%s=%d' % kv for kv in sorted(shapes.items())) or '(空)')

        problems = []
        for table, (expected, _write_tool, delete_tool) in KNOWN_CHILD_TABLES.items():
            if table not in tables:
                continue
            rows = list(con.execute('select id, subjectId from %s' % table))
            orphaned = [r for r in rows if r['subjectId'] not in subjects]
            wrong_type = [
                r for r in rows
                if r['subjectId'] in subjects and subjects[r['subjectId']][1] != expected
            ]
            print('\n[%s] 共 %d 行，允许类型 %s' % (table, len(rows), expected))
            print('  类型错配（正文读不到）: %d' % len(wrong_type))
            print('  无主记录（笔记已不存在）: %d' % len(orphaned))
            for r in wrong_type[:20]:
                title, typ = subjects[r['subjectId']]
                print('    - subject_id=%s title=%r type=%s child_id=%s'
                      % (r['subjectId'], title, typ, r['id']))
                print('      清理: %s(subject_id="%s", id="%s")'
                      % (delete_tool, r['subjectId'], r['id']))
                problems.append((table, r['id'], r['subjectId']))
            if len(wrong_type) > 20:
                print('    ... 另有 %d 行未列出' % (len(wrong_type) - 20))
            for r in orphaned[:10]:
                print('    - 无主: subject_id=%s child_id=%s' % (r['subjectId'], r['id']))
                problems.append((table, r['id'], r['subjectId']))

        print('\n' + '=' * 68)
        if not problems:
            print('结论：未发现类型错配或无主的孤立数据。')
        else:
            print('结论：发现 %d 条可疑记录。上面已逐条给出清理参数；' % len(problems))
            print('      新版工具已阻断新增，历史数据请用对应删除工具逐条清理（脚本不自动删除）。')
        print('=' * 68)
        con.close()
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
