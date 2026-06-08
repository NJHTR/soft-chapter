<template>
  <div class="JoinedGroupChat">
    <BaseHeader backMode="light" backImg="back" style="z-index: 7">
      <template v-slot:center>
        <span class="f16">已加入的群聊</span>
      </template>
    </BaseHeader>
    <div class="content">
      <div class="list">
        <div class="local-row" :key="i" v-for="(item, i) of data.groups" @click="navToGroup(item)">
          <GroupAvatar :avatars="item.member_avatars || []" :size="45" />
          <div class="desc">
            <span class="name">{{
              item.name.length > 20 ? item.name.substr(0, 20) + '...' : item.name
            }}</span>
            <span class="num">({{ item.member_count || 0 }})</span>
          </div>
          <dy-back direction="right" mode="light" scale="0.7" />
        </div>
      </div>
      <NoMore v-if="data.groups.length" />
      <div class="empty" v-else>
        <span>暂无加入的群聊</span>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { getGroupList } from '@/api/message'
import { onMounted, reactive } from 'vue'
import { useNav } from '@/utils/hooks/useNav'
import GroupAvatar from '@/components/GroupAvatar.vue'

defineOptions({
  name: 'JoinedGroupChat'
})

const nav = useNav()
const data = reactive({
  groups: [] as any[]
})

onMounted(() => {
  loadGroups()
})

function navToGroup(group: any) {
  nav('/message/group-chat', {
    group_id: group.id,
    name: group.name,
    avatar: group.avatar || ''
  })
}

async function loadGroups() {
  try {
    let res = await getGroupList()
    if (res.success) {
      data.groups = res.data || []
    }
  } catch {
    /* ignore */
  }
}
</script>

<style scoped lang="less">
@import '@/assets/less/index';

.JoinedGroupChat {
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
  }

  .list {
    .local-row {
      display: flex;
      align-items: center;
      padding: 10rem 20rem;

      &:active {
        background: rgb(35, 41, 58);
      }

      img {
        height: 45rem;
        width: 45rem;
        border-radius: 50%;
        margin-right: 15rem;
        object-fit: cover;
      }

      .desc {
        flex: 1;
        .num {
          margin-left: 5rem;
          color: var(--second-text-color);
        }
      }
    }
  }

  .empty {
    text-align: center;
    padding: 50rem;
    color: var(--second-text-color);
  }
}
</style>
