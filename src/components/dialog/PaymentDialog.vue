<template>
  <div class="payment-overlay" v-if="visible" @click.self="close">
    <!-- Step 1: 收货信息 -->
    <div class="payment-dialog" v-if="step === 1">
      <div class="header">
        <span class="title">确认订单</span>
        <Icon icon="ep:close" class="close-icon" @click="close" />
      </div>
      <div class="body">
        <div class="amount">
          <span class="label">应付金额</span>
          <span class="price">￥{{ amount }}</span>
        </div>
        <div class="form">
          <input v-model="receiverName" placeholder="收货人姓名" />
          <input v-model="receiverPhone" placeholder="收货人电话" />
          <input v-model="receiverAddress" placeholder="收货地址" />
          <input v-model="remark" placeholder="备注（选填）" />
        </div>
      </div>
      <div class="footer">
        <div class="btn cancel" @click="close">取消</div>
        <div class="btn confirm" @click="doPlaceOrder" :class="{ loading: submitting }">
          {{ submitting ? '提交中...' : '提交订单' }}
        </div>
      </div>
    </div>

    <!-- Step 2: 选择支付方式 -->
    <div class="payment-dialog" v-if="step === 2">
      <div class="header">
        <span class="title">选择支付方式</span>
        <Icon icon="ep:close" class="close-icon" @click="close" />
      </div>
      <div class="body">
        <div class="amount">
          <span class="label">支付金额</span>
          <span class="price">￥{{ amount }}</span>
        </div>
        <div class="pay-methods">
          <div
            class="pay-item"
            :class="{ active: payMethod === 'wallet' }"
            @click="payMethod = 'wallet'"
          >
            <Icon icon="ri:wallet-3-fill" class="pay-icon wallet" />
            <div class="pay-info">
              <span>零钱支付</span>
              <span class="sub">余额 ￥{{ walletBalance }}</span>
            </div>
            <Icon v-if="payMethod === 'wallet'" icon="ep:check" class="check" />
          </div>
          <div
            class="pay-item"
            :class="{ active: payMethod === 'wechat' }"
            @click="payMethod = 'wechat'"
          >
            <Icon icon="ri:wechat-pay-fill" class="pay-icon wechat" />
            <span>微信支付</span>
            <Icon v-if="payMethod === 'wechat'" icon="ep:check" class="check" />
          </div>
          <div
            class="pay-item"
            :class="{ active: payMethod === 'alipay' }"
            @click="payMethod = 'alipay'"
          >
            <Icon icon="ri:alipay-fill" class="pay-icon alipay" />
            <span>支付宝</span>
            <Icon v-if="payMethod === 'alipay'" icon="ep:check" class="check" />
          </div>
          <div
            class="pay-item"
            :class="{ active: payMethod === 'bank' }"
            @click="payMethod = 'bank'"
          >
            <Icon icon="ri:bank-card-fill" class="pay-icon bank" />
            <span>银行卡</span>
            <Icon v-if="payMethod === 'bank'" icon="ep:check" class="check" />
          </div>
        </div>
      </div>
      <div class="footer">
        <div class="btn cancel" @click="step = 1">上一步</div>
        <div
          class="btn confirm"
          :class="{ disabled: !payMethod || (payMethod === 'wallet' && walletInsufficient) }"
          @click="payMethod === 'wallet' ? doPay() : (step = 3)"
        >
          {{ payMethod === 'wallet' ? (walletInsufficient ? '余额不足' : '立即支付') : '确认支付' }}
        </div>
      </div>
    </div>

    <!-- Step 3: 非零钱支付确认 -->
    <div class="payment-dialog" v-if="step === 3">
      <div class="header">
        <span class="title">确认支付</span>
        <Icon icon="ep:close" class="close-icon" @click="close" />
      </div>
      <div class="body pay-confirm">
        <div class="pay-icon-wrap">
          <Icon v-if="payMethod === 'wechat'" icon="ri:wechat-pay-fill" class="big-icon wechat" />
          <Icon v-else-if="payMethod === 'alipay'" icon="ri:alipay-fill" class="big-icon alipay" />
          <Icon v-else icon="ri:bank-card-fill" class="big-icon bank" />
        </div>
        <div class="pay-label">{{ payLabel }}</div>
        <div class="amount">￥{{ amount }}</div>
        <div class="tip">确认支付后将从您的账户扣款</div>
      </div>
      <div class="footer">
        <div class="btn cancel" @click="step = 2">上一步</div>
        <div class="btn confirm" @click="doPay" :class="{ loading: paying }">
          {{ paying ? '支付中...' : '立即支付' }}
        </div>
      </div>
    </div>

    <!-- Step 4: 支付成功 -->
    <div class="payment-dialog success-box" v-if="step === 4">
      <div class="body success">
        <Icon icon="ep:circle-check-filled" class="success-icon" />
        <div class="title">支付成功</div>
        <div class="amount">￥{{ amount }}</div>
        <div class="tip" v-if="payMethod === 'wallet'">已从零钱扣款</div>
        <div class="tip" v-else>请在「我的订单」中查看物流</div>
      </div>
      <div class="footer">
        <div class="btn" @click="close">完成</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Icon } from '@iconify/vue'
import { placeOrder, placeOrderFromCart, payOrder } from '@/api/user'
import { getBalance } from '@/api/wallet'

defineOptions({ name: 'PaymentDialog' })

const visible = ref(false)
const amount = ref('0.00')
const step = ref(1)
const submitting = ref(false)
const paying = ref(false)

const mode = ref<'single' | 'cart'>('single')
const goodsId = ref<number>(0)
const cartIds = ref<number[]>([])
const orderIds = ref<number[]>([])

const receiverName = ref('')
const receiverPhone = ref('')
const receiverAddress = ref('')
const remark = ref('')

const payMethod = ref<'wallet' | 'wechat' | 'alipay' | 'bank' | ''>('')
const walletBalance = ref('0.00')

const walletInsufficient = computed(() => {
  return Number(walletBalance.value) < Number(amount.value)
})

const payLabel = computed(() => {
  const map: Record<string, string> = { wallet: '零钱支付', wechat: '微信支付', alipay: '支付宝', bank: '银行卡支付' }
  return map[payMethod.value] || ''
})

function show(payAmount: string, options?: { goods_id?: number; cart_ids?: number[] }) {
  amount.value = payAmount
  step.value = 1
  submitting.value = false
  paying.value = false
  orderIds.value = []
  payMethod.value = ''

  receiverName.value = ''
  receiverPhone.value = ''
  receiverAddress.value = ''
  remark.value = ''

  if (options?.cart_ids?.length) {
    mode.value = 'cart'
    cartIds.value = options.cart_ids
    goodsId.value = 0
  } else if (options?.goods_id) {
    mode.value = 'single'
    goodsId.value = options.goods_id
    cartIds.value = []
  } else {
    mode.value = 'single'
    goodsId.value = 0
    cartIds.value = []
  }

  visible.value = true
}

async function fetchWalletBalance() {
  try {
    const res: any = await getBalance()
    if (res.data?.balance != null) walletBalance.value = Number(res.data.balance).toFixed(2)
  } catch { /* ignore */ }
}

async function doPlaceOrder() {
  if (submitting.value) return
  submitting.value = true
  try {
    const data = {
      receiver_name: receiverName.value,
      receiver_phone: receiverPhone.value,
      receiver_address: receiverAddress.value,
      remark: remark.value
    }
    if (mode.value === 'cart') {
      const res: any = await placeOrderFromCart({ ...data, cart_ids: cartIds.value } as any)
      const orders = res.data || res
      orderIds.value = (Array.isArray(orders) ? orders : []).map((o: any) => o.id)
    } else {
      const res: any = await placeOrder({ ...data, goods_id: goodsId.value } as any)
      const order = res.data || res
      orderIds.value = [order.id]
    }
    await fetchWalletBalance()
    step.value = 2
  } catch {
    /* ignore */
  } finally {
    submitting.value = false
  }
}

function genIdempotencyKey(): string {
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

async function doPay() {
  if (paying.value) return
  if (payMethod.value === 'wallet' && walletInsufficient.value) return
  paying.value = true
  try {
    for (const id of orderIds.value) {
      const key = genIdempotencyKey()
      await payOrder(id, payMethod.value, key)
    }
    step.value = 4
  } catch {
    /* ignore */
  } finally {
    paying.value = false
  }
}

function close() {
  visible.value = false
  if (step.value === 4) {
    window.location.reload()
  }
}

defineExpose({ show })
</script>

<style scoped lang="less">
.payment-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.5);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;

  .payment-dialog {
    width: 340rem;
    max-height: 90vh;
    overflow-y: auto;
    background: white;
    border-radius: 16rem;

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16rem 20rem;
      border-bottom: 1px solid #f0f0f0;
      .title { font-size: 17rem; font-weight: 700; }
      .close-icon { font-size: 20rem; cursor: pointer; color: #999; }
    }

    .body {
      padding: 20rem;
      text-align: center;

      .amount {
        margin-bottom: 16rem;
        .label { font-size: 13rem; color: #999; margin-right: 8rem; }
        .price { font-size: 26rem; color: #fe2c55; font-weight: 700; }
      }

      .form {
        display: flex;
        flex-direction: column;
        gap: 10rem;
        input {
          width: 100%;
          padding: 10rem 12rem;
          border: 1px solid #e5e5e5;
          border-radius: 8rem;
          font-size: 14rem;
          outline: none;
          box-sizing: border-box;
          &:focus { border-color: #fe2c55; }
        }
      }

      .pay-methods {
        display: flex;
        flex-direction: column;
        gap: 8rem;

        .pay-item {
          display: flex;
          align-items: center;
          gap: 12rem;
          padding: 12rem 14rem;
          border: 2px solid #eee;
          border-radius: 10rem;
          cursor: pointer;
          font-size: 15rem;
          transition: border-color 0.2s;

          &.active { border-color: #fe2c55; background: #fff5f7; }

          .pay-icon {
            font-size: 26rem;
            &.wallet { color: #fe2c55; }
            &.wechat { color: #07c160; }
            &.alipay { color: #1677ff; }
            &.bank { color: #f5a623; }
          }

          .pay-info {
            flex: 1;
            text-align: left;
            .sub { display: block; font-size: 12rem; color: #999; }
          }

          .check { color: #fe2c55; font-size: 18rem; flex-shrink: 0; }
        }
      }

      &.pay-confirm {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12rem;
        padding: 30rem 20rem;

        .pay-icon-wrap {
          .big-icon {
            font-size: 56rem;
            &.wechat { color: #07c160; }
            &.alipay { color: #1677ff; }
            &.bank { color: #f5a623; }
          }
        }
        .pay-label { font-size: 16rem; font-weight: 600; }
        .amount { font-size: 28rem; color: #fe2c55; font-weight: 700; margin: 0; }
        .tip { font-size: 13rem; color: #999; }
      }

      &.success {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8rem;
        padding: 30rem 20rem;
        .success-icon { font-size: 48rem; color: #07c160; }
        .title { font-size: 18rem; font-weight: 700; }
        .amount { font-size: 22rem; color: #fe2c55; font-weight: 700; margin: 0; }
        .tip { font-size: 13rem; color: #999; }
      }
    }

    .footer {
      display: flex;
      gap: 12rem;
      padding: 0 20rem 20rem;

      .btn {
        flex: 1;
        padding: 12rem 0;
        text-align: center;
        border-radius: 8rem;
        font-size: 16rem;
        font-weight: 600;
        cursor: pointer;
        &.cancel { background: #f5f5f5; color: #666; }
        &.confirm {
          background: #fe2c55;
          color: white;
          &.loading { opacity: 0.6; pointer-events: none; }
          &.disabled { opacity: 0.4; pointer-events: none; }
        }
      }
    }
  }
}
</style>
