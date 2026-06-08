<template>
  <div class="payment-overlay" v-if="visible" @click.self="close">
    <div class="payment-dialog">
      <div class="header">
        <span class="title">微信扫码支付</span>
        <Icon icon="ep:close" class="close-icon" @click="close" />
      </div>
      <div class="body">
        <div class="amount">
          <span class="label">支付金额</span>
          <span class="price">￥{{ amount }}</span>
        </div>
        <div class="qrcode-wrap">
          <img :src="qrcodeUrl" alt="微信收款码" class="qrcode" />
        </div>
        <div class="tip">请使用微信扫一扫付款</div>
        <div class="tip sub">付款后请联系客服确认订单</div>
      </div>
      <div class="footer">
        <div class="btn" @click="close">已付款</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Icon } from '@iconify/vue'

defineOptions({ name: 'PaymentDialog' })

const visible = ref(false)
const amount = ref('0.00')

// 替换为你的微信收款二维码图片路径
const qrcodeUrl = ref('/qrcode.jpg')

function show(payAmount: string) {
  amount.value = payAmount
  visible.value = true
}

function close() {
  visible.value = false
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
    width: 300rem;
    background: white;
    border-radius: 16rem;
    overflow: hidden;

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16rem 20rem;
      border-bottom: 1px solid #f0f0f0;
      .title {
        font-size: 17rem;
        font-weight: 700;
      }
      .close-icon {
        font-size: 20rem;
        cursor: pointer;
        color: #999;
      }
    }

    .body {
      padding: 20rem;
      text-align: center;

      .amount {
        margin-bottom: 16rem;
        .label {
          font-size: 13rem;
          color: #999;
          margin-right: 8rem;
        }
        .price {
          font-size: 26rem;
          color: #fe2c55;
          font-weight: 700;
        }
      }

      .qrcode-wrap {
        width: 200rem;
        height: 200rem;
        margin: 0 auto 12rem;
        border: 1px solid #eee;
        border-radius: 8rem;
        display: flex;
        align-items: center;
        justify-content: center;

        .qrcode {
          width: 180rem;
          height: 180rem;
          object-fit: contain;
        }
      }

      .tip {
        font-size: 14rem;
        color: #333;
        margin-bottom: 4rem;
      }
      .tip.sub {
        font-size: 12rem;
        color: #999;
      }
    }

    .footer {
      padding: 0 20rem 20rem;
      .btn {
        width: 100%;
        padding: 12rem 0;
        text-align: center;
        background: #07c160;
        color: white;
        border-radius: 8rem;
        font-size: 16rem;
        font-weight: 600;
        cursor: pointer;
      }
    }
  }
}
</style>
