<template>
  <div
    class="ChatMessage"
    :class="!isMe ? 'left' : 'right'"
    :style="message.type === MESSAGE_TYPE.TIME && 'margin-bottom: 0;'"
  >
    <div class="time" v-if="message.type === MESSAGE_TYPE.TIME">
      {{ message.time }}
    </div>
    <template v-else>
      <img
        v-if="!isMe"
        :src="_checkImgUrl(message.user.avatar)"
        alt=""
        class="avatar"
        @click.stop="goUserHome"
      />
      <div class="chat-wrapper" @click="$emit('itemClick', message)">
        <div class="chat-text" v-if="message.type === MESSAGE_TYPE.TEXT">
          {{ message.data }}
        </div>

        <div class="douyin_video" v-if="message.type === MESSAGE_TYPE.DOUYIN_VIDEO">
          <img
            v-if="message.data.poster"
            class="poster"
            :src="_checkImgUrl(message.data.poster)"
            alt=""
            @error="(e) => (e.target.style.display = 'none')"
          />
          <div class="poster-placeholder" v-else></div>
          <div class="mask"></div>
          <div class="title">{{ message.data.title }}</div>
          <img src="../../../assets/img/icon/play-white.png" class="pause" />
          <div class="author">
            <img class="video-avatar" :src="_checkImgUrl(message.data.author?.avatar)" alt="" />
            <span class="name">{{ message.data.author?.name }}</span>
          </div>
        </div>

        <div class="douyin_video" v-if="message.type === MESSAGE_TYPE.VIDEO">
          <img class="poster" :src="_checkImgUrl(message.data.poster)" alt="" />
          <img src="../../../assets/img/icon/play-white.png" class="pause" />
        </div>

        <div class="audio" v-if="message.type === MESSAGE_TYPE.AUDIO" @click="playAudio">
          <template v-if="isMe">
            <div class="duration">{{ message.data.duration }}″</div>
            <div class="wave-bars" :class="{ playing: audioPlaying }">
              <span
                class="bar"
                v-for="i in 4"
                :key="i"
                :style="{ animationDelay: i * 0.15 + 's' }"
              ></span>
            </div>
            <img src="../../../assets/img/icon/message/chat/rss2.png" alt="" class="audio-icon" />
          </template>
          <template v-else>
            <img src="../../../assets/img/icon/message/chat/rss.png" alt="" class="audio-icon" />
            <div class="wave-bars" :class="{ playing: audioPlaying }">
              <span
                class="bar"
                v-for="i in 4"
                :key="i"
                :style="{ animationDelay: i * 0.15 + 's' }"
              ></span>
            </div>
            <div class="duration">{{ message.data.duration }}″</div>
          </template>
        </div>

        <div
          class="call"
          v-if="
            message.type === MESSAGE_TYPE.VIDEO_CALL || message.type === MESSAGE_TYPE.AUDIO_CALL
          "
        >
          <div class="finish" v-if="message.state === CALL_STATE.RESOLVE">
            <img
              class="icon"
              :src="message.type === MESSAGE_TYPE.AUDIO_CALL ? callIcon : videoIcon"
              alt=""
            />
            <div class="notice">
              <span class="state">通话时长 {{ message.data }}</span>
              <span>点击回拨</span>
            </div>
          </div>
          <div
            class="reject"
            v-if="message.state === CALL_STATE.REJECT || message.state === CALL_STATE.NONE"
          >
            <img
              class="icon"
              :src="message.type === MESSAGE_TYPE.AUDIO_CALL ? callIcon : videoIcon"
              alt=""
            />
            <div class="notice">
              <span class="state" v-if="message.state === CALL_STATE.REJECT">对方已拒绝</span>
              <span class="state" v-if="message.state === CALL_STATE.NONE">对方未接通</span>
              <span>点击呼叫</span>
            </div>
          </div>
        </div>

        <div class="image" v-if="message.type === MESSAGE_TYPE.IMAGE" @click="previewImage">
          <img :src="message.data" alt="" />
        </div>
        <!-- 图片全屏预览 -->
        <div class="image-preview" v-if="showPreview" @click="showPreview = false">
          <img :src="message.data" alt="" />
        </div>

        <div class="meme" v-if="message.type === MESSAGE_TYPE.MEME">
          <img :src="message.data" alt="" />
        </div>

        <div
          class="red_packet"
          :class="message.data.state !== '未领取' ? 'invalid' : ''"
          v-if="message.type === MESSAGE_TYPE.RED_PACKET"
        >
          <div class="top">
            <img src="../../../assets/img/icon/message/chat/redpack-logo.webp" alt="" />
            <div class="right">
              <div class="title">{{ message.data.title }}</div>
              <div v-if="message.data.state !== '未领取'" class="state">
                {{ message.data.state }}
              </div>
            </div>
          </div>
          <span class="bottom">SeekFlow红包</span>
        </div>

        <div class="loves" v-if="message.loved?.length">
          <img src="../../../assets/img/icon/loved.svg" alt="" />
          <img
            :key="user"
            v-for="user in message.loved"
            src="../../../assets/img/icon/head-image.jpeg"
            alt=""
            class="love-avatar"
          />
        </div>
        <!-- 已读回执 -->
        <div class="read-receipt" v-if="message.readByAvatar">
          <img :src="_checkImgUrl(message.readByAvatar)" alt="" class="read-avatar" />
          <span>已读</span>
        </div>
      </div>
      <img
        v-if="isMe"
        :src="_checkImgUrl(message.user.avatar)"
        alt=""
        class="avatar"
        @click.stop="goUserHome"
      />
    </template>
  </div>
</template>

<script>
import { mapState } from 'pinia'
import { useBaseStore } from '@/store/pinia'
import { _checkImgUrl } from '@/utils'
import callIcon from '@/assets/img/icon/message/chat/call.png'
import videoIcon from '@/assets/img/icon/message/chat/video-white.png'

let CALL_STATE = {
  REJECT: 0,
  RESOLVE: 1,
  NONE: 2
}
// eslint-disable-next-line
let VIDEO_STATE = {
  VALID: 0,
  INVALID: 1
}
// eslint-disable-next-line
let AUDIO_STATE = {
  NORMAL: 0,
  SENDING: 1
}
// eslint-disable-next-line
let READ_STATE = {
  SENDING: 0,
  ARRIVED: 1,
  READ: 1
}
let RED_PACKET_MODE = {
  SINGLE: 1,
  MULTIPLE: 2
}
let MESSAGE_TYPE = {
  TEXT: 0,
  TIME: 1,
  VIDEO: 2,
  DOUYIN_VIDEO: 9,
  AUDIO: 3,
  IMAGE: 6,
  VIDEO_CALL: 4,
  AUDIO_CALL: 5,
  MEME: 7, //表情包
  RED_PACKET: 8 //红包
}
export default {
  name: 'ChatMessage',
  props: {
    message: {
      type: Object,
      default() {
        return {}
      }
    }
  },
  data() {
    return {
      MESSAGE_TYPE,
      CALL_STATE,
      RED_PACKET_MODE,
      callIcon,
      videoIcon,
      audioPlaying: false,
      audioEl: null,
      showPreview: false
    }
  },
  computed: {
    ...mapState(useBaseStore, ['userinfo']),
    isMe() {
      return this.userinfo.uid === this.message.user.id
    }
  },
  created() {},
  methods: {
    _checkImgUrl,
    goUserHome() {
      const uid = this.message.user?.id
      if (uid && !isNaN(Number(uid))) {
        this.$router.push('/people/user-home/' + uid)
      }
    },
    playAudio() {
      const url = this.message.data?.url || this.message.data
      if (!url) return
      if (this.audioEl) {
        this.audioEl.pause()
        this.audioEl = null
        this.audioPlaying = false
        return
      }
      this.audioEl = new Audio(url)
      this.audioEl.play()
      this.audioPlaying = true
      this.audioEl.onended = () => {
        this.audioPlaying = false
        this.audioEl = null
      }
      this.audioEl.onerror = () => {
        this.audioPlaying = false
        this.audioEl = null
      }
    },
    previewImage() {
      this.showPreview = true
    }
  }
}
</script>

<style scoped lang="less">
@import '../../../assets/less/index';

.ChatMessage {
  padding: 0 10rem;
  margin-bottom: 20rem;
  display: flex;
  //@chat-bg-color: dodgerblue;
  @chat-bg-right-color: rgb(72, 116, 230);
  @chat-bg-left-color: rgb(59, 59, 67);

  &.right {
    justify-content: flex-end;

    .avatar {
      margin-left: 10rem;
      height: 36rem;
      border-radius: 50%;
      cursor: pointer;
    }

    .audio-icon {
      margin-left: 50rem;
    }

    .chat-text,
    .call,
    .audio {
      background: @chat-bg-right-color;
    }
  }

  &.left {
    justify-content: flex-start;

    .avatar {
      margin-right: 10rem;
      height: 36rem;
      border-radius: 50%;
      cursor: pointer;
    }

    .audio-icon {
      margin-right: 50rem;
    }

    .chat-text,
    .call,
    .audio {
      background: @chat-bg-left-color;
    }
  }

  @border-radius: 10rem;

  .chat-wrapper {
  }

  .time {
    width: 100%;
    color: var(--second-text-color);
    text-align: center;
    height: 40rem;
    line-height: 40rem;
    font-size: 12rem;
  }

  .red_packet {
    border-radius: @border-radius;
    @not-received: rgb(253, 92, 72);
    @received: rgba(253, 92, 72, 0.8);
    width: 60vw;
    background: @not-received;
    display: flex;
    flex-direction: column;
    color: rgb(255, 231, 206);

    &.invalid {
      background: @received;
    }

    .top {
      padding: 10rem;
      display: flex;
      align-items: center;
      border-bottom: 1px solid rgb(253, 124, 81);

      img {
        border-radius: 3rem;
        height: 38rem;
        margin-right: 10rem;
      }

      .title {
        font-size: 14rem;
      }

      .state {
        font-size: 12rem;
        color: rgba(255, 231, 206, 0.8);
      }
    }

    .bottom {
      font-size: 12rem;
      padding: 5rem 10rem 10rem 10rem;
    }
  }

  .meme {
    img {
      border-radius: @border-radius;
      //height: 30vh;
      max-width: 40vw;
    }
  }

  .image {
    img {
      border-radius: @border-radius;
      max-width: 40vw;
      cursor: pointer;
    }
  }

  .image-preview {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: calc(var(--vh, 1vh) * 100);
    background: rgba(0, 0, 0, 0.95);
    z-index: 999;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
      max-width: 100vw;
      max-height: 100vh;
      object-fit: contain;
    }
  }

  .call {
    padding: 10rem;
    border-radius: @border-radius;
    display: flex;
    align-items: center;
    font-size: 14rem;
    cursor: pointer;

    .finish,
    .reject {
      display: flex;
      align-items: center;

      .icon {
        padding: 6rem;
        border-radius: 50%;
        background: rgba(27, 100, 172, 0.8);
        margin-right: 10rem;
        width: 18rem;
      }

      .notice {
        font-size: 13rem;
        display: flex;
        flex-direction: column;
        color: #dedede;

        .state {
          margin-bottom: 2rem;
          font-size: 15rem;
          color: white;
        }
      }
    }
  }

  .audio {
    max-width: 60vw;
    min-width: 100rem;
    padding: 10rem 12rem;
    border-radius: @border-radius;
    display: flex;
    align-items: center;
    font-size: 14rem;
    cursor: pointer;
    gap: 6rem;

    .audio-icon {
      width: 18rem;
      height: 18rem;
      flex-shrink: 0;
    }

    .duration {
      font-size: 13rem;
      white-space: nowrap;
      min-width: 24rem;
    }

    .wave-bars {
      display: flex;
      align-items: flex-end;
      gap: 2rem;
      height: 20rem;
      flex: 1;
      justify-content: center;

      .bar {
        width: 2.5rem;
        border-radius: 2rem;
        background: rgba(255, 255, 255, 0.4);
        height: 6rem;
        transition: height 0.2s;
      }

      &.playing .bar {
        animation: wave 0.8s ease-in-out infinite alternate;

        &:nth-child(1) {
          height: 8rem;
        }
        &:nth-child(2) {
          height: 14rem;
        }
        &:nth-child(3) {
          height: 18rem;
        }
        &:nth-child(4) {
          height: 10rem;
        }
      }
    }

    @keyframes wave {
      0% {
        transform: scaleY(0.4);
      }
      100% {
        transform: scaleY(1);
      }
    }
  }

  .douyin_video {
    position: relative;
    width: 45vw;
    border-radius: @border-radius;
    overflow: hidden;

    .poster {
      width: 100%;
      height: 60vw;
      object-fit: cover;
      display: block;
      border-radius: @border-radius;
    }

    .poster-placeholder {
      width: 100%;
      height: 60vw;
      background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
      border-radius: @border-radius;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .mask {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 60rem;
      background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
      border-radius: 0 0 @border-radius @border-radius;
      pointer-events: none;
    }

    .pause {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translateY(-50%) translateX(-50%);
      width: 28rem;
      pointer-events: none;
    }

    .title {
      position: absolute;
      font-size: 12rem;
      bottom: 28rem;
      width: calc(100% - 16rem);
      left: 8rem;
      color: #fff;
      text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
      word-break: break-word;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-overflow: ellipsis;
      line-height: 1.3;
    }

    .author {
      position: absolute;
      bottom: 8rem;
      left: 8rem;
      right: 8rem;
      display: flex;
      align-items: center;
      z-index: 1;

      .video-avatar {
        margin-right: 6rem;
        width: 18rem;
        height: 18rem;
        border-radius: 50%;
        object-fit: cover;
        border: 1px solid rgba(255, 255, 255, 0.3);
        flex-shrink: 0;
      }

      .name {
        color: #fff;
        font-size: 11rem;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
      }
    }
  }

  .chat-text {
    border-radius: @border-radius;
    max-width: 60vw;
    padding: 10rem;
    display: flex;
    align-items: center;
    font-size: 14rem;
  }

  .loves {
    margin-top: 10rem;

    img {
      width: 16rem;
      height: 16rem;
      border-radius: 50%;
      margin-right: 5rem;
    }
  }

  .read-receipt {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    margin-top: 4rem;
    gap: 4rem;

    .read-avatar {
      width: 14rem;
      height: 14rem;
      border-radius: 50%;
      object-fit: cover;
    }

    span {
      font-size: 11rem;
      color: var(--second-text-color);
    }
  }

  &.left .read-receipt {
    justify-content: flex-start;
  }
}
</style>
