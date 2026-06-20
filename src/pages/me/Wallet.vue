<template>
  <div class="wallet-page base-page">
    <header class="top-bar">
      <Icon icon="material-symbols-light:arrow-back-ios-new" @click="router.back()" />
      <span class="title">我的钱包</span>
      <span></span>
    </header>

    <div class="balance-card">
      <div class="label">账户余额（元）</div>
      <div class="value">￥{{ balance }}</div>
      <div class="actions">
        <div class="act-btn" @click="showRecharge = true">充值</div>
      </div>
    </div>

    <!-- 充值区域 -->
    <div class="recharge-section" v-if="showRecharge">
      <div class="section-title">选择充值金额</div>
      <div class="amount-grid">
        <div
          v-for="amt in presetAmounts"
          :key="amt"
          class="amount-item"
          :class="{ active: rechargeAmount === amt }"
          @click="rechargeAmount = amt"
        >￥{{ amt }}</div>
        <div class="amount-item custom">
          <input v-model="customAmount" placeholder="自定义" type="number" @focus="rechargeAmount = 0" />
        </div>
      </div>
      <div class="recharge-btn" @click="doRecharge" :class="{ loading: recharging }">
        {{ recharging ? '充值中...' : '确认充值' }}
      </div>
    </div>

    <!-- 交易明细 -->
    <div class="tx-section">
      <div class="section-title">交易明细</div>
      <div class="tx-list" v-if="txList.length">
        <div class="tx-item" v-for="tx in txList" :key="tx.id">
          <div class="tx-left">
            <div class="tx-type">{{ tx.remark || typeMap[tx.type] }}</div>
            <div class="tx-time">{{ tx.createTime?.slice(0, 16) }}</div>
          </div>
          <div class="tx-right">
            <div class="tx-amount" :class="{ green: tx.type === 'RECHARGE', red: tx.type === 'PAY' }">
              {{ tx.type === 'RECHARGE' ? '+' : '' }}{{ tx.amount }}
            </div>
            <div class="tx-balance">余额 {{ tx.balanceAfter }}</div>
          </div>
        </div>
      </div>
      <div class="tx-empty" v-else>暂无交易记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { getBalance, recharge, getTransactions } from '@/api/wallet'

defineOptions({ name: 'Wallet' })

const router = useRouter()
const balance = ref('0.00')
const showRecharge = ref(false)
const rechargeAmount = ref(0)
const customAmount = ref('')
const recharging = ref(false)
const txList = ref<any[]>([])

const presetAmounts = [10, 50, 100, 200, 500]

const typeMap: Record<string, string> = {
  RECHARGE: '充值',
  PAY: '支付',
  REFUND: '退款'
}

const finalAmount = computed(() => {
  if (rechargeAmount.value > 0) return rechargeAmount.value
  return Number(customAmount.value) || 0
})

onMounted(async () => {
  try {
    const [balRes, txRes]: any[] = await Promise.all([
      getBalance(),
      getTransactions({ pageNo: 1, pageSize: 50 })
    ])
    if (balRes.data?.balance != null) balance.value = Number(balRes.data.balance).toFixed(2)
    const txData = txRes.data || txRes
    txList.value = txData.list || txData.records || []
  } catch { /* ignore */ }
})

async function doRecharge() {
  const amt = finalAmount.value
  if (amt <= 0) return
  recharging.value = true
  try {
    const res: any = await recharge(amt)
    if (res.data?.balance != null) balance.value = Number(res.data.balance).toFixed(2)
    showRecharge.value = false
    rechargeAmount.value = 0
    customAmount.value = ''
    // refresh tx list
    const txRes: any = await getTransactions({ pageNo: 1, pageSize: 50 })
    const txData = txRes.data || txRes
    txList.value = txData.list || txData.records || []
  } catch { /* ignore */ }
  recharging.value = false
}
</script>

<style scoped lang="less">
.wallet-page {
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
    .title { flex: 1; text-align: center; font-size: 17rem; font-weight: 600; }
  }

  .balance-card {
    background: linear-gradient(135deg, #fe2c55, #ff6b81);
    margin: 16rem;
    border-radius: 16rem;
    padding: 24rem 20rem;
    color: white;
    text-align: center;

    .label { font-size: 13rem; opacity: 0.85; }
    .value { font-size: 40rem; font-weight: 700; margin: 8rem 0 16rem; }
    .actions {
      display: flex;
      justify-content: center;
      .act-btn {
        padding: 8rem 32rem;
        border: 1px solid rgba(255,255,255,0.8);
        border-radius: 20rem;
        font-size: 14rem;
        cursor: pointer;
      }
    }
  }

  .recharge-section {
    background: white;
    margin: 0 16rem 16rem;
    border-radius: 12rem;
    padding: 16rem;
  }

  .section-title {
    font-size: 15rem;
    font-weight: 600;
    margin-bottom: 12rem;
  }

  .amount-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10rem;
    .amount-item {
      width: calc(33.33% - 7rem);
      padding: 14rem 0;
      text-align: center;
      border: 1px solid #eee;
      border-radius: 8rem;
      font-size: 16rem;
      font-weight: 600;
      cursor: pointer;
      &.active { border-color: #fe2c55; color: #fe2c55; background: #fff5f7; }
      &.custom {
        input {
          width: 100%;
          text-align: center;
          border: none;
          font-size: 14rem;
          outline: none;
        }
      }
    }
  }

  .recharge-btn {
    margin-top: 14rem;
    padding: 12rem 0;
    text-align: center;
    background: #fe2c55;
    color: white;
    border-radius: 8rem;
    font-size: 16rem;
    font-weight: 600;
    cursor: pointer;
    &.loading { opacity: 0.6; pointer-events: none; }
  }

  .tx-section {
    background: white;
    margin: 0 16rem;
    border-radius: 12rem;
    padding: 16rem;
  }

  .tx-list {
    .tx-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12rem 0;
      border-bottom: 1px solid #f5f5f5;
      &:last-child { border-bottom: none; }
      .tx-left {
        .tx-type { font-size: 14rem; }
        .tx-time { font-size: 12rem; color: #999; margin-top: 2rem; }
      }
      .tx-right { text-align: right;
        .tx-amount { font-size: 15rem; font-weight: 600;
          &.green { color: #07c160; }
          &.red { color: #333; }
        }
        .tx-balance { font-size: 12rem; color: #999; margin-top: 2rem; }
      }
    }
  }

  .tx-empty { text-align: center; padding: 30rem 0; color: #999; font-size: 14rem; }
}
</style>
