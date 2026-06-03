<template>
  <div class="edit">
    <BaseHeader>
      <template v-slot:center>
        <div class="title">
          <span class="f16">编辑资料</span>
        </div>
      </template>
    </BaseHeader>
    <div class="userinfo">
      <!-- 头像 -->
      <div class="change-avatar">
        <div class="avatar-ctn" @click="showAvatarDialog">
          <img class="avatar" :src="avatarUrl" alt="" />
          <img class="change" src="../../../assets/img/icon/me/camera-light.png" alt="" />
        </div>
        <span>点击更换头像</span>
      </div>

      <!-- 背景图 -->
      <div class="change-cover" @click="showCoverDialog">
        <div class="cover-preview" v-if="coverUrl">
          <img :src="coverUrl" alt="" />
        </div>
        <div class="cover-placeholder" v-else>
          <Icon icon="mdi:image-area" />
        </div>
        <span>点击更换主页背景</span>
      </div>

      <div class="row" @click="nav('/me/edit-userinfo-item', { type: 1 })">
        <div class="left">名字</div>
        <div class="right">
          <span>{{ store.userinfo.nickname || '点击设置' }}</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="nav('/me/edit-userinfo-item', { type: 2 })">
        <div class="left">抖音号</div>
        <div class="right">
          <span>{{ _getUserDouyinId({ author: store.userinfo }) || '点击设置' }}</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="nav('/me/edit-userinfo-item', { type: 3 })">
        <div class="left">简介</div>
        <div class="right">
          <span>{{ store.userinfo.signature || '点击设置' }}</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="showSexDialog">
        <div class="left">性别</div>
        <div class="right">
          <span>{{ sex || '点击设置' }}</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="showBirthdayDialog">
        <div class="left">生日</div>
        <div class="right">
          <span>{{ store.userinfo.birthday || '点击设置' }}</span>
          <div v-show="false" id="trigger1"></div>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="nav('/me/choose-location')">
        <div class="left">所在地</div>
        <div class="right">
          <span v-if="store.userinfo.province || store.userinfo.city">
            {{ store.userinfo.province }}{{ store.userinfo.province && store.userinfo.city ? ' - ' : '' }}{{ store.userinfo.city }}
          </span>
          <span v-else>点击设置</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
      <div class="row" @click="nav('/me/add-school')">
        <div class="left">学校</div>
        <div class="right">
          <span>{{ store.userinfo.school?.name || '点击设置' }}</span>
          <dy-back scale=".8" direction="right"></dy-back>
        </div>
      </div>
    </div>

    <!-- 隐藏的文件选择器 -->
    <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarFile" />
    <input ref="coverInput" type="file" accept="image/*" style="display:none" @change="onCoverFile" />

    <transition name="fade">
      <div class="preview-img" v-if="data.previewImg" @click="data.previewImg = ''">
        <img class="resource" :src="data.previewImg" alt="" />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import MobileSelect from '../../../components/mobile-select/mobile-select'
import { useBaseStore } from '@/store/pinia'
import {
  _checkImgUrl,
  _getUserDouyinId,
  _hideLoading,
  _no,
  _notice,
  _showLoading,
  _showSelectDialog,
  _sleep
} from '@/utils'
import { computed, reactive, ref } from 'vue'
import { useNav } from '@/utils/hooks/useNav'
import { updateProfile, updateAvatar, updateCover, uploadImage } from '@/api/user'

defineOptions({ name: 'EditUserInfo' })

const store = useBaseStore()
const nav = useNav()
const avatarInput = ref<HTMLInputElement>()
const coverInput = ref<HTMLInputElement>()

const data = reactive({
  sexList: [
    { id: 1, name: '男' },
    { id: 2, name: '女' },
    { id: 0, name: '不展示' }
  ],
  avatarList: [
    { id: 1, name: '拍一张' },
    { id: 2, name: '从相册选择' },
    { id: 3, name: '查看大图' },
    { id: 4, name: '取消' }
  ],
  coverList: [
    { id: 1, name: '拍一张' },
    { id: 2, name: '从相册选择' },
    { id: 4, name: '取消' }
  ],
  previewImg: ''
})

const avatarUrl = computed(() => {
  const url = store.userinfo.avatar_168x168?.url_list?.[0]
  return url ? _checkImgUrl(url) : ''
})

const coverUrl = computed(() => {
  const url = store.userinfo.cover_url?.[0]?.url_list?.[0]
  return url ? _checkImgUrl(url) : ''
})

const sex = computed(() => {
  switch (Number(store.userinfo.gender)) {
    case 1: return '男'
    case 2: return '女'
    default: return ''
  }
})

function showSexDialog() {
  _showSelectDialog(data.sexList, async (e) => {
    _showLoading()
    try {
      await updateProfile({ gender: e.id })
      store.setUserinfo({ gender: e.id })
    } catch { /* ignore */ }
    _hideLoading()
  })
}

function showAvatarDialog() {
  _showSelectDialog(data.avatarList, (e) => {
    switch (e.id) {
      case 1:
      case 2:
        avatarInput.value?.click()
        break
      case 3:
        if (avatarUrl.value) data.previewImg = avatarUrl.value
        break
    }
  })
}

function showCoverDialog() {
  _showSelectDialog(data.coverList, (e) => {
    switch (e.id) {
      case 1:
      case 2:
        coverInput.value?.click()
        break
    }
  })
}

async function onAvatarFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  _showLoading()
  try {
    const res = await uploadImage(file)
    if (res.success && res.data.url) {
      await updateAvatar(res.data.url)
      store.setUserinfo({
        avatar_168x168: { url_list: [res.data.url] },
        avatar_300x300: { url_list: [res.data.url] }
      })
      _notice('头像更新成功')
    } else {
      _notice('上传失败，请重试')
    }
  } catch {
    _notice('上传失败，请重试')
  }
  _hideLoading()
  // reset so same file can be re-selected
  ;(e.target as HTMLInputElement).value = ''
}

async function onCoverFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  _showLoading()
  try {
    const res = await uploadImage(file)
    if (res.success && res.data.url) {
      await updateCover(res.data.url)
      store.setUserinfo({
        cover_url: [{ url_list: [res.data.url] }],
        white_cover_url: [{ url_list: [res.data.url] }]
      })
      _notice('背景图更新成功')
    } else {
      _notice('上传失败，请重试')
    }
  } catch {
    _notice('上传失败，请重试')
  }
  _hideLoading()
  ;(e.target as HTMLInputElement).value = ''
}

function showBirthdayDialog() {
  new MobileSelect({
    trigger: '#trigger1',
    title: '生日',
    wheels: [
      { data: Array.apply(null, { length: 100 }).map((_v: any, i: number) => new Date().getFullYear() - i) },
      { data: Array.apply(null, { length: 12 }).map((_v: any, i: number) => 12 - i) },
      { data: Array.apply(null, { length: 31 }).map((_v: any, i: number) => 31 - i) }
    ],
    callback: async (indexArr: number[], wheelData: any[]) => {
      _showLoading()
      const birthday = wheelData.join('-')
      try {
        await updateProfile({ birthday })
        store.setUserinfo({ birthday })
      } catch { /* ignore */ }
      _hideLoading()
    }
  }).show()
}
</script>

<style scoped lang="less">
@import '../../../assets/less/index';

.edit {
  position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  overflow: auto; font-size: 14rem;
}

.title {
  display: flex; flex-direction: column; align-items: center;
}

.preview-img {
  z-index: 9; position: fixed; left: 0; right: 0; bottom: 0; top: 0;
  background: black; display: flex; align-items: center; justify-content: center;
  .resource { width: 100%; max-height: 100%; }
}

.userinfo {
  padding-top: 60rem; color: white;

  .change-avatar, .change-cover {
    display: flex; justify-content: center; align-items: center;
    flex-direction: column; margin: 20rem 0;
    @avatar-width: 80rem;

    .avatar-ctn {
      position: relative; display: flex; justify-content: center; align-items: center;
      margin-bottom: 10rem; width: @avatar-width; height: @avatar-width;

      .avatar {
        opacity: 0.5; position: absolute;
        width: @avatar-width; height: @avatar-width; border-radius: 50%;
      }
      .change { width: 28rem; z-index: 9; position: relative; }
    }

    .cover-preview, .cover-placeholder {
      width: 100%; height: 120rem; border-radius: 8rem; overflow: hidden;
      margin-bottom: 8rem;
      img { width: 100%; height: 100%; object-fit: cover; }
    }
    .cover-placeholder {
      background: var(--active-main-bg);
      display: flex; align-items: center; justify-content: center;
      svg { font-size: 40rem; color: var(--second-text-color); }
    }
  }
}
</style>
