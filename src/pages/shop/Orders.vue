<template>
  <div class="orders-page base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">我的订单</span>
      <span class="status-tabs">
        <span
          v-for="tab in tabs"
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key; fetchOrders()"
        >{{ tab.label }}</span>
      </span>
    </header>

    <div class="order-list" v-if="list.length">
      <div class="order-item" v-for="order in list" :key="order.id">
        <div class="o-header">
          <span class="o-status">{{ statusText(order.status) }}</span>
          <span class="o-time">{{ order.createTime?.slice(0, 10) }}</span>
        </div>
        <div class="o-body" @click="router.push('/shop/detail?id=' + order.goodsId)">
          <img class="o-cover" :src="_checkImgUrl(order.goodsCover)" />
          <div class="o-info">
            <div class="o-name">{{ order.goodsName }}</div>
            <div class="o-price">￥{{ order.price }} × {{ order.quantity }}</div>
            <div class="o-receiver" v-if="order.receiverName">
              收货人：{{ order.receiverName }} {{ order.receiverPhone }}
            </div>
          </div>
        </div>
        <div class="o-footer">
          <span class="o-total">合计：<b>￥{{ order.totalAmount }}</b></span>
          <span class="o-actions">
            <span class="act-btn" v-if="order.status === 'PENDING'" @click="doCancel(order.id)">取消订单</span>
            <span class="act-btn primary" v-if="order.status === 'SHIPPED'" @click="doReceive(order.id)">确认收货</span>
          </span>
        </div>
      </div>

      <div class="load-more" v-if="hasMore" @click="fetchOrders(true)">加载更多</div>
    </div>

    <div class="empty" v-else>
      <Icon icon="icon-park-outline:order" />
      <span>暂无订单</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrders, cancelOrder, receiveOrder } from '@/api/user'
import { _checkImgUrl } from '@/utils'
import { Icon } from '@iconify/vue'

defineOptions({ name: 'Orders' })

const router = useRouter()
const list = ref<any[]>([])
const activeTab = ref('')
const pageNo = ref(1)
const hasMore = ref(false)

const tabs = [
  { key: '', label: '全部' },
  { key: 'PENDING', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'RECEIVED', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' }
]

const statusTextMap: Record<string, string> = {
  PENDING: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  RECEIVED: '已完成',
  CANCELLED: '已取消'
}

function statusText(s: string) {
  return statusTextMap[s] || s
}

async function fetchOrders(loadMore = false) {
  if (!loadMore) { pageNo.value = 1; list.value = [] }
  try {
    const res: any = await getOrders({
      status: activeTab.value || undefined,
      pageNo: pageNo.value,
      pageSize: 10
    })
    const data = res.data || res
    const newList = data.list || data.records || []
    if (loadMore) {
      list.value.push(...newList)
    } else {
      list.value = newList
    }
    hasMore.value = newList.length >= 10
    pageNo.value++
  } catch { /* ignore */ }
}

async function doCancel(id: number) {
  try {
    await cancelOrder(id)
    fetchOrders()
  } catch { /* ignore */ }
}

async function doReceive(id: number) {
  try {
    await receiveOrder(id)
    fetchOrders()
  } catch { /* ignore */ }
}

onMounted(() => fetchOrders())
</script>

<style scoped lang="less">
.orders-page {
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

    svg { font-size: 22rem; cursor: pointer; }
    .title { font-size: 17rem; font-weight: 600; margin: 0 12rem; }
    .status-tabs {
      flex: 1;
      display: flex;
      overflow-x: auto;
      gap: 8rem;
      font-size: 13rem;
      color: #666;
      &::-webkit-scrollbar { display: none; }
      span {
        white-space: nowrap;
        cursor: pointer;
        padding: 4rem 8rem;
        border-radius: 12rem;
        &.active {
          color: #fe2c55;
          background: #fff0f3;
          font-weight: 600;
        }
      }
    }
  }

  .order-list {
    padding: 8rem 12rem;
  }

  .order-item {
    background: white;
    border-radius: 10rem;
    padding: 12rem;
    margin: 8rem 0;

    .o-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 8rem;
      border-bottom: 1px solid #f5f5f5;
      .o-status { font-size: 14rem; font-weight: 600; color: #fe2c55; }
      .o-time { font-size: 12rem; color: #999; }
    }
    .o-body {
      display: flex;
      gap: 10rem;
      padding: 10rem 0;
      cursor: pointer;
      .o-cover {
        width: 72rem;
        height: 72rem;
        border-radius: 6rem;
        object-fit: cover;
        flex-shrink: 0;
      }
      .o-info {
        flex: 1;
        .o-name { font-size: 14rem; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
        .o-price { font-size: 14rem; color: #999; margin-top: 4rem; }
        .o-receiver { font-size: 12rem; color: #999; margin-top: 2rem; }
      }
    }
    .o-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 8rem;
      border-top: 1px solid #f5f5f5;
      .o-total {
        font-size: 13rem;
        color: #333;
        b { color: #fe2c55; font-size: 15rem; }
      }
      .o-actions {
        display: flex;
        gap: 8rem;
        .act-btn {
          font-size: 13rem;
          padding: 5rem 12rem;
          border: 1px solid #ddd;
          border-radius: 14rem;
          cursor: pointer;
          &.primary {
            border-color: #fe2c55;
            color: #fe2c55;
          }
        }
      }
    }
  }

  .load-more {
    text-align: center;
    padding: 16rem;
    color: #999;
    font-size: 13rem;
    cursor: pointer;
  }

  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 40vh;
    color: #999;
    gap: 12rem;
    svg { font-size: 48rem; }
  }
}
</style>
