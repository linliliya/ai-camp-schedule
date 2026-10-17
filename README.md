# AI 编程训练营排期

查看营期进度、每周任务和完成情况。

线上地址保持为 [AI 编程训练营排期](https://internal.aurange.cn/ai-camp-schedule/)。

## 开发

`index.html` 负责页面和交互，`data.js` 保存排期规则与任务内容。

```sh
git clone https://github.com/linliliya/ai-camp-schedule.git
cd ai-camp-schedule
git switch -c feature/your-change
python3 -m http.server 8899
```

打开 `http://localhost:8899/` 预览。完成后推送开发分支，向 `main` 提交 Pull Request，由仓库所有者检查并合并。

## 发布

网站由 [okr-robot](https://github.com/linliliya/okr-robot) 统一发布。发布流程每 10 分钟检查一次本仓库的 `main`，有新版本时更新网站。GitHub 调度可能延迟，也可以由所有者在原仓库手动运行 `Publish internal tools`。

协作者无需获得原仓库的写入权限。开发分支不会发布到正式网站。

## 数据

营期配置保存在浏览器 `localStorage`，分享链接的 `#` 后面也包含营期配置。任务勾选记录只保存在当前浏览器。

迁移保留了原网址，以及 `ai-camps-v1`、`ai-camps-done-v1` 两个存储键。请勿在常规改动中随意修改它们。
