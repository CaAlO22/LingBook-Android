# 2.4.1

> 本文件是「当前未发布版本」的更新日志源。版本号取自上面的标题，必须与
> `app/build.gradle.kts` 的 `versionName` 一致；发版前由 agent 或人手动重写，
> 然后执行 `python tools/gen_changelog.py` 生成 `app/src/main/res/raw/changelog.json`。
> 已发布版本的历史日志由脚本从 GitHub Release 抓取，不需要写在这里。

## 修复

- 修复笔记预览中 Markdown 渲染不完整的问题：含数学公式的笔记会丢失正文段落，整篇被渲染成一个公式
- 修复笔记预览中表格不渲染的问题：表格现在会正常显示为表格
