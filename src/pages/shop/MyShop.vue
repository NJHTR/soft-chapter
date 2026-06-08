<template>
  <div class="my-shop base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">我的商品</span>
      <span class="add-btn" @click="nav('/shop/publish')">发布</span>
    </header>

    <div class="stat-bar" v-if="list.length">
      <span>共 {{ total }} 件商品</span>
    </div>

    <div class="goods-list" v-if="list.length">
      <div class="goods-item" v-for="item in list" :key="item.id">
        <img
          class="g-cover"
          :src="_checkImgUrl(item.cover)"
          @click="nav('/shop/detail', {}, item)"
        />
        <div class="g-info">
          <div class="g-name" @click="nav('/shop/detail', {}, item)">{{ item.name }}</div>
          <div class="g-price">
            <span class="price-symbol">￥</span>
            <span class="price-num">{{ item.price }}</span>
            <span class="sold">已售 {{ item.sold || 0 }}</span>
          </div>
          <div class="g-status" :class="{ off: item.status === 0 }">
            {{ item.status === 1 ? '已上架' : '已下架' }}
          </div>
        </div>
        <div class="g-actions">
          <div class="act-btn" @click="editGoods(item)">编辑</div>
          <div class="act-btn" @click="toggleStatus(item)">
            {{ item.status === 1 ? '下架' : '上架' }}
          </div>
          <div class="act-btn danger" @click="handleDelete(item)">删除</div>
        </div>
      </div>
    </div>

    <div class="empty" v-else-if="!loading">
      <Icon icon="iconoir:shop-window" />
      <span>暂无商品</span>
      <div class="go-publish" @click="nav('/shop/publish')">去发布</div>
    </div>

    <div class="loading" v-if="loading">加载中...</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { Icon } from '@iconify/vue'
import { getMyGoods, deleteGoods, updateGoods } from '@/api/user'
import { _checkImgUrl } from '@/utils'

defineOptions({ name: 'MyShop' })

const router = useRouter()
const nav = useNav()

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await getMyGoods({ pageNo: 1, pageSize: 100 })
    const data = res.data || res
    list.value = data.list || data.records || []
    total.value = data.total || list.value.length
  } catch {
    /* ignore */
  }
  loading.value = false
})

function editGoods(item: any) {
  router.push({ path: '/shop/publish', state: { goods: item } })
}

async function toggleStatus(item: any) {
  const newStatus = item.status === 1 ? 0 : 1
  try {
    await updateGoods(item.id, { status: newStatus })
    item.status = newStatus
  } catch {
    /* ignore */
  }
}

async function handleDelete(item: any) {
  if (!confirm(`确认删除「${item.name}」？`)) return
  try {
    await deleteGoods(item.id)
    list.value = list.value.filter((g) => g.id !== item.id)
  } catch {
    /* ignore */
  }
}
</script>

<style scoped lang="less">
.my-shop {
  min-height: 100vh;
  background: #f5f5f5;
  color: #333;

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
    .add-btn {
      color: #fe2c55;
      font-size: 15rem;
      font-weight: 600;
      cursor: pointer;
    }
  }

  .stat-bar {
    padding: 12rem 16rem;
    font-size: 13rem;
    color: #999;
  }

  .goods-list {
    padding: 0 12rem;
  }

  .goods-item {
    background: white;
    border-radius: 10rem;
    padding: 12rem;
    margin-bottom: 8rem;
    display: flex;
    gap: 12rem;

    .g-cover {
      width: 90rem;
      height: 90rem;
      border-radius: 8rem;
      object-fit: cover;
      flex-shrink: 0;
      cursor: pointer;
    }

    .g-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6rem;
      min-width: 0;

      .g-name {
        font-size: 15rem;
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        cursor: pointer;
      }

      .g-price {
        .price-symbol {
          color: #fe2c55;
          font-size: 12rem;
        }
        .price-num {
          color: #fe2c55;
          font-size: 18rem;
          font-weight: 700;
        }
        .sold {
          margin-left: 8rem;
          font-size: 12rem;
          color: #999;
        }
      }

      .g-status {
        font-size: 12rem;
        color: #14bf5f;
        &.off {
          color: #999;
        }
      }
    }

    .g-actions {
      display: flex;
      flex-direction: column;
      gap: 6rem;
      justify-content: center;

      .act-btn {
        padding: 6rem 12rem;
        border-radius: 6rem;
        font-size: 12rem;
        text-align: center;
        background: #f5f5f5;
        cursor: pointer;
        &:active {
          background: #e8e8e8;
        }
        &.danger {
          color: #fe2c55;
        }
      }
    }
  }

  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 40vh;
    color: #999;
    gap: 12rem;

    svg {
      font-size: 48rem;
    }
    .go-publish {
      color: #fe2c55;
      font-size: 15rem;
      cursor: pointer;
      padding: 10rem 30rem;
      border: 1px solid #fe2c55;
      border-radius: 20rem;
    }
  }

  .loading {
    text-align: center;
    padding: 40rem;
    color: #999;
  }
}
</style>
