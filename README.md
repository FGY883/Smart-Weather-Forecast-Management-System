# 智能天气预报系统

> 基于 Spring Boot + Thymeleaf + AI 的智能天气预报系统的设计与实现

## 项目简介

本系统是一个集实时天气查询、AI智能预测、极端天气预警、数据可视化于一体的综合天气预报平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| 前端模板 | Thymeleaf |
| 数据库 | MySQL 8.0 |
| ORM框架 | MyBatis-Plus 3.5.3.1 |
| 安全框架 | Spring Security |
| 前端UI | Bootstrap 5 + Bootstrap Icons |
| 图表库 | Apache ECharts |
| 天气API | 和风天气 (QWeather) |
| AI算法 | 线性回归 + 移动平均融合预测 |

## 功能模块

### 1. 用户管理
- 用户注册、登录（Spring Security）
- 个人信息管理（昵称、邮箱、手机号、默认城市）
- 密码修改
- 推送开关设置

### 2. 实时天气
- 城市搜索与天气查询
- 实时天气数据展示（温度、湿度、风力、气压、能见度、降水量等）
- 7天天气预报展示

### 3. AI智能预测
- 基于30天历史数据的线性回归+移动平均融合算法
- 未来7天温度、湿度、降水量趋势预测
- 预测置信度计算
- ECharts趋势图表可视化

### 4. 极端天气预警
- AI自动检测：高温预警（33℃/35℃）、寒潮预警（0℃/-10℃）、暴雨预警（30mm/50mm）、大风预警
- 四级预警等级：蓝色、黄色、橙色、红色
- 预警信息推送

### 5. 天气收藏与推送
- 城市天气收藏管理
- 每日定时天气预报推送（早7点）
- 极端天气实时推送
- 推送记录查看

### 6. 数据统计与可视化
- 月度天气统计（平均温度、最高/最低温度、降水量、天气类型分布）
- ECharts多维度图表（折线图、柱状图、饼图）
- 年份切换查看历史统计

## 项目结构

```
├── pom.xml
├── src/main/java/com/weather/
│   ├── WeatherApplication.java          # 启动类
│   ├── common/
│   │   └── Result.java                  # 统一返回结果
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security配置
│   │   ├── CustomUserDetailsService.java # 用户认证服务
│   │   └── MyMetaObjectHandler.java     # MyBatis-Plus自动填充
│   ├── controller/
│   │   ├── UserController.java          # 用户控制器
│   │   ├── WeatherController.java       # 天气页面控制器
│   │   └── ApiController.java           # REST API控制器
│   ├── entity/                          # 实体类（8个）
│   ├── mapper/                          # MyBatis-Plus Mapper（8个）
│   ├── service/                         # Service接口（6个）
│   ├── service/impl/                    # Service实现（6个）
│   ├── task/
│   │   └── WeatherScheduledTask.java    # 定时任务
│   └── util/
│       ├── WeatherApiUtil.java          # 天气API工具
│       └── AiPredictionUtil.java        # AI预测算法
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   ├── sql/schema.sql                   # 数据库建表脚本
│   ├── templates/                       # Thymeleaf模板（9个）
│   │   ├── fragments.html               # 公共片段
│   │   ├── index.html                   # 首页
│   │   ├── login.html                   # 登录页
│   │   ├── register.html               # 注册页
│   │   ├── detail.html                  # 天气详情页
│   │   ├── ai-forecast.html            # AI预测页
│   │   ├── warnings.html               # 预警页
│   │   ├── statistics.html             # 统计页
│   │   └── profile.html                # 个人中心
│   └── static/
│       ├── css/style.css                # 全局样式
│       └── js/main.js                   # 主脚本
```

## 快速开始

### 环境要求
- JDK 11+
- Maven 3.6+
- MySQL 8.0+

### 1. 创建数据库
```sql
-- 执行 src/main/resources/sql/schema.sql
mysql -u root -p < src/main/resources/sql/schema.sql
```

### 2. 配置天气API
编辑 `src/main/resources/application.yml`，将 `weather.api.key` 替换为你的和风天气API Key：
```yaml
weather:
  api:
    key: your_api_key_here
```
> 获取API Key: [和风天气开发平台](https://dev.qweather.com/)

### 3. 配置数据库
修改 `application.yml` 中的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/weather_forecast?...
    username: your_username
    password: your_password
```

### 4. 运行项目
```bash
mvn spring-boot:run
```

### 5. 访问系统
打开浏览器访问: http://localhost:8080

### 测试账号
| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 普通用户 | testuser | user123 |

## AI预测算法说明

本系统采用**线性回归 + 移动平均融合算法**进行天气预测：

1. **线性回归**：使用最小二乘法拟合历史数据的趋势线 y = ax + b
2. **移动平均**：计算最近7天数据的平均值，平滑短期波动
3. **融合策略**：70%线性回归 + 30%移动平均
4. **季节性修正**：叠加正弦函数模拟年周期变化
5. **置信度计算**：基于历史数据方差评估预测可靠度

## 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| 天气数据采集 | 每小时 | 采集收藏城市的天气数据 |
| AI预测与推送 | 每天7:00 | 生成AI预测并推送给用户 |
| 极端天气检查 | 每6小时 | 检查并生成极端天气预警 |
