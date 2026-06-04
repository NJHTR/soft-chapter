<template>
  <div id="ChatDetail">
    <BaseHeader>
      <template v-slot:center>
        <span class="f16">聊天详情</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="peoples">
        <People
          @follow="follow(index)"
          @unfollow="unfollow(index)"
          mode="normal-add-button"
          :key="index"
          v-for="(item, index) in data.list"
          :people="item"
        />
        <div class="add-people" @click="nav('/message/share-to-friend')">
          <img src="../../../assets/img/icon/message/chat/add.png" alt="" class="head-image" />
          <div class="name">多人聊天</div>
        </div>
      </div>
      <div class="setting">
        <div class="row">
          <div class="left">消息免打扰</div>
          <div class="right">
            <switches v-model="data.noMessage" theme="bootstrap" color="success"></switches>
          </div>
        </div>
        <div class="row">
          <div class="left">置顶聊天</div>
          <div class="right">
            <switches v-model="data.top" theme="bootstrap" color="success"></switches>
          </div>
        </div>
        <div class="row" @click="nav('/set-remark')">
          <div class="left">设备备注</div>
          <div class="right">
            <dy-back direction="right" scale=".7"></dy-back>
          </div>
        </div>
        <div class="row" @click="nav('/home/report', { mode: 'chat' })">
          <div class="left">举报</div>
          <div class="right">
            <dy-back direction="right" scale=".7"></dy-back>
          </div>
        </div>
        <div class="row" @click="data.blockDialog = true">
          <div class="left">拉黑</div>
          <div class="right">
            <dy-back direction="right" scale=".7"></dy-back>
          </div>
        </div>
      </div>
    </div>
    <BlockDialog v-model="data.blockDialog" />
  </div>
</template>
<script setup lang="ts">
import Switches from '../components/swtich/switches.vue'
import People from '../../people/components/People.vue'
import BlockDialog from '../components/BlockDialog.vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useNav } from '@/utils/hooks/useNav'
import { _showConfirmDialog } from '@/utils'
import { toggleFollowUser, panel } from '@/api/user'

defineOptions({
  name: 'ChatDetail'
})

const nav = useNav()
const route = useRoute()
const targetUserId = ref((route.query.user_id as string) || '')
const targetUserName = ref((route.query.name as string) || '对方')
const targetUserAvatar = ref((route.query.avatar as string) || '')

const data = reactive({
  noMessage: false,
  top: false,
  blockDialog: false,
  list: [] as any[]
})

onMounted(async () => {
  let uid = targetUserId.value
  if (uid) {
    // 加载用户详细信息
    try {
      const res = await panel({ uid })
      if (res.success && res.data) {
        const u = res.data
        data.list = [{
          id: u.uid || uid,
          uid: u.uid || uid,
          avatar: targetUserAvatar.value || u.avatar_168x168?.url_list?.[0] || '',
          name: targetUserName.value || u.nickname || '用户',
          account: u.unique_id || '',
          sex: u.gender === 1 ? '男' : u.gender === 2 ? '女' : '',
          type: u.is_followed ? 2 : 0,
          select: false
        }]
      }
    } catch { /* ignore */ }
  } else {
    // 没有用户ID，显示占位
    data.list = [{
      uid: 0, name: '未知用户', avatar: '', sex: '', type: 0, select: false
    }]
  }
})

async function follow(index: number) {
  const uid = data.list[index]?.uid
  if (uid && !isNaN(Number(uid))) {
    const res = await toggleFollowUser(Number(uid))
    if (res.success && res.data.isAttention) {
      data.list[index].type = 2 // follow each other
    }
  }
}

function unfollow(index: number) {
  const uid = data.list[index]?.uid
  if (!uid || isNaN(Number(uid))) return
  _showConfirmDialog(
    '是否不再关注该用户',
    null,
    'gray',
    async () => {
      const res = await toggleFollowUser(Number(uid))
      if (res.success && !res.data.isAttention) {
        data.list[index].type = 0
      }
    },
    () => {},
    '取消关注',
    '返回'
  )
}
</script>

<style scoped lang="less">
@import '../../../assets/less/index';

#ChatDetail {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  overflow: auto;
  color: white;
  font-size: 14rem;

  .content {
    padding-top: 60rem;

    .peoples {
      padding: 0 20rem;

      .People {
        border-bottom: 1px solid var(--second-btn-color-tran);
      }

      .add-people {
        transition: all 0.3s ease;
        width: 100%;
        height: 70rem;
        display: flex;
        align-items: center;
        position: relative;
        border-bottom: 1px solid var(--second-btn-color-tran);

        .head-image {
          margin-right: 15rem;
          width: 45rem;
          height: 45rem;
          border-radius: 50%;
        }
      }
    }

    .setting {
      .row {
        padding-left: 20rem;
        padding-right: 20rem;
        display: flex;
        align-items: center;
        justify-content: space-between;
        height: 40rem;

        .right {
          img {
            height: 20rem;
          }
        }
      }
    }
  }
}
</style>
