# 试卷工坊 · 项目长期记忆

## 🔴 双端同步（2026-11-17 小宝定的规矩，最高优先级，不可只改一端）

**App 有的网页版必须有；网页版改的 App 也要跟上。任何功能与修复都当「一次改两端」。**

| 改什么 | App 端 | 网页端 |
|---|---|---|
| 出题算法 / 新增题型 | `domain/generate/*.kt` | `web/js/gen/*.js`（同名生成器、同一 `Rng`） |
| 内置数据（词表/古诗/成语/速查表/格纸） | 先改 Kotlin 数据源 | 再跑 `tools/dump/Dump.kt` 导出 `web/data/content.json`，禁止手抄 JSON |
| 排版规则（分栏/避头尾/页脚/试排） | `core/pdf/` | `web/js/core/` 同名模块 |
| 界面功能 | `feature/Screens.kt` | `web/js/app.js` |

- 验收：App 侧 `./gradlew :app:assembleRelease` 装机点一遍；网页侧 `web/e2e.html` 26 项全绿。
- 准绳：同一份 spec + 同一个 seed，两边出同一张卷。
- 网页端的电脑端布局（≥1024px 左侧导航 + 两列卡片）算同一功能的两种排版，不是功能差异。
- **唯一例外**：网页端的「✎ 编辑排版」（点纸面改题干 / 上下移 / 删题 + 字号行距栏数页边距滑杆）是用户 2026-11-17 点名要的**电脑端独占**功能，App 不做。

## 技术要点（踩过的坑）

- 单位全用 pt：A4 = 595×842pt，CSS 的 pt 就是 1/72 英寸，打印时零换算。
- **网页版两种形态，源码同一份**：
  - 主交付 = 根目录 `试卷工坊.html`（3.3 MB 单文件，**双击即开**、不用起服务）。构成：esbuild 把 24 个 ES 模块合成一个 IIFE + 两份 CSS 内联 + 10 份 JSON 以 `<script type="application/json" data-pm="路径">` 内嵌。
  - **改完 `web/` 任何源码都必须重跑 `python3 tools/build-single.py`**，否则单文件版还是老代码。
  - 开发版 `web/index.html` 仍走 http（自检页、调试用），`file://` 下会「数据没加载上」——这是浏览器拦 ES module 与 fetch，不是 bug。
- 单文件版的数据读取在 `web/js/data/store.js` 的 `inlineJson()`：先按 `data-pm` 找内联标签、`JSON.parse` 后缓存，找不到才 `fetch`。所以同一套代码两种形态都能跑，不要再分叉第二份实现。
- 构建时把 JSON 里所有 `<` 转成 `\u003c`，否则题库里的「<」会提前闭合 `</script>`。
- 内嵌数据别写成 JS 对象字面量：3 MB 字面量打开即解析；`type="application/json"` 浏览器只当文本，用到哪科解哪科。
- 本地静态服务必须多线程（单线程 `python3 -m http.server` 会让 ES module 并发加载失败）；当前验证端口 8779。
- 无头 Chrome 截图必须带**独立** `--user-data-dir`，否则会拿到上一次缓存的旧图，容易误判成「页面空白」。
- macOS 上 Chrome 窗口最小宽度约 500px：430px 截图其实是 500 布局的裁切，判断手机端布局要用 iframe 探针 `web/probe.html`。
- 平板横屏 1024 也走桌面布局（断点 ≥1024px），断点以下沿用手机底部胶囊导航。

## 编辑排版的两条铁律（2026-11-17 用户点名要的）

1. **改字绝不洗牌。** 进编辑模式时排版结果是**冻结**的（`state.result` 不再重跑 FlowLayout），改完一道题只调 `repaintQuestion(pages, qid, stem)` 在原地重画：文字变长就按 5% 逐档缩这一题的字号（下限 70%），再不行允许压进下方留白。**后面的题、页数、栏数一律不动**。
   - 任何"顺手重排一下会更整齐"的想法都是错的：用户改一个字就得重新找自己刚改的题。
   - 只有"用户主动改版式"（滑杆 / 页边距 / 每大题栏数 / 标题一行 / 页脚）才允许重排。
2. **双击直接改，不用先点「编辑排版」。** 预览页也盖一层热区（`.hit.dbl`）：单击什么都不做（只显示悬停虚线），**双击**才在原地弹出输入框，且**不切进编辑模式** —— 切模式会插右侧面板、挤窄纸面、缩放跟着变，看着像布局被打乱。
   原地编辑本身：点纸面某题 → 光标直接在**那个位置**出现（`paper.js` 的 `inlineEditor`），不是去右侧面板找输入框。输入期间只改数据不重绘（`onChange` 不触发 draw），blur / ⌘↵ 才落纸，Esc 或「还原」回到原值。
   - 编辑框是浮起卡片（白底 + 品牌色描边 + 重阴影），允许临时遮住下面几行 —— 因为题目之间本来就没有留白可借；但**提交后位置一个都不许变**。

## 出题去重：按「素材」而不是按题干

- 同一素材常有 3~4 种问法（拼音 / 生字 / 补词 / 英语单词），框架的 stem hash 拦不住 —— 用户抓到 31 与 34 都是 tóng xué。
- 生成器自己记账：web 用 `nextFreshWord(ctx, pool, keyOf)`（`ctx.usedKeys`），App 用 `GenContext.usedMaterials` + `freshWord()`（素材光时抛 `NoFreshMaterialException`，宁可少出几道）。
- 去重键：拼音按整词、生字与补词按**首字**（题干只露首字）、英语按单词。
- 加词**只能追加在末尾**：`WordBank.chinese`（Kotlin）与 `CN_WORDS`（web）必须逐项一致、同序，`rotatedPool` 的轮换偏移按下标算。

## 代码里踩过的坑（改之前先看一眼）

- `h(tag, attrs, children)` 的 children 里有数组（`.map()` 的结果）必须先 `flat()`，否则 `appendChild(数组)` 直接抛错，整页渲染不出来（工具栏就是这么白过一次）。
- `.field` 在手机断点变成 `flex-direction: column`，此时子元素的 `flex-basis` 会跑去当**高度**用：下拉/数字框必须在该断点显式 `flex: none`，否则下拉框被拉成 200px 高。
- 原生 `<select>` 与 `input[type=number]` 的箭头在 macOS 上又灰又小且无法改样式 → 统一用自绘 `.dd` 下拉与 `.stepper` 加减器（`ui/dialogs.js`）。
- 导出 PDF 走「Canvas 按 pt 重画每页 → JPEG → 手写 PDF」，不引第三方库；测试用 `buildPdfBlob()`，不要调 `exportPdf()`（会触发下载）。
- 编辑器手动覆盖走 `doc.forceTypeScale / forceItemGap / forceColumns / margin`，一旦设置就跳过全部自动试排，否则滑杆拖出来的结果会被「自动优化」改回去。
