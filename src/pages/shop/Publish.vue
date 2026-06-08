<template>
  <div class="publish-goods base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">{{ isEdit ? '编辑商品' : '发布商品' }}</span>
      <span class="submit" @click="handleSubmit">发布</span>
    </header>

    <div class="form">
      <!-- 封面图 -->
      <div class="section">
        <div class="label">封面图</div>
        <div class="cover-upload" @click="triggerCoverInput">
          <img v-if="form.cover" :src="form.cover" class="cover-preview" />
          <div v-else class="cover-placeholder">
            <Icon icon="ic:baseline-plus" />
            <span>上传封面</span>
          </div>
        </div>
        <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverChange" />
      </div>

      <!-- 商品图片 -->
      <div class="section">
        <div class="label">商品图片</div>
        <div class="img-list">
          <div v-for="(url, i) in form.imgList" :key="i" class="img-item" @click="removeImg(i)">
            <img :src="url" />
            <div class="remove-badge">×</div>
          </div>
          <div class="img-add" @click="triggerImgInput">
            <Icon icon="ic:baseline-plus" />
          </div>
        </div>
        <input ref="imgInput" type="file" accept="image/*" multiple hidden @change="onImgsChange" />
      </div>

      <!-- 基本信息 -->
      <div class="section">
        <div class="label">商品名称</div>
        <input v-model="form.name" class="input" placeholder="请输入商品名称" maxlength="100" />
      </div>

      <div class="section row">
        <div class="field">
          <div class="label">原价 (¥)</div>
          <input v-model="form.price" class="input" type="number" step="0.01" placeholder="0.00" />
        </div>
        <div class="field">
          <div class="label">券后价 (¥)</div>
          <input
            v-model="form.real_price"
            class="input"
            type="number"
            step="0.01"
            placeholder="选填"
          />
        </div>
      </div>

      <div class="section">
        <div class="label">优惠标签</div>
        <input v-model="form.discount" class="input" placeholder="如: 满200减20" maxlength="50" />
      </div>

      <div class="section">
        <div class="label">商品描述</div>
        <textarea
          v-model="form.description"
          class="textarea"
          placeholder="请输入商品描述"
          maxlength="500"
        />
      </div>

      <div class="section">
        <div class="label">保障信息</div>
        <input
          v-model="form.guarantee"
          class="input"
          placeholder="如: 假一赔四·运费险·极速退款"
          maxlength="500"
        />
      </div>

      <div class="section">
        <div class="label">规格参数</div>
        <input
          v-model="form.specsInput"
          class="input"
          placeholder="多个规格用逗号分隔，如: 黑色,白色,红色"
          maxlength="500"
        />
        <div style="font-size: 11rem; color: #999; margin-top: 4rem">
          填写后商品详情页会展示「选择」「参数」入口
        </div>
      </div>

      <div class="section row">
        <div class="field">
          <div class="label">发货地</div>
          <input v-model="form.shipping_from" class="input" placeholder="如: 四川成都" />
        </div>
        <div class="field">
          <div class="label">运费 (¥)</div>
          <input
            v-model="form.shipping_fee"
            class="input"
            type="number"
            step="0.01"
            placeholder="0=免运费"
          />
        </div>
      </div>

      <div class="section">
        <div class="label">发货时间</div>
        <input
          v-model="form.shipping_time"
          class="input"
          placeholder="如: 48小时内发货"
          maxlength="100"
        />
      </div>
    </div>

    <div class="loading-mask" v-if="uploading">
      <span>上传中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { uploadImage, publishGoods, updateGoods } from '@/api/user'
import { _checkImgUrl } from '@/utils'
import { Icon } from '@iconify/vue'

defineOptions({ name: 'PublishGoods' })

const router = useRouter()
const route = useRoute()

const coverInput = ref<HTMLInputElement>()
const imgInput = ref<HTMLInputElement>()
const uploading = ref(false)

const isEdit = ref(false)
const editId = ref<number | null>(null)

const form = reactive({
  name: '',
  price: '',
  real_price: '',
  cover: '',
  imgList: [] as string[],
  description: '',
  discount: '',
  guarantee: '',
  specsInput: '',
  shipping_from: '',
  shipping_fee: '',
  shipping_time: ''
})

onMounted(() => {
  const editData = history.state?.goods
  if (editData) {
    isEdit.value = true
    editId.value = editData.id
    form.name = editData.name || ''
    form.price = editData.price || ''
    form.real_price = editData.real_price || ''
    form.cover = editData.cover || ''
    form.imgList = editData.imgs || []
    form.description = editData.description || ''
    form.discount = editData.discount || ''
    form.guarantee = editData.guarantee || ''
    form.shipping_from = editData.shipping_from || ''
    form.shipping_fee = editData.shipping_fee != null ? String(editData.shipping_fee) : ''
    form.shipping_time = editData.shipping_time || ''
    if (editData.specs) {
      try {
        form.specsInput = JSON.parse(editData.specs).join(', ')
      } catch {
        form.specsInput = editData.specs
      }
    }
  }
})

function triggerCoverInput() {
  coverInput.value?.click()
}
function triggerImgInput() {
  imgInput.value?.click()
}

async function onCoverChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadImage(file)
    form.cover = (res as any).data?.url || (res as any).url || ''
    if (!form.cover) {
      const d = (res as any).data
      form.cover = typeof d === 'string' ? d : ''
    }
  } catch {
    /* ignore */
  }
  uploading.value = false
}

async function onImgsChange(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files?.length) return
  uploading.value = true
  for (const file of Array.from(files)) {
    try {
      const res = await uploadImage(file)
      const url =
        (res as any).data?.url ||
        (res as any).url ||
        (typeof (res as any).data === 'string' ? (res as any).data : '')
      if (url) form.imgList.push(url)
    } catch {
      /* ignore */
    }
  }
  uploading.value = false
}

function removeImg(index: number) {
  form.imgList.splice(index, 1)
}

async function handleSubmit() {
  if (!form.name) return alert('请输入商品名称')
  if (!form.price) return alert('请输入商品价格')

  uploading.value = true
  try {
    // 规格: 逗号分隔字符串转 JSON 数组
    let specsJson = undefined
    if (form.specsInput.trim()) {
      const arr = form.specsInput
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
      if (arr.length) specsJson = JSON.stringify(arr)
    }

    const payload = {
      name: form.name,
      price: form.price,
      real_price: form.real_price || undefined,
      cover: form.cover || undefined,
      imgs: form.imgList.length ? JSON.stringify(form.imgList) : undefined,
      description: form.description || undefined,
      discount: form.discount || undefined,
      guarantee: form.guarantee || undefined,
      specs: specsJson,
      shipping_from: form.shipping_from || undefined,
      shipping_fee: form.shipping_fee || undefined,
      shipping_time: form.shipping_time || undefined
    }

    if (isEdit.value && editId.value) {
      await updateGoods(editId.value, payload)
    } else {
      await publishGoods(payload)
    }
    router.back()
  } catch (e: any) {
    alert(e?.message || '发布失败')
  }
  uploading.value = false
}
</script>

<style scoped lang="less">
.publish-goods {
  min-height: 100vh;
  background: #f5f5f5;
  color: #333;
  padding-bottom: 40rem;

  .top-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    background: white;
    display: flex;
    align-items: center;
    padding: 0 16rem;
    height: 48rem;
    border-bottom: 1px solid #eee;

    svg {
      font-size: 22rem;
      cursor: pointer;
    }
    .title {
      flex: 1;
      text-align: center;
      font-size: 17rem;
      font-weight: 600;
    }
    .submit {
      color: #fe2c55;
      font-size: 15rem;
      font-weight: 600;
      cursor: pointer;
    }
  }

  .form {
    padding: 12rem;
  }

  .section {
    background: white;
    border-radius: 10rem;
    padding: 14rem;
    margin-bottom: 10rem;

    .label {
      font-size: 14rem;
      font-weight: 600;
      margin-bottom: 10rem;
      color: #333;
    }

    &.row {
      display: flex;
      gap: 12rem;
      .field {
        flex: 1;
      }
    }
  }

  .cover-upload {
    width: 120rem;
    height: 120rem;
    border-radius: 8rem;
    overflow: hidden;
    cursor: pointer;

    .cover-placeholder {
      width: 100%;
      height: 100%;
      background: #f0f0f0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #999;
      font-size: 12rem;
      gap: 4rem;
      svg {
        font-size: 28rem;
      }
    }

    .cover-preview {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .img-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8rem;

    .img-item {
      width: 80rem;
      height: 80rem;
      border-radius: 6rem;
      overflow: hidden;
      position: relative;
      cursor: pointer;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .remove-badge {
        position: absolute;
        top: 2rem;
        right: 2rem;
        width: 18rem;
        height: 18rem;
        border-radius: 50%;
        background: rgba(0, 0, 0, 0.5);
        color: white;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13rem;
        line-height: 1;
      }
    }

    .img-add {
      width: 80rem;
      height: 80rem;
      border-radius: 6rem;
      background: #f0f0f0;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      svg {
        font-size: 28rem;
        color: #999;
      }
    }
  }

  .input {
    width: 100%;
    box-sizing: border-box;
    height: 42rem;
    border: 1px solid #eee;
    border-radius: 8rem;
    padding: 0 12rem;
    font-size: 15rem;
    background: #fafafa;
    outline: none;
    &:focus {
      border-color: #fe2c55;
    }
  }

  .textarea {
    width: 100%;
    box-sizing: border-box;
    height: 120rem;
    border: 1px solid #eee;
    border-radius: 8rem;
    padding: 12rem;
    font-size: 15rem;
    background: #fafafa;
    resize: none;
    outline: none;
    &:focus {
      border-color: #fe2c55;
    }
  }

  .loading-mask {
    position: fixed;
    inset: 0;
    z-index: 99;
    background: rgba(0, 0, 0, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 16rem;
  }
}
</style>
