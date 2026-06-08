<template>
  <div id="Shop">
    <div class="wrapper">
      <div class="search">
        <div class="search-input" :class="{ focused: isSearching }">
          <Icon icon="tabler:search" />
          <input
            v-model="searchKeyword"
            class="placeholder"
            placeholder="搜索商品"
            @keyup.enter="doSearch"
            @focus="onSearchFocus"
          />
          <Icon
            v-if="searchKeyword && isSearching"
            icon="ep:close"
            class="clear-icon"
            @click="clearSearch"
          />
          <Icon color="gray" icon="lucide:camera" />
          <div class="search-notice" @click="doSearch">搜索</div>
        </div>
        <div class="more" @click="nav('/shop/cart')">
          <Icon icon="ep:shopping-cart" />
        </div>
      </div>
    </div>

    <!-- 搜索结果 -->
    <template v-if="isSearching">
      <div class="search-header" v-if="searchKeyword">
        <span>搜索 "{{ searchKeyword }}"</span>
        <span class="cancel" @click="clearSearch">取消</span>
      </div>
      <ScrollList
        v-if="searchKeyword"
        class="Scroll"
        :key="'search-' + searchKeyword"
        :api="searchApi"
      >
        <template v-slot="{ list }">
          <div class="empty-search" v-if="list.length === 0">
            <span>暂无相关商品</span>
          </div>
          <WaterfallList :list="list">
            <template v-slot="{ item }">
              <div class="goods" @click="nav('/shop/detail', {}, item)">
                <div class="item">
                  <img class="poster" v-lazy="_checkImgUrl(item.cover)" />
                  <div class="bottom">
                    <div class="desc">{{ item.name }}</div>
                    <div class="discounts" v-if="item.discount">{{ item.discount }}</div>
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
    </template>

    <!-- 正常推荐 -->
    <template v-else>
      <ScrollList class="Scroll" :api="recommendedShop">
        <template v-slot="{ list }">
          <div class="top-card">
            <div class="card">
              <div class="options">
                <div
                  class="option"
                  v-if="store.userinfo.role === 'merchant'"
                  @click="nav('/shop/my')"
                >
                  <Icon icon="iconoir:shop-window" />
                  <div>我的商品</div>
                </div>
                <div class="option" @click="nav('/shop/cart')">
                  <Icon icon="lets-icons:order-light" />
                  <div>我的订单</div>
                </div>
                <div class="option" @click="_no">
                  <Icon icon="material-symbols-light:charging-station-outline" />
                  <div>手机充值</div>
                </div>
                <div class="option" @click="nav('/message')">
                  <Icon icon="system-uicons:message" />
                  <div>购物消息</div>
                </div>
                <div class="option" @click="nav('/message')">
                  <Icon icon="dashicons:money-alt" />
                  <div>退款/售后</div>
                </div>
                <div class="option" @click="_no">
                  <Icon icon="icon-park-outline:clothes-turtleneck" />
                  <div>潮流服饰</div>
                </div>
              </div>
            </div>
          </div>
          <WaterfallList :list="list">
            <template v-slot="{ item }">
              <div class="goods" @click="nav('/shop/detail', {}, item)">
                <div class="item">
                  <img class="poster" v-lazy="_checkImgUrl(item.cover)" />
                  <div class="bottom">
                    <div class="desc">{{ item.name }}</div>
                    <div class="discounts" v-if="item.discount">{{ item.discount }}</div>
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
    </template>

    <BaseFooter v-bind:init-tab="2" :is-white="true" style="position: fixed; left: 0" />
  </div>
</template>

<script setup lang="tsx">
import { ref } from 'vue'
import { useNav } from '@/utils/hooks/useNav'
import { _checkImgUrl, _no } from '@/utils'
import { useBaseStore } from '@/store/pinia'
import { Icon } from '@iconify/vue'
import ScrollList from '@/components/ScrollList.vue'
import { recommendedShop, searchGoods } from '@/api/user'
import WaterfallList from '@/components/WaterfallList.vue'

defineOptions({
  name: 'Shop'
})

const nav = useNav()
const store = useBaseStore()

const searchKeyword = ref('')
const isSearching = ref(false)

function searchApi(params: any) {
  return searchGoods(searchKeyword.value, params.pageNo, params.pageSize)
}

function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  isSearching.value = true
}

function onSearchFocus() {
  if (searchKeyword.value.trim()) {
    isSearching.value = true
  }
}

function clearSearch() {
  searchKeyword.value = ''
  isSearching.value = false
}
</script>

<style scoped lang="less">
@fColor: #f1f1f1;

#Shop {
  font-size: 14rem;
  position: relative;
  background: #f8f8f8;
  padding: 10rem;
  color: black;

  .wrapper {
  }

  .search {
    position: relative;
    z-index: 2;
    display: flex;
    align-items: center;
    height: 36rem;
    box-sizing: border-box;
    margin-bottom: 10rem;

    svg {
      color: gray;
      font-size: 20rem;
    }

    .search-input {
      border: 2rem solid red;
      border-radius: 12rem;
      height: 100%;
      padding: 0 10rem;
      padding-right: 3rem;
      flex: 1;
      gap: 10rem;
      display: flex;
      align-items: center;

      .placeholder {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        font-size: 13rem;
        color: #333;
        &::placeholder {
          color: #999;
        }
      }

      .clear-icon {
        cursor: pointer;
        font-size: 16rem !important;
        flex-shrink: 0;
      }

      img {
        margin-right: 10rem;
      }

      .search-notice {
        background: rgb(242, 62, 92);
        padding: 0 10rem;
        height: 85%;
        border-radius: 10rem;
        display: flex;
        justify-content: center;
        align-items: center;
        color: white;
        cursor: pointer;
        flex-shrink: 0;
        font-size: 12rem;
      }
    }

    .more {
      margin-left: 10rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: space-between;
      font-size: 12rem;
      cursor: pointer;
    }
  }

  .search-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 5rem 10rem;
    font-size: 14rem;
    color: #333;
    .cancel {
      color: #fe2c55;
      cursor: pointer;
    }
  }

  .empty-search {
    text-align: center;
    padding: 60rem 0;
    color: #999;
    font-size: 14rem;
  }

  .card {
    background: white;
    padding: 10rem 15rem;
    margin-bottom: 10rem;
    border-radius: 8rem;
  }

  .options {
    display: flex;
    overflow-x: auto;
    box-sizing: border-box;

    .option {
      width: 17vw;
      flex-shrink: 0;
      font-size: 13rem;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;

      svg {
        font-size: 30rem;
        margin-bottom: 3rem;
      }
    }
  }

  .Scroll {
    position: relative;
    z-index: 2;
    height: calc(var(--vh, 1vh) * 100 - var(--footer-height) * 2) !important;
  }

  .fixed {
    background: @fColor;
  }

  .top-card {
    margin-bottom: 10rem;
  }

  @p: 5rem;

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
              font-weight: bold;
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
</style>
