<script setup lang="ts">
import BaseMusic from '../BaseMusic.vue'
import { _formatNumber, cloneDeep, _notice } from '@/utils'
import bus, { EVENT_KEY } from '@/utils/bus'
import { Icon } from '@iconify/vue'
import { useClick } from '@/utils/hooks/useClick'
import { inject, onMounted, onUnmounted } from 'vue'
import { toggleVideoLike, toggleCollect } from '@/api/videos'
import { toggleFollowUser } from '@/api/user'

const props = defineProps({
  isMy: {
    type: Boolean,
    default: () => {
      return false
    }
  },
  item: {
    type: Object,
    default: () => {
      return {}
    }
  }
})

const position = inject<any>('position', {})

const emit = defineEmits(['update:item', 'goUserInfo', 'showComments', 'showShare', 'goMusic'])

function _updateItem(props, key, val) {
  const old = cloneDeep(props.item)
  old[key] = val
  emit('update:item', old)
  bus.emit(EVENT_KEY.UPDATE_ITEM, { position: position.value, item: old })
}

let liking = false

async function loved() {
  if (liking) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  liking = true
  const wasLoved = props.item.is_loved
  // 乐观更新
  props.item.is_loved = !wasLoved
  props.item.statistics.digg_count += wasLoved ? -1 : 1
  _updateItem(props, 'is_loved', !wasLoved)

  try {
    const res = await toggleVideoLike(awemeId)
    if (res.success) {
      props.item.statistics.digg_count = res.data.likeCount
      _updateItem(props, 'is_loved', res.data.isLoved)
      bus.emit(EVENT_KEY.LIKE_UPDATED)
    } else {
      // 回滚
      props.item.is_loved = wasLoved
      props.item.statistics.digg_count += wasLoved ? 1 : -1
      _updateItem(props, 'is_loved', wasLoved)
    }
  } finally {
    liking = false
  }
}

let collecting = false

async function collected() {
  if (collecting) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  collecting = true
  const wasCollected = props.item.is_collect
  // 乐观更新
  props.item.is_collect = !wasCollected
  props.item.statistics.collect_count += wasCollected ? -1 : 1
  _updateItem(props, 'is_collect', !wasCollected)

  try {
    const res = await toggleCollect(awemeId)
    if (res.success) {
      props.item.is_collect = res.data.isCollected
      props.item.statistics.collect_count = res.data.collectCount
      _updateItem(props, 'is_collect', res.data.isCollected)
      bus.emit(EVENT_KEY.COLLECT_UPDATED)
    } else {
      // 回滚
      props.item.is_collect = wasCollected
      props.item.statistics.collect_count += wasCollected ? 1 : -1
      _updateItem(props, 'is_collect', wasCollected)
    }
  } catch {
    props.item.is_collect = wasCollected
    props.item.statistics.collect_count += wasCollected ? 1 : -1
    _updateItem(props, 'is_collect', wasCollected)
  } finally {
    collecting = false
  }
}

async function attention(e) {
  const authorId = props.item.author?.uid
  if (!authorId) return
  e.currentTarget.classList.add('attention')
  const res = await toggleFollowUser(authorId)
  if (res.success) {
    _updateItem(props, 'is_attention', res.data.isAttention)
  } else {
    e.currentTarget.classList.remove('attention')
    _notice(res.msg || '操作失败')
  }
}

function showComments() {
  bus.emit(EVENT_KEY.OPEN_COMMENTS, props.item.aweme_id)
}

const vClick = useClick()

onMounted(() => {
  bus.on(EVENT_KEY.COMMENT_ADDED, (videoId: string) => {
    if (String(videoId) === String(props.item.aweme_id)) {
      props.item.statistics.comment_count++
      _updateItem(props, 'statistics', { ...props.item.statistics })
    }
  })
})
onUnmounted(() => {
  bus.off(EVENT_KEY.COMMENT_ADDED)
})
</script>

<template>
  <div class="toolbar mb1r">
    <div class="avatar-ctn mb2r">
      <img
        class="avatar"
        :src="item.author?.avatar_168x168?.url_list?.[0]"
        alt=""
        v-click="() => bus.emit(EVENT_KEY.GO_USERINFO)"
      />
      <transition name="fade">
        <div v-if="!item.is_attention && !isMy" v-click="attention" class="options">
          <img class="no" src="../../assets/img/icon/add-light.png" alt="" />
          <img class="yes" src="../../assets/img/icon/ok-red.png" alt="" />
        </div>
      </transition>
    </div>
    <div class="love mb2r" v-click="loved">
      <div>
        <img src="../../assets/img/icon/love.svg" class="love-image" v-if="!item.is_loved" />
        <img src="../../assets/img/icon/loved.svg" class="love-image" v-if="item.is_loved" />
      </div>
      <span>{{ _formatNumber(item.statistics.digg_count) }}</span>
    </div>
    <div class="message mb2r" v-click="showComments">
      <Icon icon="mage:message-dots-round-fill" class="icon" style="color: white" />
      <span>{{ _formatNumber(item.statistics.comment_count) }}</span>
    </div>
    <!--TODO     -->
    <div class="message mb2r" v-click="collected">
      <Icon
        v-if="item.is_collect"
        icon="ic:round-star"
        class="icon"
        style="color: rgb(252, 179, 3)"
      />
      <Icon v-else icon="ic:round-star" class="icon" style="color: white" />
      <span>{{ _formatNumber(item.statistics.collect_count) }}</span>
    </div>
    <div v-if="!props.isMy" class="share mb2r" v-click="() => bus.emit(EVENT_KEY.SHOW_SHARE)">
      <img src="../../assets/img/icon/share-white-full.png" alt="" class="share-image" />
      <span>{{ _formatNumber(item.statistics.share_count) }}</span>
    </div>
    <div v-else class="share mb2r" v-click="() => bus.emit(EVENT_KEY.SHOW_SHARE)">
      <img src="../../assets/img/icon/menu-white.png" alt="" class="share-image" />
    </div>
    <!--    <BaseMusic-->
    <!--        :cover="item.music.cover"-->
    <!--        v-click="$router.push('/home/music')"-->
    <!--    /> -->
    <BaseMusic />
  </div>
</template>

<style scoped lang="less">
.toolbar {
  //width: 40px;
  position: absolute;
  bottom: 0;
  right: 10rem;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;

  .avatar-ctn {
    position: relative;

    @w: 45rem;

    .avatar {
      width: @w;
      height: @w;
      border: 3rem solid white;
      border-radius: 50%;
    }

    .options {
      position: absolute;
      border-radius: 50%;
      margin: auto;
      left: 0;
      right: 0;
      bottom: -5px;
      background: red;
      //background: black;
      width: 18rem;
      height: 18rem;
      display: flex;
      justify-content: center;
      align-items: center;
      transition: all 1s;

      img {
        position: absolute;
        width: 14rem;
        height: 14rem;
        transition: all 1s;
      }

      .yes {
        opacity: 0;
        transform: rotate(-180deg);
      }

      &.attention {
        background: white;

        .no {
          opacity: 0;
          transform: rotate(180deg);
        }

        .yes {
          opacity: 1;
          transform: rotate(0deg);
        }
      }
    }
  }

  .love,
  .message,
  .share {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    @width: 35rem;

    img {
      width: @width;
      height: @width;
    }

    span {
      font-size: 12rem;
    }
  }

  .icon {
    font-size: 40rem;
  }

  .loved {
    background: red;
  }
}
</style>
