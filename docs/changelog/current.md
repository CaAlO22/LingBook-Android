# 2.3.1B

> 本文件是「当前未发布版本」的更新日志源。版本号取自上面的标题，必须与
> `app/build.gradle.kts` 的 `versionName` 一致；发版前由 agent 或人手动重写，
> 然后执行 `python tools/gen_changelog.py` 生成 `app/src/main/res/raw/changelog.json`。
> 已发布版本的历史日志由脚本从 GitHub Release 抓取，不需要写在这里。

## 新功能

- 聚合页待办支持直接勾选完成，勾选后自动从未完成分区移除

## 优化

- 从笔记返回时停留在进入前的页面，从聚合页进入的笔记会返回聚合页，不再跳回首页卡片列表

## 修复

- 修复待办正文长按复制时按压阴影没有圆角的问题
