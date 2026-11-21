# 2.3.1A

> 本文件是「当前未发布版本」的更新日志源。版本号取自上面的标题，必须与
> `app/build.gradle.kts` 的 `versionName` 一致；发版前由 agent 或人手动重写，
> 然后执行 `python tools/gen_changelog.py` 生成 `app/src/main/res/raw/changelog.json`。
> 已发布版本的历史日志由脚本从 GitHub Release 抓取，不需要写在这里。

## 修复

- 修复待办列表的整体提醒设置保存后仍显示「未设置」的问题，现在保存后会正确回显，并按时触发提醒
