import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('fota_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const { data } = response
    if (data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || 'Request failed'))
    }
    return data.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    if (error.response?.status === 401) {
      localStorage.removeItem('fota_token')
      localStorage.removeItem('fota_user')
      window.location.href = '/login'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
