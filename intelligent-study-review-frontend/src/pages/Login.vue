<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { loginUser, getCaptcha } from '@/utils/auth.js'
import UiButton from '@/components/ui/UiButton.vue'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const formError = ref('')
const isLoading = ref(false)

const form = reactive({
  userAccount: '',
  userPassword: '',
  captchaCode: ''
})

// 验证码：captchaKey 对应后端 Redis 里的 captcha:{key}，captchaImage 是 base64
const captchaKey = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)

const fieldErrors = reactive({
  userAccount: '',
  userPassword: '',
  captchaCode: ''
})

const isFormValid = computed(() => {
  return form.userAccount.trim() !== ''
    && form.userPassword !== ''
    && form.captchaCode.trim() !== ''
})

function validateField(field) {
  if (field === 'userAccount') {
    fieldErrors.userAccount = form.userAccount.trim() === '' ? '请输入用户名' : ''
  }
  if (field === 'userPassword') {
    fieldErrors.userPassword = form.userPassword === '' ? '请输入密码' : ''
  }
  if (field === 'captchaCode') {
    fieldErrors.captchaCode = form.captchaCode.trim() === '' ? '请输入验证码' : ''
  }
}

function validateAll() {
  validateField('userAccount')
  validateField('userPassword')
  validateField('captchaCode')
  return !fieldErrors.userAccount && !fieldErrors.userPassword && !fieldErrors.captchaCode
}

// easy-captcha 的 toBase64() 已带 data: 前缀，这里兼容后端只回裸 base64 的情况
function normalizeCaptchaImage(raw) {
  if (!raw) return ''
  return raw.startsWith('data:') ? raw : `data:image/png;base64,${raw}`
}

/**
 * 拉取新验证码。
 * 传上一条 captchaKey 是为了让后端顺手把旧码从 Redis 里删掉，不留废键。
 * @returns {Promise<boolean>} 是否成功
 */
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha(captchaKey.value || undefined)
    if (res.code === 0 && res.data) {
      captchaKey.value = res.data.captchaKey
      captchaImage.value = normalizeCaptchaImage(res.data.captchaImage)
      form.captchaCode = ''
      fieldErrors.captchaCode = ''
      return true
    }
    captchaImage.value = ''
    return false
  } catch (e) {
    captchaImage.value = ''
    return false
  } finally {
    captchaLoading.value = false
  }
}

// 手动点图刷新
async function onRefreshCaptcha() {
  formError.value = ''
  if (!(await refreshCaptcha())) {
    formError.value = '验证码获取失败，请点击图片重试'
  }
}

onMounted(refreshCaptcha)

async function handleSubmit() {
  formError.value = ''
  if (!validateAll() || !isFormValid.value) return

  isLoading.value = true
  try {
    const res = await loginUser(
      form.userAccount.trim(),
      form.userPassword,
      captchaKey.value,
      form.captchaCode.trim()
    )
    if (res.code === 0) {
      // 登录成功后后端通过 Session 维护登录态，直接跳转
      router.push('/')
      return
    }
    formError.value = res.message || '账号或密码错误'
    // 登录失败一律换一张：旧码可能已过期，或下一次失败会连报「验证码错误」把人绕住
    if (!(await refreshCaptcha())) {
      formError.value += '（验证码刷新失败，请点图片重试）'
    }
  } catch (e) {
    formError.value = '网络错误，请稍后重试'
    if (!(await refreshCaptcha())) {
      formError.value += '（验证码刷新失败，请点图片重试）'
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-header">
      <h1 class="auth-title">欢迎回来</h1>
      <p class="auth-subtitle">登录后即可生成、批改与管理试卷</p>
    </div>

    <form class="auth-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label class="form-label" for="login-account">用户名</label>
        <input
          id="login-account"
          v-model="form.userAccount"
          type="text"
          class="form-input"
          :class="{ 'has-error': fieldErrors.userAccount }"
          placeholder="请输入用户名"
          autocomplete="username"
          @input="validateField('userAccount')"
          @blur="validateField('userAccount')"
        />
        <p v-if="fieldErrors.userAccount" class="field-error">{{ fieldErrors.userAccount }}</p>
      </div>

      <div class="form-group">
        <label class="form-label" for="login-password">密码</label>
        <input
          id="login-password"
          v-model="form.userPassword"
          type="password"
          class="form-input"
          :class="{ 'has-error': fieldErrors.userPassword }"
          placeholder="请输入密码"
          autocomplete="current-password"
          @input="validateField('userPassword')"
          @blur="validateField('userPassword')"
        />
        <p v-if="fieldErrors.userPassword" class="field-error">{{ fieldErrors.userPassword }}</p>
      </div>

      <div class="form-group">
        <label class="form-label" for="login-captcha">验证码</label>
        <div class="captcha-row">
          <input
            id="login-captcha"
            v-model="form.captchaCode"
            type="text"
            class="form-input"
            :class="{ 'has-error': fieldErrors.captchaCode }"
            placeholder="请输入右侧验证码"
            maxlength="4"
            autocomplete="off"
            @input="validateField('captchaCode')"
            @blur="validateField('captchaCode')"
          />
          <button
            type="button"
            class="captcha-box"
            :class="{ 'is-loading': captchaLoading }"
            :disabled="captchaLoading"
            title="点击刷新验证码"
            aria-label="点击刷新验证码"
            @click="onRefreshCaptcha"
          >
            <img v-if="captchaImage" :src="captchaImage" alt="验证码，点击刷新" />
            <span v-else class="captcha-fallback">点击加载</span>
          </button>
        </div>
        <p v-if="fieldErrors.captchaCode" class="field-error">{{ fieldErrors.captchaCode }}</p>
      </div>

      <p v-if="formError" class="form-error">{{ formError }}</p>

      <UiButton
        native-type="submit"
        variant="primary"
        size="lg"
        block
        :loading="isLoading"
        :disabled="!isFormValid"
      >
        登 录
      </UiButton>
    </form>

    <div class="auth-footer">
      还没有账号？
      <router-link to="/register" class="auth-link">立即注册</router-link>
    </div>
  </AuthShell>
</template>

<style scoped>
.auth-header { margin-bottom: var(--space-8); }

.auth-title {
  font-size: var(--fs-2xl);
  font-weight: var(--fw-heavy);
  color: var(--color-text-primary);
}

.auth-subtitle {
  margin-top: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-label {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-secondary);
}

/* 固定 48px 高：既是舒适的点击高度，也让「验证码输入框 + 图片」严丝合缝对齐 */
.form-input {
  width: 100%;
  height: 48px;
  padding: 0 var(--space-4);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-primary);
  /* 16px 起，避免 iOS 聚焦时页面自动缩放 */
  font-size: 1rem;
  transition: all var(--transition-fast);
  outline: none;
}

.form-input:focus {
  border-color: var(--color-primary-light);
  box-shadow: var(--ring-primary);
}

.form-input.has-error { border-color: var(--color-error); }
.form-input.has-error:focus { box-shadow: var(--ring-error); }
.form-input::placeholder { color: var(--color-text-muted); }

/* ===== 验证码 ===== */
.captcha-row {
  display: flex;
  align-items: stretch;
  gap: var(--space-3);
}
.captcha-row .form-input {
  flex: 1;
  min-width: 0;
}

/* 后端 SpecCaptcha 生成的就是 130×48，这里按原尺寸放，不缩放 */
.captcha-box {
  flex-shrink: 0;
  width: 130px;
  height: 48px;
  padding: 0;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  overflow: hidden;
  cursor: pointer;
  transition: border-color var(--transition-fast);
}
.captcha-box:hover:not(:disabled) { border-color: var(--color-primary-light); }
.captcha-box:disabled { cursor: wait; opacity: 0.7; }
.captcha-box img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.captcha-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.field-error {
  font-size: var(--fs-xs);
  color: var(--color-error);
}

.form-error {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-error-text);
  padding: var(--space-2) var(--space-4);
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-border);
  border-radius: var(--radius-md);
}

.auth-footer {
  margin-top: var(--space-6);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
}

.auth-link {
  color: var(--color-primary);
  font-weight: var(--fw-semibold);
}
.auth-link:hover { text-decoration: underline; }

@media (max-width: 480px) {
  .auth-title { font-size: var(--fs-xl); }
  .captcha-box { width: 110px; }
}
</style>
