<template>
  <div class="goods-detail base-page" ref="page" @scroll="scroll">
    <header ref="header">
      <div class="top">
        <Icon @click="router.back()" icon="material-symbols-light:arrow-back-ios-new" />
        <div class="right">
          <div class="search">
            <Icon icon="jam:search" />
            <div class="placeholder">多功能电源插座</div>
          </div>
          <div class="option">
            <Icon icon="jam:search" />
            <Icon
              icon="mynaui:star"
              :class="{ favorited: state.isFavorited }"
              @click="doFavorite"
            />
            <Icon icon="ph:share-fat" @click="doShare" />
          </div>
        </div>
      </div>
    </header>
    <header class="shadow" ref="headerShadow">
      <div class="top">
        <Icon @click="router.back()" icon="material-symbols-light:arrow-back-ios-new" />
        <div class="right">
          <div class="search">
            <Icon icon="jam:search" />
            <div class="placeholder">多功能电源插座</div>
          </div>
          <div class="option">
            <Icon
              icon="mynaui:star"
              :class="{ favorited: state.isFavorited }"
              @click="doFavorite"
            />
            <Icon icon="ph:share-fat" @click="doShare" />
          </div>
        </div>
      </div>
      <div class="bottom">
        <div
          class="tab"
          :class="{ active: currentTab === 'goods' }"
          @click="scrollToSection('goods')"
        >
          <div class="text">商品</div>
        </div>
        <div
          class="tab"
          :class="{ active: currentTab === 'review' }"
          @click="scrollToSection('review')"
        >
          <div class="text">评价</div>
        </div>
        <div
          class="tab"
          :class="{ active: currentTab === 'detail' }"
          @click="scrollToSection('detail')"
        >
          <div class="text">详情</div>
        </div>
        <div
          class="tab"
          :class="{ active: currentTab === 'recommend' }"
          @click="scrollToSection('recommend')"
        >
          <div class="text">推荐</div>
        </div>
      </div>
    </header>

    <div class="slide-imgs">
      <SlideHorizontal v-model:index="state.index">
        <SlideItem v-for="(item, i) in state.detail.imgs" :key="i">
          <img v-lazy="_checkImgUrl(item)" alt="" />
        </SlideItem>
      </SlideHorizontal>
      <div class="index">{{ state.index + 1 }}/{{ state.detail.imgs.length }}</div>
    </div>
    <div class="content p" ref="sectionGoods">
      <div class="info">
        <div class="price-wrap">
          <div class="price">
            <span class="symbol">￥</span>
            <span class="int">{{ state.detail.price }}</span>
            <!--            <span class="decimal">.8</span>-->
          </div>
          <div class="discount">
            <span class="text">热销款券后</span>
            <div class="price">
              <span class="symbol">￥</span>
              <span class="int">{{ state.detail.real_price }}</span>
              <!--              <span class="decimal">.9</span>-->
            </div>
          </div>
        </div>
        <div class="name">{{ state.detail.name }}</div>
        <div class="num">已售{{ state.detail.sold }}</div>
      </div>

      <div class="card desc-wrapper">
        <div class="item" v-if="state.detail.guarantee">
          <div class="label">保障</div>
          <div class="desc">{{ state.detail.guarantee }}</div>
        </div>
        <div class="item" v-if="parsedSpecs.length">
          <div class="label">选择</div>
          <div class="desc">
            <div class="left">
              <div class="options">
                <div class="option" v-for="(s, i) in parsedSpecs" :key="i">{{ s }}</div>
              </div>
              <div class="all" v-if="parsedSpecs.length > 2">
                <div class="bg"></div>
                <div class="count">共{{ parsedSpecs.length }}种规格可选</div>
              </div>
            </div>
            <Icon class="arrow" icon="mingcute:right-line" />
          </div>
        </div>
        <div class="item" v-if="state.detail.shipping_from">
          <div class="label">物流</div>
          <div class="desc" style="display: block">
            <div style="display: flex; gap: 5rem">
              <span>发货 {{ state.detail.shipping_from }}</span>
              <span style="color: #dedede">|</span>
              <span>{{
                state.detail.shipping_fee == 0 ? '免运费' : '运费 ¥' + state.detail.shipping_fee
              }}</span>
            </div>
            <div class="flex space-between mt1r" v-if="state.detail.shipping_time">
              <div>{{ state.detail.shipping_time }}</div>
            </div>
          </div>
        </div>
        <div class="item mb0r" v-if="parsedSpecs.length">
          <div class="label">参数</div>
          <div class="desc">
            <div class="ellipsis">{{ parsedSpecs.join(' / ') }}</div>
            <Icon class="arrow" icon="mingcute:right-line" />
          </div>
        </div>
      </div>

      <div class="card shop" v-if="state.detail.seller_name">
        <header>
          <img :src="_checkImgUrl(state.detail.seller_avatar)" alt="" class="avatar" />
          <div class="right">
            <div class="l">
              <div class="name">{{ state.detail.seller_name }}</div>
              <div class="c2">已售 {{ state.detail.sold || 0 }} 件</div>
            </div>
            <div class="r" @click="goStore">进店</div>
          </div>
        </header>

        <div class="recommend">
          <header>
            <span class="left">店铺推荐</span>
            <div class="right">
              <span class="gray">查看全部</span>
              <Icon class="arrow" icon="mingcute:right-line" />
            </div>
          </header>
          <div class="wrap">
            <div
              class="item"
              v-for="g in state.sellerGoods"
              :key="g.id"
              @click="nav('/shop/detail', {}, g)"
            >
              <img :src="_checkImgUrl(g.cover)" alt="" class="avatar" />
              <div class="name">{{ g.name }}</div>
              <div class="price">
                <span class="symbol">￥</span>
                <span class="int">{{ g.price }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="img-list" ref="sectionDetail">
      <header>
        <div class="l"></div>
        <span class="gray">商品详情</span>
        <div class="r"></div>
      </header>

      <div class="imgs">
        <img
          v-lazy="_checkImgUrl(i)"
          alt=""
          class="avatar"
          :key="j"
          v-for="(i, j) in state.detail.imgs"
        />
      </div>
    </div>

    <div class="p">
      <div class="card other-desc">
        <div
          class="item"
          :class="activeIndexs.includes(i) && 'active'"
          @click="toggle(i)"
          :key="i"
          v-for="(item, i) in 3"
        >
          <header>
            <div class="l">价格说明</div>
            <Icon class="arrow" icon="mingcute:right-line" />
          </header>
          <div class="text">
            价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明价格说明
          </div>
        </div>
      </div>

      <div class="other-recommend" ref="sectionRecommend">
        <header>你可以还会喜欢</header>

        <ScrollList class="Scroll" :api="recommendedShop">
          <template v-slot="{ list }">
            <WaterfallList :list="list">
              <template v-slot="{ item }">
                <div class="goods" @click="nav('/shop/detail', {}, item)">
                  <div class="item">
                    <img class="poster" v-lazy="_checkImgUrl(item.cover)" />
                    <div class="bottom">
                      <div class="desc">
                        {{ item.name }}
                      </div>
                      <div class="discounts" v-if="item.discount">
                        {{ item.discount }}
                      </div>
                      <div class="info">
                        <div class="price">
                          ￥
                          <div class="big">{{ item.price }}</div>
                        </div>
                        <div class="num">已售{{ item.sold }}件</div>
                      </div>
                      <div class="low" v-if="item.isLowPrice">近30天低价</div>
                    </div>
                  </div>
                </div>
              </template>
            </WaterfallList>
          </template>
        </ScrollList>
      </div>
    </div>

    <div class="toolbar">
      <div class="options">
        <div class="option" @click="goStore">
          <Icon icon="iconoir:shop-window" />
          <div class="text">进店</div>
        </div>
        <div class="option" @click="goChat">
          <Icon icon="icon-park-outline:message-emoji" />
          <div class="text">客服</div>
        </div>
        <div class="option cart-icon" @click="router.push('/shop/cart')">
          <Icon icon="icon-park-outline:shopping" />
          <span class="cart-badge" v-if="cartCount">{{ cartCount > 99 ? '99+' : cartCount }}</span>
          <div class="text">购物车</div>
        </div>
      </div>
      <div class="btns">
        <div class="btn" @click="doAddCart($event)">加入购物车</div>
        <div class="btn" @click="doBuy($event)">领券购买</div>
      </div>
    </div>
    <!-- 飞入动画 -->
    <div
      v-for="ball in flyingBalls"
      :key="ball.id"
      class="flying-ball"
      :style="{ left: ball.x + 'px', top: ball.y + 'px' }"
    />
    <PaymentDialog ref="payDialog" />
  </div>
</template>

<script setup lang="ts">
import SlideHorizontal from '@/components/slide/SlideHorizontal.vue'
import SlideItem from '@/components/slide/SlideItem.vue'
import { onMounted, onUnmounted, reactive, ref, computed } from 'vue'
import { useNav } from '@/utils/hooks/useNav'
import { _checkImgUrl } from '@/utils'
import { useBaseStore } from '@/store/pinia'
import {
  recommendedShop,
  getGoodsDetail,
  toggleFavorite,
  addToCart,
  getSellerGoods
} from '@/api/user'
import WaterfallList from '@/components/WaterfallList.vue'
import ScrollList from '@/components/ScrollList.vue'
import PaymentDialog from '@/components/dialog/PaymentDialog.vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'

defineOptions({
  name: 'GoodsDetail'
})

const router = useRouter()
let activeIndexs = ref([]) as any
const nav = useNav()
const store = useBaseStore()
let page = ref()
let header = ref()
let headerShadow = ref()
let sectionGoods = ref()
let sectionDetail = ref()
let sectionRecommend = ref()
const currentTab = ref('goods')

function scroll() {
  const st = page.value.scrollTop
  let d = st / 200
  if (d > 0) {
    header.value.style.opacity = 1 - d
  } else {
    header.value.style.opacity = 1 - d
  }
  headerShadow.value.style.opacity = d

  // 检测当前所在区域
  const goodsTop = sectionGoods.value?.offsetTop || 0
  const detailTop = sectionDetail.value?.offsetTop || 0
  const recommendTop = sectionRecommend.value?.offsetTop || 0
  const offset = 80 // header高度补偿
  if (st + offset >= recommendTop) {
    currentTab.value = 'recommend'
  } else if (st + offset >= detailTop) {
    currentTab.value = 'detail'
  } else if (st + offset >= goodsTop) {
    currentTab.value = 'goods'
  }
}

function scrollToSection(tab: string) {
  currentTab.value = tab
  const el =
    tab === 'goods'
      ? sectionGoods.value
      : tab === 'detail'
        ? sectionDetail.value
        : tab === 'recommend'
          ? sectionRecommend.value
          : null
  if (el) {
    page.value.scrollTo({ top: el.offsetTop - 50, behavior: 'smooth' })
  }
}

const state = reactive({
  detail: {
    id: null,
    price: '',
    name: '',
    sold: '',
    real_price: '',
    imgs: [],
    seller_name: '',
    seller_avatar: '',
    seller_id: null,
    guarantee: '',
    specs: '',
    shipping_from: '',
    shipping_fee: 0,
    shipping_time: ''
  } as any,
  index: 0,
  isFavorited: false,
  sellerGoods: [] as any[],
  listEl: null,
  fixed: false
})

const parsedSpecs = computed(() => {
  if (!state.detail.specs) return []
  try {
    return JSON.parse(state.detail.specs)
  } catch {
    return []
  }
})

function toggle(i) {
  let rIndex = activeIndexs.value.findIndex((v) => v === i)
  if (rIndex > -1) {
    activeIndexs.value.splice(rIndex, 1)
  } else {
    activeIndexs.value.push(i)
  }
}

async function doFavorite() {
  if (!store.isLoggedIn) {
    nav('/login')
    return
  }
  try {
    const res: any = await toggleFavorite(state.detail.id)
    state.isFavorited = res.data?.favorited ?? res.favorited ?? false
  } catch {
    /* ignore */
  }
}

async function doShare() {
  const url = window.location.href
  if (navigator.share) {
    try {
      await navigator.share({ title: state.detail.name, url })
    } catch {
      /* ignore */
    }
  } else {
    try {
      await navigator.clipboard.writeText(url)
      alert('链接已复制')
    } catch {
      /* ignore */
    }
  }
}

function goStore() {
  if (state.detail.seller_id) {
    router.push('/people/user-home/' + state.detail.seller_id)
  }
}

function goChat() {
  if (!store.isLoggedIn) {
    nav('/login')
    return
  }
  router.push({
    path: '/shop/ai-chat',
    query: {
      product_id: state.detail.id,
      product_name: state.detail.name,
      product_cover: state.detail.cover,
      product_info: `商品:${state.detail.name}, 价格:¥${state.detail.price}, 券后价:¥${state.detail.real_price || state.detail.price}, 已售:${state.detail.sold}件, 描述:${state.detail.description || ''}`
    }
  })
}

const cartCount = ref(0)
const flyingBalls = ref([]) as any
const payDialog = ref()

async function doAddCart(e?: MouseEvent) {
  if (!store.isLoggedIn) {
    nav('/login')
    return
  }
  try {
    await addToCart(state.detail.id)
    cartCount.value++
    if (e) spawnBall(e.clientX, e.clientY)
  } catch {
    /* ignore */
  }
}

function doBuy(e?: MouseEvent) {
  if (!store.isLoggedIn) {
    nav('/login')
    return
  }
  const price = state.detail.real_price || state.detail.price
  payDialog.value?.show(String(price))
}

function spawnBall(startX: number, startY: number) {
  const cartEl = document.querySelector('.toolbar .option:last-child') as HTMLElement
  if (!cartEl) return
  const cartRect = cartEl.getBoundingClientRect()
  const endX = cartRect.left + cartRect.width / 2
  const endY = cartRect.top

  const id = Date.now()
  const ball = { id, x: startX, y: startY, endX, endY }
  flyingBalls.value.push(ball)
  // 下一帧触发动画
  requestAnimationFrame(() => {
    const idx = flyingBalls.value.findIndex((b: any) => b.id === id)
    if (idx > -1) {
      flyingBalls.value[idx].x = endX
      flyingBalls.value[idx].y = endY
    }
  })
  setTimeout(() => {
    flyingBalls.value = flyingBalls.value.filter((b: any) => b.id !== id)
  }, 500)
}

async function loadDetail(id: number) {
  try {
    const res: any = await getGoodsDetail(id)
    const d = res.data || res
    state.detail = Object.assign(state.detail, d)
    state.isFavorited = d.is_favorited || d.isFavorited || false
    // 加载卖家其他商品
    if (d.seller_id) {
      const sg: any = await getSellerGoods(d.seller_id, 6)
      state.sellerGoods = (sg.data || sg).filter((g: any) => g.id !== d.id).slice(0, 4)
    }
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  const item = store.routeData
  if (item && item.id) {
    state.detail = Object.assign(state.detail, item)
    loadDetail(item.id)
  } else {
    const id = router.currentRoute.value.query?.id
    if (id) {
      loadDetail(Number(id))
    }
  }
})

onUnmounted(() => {
  console.log('onUnmounted')
})
</script>

<style scoped lang="less">
@import '@/assets/less/index.less';

.goods-detail {
  background: #f5f5f5;
  color: black;
  font-size: 14rem;
  @c: #a2a2a2;
  @c2: #c0c0c0;
  @red: rgb(248, 38, 74);

  & > header {
    position: fixed;
    left: 0;
    top: 0;
    width: 100vw;
    z-index: 9;

    .top {
      height: var(--common-header-height);
      display: flex;
      align-items: center;
      padding: 0 10rem;

      svg {
        font-size: 22rem;
        background: rgba(176, 176, 176, 0.4);
        padding: 5rem;
        color: white;
        border-radius: 50%;
      }

      .right {
        margin-left: 10rem;
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .search {
          font-size: 12rem;
          border-radius: 20rem;
          padding: 5rem 10rem;
          flex: 1;
          background: rgb(243, 243, 243);
          display: flex;
          align-items: center;
          color: gray;
          visibility: hidden;

          svg {
            padding: 0;
            background: unset;
          }
        }

        .option {
          margin-left: 15rem;
          display: flex;
          align-items: center;
          gap: 8rem;
        }
      }
    }

    .bottom {
      display: flex;
      display: none;

      .tab {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        color: #646464;

        .text {
          padding: 10rem 0;
          padding-bottom: 15rem;
        }
      }

      .active {
        color: black;

        .text {
          border-bottom: 2.5rem solid black;
        }
      }
    }
  }

  .shadow {
    opacity: 0;
    background: white;

    svg {
      background: unset !important;
      color: black !important;
    }

    .search {
      svg {
        color: gray !important;
      }

      visibility: unset !important;
    }

    .bottom {
      display: flex;
    }
  }

  .slide-imgs {
    position: relative;
    height: 55vh;

    img {
      height: 100%;
      width: 100%;
      object-fit: cover;
      touch-action: none;
    }

    .index {
      font-size: 12rem;
      position: absolute;
      padding: 3rem 10rem;
      border-radius: 15rem;
      background: rgba(91, 89, 89, 0.5);
      right: 10rem;
      bottom: 30rem;
      color: white;
    }
  }

  .p {
    padding: 8rem;
  }

  .gray {
    color: @c;
  }

  .c2 {
    color: @c2;
    font-size: 13rem;
  }

  .card {
    margin: 5rem;
    margin-bottom: 10rem;
    background: white;
    border-radius: 10rem;
    padding: 10rem 15rem;
  }

  .arrow {
    font-size: 18rem;
    color: @c;
  }

  .content {
    //background: rgb(247, 247, 249);
    background: #f5f5f5;
    padding-bottom: 0;
    border-radius: 16rem 16rem 0 0;
    transform: translateY(-20rem);

    .price {
      color: red;
      font-weight: 900;

      .symbol {
        font-size: 16rem;
      }

      .int {
        font-size: 26rem;
      }

      .decimal {
        letter-spacing: 2px;
        font-size: 20rem;
      }
    }

    .info {
      padding: 0 8rem;
      margin-bottom: 20rem;

      .price-wrap {
        margin-bottom: 20rem;
        display: flex;
        align-items: flex-end;

        .discount {
          margin-left: 10rem;
          //color: rgb(248, 38, 74);
          color: white !important;
          display: flex;
          align-items: flex-end;
          padding: 2rem 15rem;
          padding-bottom: 4rem;
          //background: rgb(255 167 183 / 25%);
          background: rgb(248, 38, 74);
          border-radius: 20rem;

          .text {
            font-size: 13rem;
          }

          .price {
            color: white !important;
            margin-top: -6rem;
            transform: translateY(4rem);
          }
        }
      }

      .name {
        color: black;
        font-size: 16rem;
        margin-bottom: 8rem;
        overflow: hidden;
        font-weight: 900;
        letter-spacing: 1rem;
      }

      .num {
        font-size: 12rem;
        color: gray;
      }
    }

    .desc-wrapper {
      .item {
        display: flex;
        align-items: flex-start;
        padding: 5rem 0;
        margin-bottom: 22rem;

        .label {
          color: @c;
        }

        .desc {
          padding-left: 15rem;
          flex: 1;
          display: flex;
          justify-content: space-between;
          align-items: center;

          .left {
            flex: 1;
            position: relative;
            display: flex;
            align-items: center;
            font-size: 10rem;
            overflow: hidden;
          }
        }

        .ellipsis {
          max-width: 70vw;
          overflow: hidden; //超出的文本隐藏
          text-overflow: ellipsis; //溢出用省略号显示
          white-space: nowrap; //溢出不换行
        }

        .options {
          display: flex;
          overflow: hidden;
          flex-shrink: 0;

          .option {
            padding: 4rem 10rem;
            background: #f5f5f5;
            margin-right: 10rem;
            border-radius: 3rem;
          }
        }

        .all {
          right: 0;
          height: 100%;
          display: flex;
          align-items: center;
          position: absolute;
          color: @c;

          .bg {
            width: 60rem;
            height: 100%;
            background: linear-gradient(to right, transparent, #f5f5f5);
          }

          .count {
            padding-left: 4rem;
            display: flex;
            align-items: center;
            height: 100%;
            background: white;
          }
        }
      }
    }

    .comments {
      & > header {
        margin-bottom: 20rem;
        display: flex;
        justify-content: space-between;
        align-items: center;

        span {
          font-size: 16rem;
          font-weight: 900;
        }
      }

      .tags {
        display: flex;
        gap: 10rem;
        margin-bottom: 20rem;

        .tag {
          display: flex;
          gap: 5rem;
          background: rgb(255 167 183 / 15%);
          padding: 6rem 8rem;
          border-radius: 8rem;
          font-size: 11rem;
        }
      }

      .comment {
        margin-bottom: 20rem;

        & > header {
          margin-bottom: 10rem;
          display: flex;
          align-items: center;
          gap: 5rem;

          img {
            border-radius: 50%;
            width: 25rem;
            height: 25rem;
          }
        }

        .w {
          display: flex;
          gap: 10rem;

          .d {
            margin-bottom: 10rem;
          }

          img {
            border-radius: 8rem;
            height: 50rem;
            width: 50rem;
          }
        }
      }
    }

    .shop {
      & > header {
        display: flex;
        align-items: center;
        gap: 10rem;

        img {
          width: 60rem;
          height: 60rem;
          border-radius: 50%;
        }

        .right {
          flex: 1;
          display: flex;
          justify-content: space-between;
          align-items: center;

          .l {
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            gap: 4rem;

            .name {
              font-size: 16rem;
              font-weight: 900;
            }

            .tags {
              display: flex;
              font-size: 10rem;
              font-weight: 900;
              gap: 10rem;

              .tag {
                padding: 2rem 3rem;
                background: rgb(253, 245, 243);
                color: rgb(217, 143, 80);
              }
            }

            .gray {
              font-size: 12rem;
            }
          }

          .r {
            border-radius: 4rem;
            padding: 5rem 14rem;
            font-weight: 900;
            background: var(--primary-btn-color);
            color: white;
          }
        }
      }

      .desc {
        margin-top: 16rem;
        display: flex;
        align-items: center;
        gap: 3rem;

        .grid {
          width: 33%;
          text-align: center;
          font-size: 13rem;
          font-weight: bold;

          .c2 {
            font-weight: normal;
            font-size: 12rem;
            margin-bottom: 6rem;
          }
        }

        .line {
          width: 1px;
          height: 30rem;
          background: lightgrey;
        }
      }

      .recommend {
        margin-top: 16rem;

        & > header {
          display: flex;
          align-items: center;
          justify-content: space-between;

          .left {
            font-weight: 900;
          }

          .right {
            display: flex;
            align-items: center;
          }
        }

        .wrap {
          margin-top: 16rem;
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          //grid-template-rows: repeat(3, 1fr);
          gap: 10rem;

          .item {
            img {
              border-radius: 12rem;
              object-fit: cover;
              height: 28vw;
              width: 100%;
            }

            .name {
              overflow: hidden;
              text-overflow: ellipsis;
              display: -webkit-box; //作为弹性伸缩盒子模型显示。
              -webkit-box-orient: vertical; //设置伸缩盒子的子元素排列方式--从上到下垂直排列
              -webkit-line-clamp: 2; //显示的行
            }

            .price {
              .symbol {
                font-size: 14rem;
              }

              .int {
                font-size: 18rem;
              }

              .decimal {
                letter-spacing: 2px;
                font-size: 14rem;
              }
            }
          }
        }
      }
    }
  }

  .img-list {
    background: #f5f5f5;

    & > header {
      font-size: 16rem;
      padding-bottom: 20rem;
      display: flex;
      justify-content: center;
      gap: 10rem;
      align-items: center;

      .l {
        width: 0;
        height: 0;
        border-right: 40px solid @c;
        border-top: 1px solid transparent;
        border-bottom: 1px solid transparent;
      }

      .r {
        .l;
        border-left: 40px solid @c;
        border-right: unset;
      }
    }

    .imgs {
      img {
        width: 100%;
        display: block;
      }
    }
  }

  .other-desc {
    .item {
      & > header {
        padding: 15rem 0;
        display: flex;
        justify-content: space-between;
        align-items: center;
      }

      .text {
        display: none;
      }

      &.active {
        .text {
          display: block;
        }
      }

      &:last-child {
        & > header {
          padding-bottom: 10rem;
        }
      }

      &:first-child {
        & > header {
          padding-top: 10rem;
        }
      }
    }
  }

  .other-recommend {
    margin-bottom: 60rem;

    & > header {
      padding: 15rem;
      font-weight: 900;
      font-size: 15rem;
    }

    @fColor: #f1f1f1;

    .fixed {
      background: @fColor;
    }

    @p: 5rem;

    .Scroll {
      padding: 5rem;
    }

    .goods {
      box-sizing: border-box;
      margin-bottom: 10rem;

      .item {
        border-radius: 8rem;
        overflow: hidden;
        background: white;

        img {
          width: 100%;
        }

        .bottom {
          padding: 10rem;

          .desc {
            color: black;
            font-size: 16rem;
            margin-bottom: 8rem;
            @lh: 18rem;
            line-height: @lh;
            height: @lh * 2;
            overflow: hidden;
          }

          .discounts {
            display: inline-block;
            @c: rgb(199, 89, 106);
            border: 1rem solid @c;
            padding: 0 4rem;
            color: @c;
            font-size: 12rem;
            margin-bottom: 4rem;
          }

          .info {
            display: flex;
            align-items: flex-end;

            .price {
              color: rgb(248, 38, 74);
              display: flex;
              align-items: flex-end;
              font-size: 14rem;
              margin-right: 5rem;

              .big {
                font-size: 22rem;
                font-weight: 900;
                transform: translateY(2rem);
              }
            }

            .num {
              color: darkgray;
              font-size: 12rem;
            }
          }

          .low {
            margin-top: 2rem;
            color: rgb(230, 153, 92);
          }
        }
      }
    }
  }

  .toolbar {
    position: fixed;
    bottom: 0;
    width: 100vw;
    left: 0;
    background: white;
    display: flex;
    padding: 10rem;
    box-sizing: border-box;
    gap: 6rem;

    .options {
      flex: 1;
      display: flex;

      .option {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        flex-direction: column;
        font-size: 11rem;
        color: #646464;
        position: relative;

        svg {
          font-size: 18rem;
        }

        .cart-badge {
          position: absolute;
          top: -4rem;
          right: 50%;
          transform: translateX(200%);
          background: #fe2c55;
          color: white;
          font-size: 10rem;
          padding: 1rem 5rem;
          border-radius: 10rem;
          min-width: 14rem;
          text-align: center;
          font-weight: 700;
        }

        &:first-child {
          svg {
            color: red;
          }
        }
      }
    }

    .btns {
      width: 60%;
      display: flex;
      font-size: 15rem;
      font-weight: bold;
      background: @red;
      color: white;
      border-radius: 12rem;
      overflow: hidden;
      height: 45rem;

      .btn {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;

        &:first-child {
          color: @red;
          background: rgb(255, 233, 237);
        }
      }
    }
  }

  .flying-ball {
    position: fixed;
    z-index: 9999;
    pointer-events: none;
    width: 14rem;
    height: 14rem;
    border-radius: 50%;
    background: @red;
    transition:
      left 0.45s cubic-bezier(0.5, -0.5, 1, 1),
      top 0.45s cubic-bezier(0.5, 0, 0.5, 1);
    margin-left: -7rem;
    margin-top: -7rem;
  }
}
</style>
