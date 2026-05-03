export interface CurrentWeather {
  temperature: number
  weatherCode: number
  isDay: 0 | 1
  lat: number
  lon: number
}

interface CoordsResult {
  lat: number
  lon: number
  source: 'browser' | 'default'
}

export type SceneType = 'sunny' | 'cloudy' | 'rainy' | 'snowy' | 'night'

/**
 * 静默获取地理位置：已有授权才使用浏览器坐标，否则降级为默认北京坐标
 */
export async function getCoords(): Promise<CoordsResult> {
  try {
    const ps = await navigator.permissions.query({ name: 'geolocation' as PermissionName })
    if (ps.state === 'granted') {
      const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 5000 })
      })
      return { lat: pos.coords.latitude, lon: pos.coords.longitude, source: 'browser' }
    }
  } catch {
    // 权限不可用或拒绝，降级
  }
  return { lat: 39.9, lon: 116.4, source: 'default' }
}

/**
 * 调用 Open-Meteo 获取当前天气
 */
export async function fetchWeather(lat: number, lon: number): Promise<CurrentWeather> {
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`
  const res = await fetch(url)
  if (!res.ok) throw new Error(`天气服务不可用 (${res.status})`)
  const data = await res.json()
  const cw = data.current_weather
  return {
    temperature: Math.round(cw.temperature),
    weatherCode: cw.weathercode,
    isDay: cw.is_day ? 1 : 0,
    lat,
    lon,
  }
}

/**
 * 根据天气码和昼夜判定场景类型
 */
export function getSceneType(weatherCode: number, isDay: 0 | 1): SceneType {
  if (isDay === 0) return 'night'
  if (weatherCode === 0) return 'sunny'
  if (weatherCode >= 1 && weatherCode <= 3) return 'cloudy'
  if (weatherCode >= 45 && weatherCode <= 57) return 'rainy'
  if (weatherCode >= 71 && weatherCode <= 77) return 'snowy'
  return 'sunny'
}
