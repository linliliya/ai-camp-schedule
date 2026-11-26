# 富文本收集组件

编辑器采用项目已安装的 Tiptap 3.32.0 及其免费开源扩展，包含 ProseMirror、Marked、LinkifyJS 等依赖。未使用商业组件、远端编辑器服务或外部字体。

每次构建从实际参与打包的 npm 模块收集许可，生成完整的 `assets/issue-editor.js.LICENSE.txt`，与 `assets/issue-editor.js` 一起保留。构建入口为 `scripts/build-issue-editor.mjs`，版本以 `local-sop/package-lock.json` 为准。

测试辅助依赖 fake-indexeddb 6.2.6 仅用于开发测试，不进入网页运行包。
