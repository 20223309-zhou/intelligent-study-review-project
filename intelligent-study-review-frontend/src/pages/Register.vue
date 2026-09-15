<script setup>
import { reactive, ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { registerUser } from '@/utils/auth.js'
import UiButton from '@/components/ui/UiButton.vue'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const formError = ref('')
const formSuccess = ref('')
const isLoading = ref(false)

const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: ''
})

const fieldErrors = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: ''
})

const isFormValid = computed(() => {
  return (
    form.userAccount.trim() !== '' &&
    form.userPassword !== '' &&
    form.checkPassword !== ''
  )
})

function validateField(field) {
  switch (field) {
    case 'userAccount':
      fieldErrors.userAccount = form.userAccount.trim() === ''
        ? '请输入用户名'
        : form.userAccount.trim().length < 4
          ? '用户名至少4个字符'
          : ''
      break
    case 'userPassword':
      fieldErrors.userPassword = form.userPassword === ''
        ? '请输入密码'
        : form.userPassword.length < 8
          ? '密码至少8位'
          : ''
      if (form.checkPassword && form.userPassword !== form.checkPassword) {
        fieldErrors.checkPassword = '两次密码不一致'
      } else if (form.checkPassword) {
        fieldErrors.checkPassword = ''
      }
      break
    case 'checkPassword':
      fieldErrors.checkPassword = form.checkPassword === ''
        ? '请确认密码'
        : form.userPassword !== form.checkPassword
          ? '两次密码不一致'
          : ''
      break
  }
}

function validateAll() {
  validateField('userAccount')
  validateField('userPassword')
  validateField('checkPassword')
  return !fieldErrors.userAccount && !fieldErrors.userPassword && !fieldErrors.checkPassword
}

// 跳登录页用的定时器，组件卸载时清掉（否则快速切页会对着已卸载组件 push）
let redirectTimer = null
onBeforeUnmount(() => { if (redirectTimer) clearTimeout(redirectTimer) })

async function handleSubmit() {
  formError.value = ''
  formSuccess.value = ''
  if (!validateAll() || !isFormValid.value) return

  isLoading.value = true
  try {
    const res = await registerUser(form.userAccount.trim(), form.userPassword, form.checkPassword)
    if (res.code === 0) {
      formSuccess.value = '注册成功！正在跳转到登录页...'
      redirectTimer = setTimeout(() => router.push('/login'), 1200)
    } else {
      formError.value = res.message || '注册失败'
    }
  } catch (e) {
    formError.value = '网络错误，请稍后重试'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-header">
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">注册后即可使用智能试卷生成器</p>
    </div>

    <form class="auth-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label class="form-label" for="reg-account">用户名</label>
        <input
          id="reg-account"
          v-model="form.userAccount"
          type="text"
          class="form-input"
          :class="{ 'has-error': fieldErrors.userAccount }"
          placeholder="请输入用户名（至少4个字符）"
          autocomplete="username"
          @input="validateField('userAccount')"
          @blur="validateField('userAccount')"
        />
        <p v-if="fieldErrors.userAccount" class="field-error">{{ fieldErrors.userAccount }}</p>
      </div>

      <div class="form-group">
        <label class="form-label" for="reg-password">密码</label>
        <input
          id="reg-password"
          v-model="form.userPassword"
          type="password"
          class="form-input"
          :class="{ 'has-error': fieldErrors.userPassword }"
          placeholder="请设置密码（至少8位）"
          autocomplete="new-password"
          @input="validateField('userPassword')"
          @blur="validateField('userPassword')"
        />
        <p v-if="fieldErrors.userPassword" class="field-error">{{ fieldErrors.userPassword }}</p>
      </div>

      <div class="form-group">
        <label class="form-label" for="reg-check">确认密码</label>
        <input
          id="reg-check"
          v-model="form.checkPassword"
          type="password"
          class="form-input"
          :class="{ 'has-error': fieldErrors.checkPassword }"
          placeholder="请再次输入密码"
          autocomplete="new-password"
          @input="validateField('checkPassword')"
          @blur="validateField('checkPassword')"
        />
        <p v-if="fieldErrors.checkPassword" class="field-error">{{ fieldErrors.checkPassword }}</p>
      </div>

      <p v-if="formError" class="form-error">{{ formError }}</p>
      <p v-if="formSuccess" class="form-success">{{ formSuccess }}</p>

      <UiButton
        native-type="submit"
        variant="primary"
        size="lg"
        block
        :loading="isLoading"
        :disabled="!isFormValid"
      >
        注 册
      </UiButton>
    </form>

    <div class="auth-footer">
      已有账号？
      <router-link to="/login" class="auth-link">立即登录</router-link>
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

/* 固定 48px 高，与登录页一致 */
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

.form-success {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-success-text);
  padding: var(--space-2) var(--space-4);
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
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
}
</style>
