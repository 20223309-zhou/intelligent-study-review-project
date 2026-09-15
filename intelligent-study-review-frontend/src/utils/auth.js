const API_BASE = '/api'

async function request(url, options = {}) {
  const res = await fetch(`${API_BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  return res.json()
}

/**
 * 注册
 */
export async function registerUser(userAccount, userPassword, checkPassword) {
  return request('/user/register', {
    method: 'POST',
    body: JSON.stringify({ userAccount, userPassword, checkPassword }),
  })
}

/**
 * 获取图形验证码（后端 POST /user/getCaptcha）
 * @param {string} [captchaKey] 传上次的 key，后端会先作废它，避免 Redis 里堆废码
 * @returns {Promise<{code:number, data:{captchaKey:string, captchaImage:string}}>}
 *          captchaImage 是 easy-captcha 的 toBase64()，已带 data: 前缀
 */
export async function getCaptcha(captchaKey) {
  const qs = captchaKey ? `?captchaKey=${encodeURIComponent(captchaKey)}` : ''
  return request(`/user/getCaptcha${qs}`, { method: 'POST' })
}

/**
 * 登录
 * 后端已接入验证码校验，captchaKey / captchaCode 为必传；
 * 缺任一项后端会直接抛「验证码错误」。
 */
export async function loginUser(userAccount, userPassword, captchaKey, captchaCode) {
  return request('/user/login', {
    method: 'POST',
    body: JSON.stringify({ userAccount, userPassword, captchaKey, captchaCode }),
  })
}

/**
 * 获取当前登录用户（判断是否已登录）
 */
export async function checkLogin() {
  return request('/user/get/login')
}

/**
 * 退出登录
 */
export async function logoutUser() {
  return request('/user/logout', { method: 'POST' })
}
