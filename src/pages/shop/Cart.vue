<template>
  <div class="cart-page base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">购物车</span>
      <span class="manage-btn" @click="isManage = !isManage">{{ isManage ? '完成' : '管理' }}</span>
    </header>

    <div class="cart-list" v-if="list.length">
      <div class="cart-item" v-for="item in list" :key="item.id">
        <div class="g-check" v-if="isManage" @click="handleRemove(item)">
          <Icon icon="material-symbols:delete-outline" style="color: #fe2c55; font-size: 22rem" />
        </div>
        <img
          class="g-cover"
          :src="_checkImgUrl(item.cover)"
          @click="nav('/shop/detail', {}, item)"
        />
        <div class="g-info">
          <div class="g-name">{{ item.name }}</div>
          <div class="g-price">￥{{ item.price }}</div>
        </div>
        <div class="g-qty" v-if="!isManage">
          <span class="qty-btn" @click="changeQty(item, -1)">-</span>
          <span class="qty-num">{{ item.qty || 1 }}</span>
          <span class="qty-btn" @click="changeQty(item, 1)">+</span>
        </div>
      </div>

      <div class="total-bar" v-if="!isManage">
        <span class="total-label">合计: </span>
        <span class="total-price">￥{{ totalPrice }}</span>
      </div>
      <div class="checkout-btn" v-if="!isManage" @click="doCheckout">结算 ({{ list.length }})</div>
    </div>

    <div class="empty" v-else>
      <Icon icon="icon-park-outline:shopping" />
      <span>购物车是空的</span>
      <div class="go-shop" @click="router.push('/shop')">去逛逛</div>
    </div>

    <PaymentDialog ref="payDialog" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { getCart, removeFromCart, updateCartQuantity } from '@/api/user'
import { _checkImgUrl } from '@/utils'
import { Icon } from '@iconify/vue'
import PaymentDialog from '@/components/dialog/PaymentDialog.vue'

defineOptions({ name: 'Cart' })

const router = useRouter()
const nav = useNav()
const list = ref<any[]>([])
const isManage = ref(false)
const payDialog = ref()

const totalPrice = computed(() => {
  return list.value
    .reduce((sum, item) => sum + (Number(item.price) || 0) * (item.qty || 1), 0)
    .toFixed(2)
})

onMounted(async () => {
  try {
    const res: any = await getCart()
    const data = res.data || res
    list.value = (data || []).map((entry: any) => ({
      cart_id: entry.cart_id,
      qty: entry.quantity || 1,
      ...entry.goods
    }))
  } catch {
    /* ignore */
  }
})

async function changeQty(item: any, delta: number) {
  const newQty = (item.qty || 1) + delta
  if (newQty < 1) return
  item.qty = newQty
  try {
    await updateCartQuantity(item.cart_id, newQty)
  } catch {
    /* ignore */
  }
}

async function handleRemove(item: any) {
  if (!confirm(`确认移除「${item.name}」？`)) return
  try {
    await removeFromCart(item.cart_id)
    list.value = list.value.filter((i) => i.cart_id !== item.cart_id)
  } catch {
    /* ignore */
  }
}

function doCheckout() {
  payDialog.value?.show(totalPrice.value)
}
</script>

<style scoped lang="less">
.cart-page {
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
    .manage-btn {
      color: #666;
      font-size: 14rem;
      cursor: pointer;
    }
  }

  .cart-list {
    padding: 0 12rem;
  }

  .cart-item {
    background: white;
    border-radius: 10rem;
    padding: 12rem;
    margin: 8rem 0;
    display: flex;
    gap: 12rem;
    align-items: center;

    .g-check {
      cursor: pointer;
      padding: 4rem;
    }
    .g-cover {
      width: 80rem;
      height: 80rem;
      border-radius: 8rem;
      object-fit: cover;
      flex-shrink: 0;
      cursor: pointer;
    }
    .g-info {
      flex: 1;
      .g-name {
        font-size: 14rem;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
      }
      .g-price {
        color: #fe2c55;
        font-size: 16rem;
        font-weight: 700;
        margin-top: 6rem;
      }
    }
    .g-qty {
      display: flex;
      align-items: center;
      gap: 8rem;
      .qty-btn {
        width: 24rem;
        height: 24rem;
        border-radius: 50%;
        background: #f5f5f5;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16rem;
        cursor: pointer;
        font-weight: 600;
      }
      .qty-num {
        font-size: 14rem;
        min-width: 20rem;
        text-align: center;
      }
    }
  }

  .total-bar {
    background: white;
    border-radius: 10rem;
    padding: 14rem 16rem;
    margin: 8rem 0;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 6rem;
    .total-label {
      font-size: 14rem;
      color: #666;
    }
    .total-price {
      color: #fe2c55;
      font-size: 20rem;
      font-weight: 700;
    }
  }

  .checkout-btn {
    position: fixed;
    bottom: 0;
    left: 0;
    width: 100vw;
    background: #fe2c55;
    color: white;
    text-align: center;
    padding: 14rem 0;
    font-size: 16rem;
    font-weight: 600;
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
    svg {
      font-size: 48rem;
    }
    .go-shop {
      color: #fe2c55;
      font-size: 15rem;
      cursor: pointer;
      padding: 10rem 30rem;
      border: 1px solid #fe2c55;
      border-radius: 20rem;
    }
  }
}
</style>
