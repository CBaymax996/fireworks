# 主页天气动效场景 — 设计文档

## 概述

将当前空壳 `HomeView` 替换为全屏纯 CSS 天气动效画布。根据当天时间和实时天气展示不同场景（太阳/云/雨/雪/月亮），右下角展示温度与坐标。

## 场景映射

Open-Meteo `current_weather.weathercode` + `is_day` → 场景：

| 条件 | 场景 | 主色调 | 元素 |
|---|---|---|---|
| is_day=1, code=0 | sunny | 蔚蓝渐变 | 太阳（旋转光芒）、零星小云 |
| is_day=1, code=1-3 | cloudy | 灰蓝渐变 | 多片大云朵漂移 |
| is_day=1, code=45-57 | rainy | 深灰渐变 | 云层 + 雨线斜落 |
| is_day=1, code=71-77 | snowy | 灰白渐变 | 云层 + 雪花飘摇 |
| is_day=0 | night | 深蓝到黑渐变 | 月亮（光晕）+ 闪烁星星 |
| is_day=1, 日出/日落(-30min) | dawn/dusk | 橙粉渐变 | 低处太阳 + 暖色调背景 |

## 文件结构

```
src/views/HomeView.vue          ← 全屏画布容器
src/components/sky/
  SkyCanvas.vue                 ← 天气主场景入口，根据数据选择子组件
  SunElement.vue                ← 太阳 SVG + rotate 动画
  MoonStars.vue                 ← 月亮 + 星星 twinkle 动画
  CloudElement.vue              ← 可复用云朵，props: size/delay/speed
  RainElement.vue               ← 雨线 fall 动画
  SnowElement.vue               ← 雪花 fall + sway 动画
src/api/weather.ts              ← Open-Meteo 请求封装
```

## 数据流

```
HomeView.onMounted()
  ├→ 静默检测地理位置权限（已有授权才获取，不发弹窗）
  ├→ 拿到坐标或降级为北京(39.9, 116.4)
  ├→ 调用 Open-Meteo /v1/forecast?current_weather=true
  ├→ 解析 weathercode + is_day
  └→ 传给 SkyCanvas → 渲染对应场景组件
  定时 30min 刷新
```

## API 设计

`weather.ts`：
```ts
interface CurrentWeather {
  temperature: number
  weatherCode: number
  isDay: 0 | 1
  lat: number
  lon: number
}

function fetchWeather(lat: number, lon: number): Promise<CurrentWeather>
function getCoords(): Promise<{lat: number; lon: number; source: 'browser' | 'default'}>
```

地理位置获取策略：`navigator.permissions.query({name:'geolocation'})` → 已授权才调用 `getCurrentPosition`，否则直接返回北京坐标。全程不触发弹窗。

## 动画规格

- 太阳光芒：`rotate` 60s linear infinite
- 云朵漂移：`translateX`，不同实例 20-40s 不等，循环
- 雨滴：`translateY`（从上到下）+ 轻微 `translateX`（倾斜），单次 0.6-0.8s
- 雪花：`translateY` + 水平 `translateX` 摇摆，单次 3-5s，带 opacity 变化
- 星星：`transform: scale()` + `opacity` 交替，2-3s，各星星不同 delay
- 场景切换：`opacity` 1.5s transition 淡入淡出

## 右下角信息

展示当前温度和坐标，半透明文字，示例如下：
```
26°C
39.9°N 116.4°E
```

## 路由

`HomeView.vue` 仍然是 `/` 路由，不需要改动 router。移除 `TheWelcome` 组件的引用。

## 覆盖范围

- ✅ 晴天
- ✅ 多云/阴天
- ✅ 雨天
- ✅ 雪天
- ✅ 夜晚（月亮+星星）
- ✅ 清晨/黄昏
