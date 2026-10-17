# AI 编程训练营排期

查看营期进度、每周任务和完成情况。

线上地址保持为 [AI 编程训练营排期](https://internal.aurange.cn/ai-camp-schedule/)。

## 开发

`index.html` 负责页面和交互，`data.js` 保存排期规则与任务内容，`cloud.js` 处理登录、同步和版本检查。

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

营期和任务完成进度保存在 Supabase，团队成员登录后共同查看和修改。账号权限由管理员统一管理，页面上的「成员权限」可以进入管理页面。两个训练营的记录分别存储，互不混用。

变更通过实时订阅更新，每 15 秒也会检查一次。断网时可以查看当前页面已经读到的排期，恢复连接前不能保存。多人同时改同一条记录时，旧版本会提示冲突。

原有 `ai-camps-v1` 和 `ai-camps-done-v1` 不会被删除。第一次升级后，在原来保存过数据的浏览器中登录，点击「导入旧排期」。导入只新增团队里还没有的期数，同期已有的日期和进度不会被覆盖。旧链接也可以作为导入来源。

部署前在现有 Supabase 项目应用 `setup.sql`。浏览器只使用公开的 publishable key，禁止放入 service role key。

## 验证

```sh
npm ci
npm test
```

测试覆盖数据库成员权限、两个训练营的数据隔离、版本冲突、任务状态、导入回滚，以及两个独立页面之间的共享行为。数据库测试在本地 PGlite 中运行，不写入线上数据。

页面使用主站 `/access/` 的统一登录和权限服务。部署时先发布主站，再发布本页面；完整安装须在建表后应用主站 `access/setup.sql`。
