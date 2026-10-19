<div align="center">

# 🌦️ 智能天气预报系统

**基于 Spring Boot + Thymeleaf + AI 的天气预报管理平台 · 毕业设计**

实时天气 · AI 趋势预测 · 灾害预警 · 数据可视化 · 智能问答助手

![Java](https://img.shields.io/badge/Java-8-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green) ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue) ![Thymeleaf](https://img.shields.io/badge/Thymeleaf-SSR-4A90D9) ![MyBatis--Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.3-8A2BE2) ![ECharts](https://img.shields.io/badge/ECharts-5-aa79e1)

</div>

---

> **一句话介绍**：一个集 *实时天气查询、AI 趋势预测、极端天气预警、多维数据可视化、城市收藏与推送* 于一体，并内置 *可拖动、会查天气、懂系统的 AI 智能助手* 的毕业设计级 Web 系统。

---

## ✨ 项目亮点

| | | |
|:---:|:---:|:---:|
| 🤖 **AI 智能预测**<br>线性回归 + 加权移动平均融合，官方预报 vs AI 预测双曲线对比 | 💬 **全局 AI 助手**<br>悬浮可拖动、跨页记忆；大模型 + RAG，能查具体城市天气 | ⚠️ **灾害预警**<br>华风爱科实时预警 + 阈值自动判定四级预警 |
| 📊 **数据可视化**<br>ECharts 折线/柱状/饼图，估算数据透明标注 | 📬 **智能推送**<br>每日天气与极端预警定时推送、推送记录可查 | 🔐 **安全加固**<br>BCrypt、CSRF、登录锁定、限流、密钥外置 |
| ⭐ **城市收藏**<br>常看城市一键收藏与跳转 | 🎨 **Aether 手绘风 UI**<br>统一设计令牌（配色/圆角/阴影/字体/缓动） | 🏗 **分层清晰**<br>Controller/Service/Mapper/Entity/Util 规整架构 |

---

## 🧭 功能总览

| 模块 | 说明 |
|------|------|
| 👤 用户管理 | 注册/登录（Spring Security）、个人信息维护、修改密码、推送开关、默认城市 |
| 🌡 实时天气 | 城市搜索、实时温度/体感/湿度/风力/气压/能见度/降水/紫外线、7 天预报 |
| 🤖 AI 预测 | 近 30 天历史驱动的回归+加权移动平均融合，未来 7 天趋势预测 + 置信度 |
| ⚠️ 灾害预警 | 华风爱科实时预警查询 + 本地阈值自动生成四级（蓝/黄/橙/红）预警 |
| 📊 数据统计 | 月度温度/降水/湿度/天气类型分布统计，多维 ECharts，支持年份切换 |
| ⭐ 收藏与推送 | 城市收藏管理、每日早 7 点与极端天气实时推送、推送记录 |
| 💬 AI 助手 | 全局悬浮球，解答系统使用问题 + 查询具体城市天气（大模型 + RAG，离线可回退） |
| 🎴 手绘天气卡 | Aether 风格城市天气卡片页 |

---

## 🏗 技术栈

| 层次 | 选型 |
|------|------|
| 后端框架 | **Spring Boot 2.7.18**（JDK **1.8**） |
| 视图层 | **Thymeleaf** 服务端渲染 + `thymeleaf-extras-java8time` |
| 安全 | **Spring Security**（BCrypt、CSRF、会话并发控制、登录锁定） |
| 持久层 | **MyBatis-Plus 3.5.3.1** + **MySQL 8.0**（HikariCP） |
| 前端 | **Bootstrap 5** + **Bootstrap Icons** + 原生 JS |
| 图表 | **Apache ECharts 5** |
| JSON / HTTP | **fastjson2 2.0.40**、**Apache HttpClient 4.5.14**（连接池 + 超时 + 重试） |
| 数据源 | **和风天气 QWeather**（实况/7 天/历史/地理编码）、**华风爱科**（灾害预警 `alerts/v1`） |
| AI | 自研预测算法（回归+加权移动平均） + 大模型问答（OpenAI 兼容接口 + RAG 检索增强） |

---

## 🗺 系统架构

```
        ┌──────────────────────── 浏览器（Thymeleaf SSR + Bootstrap + ECharts） ───────────────────────┐
        │  首页 / 详情 / 7天预报(AI对比) / 预警 / 统计 / 手绘卡片 / 个人中心 / 登录 / 注册            │
        │                                   ＋ 右下角全局 AI 助手悬浮球（可拖动）                     │
        └───────────────┬───────────────────────────────▲───────────────────────────────┬────────────┘
                        │ 页面渲染(视图)                  │ JSON(/api/**)                 │ 助手(/api/assistant)
                ┌───────▼────────────────────────────────┴───────────────┐               │
                │  Controller 层  Weather / User / Api / Aether / Assistant │              │
                ├─────────────────────────────────────────────────────────┤◄─────────────┘
                │  Service 层  预测·预警·数据·统计·收藏·推送·助手·天气卡·城市天气│
                ├─────────────────────────────────────────────────────────┤
                │  工具/基础设施  WeatherApiUtil · AiPredictionUtil · LlmClient │
                │            RateLimiter · WeatherCache · UserContext          │
                ├──────────────────┬──────────────────────┬─────────────────┤
                │  MyBatis-Plus     │  Apache HttpClient    │ 定时任务         │
                └─────────┬────────┴──────────┬───────────┴─────────┬────────┘
                          ▼                    ▼                      ▼
                     MySQL 8.0         和风 QWeather / 华风爱科       大模型 API
                                       大模型(xiaomimimo)
```

---

## 📁 项目结构

```
src/main/java/com/weather/
├── WeatherApplication.java              # 启动类
├── common/    Result.java                # 统一响应体
├── config/    SecurityConfig · CustomUserDetailsService · GlobalExceptionHandler
│              MyMetaObjectHandler · SchedulingConfig
├── controller/ WeatherController · UserController · ApiController
│              AetherController · AssistantController
├── entity/    SysUser · WeatherData · WeatherWarning · AiPrediction · WeatherFavorite
│              PushRecord · WeatherStatistics · UserWarningNotice
├── mapper/    与实体一一对应的 MyBatis-Plus Mapper
├── service/   接口 + impl/（预测/预警/数据/统计/收藏/推送/用户）
│              独立组件：WeatherCardService · AssistantService · CityWeatherService
├── security/  LoginAttemptService        # 登录失败限流与锁定
├── task/      WeatherScheduledTask       # 采集/预测推送/预警检查
└── util/      WeatherApiUtil · AiPredictionUtil · WeatherCache · RateLimiter
               LlmClient · UserContext
src/main/resources/
├── application.yml / application-local.yml(gitignore) / application-prod.yml
├── assistant/knowledge.json             # 助手角色人设 + 知识库 + 示例问题
├── sql/        schema.sql · data.sql
├── templates/  index · detail · ai-forecast · warnings · statistics · aether
│               profile · login · register · error · fragments（10 页 + 公共片段）
└── static/     css/style.css · js/main.js · js/aether-card.js · 图标字体
```

---

## 🤖 AI 能力（本系统核心，两大块）

### ① AI 趋势预测（自研算法，实现见 `AiPredictionUtil.predictValues`）

不是深度学习，而是**稳健可解释的统计融合**——刻意选小模型，避免"AI 名不副实"的答辩风险：

1. **数据预处理**：近 30 天历史按天聚合去重（最高温取 `tempMax`/`temp` 较大、最低温取较小、湿度均值、降水最大），避免实时行 NULL 被当成 0 拉偏回归。
2. **线性回归**：最小二乘拟合 `y = ax + b`，斜率 **±0.5℃/天限幅**，防止无限外推。
3. **均值回复阻尼**：外推相对均值的偏离按 `0.88ⁱ` 衰减，越远越向气候均值回归。
4. **加权移动平均**：近 7 天线性加权（越近权重越大）锚定当前水平。
5. **融合**：**60% 阻尼回归 + 40% 加权移动平均**，结果钳制在历史区间 `[min−4, max+4]`。
6. **置信度**：基于最高温序列标准差 `conf = max(0.5, 1 − σ/20)`。

> 📍 前端 `/weather/ai-forecast` 以 **实线=官方预报、虚线=AI 预测** 双线对比 + AI 明细表呈现；历史不足时依次回退（历史接口补齐 → 7 天预报 → 默认）。

### ② 全局 AI 智能助手（悬浮球组件 + 后端问答）

- 🖱 **交互**：右下角悬浮球，点击展开对话面板、再点收起；**可自由拖动**，位置与展开态经 `localStorage` **跨页面保持**；`position:fixed` 脱离滚动，`z-index` 高于页面内容。
- 🎯 **角色人设**：定义在 `assistant/knowledge.json` 的 `persona`（`name/title/systemPrompt`）——专注解答本系统的功能与使用。
- 🧠 **回答方式**：命中知识库主题 + 城市实时/预报天气 → 作为**参考资料注入提示词（RAG 检索增强）** → 大模型（OpenAI 兼容 `mimo-v2.6-flash`）基于事实简洁作答；**大模型不可用时自动回退**为本地知识库/天气摘要，离线亦可演示。
- 🌍 **城市天气**：可问「北京今天天气怎么样」，内置目录 + 地理编码识别城市后抓取实时与未来数日。
- 🎨 **样式**：复用全站设计令牌（主色 `#4A90D9`、圆角/阴影/字体/缓动），与整体风格一致。

---

## 🔐 安全设计

- **密码**：BCrypt 加密存储。
- **CSRF**：默认开启；表单由 Thymeleaf 自动携带令牌，`fetch` 的 POST 经统一注入 `X-XSRF-TOKEN` 头；退出登录为 **POST**。
- **登录防爆破**：5 分钟内失败 5 次锁定 10 分钟，且**认证前**即校验锁定（跳过口令校验）；登录失败原因不泄露账号存在性。
- **会话**：同一账号单点会话（`SessionRegistry`）。
- **接口限流**：`/api/prediction/generate` 6 次/分、`/api/assistant/chat` 30 次/分（按用户）。
- **XSS**：注入 `<script>` 的 JSON 对 `</` 转义；前端动态渲染统一转义。
- **密钥治理**：DB/天气/大模型密钥一律 `${ENV}` 占位，真实值仅存于 **gitignore 的 `application-local.yml`** 或环境变量；生产 `application-prod.yml` 关闭自动建表（`sql.init.mode=never`）。
- **异常**：`GlobalExceptionHandler` 页面返回错误视图、`/api` 返回统一 JSON，不外泄堆栈。

---

## 🗄 数据库设计（MySQL 8.0 / utf8mb4，8 张表）

| 表 | 用途 | 关键约束 |
|----|------|----------|
| `sys_user` | 用户账号 | 用户名唯一 |
| `weather_data` | 实况/预报/历史天气 | **唯一键 `(city_code, weather_date, data_type)`** 防重复入库 |
| `ai_prediction` | AI 预测结果 | 预测日期 + 城市 |
| `weather_warning` | 灾害预警 | 城市 + 类型去重、`status`/`end_time` 过期 |
| `weather_favorite` | 城市收藏 | `(user_id, city_code)` 唯一 |
| `push_record` | 推送历史 | 用户 + 类型 + 状态 |
| `weather_statistics` | 月度统计 | `(city_code, year, month)` 唯一 |
| `user_warning_notice` | 用户预警通知 | 已读标记 |

---

## 🌐 接口一览

**页面路由**：`/`、`/weather/search`、`/weather/detail/{code}`、`/weather/ai-forecast`、`/weather/warning`、`/weather/statistics`、`/weather/aether`、`/user/profile`、`/login`、`/register`

**REST API（`/api/**`，需登录；`/api/aether/**` 只读放行）**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/favorite/add` | 收藏城市 |
| POST | `/api/favorite/remove` | 取消收藏（按 id 或 cityCode） |
| GET | `/api/favorite/list` | 收藏列表 |
| POST | `/api/prediction/generate` | 生成 AI 预测 |
| GET | `/api/warning/active` `/api/warning/all` | 预警查询 |
| GET | `/api/push/records` | 推送记录 |
| GET | `/api/aether/weather` | 手绘卡片天气 |
| GET | `/api/assistant/meta` | 助手元信息 |
| GET | `/api/assistant/chat` | 助手问答（支持 `history` 上下文） |

---

## ⚙️ 配置与环境变量

`application.yml` 通过 `${ENV:默认}` 注入；本地开发用 **被 gitignore 的 `application-local.yml`** 覆盖密钥（经 `spring.profiles.include: local` 自动加载），生产用 `application-prod.yml`。

| 环境变量 | 用途 |
|----------|------|
| `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` | MySQL 连接 |
| `WEATHER_API_KEY` | 和风天气 Key |
| `WEATHERCN_API_KEY` | 华风爱科预警 Key |
| `LLM_ENABLED / LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / LLM_MAX_TOKENS` | 助手大模型（默认 `mimo-v2.6-flash`） |
| `SQL_INIT_MODE` | 建表脚本模式（prod 建议 `never`） |

> 未配置大模型 Key 时助手自动降级为**离线检索式问答**，仍可演示。

---

## 🚀 快速开始

### 环境要求
- **JDK 8**（`1.8`）· **Maven 3.6+** · **MySQL 8.0+**

### 步骤
```bash
# 1) 建库建表 + 初始数据（schema.sql 会建库，data.sql 幂等）
mysql -u root -p < src/main/resources/sql/schema.sql
mysql -u root -p < src/main/resources/sql/data.sql

# 2) 配置密钥：复制一份本地覆盖文件（该文件已在 .gitignore 中）
#    src/main/resources/application-local.yml 内填 和风/华风/大模型 key

# 3) 运行
mvn spring-boot:run
```

访问 **http://localhost:8080**

### 内置测试账号
| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `testuser` | `user123` |

> 演示用弱口令，生产环境请在 `application-prod.yml` 关闭自动播种（`sql.init.mode=never`）并修改密码。

---

## 🗓 定时任务（`WeatherScheduledTask`）

| 任务 | Cron | 说明 |
|------|------|------|
| 天气数据采集 | `0 0 * * * ?` | 每小时采集收藏城市天气 |
| AI 预测与推送 | `0 0 7 * * ?` | 每天 7:00 生成预测并推送 |
| 极端天气检查 | `0 0 */6 * * ?` | 每 6 小时过期失效预警并生成新预警 |

---

## 🎨 前端与设计

- **Aether 手绘设计语言**：`style.css` 统一设计令牌（主色、墨色系、圆角、软阴影、中文显示字体 `ZCOOL KualeLe` 等、`cubic-bezier` 缓动）。
- **可视化**：ECharts 温度对比、降水/湿度趋势、天气类型分布饼图；含 resize 防抖与来源标注。
- **公共片段**：`fragments.html` 统一导航（登录态显隐）、页脚、预警中文徽章、AI 助手组件。

---

## 🧪 质量与已知说明

- ✅ 编译与关键脚本静态校验通过；分层清晰、异常统一处理、接口限流、数据幂等入库。
- ⚠️ 预测为**统计融合**而非深度学习；置信度为方差启发式估计。
- ⚠️ 助手为**大模型 + RAG**，无外网/密钥时回退本地知识库；城市天气支持内置目录与地理编码可解析城市。
- ⚠️ Spring Boot 2.7.18 / JDK8 生命周期偏旧（毕业设计场景可接受，论文可注明）。
- ⚠️ 前端 CDN（Bootstrap/ECharts/图标/字体）目前走公网。

---

<div align="center">

**基于 Spring Boot + Thymeleaf + AI 的智能天气预报系统的设计与实现** · 毕业设计

</div>
