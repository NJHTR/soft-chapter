<template>
  <div class="password-setting">
    <BaseHeader @back="$router.back()">
      <template v-slot:center>
        <span class="f16">{{ isNewPassword ? '设置密码' : '修改密码' }}</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="notice">{{ isNewPassword ? '您当前未设置密码，建议设置密码以便使用邮箱密码登录' : '修改您的登录密码' }}</div>
      <div class="input-ctn">
        <input v-model="password" type="password" autocomplete="new-password" placeholder="请输入新密码（至少6位）" />
      </div>
      <div class="input-ctn" style="margin-top: 1rem" v-if="!isNewPassword">
        <input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入新密码" />
      </div>
      <div class="input-ctn" style="margin-top: 1rem">
        <input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="请确认密码" />
      </div>
      <div class="notice error" v-if="errorMsg">{{ errorMsg }}</div>
      <dy-button type="primary" :loading="loading" :disabled="!canSave" @click="save">
        {{ loading ? '保存中...' : '保存' }}
      </dy-button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { _hideLoading, _notice, _showLoading } from '@/utils'
import { setUserPassword, hasPassword } from '@/api/auth'
import { useBaseStore } from '@/store/pinia'

defineOptions({ name: 'PasswordSetting' })

const router = useRouter()
const store = useBaseStore()

const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const errorMsg = ref('')
const isNewPassword = ref(true)

const canSave = computed(() => {
  return password.value.length >= 6 && confirmPassword.value.length >= 6
})

onMounted(async () => {
  try {
    const res = await hasPassword()
    if (res.success) {
      isNewPassword.value = !res.data
    }
  } catch { /* ignore */ }
})

async function save() {
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await setUserPassword(password.value)
    if (res.success) {
      store.setUserinfo({ has_password: true })
      _notice(isNewPassword.value ? '密码设置成功' : '密码修改成功')
      router.back()
    } else {
      errorMsg.value = (res as any).msg || '保存失败'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="less">
@import '../../../assets/less/index';

.password-setting {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  color: white; font-size: 14rem;

  .content {
    padding-top: 70rem;
    padding: 80rem 20rem 20rem;

    .notice {
      font-size: 12rem; color: var(--second-text-color); margin-bottom: 20rem;
      &.error { color: #ff4d4f; margin-top: 10rem; }
    }

    .input-ctn {
      border-bottom: 1px solid var(--line-color);
      input {
        margin: 5rem 0; color: white; height: 30rem; width: 100%;
        outline: none; border: none; background: transparent;
        &::placeholder { color: var(--second-text-color); }
      }
    }

    .button { margin-top: 30rem; width: 100%; }
  }
}
</style>
