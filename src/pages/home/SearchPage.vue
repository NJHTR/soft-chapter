<template>
  <div class="Search">
    <div class="header">
      <dy-back mode="light" @click="router.back" class="mr1r"></dy-back>
      <Search
        ref="searchRef"
        v-model="data.searchKey"
        placeholder="搜索视频/用户"
        :isShowRightText="true"
        :rightText="'搜索'"
        @notice="doSearch"
        @search="doSearch"
        @clear="clearSearch"
      ></Search>
    </div>
    <div class="content">
      <!-- 搜索建议列表（输入中但未提交搜索） -->
      <template v-if="data.searchKey && !data.isSearched">
        <div class="suggestions" v-if="data.suggestions.length">
          <div
            class="suggest-item"
            v-for="(item, index) in data.suggestions"
            :key="index"
            @click="selectSuggestion(item)"
          >
            <img class="search-icon" src="../../assets/img/icon/search-gray.png" alt="" />
            <span class="text">{{ item }}</span>
          </div>
        </div>
        <div class="suggestions" v-else>
          <div class="suggest-item no-match">暂无匹配建议，按回车搜索</div>
        </div>
      </template>

      <!-- 搜索结果 -->
      <template v-else-if="data.isSearched">
        <!-- Tab 栏 -->
        <div class="search-tabs-wrap">
          <div class="search-tabs">
            <div
              class="tab"
              v-for="tab in TAB_LIST"
              :key="tab"
              :class="{ active: data.searchTab === tab }"
              @click="switchTab(tab)"
            >
              {{ tab }}
            </div>
          </div>
        </div>

        <!-- AI 智能总结 (仅综合 Tab) -->
        <template v-if="data.searchTab === '综合'">
          <!-- 加载中 -->
          <div class="ai-summary ai-loading" v-if="data.aiLoading">
            <span class="ai-loading-dot"></span>
            <span class="ai-loading-dot"></span>
            <span class="ai-loading-dot"></span>
            <span class="ai-loading-text">{{ loadingMsg }}</span>
          </div>

          <!-- 打字机阶段: 单卡片 -->
          <div class="ai-summary" v-else-if="data.aiSummary && !typingFinished">
            <div class="ai-text">{{ displayedSummary }}<span class="ai-cursor">|</span></div>
          </div>

          <!-- 打字完成: 双模块卡片 -->
          <template v-else-if="data.aiSummary && typingFinished">
            <!-- 智能解读模块 -->
            <div
              class="ai-module ai-knowledge"
              v-if="knowledgeText"
              :class="{ expanded: knowledgeExpanded }"
            >
              <div class="ai-module-header">
                <Icon icon="icon-park-outline:book-open" class="module-icon" />
                <span>智能解读</span>
              </div>
              <div class="ai-module-body ai-md" v-html="knowledgeHtml"></div>
              <div class="ai-fade" v-if="!knowledgeExpanded && knowledgeText.length > 200"></div>
              <div
                class="ai-expand-btn"
                v-if="!knowledgeExpanded && knowledgeText.length > 200"
                @click="knowledgeExpanded = true"
              >
                <span>展开更多</span>
                <Icon icon="icon-park-outline:down" class="expand-arrow" />
              </div>
            </div>
            <!-- 平台发现模块 -->
            <div
              class="ai-module ai-discovery"
              v-if="discoveryText"
              :class="{ expanded: discoveryExpanded }"
            >
              <div class="ai-module-header">
                <Icon icon="icon-park-outline:analysis" class="module-icon" />
                <span>平台发现</span>
              </div>
              <div class="ai-module-body ai-md" v-html="discoveryHtml"></div>
              <div class="ai-fade" v-if="!discoveryExpanded && discoveryText.length > 200"></div>
              <div
                class="ai-expand-btn"
                v-if="!discoveryExpanded && discoveryText.length > 200"
                @click="discoveryExpanded = true"
              >
                <span>展开更多</span>
                <Icon icon="icon-park-outline:down" class="expand-arrow" />
              </div>
            </div>
          </template>
        </template>

        <!-- 综合 Tab --->
        <template v-if="data.searchTab === '综合'">
          <div class="loading-tip" v-if="data.videoLoading">搜索中...</div>
          <div class="empty-tip" v-else-if="!allResults.length">暂无相关内容</div>
          <div class="waterfall" v-else>
            <div
              class="waterfall-item"
              v-for="item in allResults"
              :key="item.aweme_id"
              @click="goVideoDetail(item)"
            >
              <div class="cover-wrap">
                <img class="cover" v-lazy="_checkImgUrl(item.video.cover.url_list[0])" alt="" />
                <span class="duration" v-if="item.duration">{{
                  _formatDuration(item.duration)
                }}</span>
              </div>
              <div class="info">
                <div class="desc">{{ item.desc }}</div>
                <div class="meta">
                  <img
                    class="avatar"
                    v-lazy="_checkImgUrl(item.author.avatar_168x168?.url_list?.[0])"
                    alt=""
                  />
                  <div class="author-info">
                    <span class="name">{{ item.author.nickname }}</span>
                    <span class="time">{{ _formatTime(item.create_time) }}</span>
                  </div>
                  <div class="likes">
                    <Icon icon="icon-park-outline:like" />
                    <span>{{ _formatNumber(item.statistics.digg_count) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 视频 Tab -->
        <template v-else-if="data.searchTab === '视频'">
          <div class="loading-tip" v-if="data.videoLoading">搜索中...</div>
          <div class="empty-tip" v-else-if="!videoOnlyResults.length">暂无相关视频</div>
          <div class="waterfall" v-else>
            <div
              class="waterfall-item"
              v-for="item in videoOnlyResults"
              :key="item.aweme_id"
              @click="goVideoDetail(item)"
            >
              <div class="cover-wrap">
                <img class="cover" v-lazy="_checkImgUrl(item.video.cover.url_list[0])" alt="" />
                <span class="duration" v-if="item.duration">{{
                  _formatDuration(item.duration)
                }}</span>
              </div>
              <div class="info">
                <div class="desc">{{ item.desc }}</div>
                <div class="meta">
                  <img
                    class="avatar"
                    v-lazy="_checkImgUrl(item.author.avatar_168x168?.url_list?.[0])"
                    alt=""
                  />
                  <div class="author-info">
                    <span class="name">{{ item.author.nickname }}</span>
                    <span class="time">{{ _formatTime(item.create_time) }}</span>
                  </div>
                  <div class="likes">
                    <Icon icon="icon-park-outline:like" />
                    <span>{{ _formatNumber(item.statistics.digg_count) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 用户 Tab -->
        <template v-else-if="data.searchTab === '用户'">
          <div class="loading-tip" v-if="data.userLoading">搜索中...</div>
          <div class="empty-tip" v-else-if="!data.userResults.length">暂无相关用户</div>
          <div class="user-list" v-else>
            <div
              class="user-item"
              v-for="item in data.userResults"
              :key="item.uid"
              @click="goUserHome(item)"
            >
              <img
                class="u-avatar"
                v-lazy="_checkImgUrl(item.avatar_168x168?.url_list?.[0])"
                alt=""
              />
              <div class="u-info">
                <div class="u-name">{{ item.nickname }}</div>
                <div class="u-sub">
                  <span>粉丝：{{ _formatNumber(item.follower_count) }}</span>
                  <span class="u-sep">|</span>
                  <span>SeekFlowID：{{ item.unique_id }}</span>
                </div>
              </div>
              <div
                class="u-follow-btn"
                :class="{
                  followed: item.is_followed,
                  mutual: item.is_following_me && item.is_followed
                }"
                @click.stop="toggleFollow(item)"
              >
                <span v-if="item.is_followed && item.is_following_me">互相关注</span>
                <span v-else-if="item.is_followed">已关注</span>
                <span v-else>关注</span>
              </div>
            </div>
          </div>
        </template>

        <!-- 图文 Tab -->
        <template v-else-if="data.searchTab === '图文'">
          <div class="loading-tip" v-if="data.videoLoading">搜索中...</div>
          <div class="empty-tip" v-else-if="!imageOnlyResults.length">暂无相关图文</div>
          <div class="waterfall" v-else>
            <div
              class="waterfall-item"
              v-for="item in imageOnlyResults"
              :key="item.aweme_id"
              @click="goVideoDetail(item)"
            >
              <div class="cover-wrap">
                <img class="cover" v-lazy="_checkImgUrl(item.video.cover.url_list[0])" alt="" />
                <span class="duration" v-if="item.duration">{{
                  _formatDuration(item.duration)
                }}</span>
              </div>
              <div class="info">
                <div class="desc">{{ item.desc }}</div>
                <div class="meta">
                  <img
                    class="avatar"
                    v-lazy="_checkImgUrl(item.author.avatar_168x168?.url_list?.[0])"
                    alt=""
                  />
                  <div class="author-info">
                    <span class="name">{{ item.author.nickname }}</span>
                    <span class="time">{{ _formatTime(item.create_time) }}</span>
                  </div>
                  <div class="likes">
                    <Icon icon="icon-park-outline:like" />
                    <span>{{ _formatNumber(item.statistics.digg_count) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 直播 / 音乐 / 店铺 / 小程序 Tab (暂无数据) -->
        <template v-else>
          <div class="empty-tip">暂无更多内容</div>
        </template>
      </template>

      <!-- 默认内容：搜索历史、猜你想搜、排行榜 -->
      <template v-else>
        <div class="history">
          <div
            class="row"
            :key="index"
            v-for="(item, index) in lHistory"
            @click="selectSuggestion(item)"
          >
            <div class="left">
              <img src="../../assets/img/icon/home/time-white.png" alt="" />
              <span> {{ item }}</span>
            </div>
            <dy-back
              img="close"
              mode="gray"
              @click.stop="deleteHistoryKeyword(item)"
              scale=".7"
            ></dy-back>
          </div>
          <div v-if="data.history.length > 2" class="history-expand" @click="toggle">
            {{ data.isExpand ? '清除全部搜索记录' : '展开全部' }}
          </div>
        </div>
        <div class="guess">
          <div class="title">
            <div class="left">猜你想搜</div>
            <div class="right" @click.stop="refresh">
              <img class="scan" src="../../assets/img/icon/home/refresh-gray.png" />
              <span>换一换</span>
            </div>
          </div>
          <div class="keys">
            <div class="key" :key="index" v-for="(item, index) in data.randomGuess">
              <span class="desc">{{ item.name }}</span>
              <img
                v-if="item.type === 1"
                src="../../assets/img/icon/home/new.webp"
                alt=""
                class="type"
              />
            </div>
          </div>
        </div>
        <div class="rank-list">
          <div class="indicator">
            <div
              class="tab"
              :class="{ active: data.slideIndex === 0 }"
              @click="data.slideIndex = 0"
            >
              SeekFlow热榜
            </div>
            <div
              class="tab"
              :class="{ active: data.slideIndex === 1 }"
              @click="data.slideIndex = 1"
            >
              直播榜
            </div>
            <div
              class="tab"
              :class="{ active: data.slideIndex === 2 }"
              @click="data.slideIndex = 2"
            >
              音乐榜
            </div>
            <div
              class="tab"
              :class="{ active: data.slideIndex === 3 }"
              @click="data.slideIndex = 3"
            >
              品牌榜
            </div>
          </div>
          <!--        TODO 滚动到下面的时候，应该禁止slide-move，因为每个slideitem的高度不一样，高的切到矮的，会闪屏-->
          <SlideHorizontal v-model:index="data.slideIndex" :style="slideListHeight">
            <SlideItem>
              <div class="slide0" ref="slide0">
                <div class="l-row">
                  <div class="rank-wrapper">
                    <img src="../../assets/img/icon/home/to-top-yellow.png" class="rank" />
                  </div>
                  <div class="right">
                    <div class="center">
                      <div class="desc">专题：嘻嘻嘻哈哈瞄瞄嘻嘻嘻</div>
                    </div>
                  </div>
                </div>
                <div class="l-row" :key="index" v-for="(item, index) in data.hotRankList">
                  <div class="rank-wrapper">
                    <img
                      v-if="index === 0"
                      src="../../assets/img/icon/home/hot1.webp"
                      alt=""
                      class="rank"
                    />
                    <img
                      v-else-if="index === 1"
                      src="../../assets/img/icon/home/hot2.webp"
                      alt=""
                      class="rank"
                    />
                    <img
                      v-else-if="index === 2"
                      src="../../assets/img/icon/home/hot3.webp"
                      alt=""
                      class="rank"
                    />
                    <div v-else class="rank">{{ index + 1 }}</div>
                  </div>
                  <div class="right">
                    <div class="center">
                      <div class="desc">{{ item.name }}</div>
                      <img
                        v-if="item.type === 1"
                        src="../../assets/img/icon/home/new.webp"
                        alt=""
                        class="type"
                      />
                      <img
                        v-if="item.type === 0"
                        src="../../assets/img/icon/home/hot.webp"
                        alt=""
                        class="type"
                      />
                    </div>
                    <div class="count">999w</div>
                  </div>
                </div>
                <div class="more" @click="_no">查看完整热点榜 ></div>
              </div>
            </SlideItem>
            <SlideItem>
              <div class="slide1" ref="slide1">
                <div class="l-row" :key="index" v-for="(item, index) in data.liveRankList">
                  <div class="rank-wrapper">
                    <div class="rank" :class="{ top: index < 3 }">
                      {{ index + 1 }}
                    </div>
                  </div>
                  <div class="right">
                    <div class="center">
                      <div class="avatar-wrapper">
                        <img src="../../assets/img/icon/avatar/1.png" alt="" class="avatar" />
                      </div>
                      <div class="desc">{{ item.name }}</div>
                      <div v-if="item.type === 0" class="live-type">
                        <img class="type1" src="../../assets/img/icon/home/pk.webp" />
                        <span>PK</span>
                      </div>
                      <div v-if="item.type === 1" class="live-type">
                        <img class="type2" src="../../assets/img/icon/home/redpack.png" />
                        <span>红包</span>
                      </div>
                    </div>
                    <div class="count">999w人气</div>
                  </div>
                </div>
                <div class="more" @click="_no">查看完整直播榜 ></div>
              </div>
            </SlideItem>
            <SlideItem>
              <div class="slide2" ref="slide2">
                <div
                  class="l-row"
                  :key="index"
                  v-for="(item, index) in data.musicRankList"
                  @click="nav('/home/music-rank-list')"
                >
                  <div class="rank-wrapper">
                    <div class="rank" :class="{ top: index < 3 }">
                      {{ index + 1 }}
                    </div>
                  </div>
                  <div class="right">
                    <div class="center">
                      <div class="avatar-wrapper">
                        <img v-lazy="_checkImgUrl(item.cover)" alt="" class="avatar" />
                      </div>
                      <div class="desc">{{ item.name }}</div>
                    </div>
                    <div class="count">
                      <img src="../../assets/img/icon/home/hot-gray.png" alt="" />
                      <span>{{ _formatNumber(item.use_count) }}</span>
                    </div>
                  </div>
                </div>
                <div class="more" @click="nav('/home/music-rank-list')">查看完整音乐榜 ></div>
              </div>
            </SlideItem>
            <SlideItem>
              <div class="slide3" ref="slide3">
                <div class="slide4-wrapper">
                  <div class="brands">
                    <div
                      class="brand"
                      @click="toggleKey(key, i)"
                      :key="i"
                      :class="{ active: key === data.selectBrandKey }"
                      v-for="(key, i) in Object.keys(data.brandRankList)"
                    >
                      {{ key }}
                    </div>
                  </div>
                  <div class="l-row" :key="index" v-for="(item, index) in selectBrandList">
                    <div class="rank-wrapper">
                      <div class="rank" :class="{ top: index < 3 }">
                        {{ index + 1 }}
                      </div>
                    </div>
                    <div class="right">
                      <div class="center">
                        <div class="avatar-wrapper" :class="item.living ? 'living' : ''">
                          <div class="avatar-out-line"></div>
                          <img v-lazy="_checkImgUrl(item.logo)" alt="" class="avatar" />
                        </div>
                        <div class="desc">{{ item.name }}</div>
                      </div>
                      <div class="count">
                        <img src="../../assets/img/icon/home/hot-gray.png" alt="" />
                        <span>{{ _formatNumber(item.hot_count) }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="more" @click="_no">查看完整品牌榜 ></div>
                </div>

                <SlideHorizontal v-model:index="data.adIndex" :autoplay="true" indicator>
                  <SlideItem>
                    <div class="ad">AD1</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD2</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD3</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD4</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD5</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD6</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD7</div>
                  </SlideItem>
                  <SlideItem>
                    <div class="ad">AD8</div>
                  </SlideItem>
                </SlideHorizontal>
              </div>
            </SlideItem>
          </SlideHorizontal>
        </div>
      </template>
    </div>
  </div>
</template>
<script setup lang="ts">
import Search from '../../components/Search.vue'
import Dom from '../../utils/dom'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { _checkImgUrl, _formatNumber, _no, _showSimpleConfirmDialog, sampleSize } from '@/utils'
import { useRoute, useRouter } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { searchVideos, searchSummary } from '@/api/videos'
import {
  searchUsers,
  toggleFollowUser,
  getSearchHistory,
  saveSearchHistory,
  clearSearchHistory,
  deleteSearchHistoryKeyword
} from '@/api/user'
import { _duration } from '@/utils'
import { useBaseStore } from '@/store/pinia'

defineOptions({
  name: 'SearchPage'
})

const TAB_LIST = ['综合', '视频', '用户', '图文', '直播', '音乐', '店铺', '小程序']

const router = useRouter()
const route = useRoute()
const nav = useNav()
const store = useBaseStore()
const data = reactive({
  searchKey: '',
  searchTab: '综合',
  videoLoading: false,
  userLoading: false,
  videoResults: [] as any[],
  userResults: [] as any[],
  isExpand: false,
  isSearched: false,
  suggestions: [] as string[],
  aiSummary: '',
  aiExpanded: false,
  aiLoading: false,
  adIndex: 0,
  history: [] as string[],
  guess: [
    { name: '少年透明人', type: -1 },
    { name: '花呗分批次接入征信', type: -1 },
    { name: '新娘婚礼上跪求悔婚', type: -1 },
    { name: '当你想换iPhone13时', type: -1 },
    { name: 'Ling OS灵犀系统', type: -1 },
    { name: '桑塔纳2022款', type: -1 },
    { name: '透明人', type: -1 },
    { name: '恒大集团凌晨发公告', type: 0 },
    { name: '2022款日产GT-R', type: 1 },
    { name: '四川双一流大学名单', type: -1 },
    { name: '一公司放假通知走红', type: -1 },
    { name: '成都新全优教育倒闭', type: -1 },
    { name: '当代女生社交现状', type: -1 },
    { name: '恒大集团凌晨发公告', type: -1 }
  ],
  randomGuess: [],
  hotRankList: [
    { name: '国内手机厂商最大的软肋就是 android 系统！', type: 0 },
    { name: '大家的官网订单现在什么状态', type: -1 },
    { name: '库克不愧是供应链管理大师， A15 一鱼三吃', type: -1 },
    { name: '找到了 iOS 被怀疑淘宝窃听的可能原因', type: 1 },
    { name: 'rebase 还是 merge？', type: -1 },
    { name: '十一出游西安，西安的大佬们能给些建议吗？', type: 0 },
    { name: '领克 01，燃油还是 phev？', type: 1 },
    { name: '为什么要抢购新手机呢？', type: -1 },
    { name: '拼多多官方处理问题跟京东真的没法比', type: -1 },
    { name: '百度输入法 VS 搜狗输入法', type: -1 },
    { name: '关于 ios 上 app 检测代理', type: 0 },
    { name: 'iPadmini6 到货以后，要不要换路由器', type: 1 },
    { name: '现在有推荐的同步盘么？', type: -1 },
    { name: '大哥们， mac 电池鼓包你们都咋修的。。', type: -1 },
    { name: '发现一个特别赞的同步盘方案 Resilio Sync', type: -1 },
    { name: '得鼻炎了, 说下症状和应对吧', type: 1 },
    { name: '打翻了一瓶矿泉水在 MacBook Pro 上，赶紧用鼠标关机了，等多久可以尝试开机？', type: 0 },
    { name: '筋膜枪哪个牌子好啊？', type: -1 },
    { name: '最近在学理财小白基础知识，然后请教大家办哪个证券账户比较好呀', type: -1 },
    { name: '有没有长期使用 sidecar 功能的 V 友，这个东西长期的稳定性如何？', type: 0 },
    { name: '犹豫是否要年年焕新', type: -1 },
    { name: '请问如何在国内给 AppStore HK/TW 区充值.', type: 0 },
    { name: '最近感觉一个妹子不错，不过我比她大 5 岁', type: 1 },
    { name: '12mini 1 月 20 号购入，现在电池健康 92%，正常现象？', type: -1 },
    { name: '现在新 iphone12/128 啥价格比较合适啊？', type: -1 },
    { name: 'iOS 15 不改地区就能看到全球所有交通卡', type: -1 },
    { name: '求推荐拼车/打车软件', type: 1 },
    { name: '如何比较方便的杀死 nohup 起的进程及其所有子进程?', type: 0 },
    { name: '换了新工作，好像又掉坑里了。', type: 0 },
    { name: '有没有这样一款记账软件？', type: 1 }
  ],
  liveRankList: [
    { name: '毛三岁（收女徒弟）', type: 0 },
    { name: '广州表哥', type: -1 },
    { name: '一只扬儿', type: -1 },
    { name: '沈酒', type: -1 },
    { name: '客家婷子', type: 1 },
    { name: '三斤.（9237）', type: -1 },
    { name: '虎哥说车', type: -1 },
    { name: '爆笑三江锅（永不言败）', type: -1 },
    { name: '子豪(尊师胜仔）5点扛把子', type: 1 },
    { name: '琪琪', type: -1 },
    { name: '战神土牛（征战全网）', type: 0 },
    { name: '小鲁班下午5点直播', type: -1 },
    { name: '惠子ssica', type: -1 },
    { name: '大狼狗郑建鹏&言真夫妇', type: -1 },
    { name: '一条小团团', type: -1 },
    { name: '高火火', type: -1 },
    { name: '郭聪明', type: -1 },
    { name: '罗永浩', type: 1 },
    { name: '陈赫', type: 0 },
    { name: '摩登兄弟', type: -1 },
    { name: '浪老师', type: -1 },
    { name: '陈死狗cnh', type: -1 },
    { name: '杨驴驴y', type: -1 },
    { name: 'imxiaoxin', type: 0 },
    { name: '丶才子欧巴', type: -1 },
    { name: '旭旭宝宝', type: -1 },
    { name: 'pigff', type: -1 },
    { name: '正经人令北', type: -1 },
    { name: '雨神丶', type: -1 },
    { name: '智勋勋勋勋', type: 0 }
  ],
  musicRankList: [
    {
      name: '龙卷风',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/5605.mp3',
      cover: new URL('../../assets/img/music-cover/1.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 99,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '爱在西元前',
      mp3: 'https://m3.8js.net:99/1916/501204165042405.mp3',
      cover: new URL('../../assets/img/music-cover/2.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '蜗牛',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/3684.mp3',
      cover: new URL('../../assets/img/music-cover/3.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '半岛铁盒',
      mp3: 'https://m3.8js.net:99/2016n/46/94745.mp3',
      cover: new URL('../../assets/img/music-cover/4.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '轨迹',
      mp3: 'https://m3.8js.net:99/1832/411204324135934.mp3',
      cover: new URL('../../assets/img/music-cover/5.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '七里香',
      mp3: 'https://m3.8js.net:99/2016n/14/53717.mp3',
      cover: new URL('../../assets/img/music-cover/6.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '发如雪',
      mp3: 'https://m3.8js.net:99/2014/211204142150965.mp3',
      cover: new URL('../../assets/img/music-cover/7.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '霍元甲',
      mp3: 'https://m3.8js.net:99/1921/261204212643140.mp3',
      cover: new URL('../../assets/img/music-cover/8.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '千里之外(周杰伦/费玉清)',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/121.mp3',
      cover: new URL('../../assets/img/music-cover/9.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '菊花台',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/2022.mp3',
      cover: new URL('../../assets/img/music-cover/10.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '不能说的秘密',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/165.mp3',
      cover: new URL('../../assets/img/music-cover/11.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '牛仔很忙',
      mp3: 'http://im5.tongbu.com/rings/singerring/zt_uunGo_1/219.mp3',
      cover: new URL('../../assets/img/music-cover/12.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '给我一首歌的时间',
      mp3: 'https://m3.8js.net:99/1938/041204380445445.mp3',
      cover: new URL('../../assets/img/music-cover/18.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '烟花易冷',
      mp3: 'https://m3.8js.net:99/1828/051204280535192.mp3',
      cover: new URL('../../assets/img/music-cover/14.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '惊叹号',
      mp3: 'https://m3.8js.net:99/20111103/150.mp3',
      cover: new URL('../../assets/img/music-cover/15.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '明明就',
      mp3: 'https://m3.8js.net:99/2016n/27/96537.mp3',
      cover: new URL('../../assets/img/music-cover/16.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '算什么男人',
      mp3: 'https://m3.8js.net:99/20150107/429.mp3',
      cover: new URL('../../assets/img/music-cover/17.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    },
    {
      name: '告白气球',
      mp3: 'https://m3.8js.net:99/20161016/481.mp3',
      cover: new URL('../../assets/img/music-cover/18.jpg', import.meta.url).href,
      author: '周杰伦',
      duration: 60,
      use_count: 37441000,
      is_collect: false,
      is_play: false
    }
  ],
  brandRankList: {} as Record<string, any[]>,
  selectBrandKey: '汽车',
  selectBrandKeyIndex: 0,
  slideIndex: 0,
  timer: null as any,
  slideItemHeight: null as any
})

// 初始化 brandRankList (保持原有数据)
;(data as any).brandRankList = {
  汽车: [
    {
      name: '五菱汽车',
      logo: 'https://www.wuling.com/favicon.ico',
      hot_count: 1395,
      living: false
    },
    {
      name: '宝马',
      logo: 'https://www.bmw.com.cn/etc/clientlibs/digitals2/clientlib/media/img/BMW_Grey_Logo.svg',
      hot_count: 1395,
      living: true
    },
    {
      name: '吉利汽车',
      logo: 'http://www.cargc.com/uploads/allimg/200828/1401364511-2.jpg',
      hot_count: 1395,
      living: false
    },
    {
      name: '一汽大众-奥迪',
      logo: 'https://www.audi.cn/bin/nemo.static.20210916063431/cms4i-nemo/assets/icons/favicon/favicon-v4.ico',
      hot_count: 1395,
      living: false
    },
    { name: '一汽-大众', logo: 'https://www.vw.com.cn/favicon.ico', hot_count: 1395, living: false }
  ],
  手机: [
    {
      name: '华为',
      logo: 'https://isesglobal.com/wp-content/uploads/2021/01/Huawei.jpg',
      hot_count: 1395,
      living: false
    },
    { name: '小米', logo: 'https://s01.mifile.cn/favicon.ico', hot_count: 1395, living: true },
    {
      name: 'vivo',
      logo: 'http://wwwstatic.vivo.com.cn/vivoportal/web/dist/img/common/favicon_ecf768e.ico',
      hot_count: 1395,
      living: false
    },
    {
      name: 'oppo',
      logo: 'https://code.oppo.com/etc.clientlibs/global-site/clientlibs/clientlib-design/resources/icons/favicon.ico',
      hot_count: 1395,
      living: false
    },
    {
      name: '三星',
      logo: 'https://www.samsung.com/etc.clientlibs/samsung/clientlibs/consumer/global/clientlib-common/resources/images/Favicon.png',
      hot_count: 1395,
      living: false
    }
  ],
  美妆: [
    {
      name: '巴黎欧莱雅',
      logo: 'https://oap-cn-prd-cd.e-loreal.cn/-/media/project/loreal/brand-sites/oap/apac/cn/identity/image-2020-06-19-20-48-00-996.png',
      hot_count: 1395,
      living: false
    },
    {
      name: '花西子',
      logo: 'https://www.haoyunbb.com/img/allimg/210607/001I43462-0.png',
      hot_count: 1395,
      living: false
    },
    {
      name: '完美日记',
      logo: 'http://5b0988e595225.cdn.sohucs.com/images/20200412/9c6caafca79e438f98d98d3986ebce4d.png',
      hot_count: 1395,
      living: false
    },
    {
      name: '雅诗兰黛',
      logo: 'https://vipyidiancom.oss-cn-beijing.aliyuncs.com/vipyidian.com/article/1_150918143107_1.png',
      hot_count: 1395,
      living: false
    },
    {
      name: 'COLORKEY珂拉琪',
      logo: 'https://www.80wzbk.com/uploads/logo/20210129/20210129104015_541.jpg',
      hot_count: 1395,
      living: false
    }
  ]
}

// ==================== 加载文案轮换 ====================
const loadingMsg = ref('正在搜索...')
let loadingMsgTimer: ReturnType<typeof setInterval> | null = null

const LOADING_MESSAGES = [
  '正在翻找数据库的每一个角落...',
  '别急，让 SeekAI 想想...',
  '查阅历史搜索结果中...',
  'SeekAI 正在挠头思考...',
  '正在匹配相似关键词...',
  '偷懒被发现了，立刻开工！',
  '翻阅视频描述中，耐心等待...',
  '数据有点多，正在逐条分析...',
  'SeekAI：这个问题有意思，让我看看...',
  '正在召唤 AI 智能助手...',
  '整理资料中，马上就好...',
  '快好了，再给 SeekAI 一秒...'
]

function startLoadingMessages() {
  loadingMsg.value = '正在搜索...'
  let idx = 0
  loadingMsgTimer = setInterval(() => {
    idx = (idx + 1) % LOADING_MESSAGES.length
    loadingMsg.value = LOADING_MESSAGES[idx]
  }, 2000)
}

function stopLoadingMessages() {
  if (loadingMsgTimer) {
    clearInterval(loadingMsgTimer)
    loadingMsgTimer = null
  }
}

// ==================== 打字机效果 ====================
const typingTimer = ref<ReturnType<typeof setInterval> | null>(null)
const displayedSummary = ref('')
const typingFinished = ref(false)

function stopTypewriter() {
  if (typingTimer.value) {
    clearInterval(typingTimer.value)
    typingTimer.value = null
  }
}

function startTypewriter(text: string) {
  stopTypewriter()
  displayedSummary.value = ''
  typingFinished.value = false
  let cursor = 0
  const charsPerTick = 6
  typingTimer.value = setInterval(() => {
    cursor += charsPerTick
    if (cursor >= text.length) {
      displayedSummary.value = text
      stopTypewriter()
      typingFinished.value = true
    } else {
      displayedSummary.value = text.slice(0, cursor)
    }
  }, 25)
}

const knowledgeExpanded = ref(false)
const discoveryExpanded = ref(false)

function expandSummary() {
  // 兼容旧调用: 双模块各自由独立按钮展开, 此函数不再使用
  stopTypewriter()
  displayedSummary.value = data.aiSummary
  typingFinished.value = true
  knowledgeExpanded.value = true
  discoveryExpanded.value = true
}

/** 轻量 Markdown 转 HTML, 处理 AI 摘要中的 ### / ## / ** / - 列表 */
function renderMarkdown(text: string): string {
  if (!text) return ''
  let html = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  // 标题 (先处理 ### 再处理 ##，避免冲突)
  html = html.replace(/^### (.+)$/gm, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3 class="md-h3">$1</h3>')

  // 加粗
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

  // 无序列表项 (连续的行包裹在 <ul> 中)
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')

  // 有序列表项
  html = html.replace(/^\d+\.\s+(.+)$/gm, '<li>$1</li>')
  // 避免重复包裹
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, (match) => {
    if (match.includes('<ul>')) return match
    return `<ul>${match}</ul>`
  })

  // 连续换行 → 段落分隔
  html = html.replace(/\n\n+/g, '<br><br>')
  // 单个换行 → <br>
  html = html.replace(/\n/g, '<br>')

  return html
}

const summaryHtml = computed(() => {
  const text = data.aiExpanded ? data.aiSummary : displayedSummary.value
  if (!text || !typingFinished.value) return ''
  return renderMarkdown(text)
})

/** 按 ## 标题拆分为两个模块 */
function splitModules(fullText: string): { knowledge: string; discovery: string } {
  if (!fullText) return { knowledge: '', discovery: '' }
  const discoveryIdx = fullText.indexOf('\n## 平台发现')
  if (discoveryIdx === -1) {
    // 没有平台发现标题, 尝试找 ## 智能解读
    const knowledgeIdx = fullText.indexOf('## 智能解读')
    if (knowledgeIdx === -1) return { knowledge: '', discovery: fullText }
    return { knowledge: fullText.substring(knowledgeIdx), discovery: '' }
  }
  let knowledgeStart = fullText.indexOf('## 智能解读')
  if (knowledgeStart === -1) knowledgeStart = 0
  const knowledge = fullText.substring(knowledgeStart, discoveryIdx).trim()
  const discovery = fullText.substring(discoveryIdx).trim()
  return { knowledge, discovery }
}

const knowledgeText = computed(() => splitModules(data.aiSummary).knowledge)
const discoveryText = computed(() => splitModules(data.aiSummary).discovery)
const knowledgeHtml = computed(() => renderMarkdown(knowledgeText.value))
const discoveryHtml = computed(() => renderMarkdown(discoveryText.value))

const lHistory = computed(() => {
  if (data.isExpand) {
    if (data.history.length > 10) return data.history.slice(0, 10)
    return data.history
  } else {
    if (data.history.length > 2) return data.history.slice(0, 2)
    return data.history
  }
})

const selectBrandList = computed(() => {
  return (data as any).brandRankList[data.selectBrandKey] || []
})

const brandListKeys = computed<string[]>(() => {
  return Object.keys((data as any).brandRankList)
})

const slideListHeight = computed(() => {
  return {
    height: data.slideItemHeight ? data.slideItemHeight + 'px' : '100%'
  }
})

// 综合结果 = 所有视频+图文
const allResults = computed(() => data.videoResults)

// 仅视频 (recommend-video + long-video)
const videoOnlyResults = computed(() =>
  data.videoResults.filter(
    (v: any) => !v.type || v.type === 'recommend-video' || v.type === 'long-video'
  )
)

// 仅图文
const imageOnlyResults = computed(() =>
  data.videoResults.filter((v: any) => v.type === 'image' || v.type === 'text')
)

const suggestionPool = computed(() => {
  const names = new Set<string>()
  data.guess.forEach((g) => names.add(g.name))
  data.hotRankList.forEach((h) => names.add(h.name))
  data.liveRankList.forEach((l) => names.add(l.name))
  data.musicRankList.forEach((m) => names.add(m.name))
  return Array.from(names)
})

let skipWatcher = false

watch(
  () => data.searchKey,
  (newVal) => {
    if (skipWatcher) {
      skipWatcher = false
      return
    }
    if (data.isSearched) {
      data.isSearched = false
      data.videoResults = []
      data.userResults = []
      data.aiSummary = ''
      data.aiExpanded = false
      knowledgeExpanded.value = false
      discoveryExpanded.value = false
      stopTypewriter()
      displayedSummary.value = ''
    }
    const kw = (newVal || '').trim().toLowerCase()
    if (!kw) {
      data.suggestions = []
      return
    }
    data.suggestions = suggestionPool.value.filter((s) => s.toLowerCase().includes(kw)).slice(0, 10)
  }
)

watch(
  () => data.slideIndex,
  (newVal) => {
    nextTick(() => {
      data.slideItemHeight = new Dom(`.slide${newVal}`).css('height')
      data.slideItemHeight = parseFloat(data.slideItemHeight) + 50
    })
    if (newVal === 3) {
      data.timer = setInterval(() => {
        if (data.selectBrandKeyIndex === brandListKeys.value.length - 1) {
          data.selectBrandKeyIndex = 0
        } else {
          data.selectBrandKeyIndex++
        }
        data.selectBrandKey = brandListKeys.value[data.selectBrandKeyIndex]
      }, 3000)
    } else {
      clearInterval(data.timer)
    }
  },
  { immediate: true }
)

function switchTab(tab: string) {
  data.searchTab = tab
}

function selectSuggestion(item: string) {
  data.searchKey = item
  doSearch()
}

async function doSearch() {
  const kw = data.searchKey.trim()
  if (!kw) {
    data.videoResults = []
    data.userResults = []
    data.searchTab = '综合'
    data.isSearched = false
    return
  }
  data.isSearched = true
  data.suggestions = []
  data.aiExpanded = false
  knowledgeExpanded.value = false
  discoveryExpanded.value = false
  data.aiSummary = ''
  stopTypewriter()
  displayedSummary.value = ''
  data.searchTab = '综合'

  // 并行请求
  startLoadingMessages()
  data.videoLoading = true
  data.userLoading = true

  // 视频搜索
  const videoPromise = (async () => {
    try {
      const res = await searchVideos(kw)
      if (res.success && res.data) {
        data.videoResults = (res.data as any[]).map(normalizeVideoForList)
      } else {
        data.videoResults = []
      }
    } catch {
      data.videoResults = []
    }
    data.videoLoading = false
  })()

  // 用户搜索
  const userPromise = (async () => {
    try {
      const res = await searchUsers(kw)
      if (res.success && res.data) {
        data.userResults = (res.data as any[]).map(normalizeSearchUser)
      } else {
        data.userResults = []
      }
    } catch {
      data.userResults = []
    }
    data.userLoading = false
  })()

  // AI 总结
  data.aiLoading = true
  stopTypewriter()
  displayedSummary.value = ''
  const aiPromise = (async () => {
    try {
      const res = await searchSummary(kw)
      if (res.success && res.data?.summary) {
        data.aiSummary = res.data.summary
        startTypewriter(res.data.summary)
      }
    } catch (e) {
      // 摘要请求失败不影响主搜索结果展示
    }
    data.aiLoading = false
    stopLoadingMessages()
  })()

  await Promise.all([videoPromise, userPromise, aiPromise])
  saveHistory(kw)
}

function clearSearch() {
  data.videoResults = []
  data.userResults = []
  data.searchTab = '综合'
  data.isSearched = false
  data.suggestions = []
  data.aiSummary = ''
  knowledgeExpanded.value = false
  discoveryExpanded.value = false
  stopTypewriter()
  stopLoadingMessages()
  displayedSummary.value = ''
  data.aiExpanded = false
}

function normalizeVideoForList(v: any): any {
  const avatar168 = v.author?.avatar_168x168?.url_list?.[0] || v.author?.avatar_168x168 || ''
  return {
    aweme_id: v.aweme_id,
    desc: v.desc,
    type: v.type || '',
    duration: v.duration,
    create_time: v.create_time || '',
    video: {
      play_addr: v.video?.play_addr,
      cover: v.video?.cover
    },
    author: {
      ...v.author,
      avatar_168x168: v.author?.avatar_168x168 || { url_list: [avatar168] }
    },
    statistics: v.statistics,
    is_loved: v.is_loved,
    is_collect: v.is_collect,
    is_attention: v.is_attention
  }
}

function normalizeSearchUser(u: any): any {
  const avatar168 = u.avatar_168x168?.url_list?.[0] || ''
  return {
    uid: u.uid,
    nickname: u.nickname || '',
    unique_id: u.unique_id || '',
    avatar_168x168: u.avatar_168x168 || { url_list: [avatar168] },
    follower_count: u.follower_count || 0,
    is_followed: u.is_followed || false,
    is_following_me: u.is_following_me || false
  }
}

function goUserHome(item: any) {
  const uid = item.uid
  if (uid && !isNaN(Number(uid))) {
    nav('/people/user-home/' + uid)
  }
}

function goVideoDetail(item: any) {
  store.routeData = { list: data.videoResults, index: data.videoResults.indexOf(item) }
  router.push({ path: '/video-detail' })
}

async function toggleFollow(item: any) {
  if (!item || item.uid == null) return
  const uid = Number(item.uid)
  if (isNaN(uid)) return
  try {
    const res = await toggleFollowUser(uid)
    if (res.success) {
      item.is_followed = !item.is_followed
    }
  } catch {
    /* ignore */
  }
}

function _formatDuration(ms: number): string {
  if (!ms) return ''
  return _duration(ms / 1000)
}

function _formatTime(timeStr: string): string {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  if (isNaN(d.getTime())) return ''
  const now = Date.now()
  const diff = now - d.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 30) return `${days}天前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => {
  loadHistory()
  refresh()
  const q = route.query.q as string
  if (q && q.trim()) {
    skipWatcher = true
    data.searchKey = q.trim()
    doSearch()
  }
})

onUnmounted(() => {
  stopLoadingMessages()
  stopTypewriter()
})

async function loadHistory() {
  try {
    const res = await getSearchHistory()
    if (res.success && Array.isArray(res.data)) data.history = res.data as string[]
  } catch {
    /* 未登录或接口不可用时保持空列表 */
  }
}

function saveHistory(keyword: string) {
  if (!keyword.trim()) return
  const kw = keyword.trim()
  data.history = [kw, ...data.history.filter((h) => h !== kw)].slice(0, 50)
  saveSearchHistory(kw).catch(() => {})
}

async function deleteHistoryKeyword(keyword: string) {
  const kw = keyword.trim()
  data.history = data.history.filter((h) => h !== kw)
  try {
    await deleteSearchHistoryKeyword(kw)
  } catch {
    /* ignore */
  }
}

async function clearHistory() {
  data.history = []
  try {
    await clearSearchHistory()
  } catch {
    /* ignore */
  }
}

function toggleKey(key: string, i: number) {
  data.selectBrandKey = key
  data.selectBrandKeyIndex = i
  clearInterval(data.timer)
}

function refresh() {
  data.randomGuess = sampleSize(data.guess, 6)
}

function toggle() {
  if (data.isExpand) {
    _showSimpleConfirmDialog('是否清空历史记录？', clearHistory, null, '确定', '取消')
  } else {
    data.isExpand = true
  }
}
</script>

<style scoped lang="less">
@import '../../assets/less/index';

.Search {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  overflow: auto;
  color: white;
  font-size: 14rem;

  .type {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 16rem;
    width: 16rem;
    font-size: 12rem;
    margin-left: 5rem;
    border-radius: 2rem;

    &.hot {
      background: var(--primary-btn-color);
    }
    &.new {
      background: rgb(186, 51, 226);
    }
  }

  .header {
    z-index: 4;
    background: var(--main-bg);
    height: 60rem;
    font-size: 14rem;
    padding: 0 var(--page-padding);
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid var(--line-color);
    position: fixed;
    width: 100%;
    box-sizing: border-box;
    top: 0;

    .search-ctn {
      flex: 1;
    }
    .scan {
      transform: scale(2);
      height: 10rem;
      width: 10rem;
    }
  }

  .content {
    padding-top: 60rem;

    .suggestions {
      padding: 8rem var(--page-padding);
      background: var(--main-bg);

      .suggest-item {
        display: flex;
        align-items: center;
        padding: 12rem 0;
        gap: 12rem;
        font-size: 15rem;
        color: white;
        border-bottom: 0.5px solid rgba(255, 255, 255, 0.06);
        cursor: pointer;

        &:active {
          opacity: 0.6;
        }

        .search-icon {
          width: 16rem;
          height: 16rem;
          opacity: 0.5;
        }
        .text {
          flex: 1;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        &.no-match {
          color: var(--second-text-color);
          cursor: default;
        }
      }
    }

    // ==================== Tab 栏 ====================
    .search-tabs-wrap {
      padding: 5rem 0;
      border-bottom: 1px solid var(--line-color);
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;

      &::-webkit-scrollbar {
        display: none;
      }
    }

    .search-tabs {
      display: flex;
      gap: 0;
      white-space: nowrap;
      padding: 0 var(--page-padding);
      font-size: 14rem;

      .tab {
        flex-shrink: 0;
        color: var(--second-text-color);
        padding: 8rem 14rem;
        cursor: pointer;
        border-radius: 20rem;
        margin-right: 4rem;
        transition: all 0.2s;

        &:active {
          opacity: 0.7;
        }

        &.active {
          color: white;
          font-weight: bold;
          background: var(--second-btn-color-tran);
        }
      }
    }

    // ==================== AI 总结 / 双模块卡片 ====================
    .ai-summary {
      position: relative;
      margin: 10rem var(--page-padding);
      padding: 12rem;
      background: var(--active-main-bg);
      border-radius: 8rem;
      overflow: hidden;

      .ai-text {
        font-size: 13rem;
        line-height: 1.7;
        color: var(--second-text-color);
        white-space: pre-line;
        max-height: 220rem;
        overflow: hidden;
      }

      .ai-cursor {
        color: var(--second-text-color);
        animation: ai-blink 0.7s step-end infinite;
      }

      @keyframes ai-blink {
        0%,
        100% {
          opacity: 1;
        }
        50% {
          opacity: 0;
        }
      }

      @keyframes ai-loading-bounce {
        0%,
        80%,
        100% {
          transform: scale(0.6);
          opacity: 0.4;
        }
        40% {
          transform: scale(1);
          opacity: 1;
        }
      }

      &.ai-loading {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6rem;
        padding: 16rem 12rem;

        .ai-loading-dot {
          width: 8rem;
          height: 8rem;
          border-radius: 50%;
          background: var(--second-btn-color-tran);
          animation: ai-loading-bounce 1.2s ease-in-out infinite;
          &:nth-child(1) {
            animation-delay: 0s;
          }
          &:nth-child(2) {
            animation-delay: 0.2s;
          }
          &:nth-child(3) {
            animation-delay: 0.4s;
          }
        }

        .ai-loading-text {
          margin-left: 4rem;
          font-size: 12rem;
          color: var(--second-text-color);
        }
      }
    }

    // 双模块卡片
    .ai-module {
      position: relative;
      margin: 10rem var(--page-padding);
      background: var(--active-main-bg);
      border-radius: 8rem;
      overflow: hidden;

      .ai-module-header {
        display: flex;
        align-items: center;
        gap: 6rem;
        padding: 10rem 12rem 0;
        font-size: 12rem;
        color: var(--second-text-color);
        letter-spacing: 0.5rem;
        text-transform: uppercase;

        .module-icon {
          width: 16rem;
          height: 16rem;
        }
      }

      .ai-module-body {
        padding: 8rem 12rem 12rem;
        font-size: 13rem;
        line-height: 1.7;
        color: var(--second-text-color);
        max-height: 180rem;
        overflow: hidden;
      }

      &.expanded .ai-module-body {
        max-height: none;
      }

      .ai-fade {
        position: absolute;
        bottom: 36rem;
        left: 0;
        right: 0;
        height: 60rem;
        background: linear-gradient(
          to bottom,
          transparent 0%,
          rgba(31, 37, 52, 0.15) 20%,
          rgba(31, 37, 52, 0.5) 50%,
          rgba(31, 37, 52, 0.85) 80%,
          rgb(31, 37, 52) 100%
        );
        pointer-events: none;
      }

      &.expanded .ai-fade {
        display: none;
      }

      // 左侧色条区分两个模块
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 12rem;
        bottom: 12rem;
        width: 3rem;
        border-radius: 0 2rem 2rem 0;
      }

      &.ai-knowledge::before {
        background: linear-gradient(to bottom, var(--second-btn-color), rgba(120, 130, 180, 0.3));
      }

      &.ai-discovery::before {
        background: linear-gradient(to bottom, var(--primary-btn-color), rgba(254, 44, 85, 0.3));
      }
    }

    // Markdown 渲染 (打字机卡片 + 双模块共用)
    .ai-md {
      white-space: normal;

      :deep(.md-h3) {
        font-size: 15rem;
        font-weight: 600;
        color: white;
        margin: 12rem 0 6rem;
        line-height: 1.4;
      }
      :deep(.md-h4) {
        font-size: 14rem;
        font-weight: 600;
        color: white;
        margin: 8rem 0 4rem;
        line-height: 1.4;
      }
      :deep(strong) {
        color: white;
        font-weight: 600;
      }
      :deep(ul) {
        padding-left: 16rem;
        margin: 4rem 0;
      }
      :deep(li) {
        margin: 2rem 0;
        list-style: disc;
      }
    }

    // 展开按钮 (每个模块卡片独立, 绝对定位在卡片底部)
    .ai-expand-btn {
      position: absolute;
      bottom: 6rem;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      align-items: center;
      gap: 4rem;
      padding: 6rem 16rem;
      font-size: 12rem;
      color: white;
      background: var(--second-btn-color);
      border-radius: 20rem;
      cursor: pointer;
      z-index: 2;

      &:active {
        opacity: 0.8;
      }

      .expand-arrow {
        width: 14rem;
        height: 14rem;
      }
    }

    // ==================== 瀑布流 (综合/视频/图文 共用) ====================
    .waterfall {
      column-count: 2;
      column-gap: 8rem;
      padding: 10rem var(--page-padding);

      .waterfall-item {
        break-inside: avoid;
        margin-bottom: 10rem;
        background: var(--active-main-bg);
        border-radius: 6rem;
        overflow: hidden;
        cursor: pointer;

        &:active {
          opacity: 0.85;
        }

        .cover-wrap {
          position: relative;
          width: 100%;

          .cover {
            width: 100%;
            display: block;
            object-fit: cover;
          }

          .duration {
            position: absolute;
            bottom: 4rem;
            right: 4rem;
            background: rgba(0, 0, 0, 0.6);
            color: white;
            font-size: 10rem;
            padding: 2rem 5rem;
            border-radius: 3rem;
          }
        }

        .info {
          padding: 8rem;

          .desc {
            font-size: 13rem;
            color: white;
            line-height: 1.4;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-bottom: 8rem;
          }

          .meta {
            display: flex;
            align-items: center;
            gap: 8rem;

            .avatar {
              width: 28rem;
              height: 28rem;
              border-radius: 50%;
              flex-shrink: 0;
              object-fit: cover;
            }

            .author-info {
              flex: 1;
              min-width: 0;
              display: flex;
              flex-direction: column;
              gap: 2rem;

              .name {
                font-size: 11rem;
                color: var(--second-text-color);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
              }

              .time {
                font-size: 10rem;
                color: var(--second-text-color);
                opacity: 0.6;
              }
            }

            .likes {
              display: flex;
              align-items: center;
              gap: 3rem;
              font-size: 12rem;
              color: var(--second-text-color);
              flex-shrink: 0;

              svg {
                width: 14rem;
                height: 14rem;
              }
            }
          }
        }
      }
    }

    // ==================== 用户列表 ====================
    .user-list {
      padding: 0 var(--page-padding);

      .user-item {
        display: flex;
        align-items: center;
        padding: 12rem 0;
        gap: 12rem;
        border-bottom: 0.5px solid rgba(255, 255, 255, 0.06);
        cursor: pointer;

        &:active {
          opacity: 0.6;
        }

        .u-avatar {
          width: 52rem;
          height: 52rem;
          border-radius: 50%;
          flex-shrink: 0;
          object-fit: cover;
        }

        .u-info {
          flex: 1;
          min-width: 0;

          .u-name {
            font-size: 15rem;
            color: white;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-bottom: 4rem;
          }

          .u-sub {
            font-size: 12rem;
            color: var(--second-text-color);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;

            .u-sep {
              margin: 0 6rem;
              opacity: 0.3;
            }
          }
        }

        .u-follow-btn {
          flex-shrink: 0;
          padding: 6rem 14rem;
          border-radius: 4rem;
          font-size: 12rem;
          font-weight: bold;
          background: var(--primary-btn-color);
          color: white;
          cursor: pointer;

          &:active {
            opacity: 0.8;
          }

          &.followed {
            background: var(--second-btn-color-tran);
            color: var(--second-text-color);
            border: 1px solid var(--second-btn-color);
          }

          &.mutual {
            background: var(--second-btn-color-tran);
            color: var(--second-text-color);
            border: 1px solid var(--second-btn-color);
          }
        }
      }
    }

    .loading-tip,
    .empty-tip {
      text-align: center;
      padding: 40rem 0;
      color: var(--second-text-color);
      font-size: 14rem;
    }

    // ==================== 默认页面 (历史/猜你想搜/排行榜) ====================
    .history {
      .row {
        min-height: 40rem;
      }
      .history-expand {
        text-align: center;
        padding: 10rem;
        color: var(--second-text-color);
      }
    }

    .guess {
      padding: 0 var(--page-padding);

      .title {
        font-size: 14rem;
        padding: 10rem 0;
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: var(--second-text-color);

        .right {
          display: flex;
          align-items: center;
          img {
            margin-right: 5rem;
            width: 13rem;
            height: 13rem;
          }
        }
      }

      .keys {
        font-size: 16rem;
        display: flex;
        flex-wrap: wrap;

        .key {
          box-sizing: border-box;
          padding: 8rem 0;
          width: 49%;
          display: flex;
          align-items: center;

          .desc {
            max-width: 80%;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
          }
        }
      }
    }

    .rank-list {
      .indicator {
        padding: 15rem;
        display: flex;
        align-items: center;
        font-size: 14rem;

        .tab {
          color: var(--second-text-color);
          margin-right: 20rem;

          &.active {
            transform: scale(1.2);
            color: white;
          }

          &:nth-child(1) {
            &.active {
              font-weight: bold;
              background: linear-gradient(to right, rgb(255, 165, 71), rgb(218, 77, 115));
              -webkit-background-clip: text;
              color: transparent;
            }
          }
        }
      }

      .slide0 {
        box-sizing: border-box;
        margin: 0 var(--page-padding) 50rem var(--page-padding);
        background: linear-gradient(to right, rgb(32, 29, 36), rgb(50, 29, 38));
        padding: var(--page-padding);
        border-radius: 10rem;

        .l-row {
          font-size: 14rem;
          display: flex;
          margin-bottom: 16rem;
          align-items: center;
          color: var(--second-text-color);

          .rank-wrapper {
            display: flex;
            align-items: center;
            .rank {
              width: 18rem;
              height: 18rem;
              line-height: 18rem;
              text-align: center;
              margin-right: 15rem;
            }
          }

          .right {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: space-between;
            min-width: 0;

            .center {
              width: calc(100% - 140rem);
              box-sizing: border-box;
              display: flex;
              align-items: center;
              font-size: 14rem;
              color: white;

              .desc {
                max-width: 85%;
                font-size: 14rem;
                white-space: nowrap;
                text-overflow: ellipsis;
                overflow: hidden;
              }
            }
            .count {
              font-size: 12rem;
            }
          }
        }
      }

      .slide1 {
        box-sizing: border-box;
        margin: 0 var(--page-padding) 50rem var(--page-padding);
        background: rgb(20, 22, 34);
        border: 1px solid rgba(31, 34, 52, 0.5);
        padding: var(--page-padding);
        border-radius: 10rem;

        .l-row {
          font-size: 14rem;
          display: flex;
          margin-bottom: 10rem;
          align-items: center;
          color: var(--second-text-color);

          &:active {
            opacity: 0.5;
          }

          .rank-wrapper {
            display: flex;
            align-items: center;
            .rank {
              width: 18rem;
              height: 18rem;
              line-height: 18rem;
              text-align: center;
              margin-right: 15rem;
              &.top {
                color: yellow;
              }
            }
          }

          .right {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: space-between;

            .center {
              width: calc(100% - 160rem);
              box-sizing: border-box;
              display: flex;
              align-items: center;
              font-size: 14rem;
              color: white;

              .avatar-wrapper {
                @width: 35rem;
                margin-right: 10rem;
                display: flex;
                align-items: center;
                justify-content: center;
                width: @width;
                height: @width;
                border-radius: 50%;
                background: var(--primary-btn-color);

                .avatar {
                  width: @width - 0.3;
                  border-radius: 50%;
                  padding: 1rem;
                  background: black;
                }
              }

              .desc {
                max-width: 55%;
                font-size: 14rem;
                white-space: nowrap;
                text-overflow: ellipsis;
                overflow: hidden;
              }

              .live-type {
                height: 22rem;
                padding: 0 5rem;
                display: flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
                font-size: 10rem;
                color: var(--second-text-color);
                margin-left: 5rem;
                border-radius: 2rem;
                background: var(--second-btn-color-tran);

                .type2 {
                  margin-right: 2rem;
                  width: 10rem;
                  height: 10rem;
                }
                .type1 {
                  margin-right: 2rem;
                  width: 15rem;
                  height: 10rem;
                }
              }
            }
            .count {
              font-size: 12rem;
            }
          }
        }
      }

      .slide2 {
        box-sizing: border-box;
        margin: 0 var(--page-padding) 50rem var(--page-padding);
        background: rgb(20, 22, 34);
        border: 1px solid rgba(31, 34, 52, 0.5);
        padding: var(--page-padding);
        border-radius: 10rem;

        .l-row {
          font-size: 14rem;
          display: flex;
          margin-bottom: 10rem;
          align-items: center;
          color: var(--second-text-color);
          &:active {
            opacity: 0.5;
          }

          .rank-wrapper {
            display: flex;
            align-items: center;
            .rank {
              width: 18rem;
              height: 18rem;
              line-height: 18rem;
              text-align: center;
              margin-right: 15rem;
              &.top {
                color: yellow;
              }
            }
          }

          .right {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: space-between;

            .center {
              width: calc(100% - 150rem);
              box-sizing: border-box;
              display: flex;
              align-items: center;
              font-size: 14rem;
              color: white;

              .avatar-wrapper {
                margin-right: 10rem;
                .avatar {
                  width: 30rem;
                  height: 30rem;
                  border-radius: 2rem;
                }
              }

              .desc {
                max-width: 95%;
                font-size: 14rem;
                white-space: nowrap;
                text-overflow: ellipsis;
                overflow: hidden;
              }
            }

            .count {
              display: flex;
              align-items: center;
              font-size: 12rem;
              img {
                margin-right: 2rem;
                width: 15rem;
                height: 15rem;
              }
            }
          }
        }
      }

      .slide3 {
        box-sizing: border-box;
        margin: 0 var(--page-padding) 50rem var(--page-padding);
        border-radius: 10rem;

        .slide4-wrapper {
          padding: 5rem var(--page-padding);

          .brands {
            color: var(--second-text-color);
            font-size: 12rem;
            margin-bottom: 15rem;
            display: flex;

            .brand {
              border-radius: 2rem;
              margin-right: 10rem;
              padding: 5rem 10rem;
              background: var(--second-btn-color-tran);
              &.active {
                color: white;
                background: var(--second-btn-color);
              }
            }
          }

          .l-row {
            font-size: 14rem;
            display: flex;
            margin-bottom: 10rem;
            align-items: center;
            color: var(--second-text-color);
            &:active {
              opacity: 0.5;
            }

            .rank-wrapper {
              display: flex;
              align-items: center;
              .rank {
                width: 18rem;
                height: 18rem;
                line-height: 18rem;
                text-align: center;
                margin-right: 15rem;
                &.top {
                  color: yellow;
                }
              }
            }

            .right {
              flex: 1;
              display: flex;
              align-items: center;
              justify-content: space-between;

              .center {
                width: calc(100% - 150rem);
                box-sizing: border-box;
                display: flex;
                align-items: center;
                font-size: 14rem;
                color: white;

                .avatar-wrapper {
                  @width: 35rem;
                  margin-right: 10rem;

                  &.living {
                    position: relative;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    width: @width;
                    height: @width;
                    border-radius: 50%;
                    background: var(--primary-btn-color);

                    .avatar-out-line {
                      width: @width;
                      height: @width;
                      position: absolute;
                      background: transparent;
                      border-radius: 50%;
                      border: 2rem solid var(--primary-btn-color);
                      animation: avatar-out-line 1s infinite;
                      @keyframes avatar-out-line {
                        from {
                          padding: 0;
                        }
                        to {
                          opacity: 0;
                          padding: 2rem;
                        }
                      }
                    }
                    .avatar {
                      padding: 1rem;
                      animation: avatar 1s infinite alternate;
                    }
                  }

                  .avatar {
                    position: relative;
                    z-index: 2;
                    width: @width - 0.3;
                    height: @width - 0.3;
                    border-radius: 50%;
                    background: black;
                    box-sizing: border-box;
                    @keyframes avatar {
                      from {
                        padding: 1rem;
                      }
                      to {
                        padding: 2rem;
                      }
                    }
                  }
                }
                .desc {
                  max-width: 95%;
                  font-size: 14rem;
                  white-space: nowrap;
                  text-overflow: ellipsis;
                  overflow: hidden;
                }
              }
              .count {
                display: flex;
                align-items: center;
                font-size: 12rem;
                img {
                  margin-right: 2rem;
                  width: 15rem;
                  height: 15rem;
                }
              }
            }
          }
        }

        .ad {
          background: var(--second-btn-color-tran);
          display: flex;
          align-items: center;
          justify-content: center;
          height: 100rem;
        }
      }

      .more {
        margin-bottom: 20rem;
        font-size: 12rem;
        padding: 10rem 10rem 0 10rem;
        text-align: center;
        color: yellow;
      }
    }
  }
}
</style>
