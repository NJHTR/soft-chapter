<template>
  <div class="message-slide-wrapper" id="Message">
    <SlideHorizontal name="message" v-model:index="data.baseIndex">
      <SlideItem class="sidebar">
        <div class="sidebar-header">
          <div class="left">下午好</div>
          <div class="right" @click="nav('/home/live')">
            <Icon icon="iconamoon:scanner" />
            <span>扫一扫</span>
          </div>
        </div>
        <div class="card">
          <div class="card-header">
            <div class="left">常用小程序</div>
            <div class="right">
              <span>全部</span>
              <Icon icon="icon-park-outline:right" />
            </div>
          </div>
          <div class="card-content">
            <div class="s-item" @click="_no">
              <img
                class="xcx"
                src="https://lf3-static.bytednsdoc.com/obj/eden-cn/pipieh7nupabozups/toutiao_web_pc/tt-icon.png"
                alt=""
              />
              <span>今日头条</span>
            </div>
            <div class="s-item" @click="_no">
              <img
                class="xcx"
                src="https://gd-hbimg.huaban.com/65130a3e6a139530bb03bd118e21a2603af7df4e1303b-OOzcBu_fw658webp"
                alt=""
              />
              <span>西瓜视频</span>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-header">
            <div class="left">最近常看</div>
            <div class="right">
              <span>全部</span>
              <Icon icon="icon-park-outline:right" />
            </div>
          </div>
          <div class="card-content">
            <div class="s-item avatar" @click="_no" :key="a.uid" v-for="a in data.recentAuthors">
              <img :src="_checkImgUrl(a.avatar_168x168?.url_list?.[0])" />
              <span>{{ a.nickname }}</span>
            </div>
            <div
              class="no-data"
              v-if="!data.recentAuthors.length"
              style="grid-column: 1/-1; text-align: center; color: gray; font-size: 12rem"
            >
              暂无常看作者
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-header">
            <div class="left">常用功能</div>
            <div class="right"></div>
          </div>
          <div class="card-content">
            <div class="s-item" @click="_no">
              <Icon icon="ion:wallet-outline" />
              <span>我的钱包</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="mingcute:coupon-line" />
              <span>券包</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="icon-park-outline:bytedance-applets" />
              <span>小程序</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="solar:history-linear" />
              <span>观看历史</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="fluent:content-settings-24-regular" />
              <span>内容偏好</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="iconoir:cloud-download" />
              <span>离线模式</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="ep:setting" />
              <span>设置</span>
            </div>
            <div class="s-item" @click="_no">
              <Icon icon="icon-park-outline:baggage-delay" />
              <span>稍后再看</span>
            </div>
          </div>
        </div>
      </SlideItem>
      <SlideItem>
        <div :class="data.createChatDialog ? 'disable-scroll' : ''" class="msg-content">
          <div class="no-search" v-show="!data.searching">
            <header>
              <Icon icon="tabler:menu-deep" class="menu-back-icon" @click="goHome" />
              <span class="header-title">消息</span>
              <div class="header-right">
                <Icon @click="data.searching = true" icon="ion:search" />
                <Icon @click="data.showPlusMenu = true" icon="formkit:add" />
              </div>
            </header>

            <Scroll ref="mainScroll">
              <div class="friends">
                <div
                  class="friend"
                  @click="navToChat({ target_user: item })"
                  :key="index"
                  v-for="(item, index) in store.friends.all"
                >
                  <div class="avatar" :class="index % 2 === 0 ? 'on-line' : ''">
                    <img :src="_checkImgUrl(item.avatar)" alt="" />
                  </div>
                  <span>{{ item.name }}</span>
                </div>
                <div class="friend">
                  <div class="avatar">
                    <img src="../../assets/img/icon/message/setting.png" alt="" />
                  </div>
                  <span>状态设置</span>
                </div>
              </div>
              <div class="messages">
                <!--      粉丝-->
                <div class="message" @click="nav('/message/fans')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon1.png" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>新朋友</span>
                      </div>
                      <div class="detail">新的关注消息</div>
                    </div>
                    <div class="right">
                      <div class="badge" v-if="data.unreadCounts.follow">
                        {{ data.unreadCounts.follow }}
                      </div>
                      <dy-back class="arrow" mode="gray" img="back" direction="right" />
                    </div>
                  </div>
                </div>
                <!--      朋友申请-->
                <div class="message" @click="nav('/message/friend-requests')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon1.png" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>朋友申请</span>
                      </div>
                      <div class="detail">查看好友申请</div>
                    </div>
                    <div class="right">
                      <dy-back class="arrow" mode="gray" img="back" direction="right" />
                    </div>
                  </div>
                </div>
                <!--      互动消息-->
                <div class="message" @click="nav('/message/all')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon2.png" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>互动消息</span>
                      </div>
                      <div class="detail">赞、评论、@等互动消息</div>
                    </div>
                    <div class="right">
                      <div class="badge" v-if="data.unreadCounts.all">
                        {{ data.unreadCounts.all }}
                      </div>
                      <dy-back class="arrow" mode="gray" img="back" direction="right" />
                    </div>
                  </div>
                </div>
                <!--      最近的私聊会话-->
                <div
                  class="message"
                  @click="navToChat(item)"
                  :key="'conv_' + i"
                  v-for="(item, i) in data.conversations.slice(0, 5)"
                >
                  <div class="avatar on-line">
                    <img
                      :src="_checkImgUrl(item.target_user?.avatar_168x168?.url_list?.[0])"
                      alt=""
                      class="head-image"
                    />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>{{ item.target_user?.nickname || '用户' }}</span>
                      </div>
                      <div class="detail">
                        {{ item.last_message }}
                      </div>
                    </div>
                    <div class="right">
                      <div class="badge" v-if="item.unread_count">{{ item.unread_count }}</div>
                    </div>
                  </div>
                </div>
                <!--      群聊会话-->
                <div
                  class="message"
                  @click="navToGroupChat(item)"
                  :key="'gconv_' + i"
                  v-for="(item, i) in data.groupConversations"
                >
                  <div class="avatar">
                    <GroupAvatar
                      :avatars="item.member_avatars || []"
                      :size="55"
                      class="head-image"
                    />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>{{ item.group_name }}</span>
                        <span class="tag">群聊</span>
                      </div>
                      <div class="detail">
                        {{ item.last_message }}
                      </div>
                    </div>
                    <div class="right">
                      <div class="badge" v-if="item.unread_count">{{ item.unread_count }}</div>
                    </div>
                  </div>
                </div>
                <!--      SeekFlow小助手-->
                <div class="message" @click="nav('/message/douyin-helper')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon5.webp" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>SeekFlow小助手</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        #今天谁请客呢
                        <div class="point"></div>
                        星期四
                      </div>
                    </div>
                    <div class="right">
                      <div class="not-read"></div>
                    </div>
                  </div>
                </div>
                <!--      系统通知-->
                <div class="message" @click="nav('/message/system-notice')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon4.png" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>系统通知</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        <template v-if="data.latestSystemNotice">
                          {{ data.latestSystemNotice.title }}
                          <div class="point"></div>
                          {{ formatSysTime(data.latestSystemNotice.create_time) }}
                        </template>
                        <template v-else>暂无通知</template>
                      </div>
                    </div>
                    <div class="right">
                      <div class="badge" v-if="data.sysNoticeUnread">
                        {{ data.sysNoticeUnread }}
                      </div>
                      <div class="not-read" v-else-if="!data.latestSystemNotice"></div>
                    </div>
                  </div>
                </div>
                <!--      求更新-->
                <div class="message" @click="nav('/me/request-update')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon7.webp" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>求更新</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        你收到过1次求更新
                        <div class="point"></div>
                        10-09
                      </div>
                    </div>
                    <div class="right">
                      <div class="not-read"></div>
                    </div>
                  </div>
                </div>
                <!--      任务通知-->
                <div class="message" @click="nav('/message/task-notice')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon6.webp" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>任务通知</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        发作品得流量
                        <div class="point"></div>
                        05-26
                      </div>
                    </div>
                    <div class="right">
                      <div class="not-read"></div>
                    </div>
                  </div>
                </div>
                <!--      直播通知-->
                <div class="message" @click="nav('/message/live-notice')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon8.webp" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>直播通知</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        举报结果通知
                        <div class="point"></div>
                        05-26
                      </div>
                    </div>
                    <div class="right">
                      <div class="not-read"></div>
                    </div>
                  </div>
                </div>
                <!--      钱包通知-->
                <div class="message" @click="nav('/message/money-notice')">
                  <div class="avatar">
                    <img src="../../assets/img/icon/msg-icon9.webp" alt="" class="head-image" />
                  </div>
                  <div class="content">
                    <div class="left">
                      <div class="name">
                        <span>钱包通知</span>
                        <span class="tag">官方</span>
                      </div>
                      <div class="detail">
                        卡券发放提醒
                        <div class="point"></div>
                        05-26
                      </div>
                    </div>
                    <div class="right">
                      <div class="not-read"></div>
                    </div>
                  </div>
                </div>

                <NoMore />

                <!--      模板-->
                <!--      <div class="message">-->
                <!--        <div class="avatar on-line">-->
                <!--          <img src="../../assets/img/icon/avatar/2.png" alt="" class="head-image">-->
                <!--        </div>-->
                <!--        <div class="content">-->
                <!--          <div class="left">-->
                <!--            <div class="name">-->
                <!--              <span>粉丝</span>-->
                <!--              <span class="tag">官方</span>-->
                <!--            </div>-->
                <!--            <div class="detail">-->
                <!--              <img class="sent" src="../../assets/img/icon/close-white.png" alt="">-->
                <!--              已送达 ，sb，凌晨 01:15-->
                <!--            </div>-->
                <!--          </div>-->
                <!--          <div class="right">-->
                <!--            &lt;!&ndash;                          <div class="not-read"></div>&ndash;&gt;-->
                <!--            &lt;!&ndash;                          <img class="camera" src="../../assets/img/icon/close-white.png" alt="">&ndash;&gt;-->
                <!--            &lt;!&ndash;            <img class="arrow" src="../../assets/img/icon/close-white.png" alt="">&ndash;&gt;-->
                <!--            <div class="badge">12</div>-->
                <!--          </div>-->
                <!--        </div>-->
                <!--      </div>-->
              </div>
            </Scroll>
            <BaseFooter v-bind:init-tab="4" />
          </div>

          <div class="searching" v-show="data.searching">
            <Search
              v-model="data.searchKey"
              right-text="取消"
              right-text-color="white"
              @notice="data.searching = false"
              :isShowRightText="true"
            />
            <div class="more-chat">
              <template v-if="data.searchKey">
                <!-- 实时建议列表 -->
                <div class="suggestions" v-if="data.suggestions.length">
                  <div
                    class="suggest-item"
                    v-for="(item, index) in data.suggestions"
                    :key="'s_' + index"
                    @click="((data.searching = false), navToChat({ target_user: item }))"
                  >
                    <img class="avatar" :src="_checkImgUrl(item.avatar)" alt="" />
                    <div class="info">
                      <span class="name">{{ item.name }}</span>
                      <span class="source">{{ item.source }}</span>
                    </div>
                  </div>
                </div>
                <!-- 聊天记录 -->
                <div class="category" v-if="data.chatResults.length">
                  <div class="sub-title">聊天记录</div>
                  <div
                    v-for="item in data.chatResults.slice(0, 5)"
                    :key="'chat_' + item.uid"
                    @click="((data.searching = false), navToChat({ target_user: item }))"
                  >
                    <People mode="search" :searchKey="data.searchKey" :people="item" />
                  </div>
                </div>
                <!-- 联系人 -->
                <div class="category" v-if="data.contactResults.length">
                  <div class="sub-title">联系人</div>
                  <div
                    v-for="item in data.contactResults.slice(0, 5)"
                    :key="'contact_' + item.uid"
                    @click="((data.searching = false), navToChat({ target_user: item }))"
                  >
                    <People mode="search" :searchKey="data.searchKey" :people="item" />
                  </div>
                </div>
                <!-- 群聊 -->
                <div class="category" v-if="data.groupResults.length">
                  <div class="sub-title">群聊</div>
                  <div
                    v-for="item in data.groupResults.slice(0, 5)"
                    :key="'group_' + item.id"
                    class="search-item"
                    @click="navToGroupChat(item)"
                  >
                    <GroupAvatar :avatars="item.member_avatars || []" :size="48" class="avatar" />
                    <span class="name">{{ item.name }}</span>
                  </div>
                </div>
                <!-- 消息 -->
                <div class="category" v-if="data.sysMsgResults.length">
                  <div class="sub-title">消息</div>
                  <div
                    v-for="item in data.sysMsgResults"
                    :key="'sys_' + item.id"
                    class="search-item"
                    @click="((data.searching = false), nav(item.route))"
                  >
                    <img class="avatar" :src="item.avatar" alt="" />
                    <div class="info">
                      <span class="name">{{ item.name }}</span>
                      <span class="detail">{{ item.detail }}</span>
                    </div>
                  </div>
                </div>
                <!-- 空结果 -->
                <div
                  class="no-result"
                  v-if="
                    !data.chatResults.length &&
                    !data.contactResults.length &&
                    !data.groupResults.length &&
                    !data.sysMsgResults.length
                  "
                >
                  <div class="notice-h1">搜索结果为空</div>
                  <div class="notice-h2">没有找到相关的内容</div>
                </div>
                <!-- 直达全局搜索 -->
                <div
                  class="goto-search-page"
                  @click="((data.searching = false), nav('/home/search', { key: data.searchKey }))"
                >
                  <img class="icon" src="../../assets/img/icon/search-light.png" alt="" />
                  <div class="right">
                    <div class="left">
                      <span
                        >搜索 <span style="color: yellow">{{ data.searchKey }}</span></span
                      >
                      <span class="second-text-color f12">视频、用户、音乐、话题、地点等</span>
                    </div>
                    <dy-back mode="gray" img="back" direction="right" scale=".7" />
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="sub-title">更多聊天</div>
                <People v-for="item in data.moreChat" :key="item.id" :people="item" />
              </template>
            </div>
          </div>
        </div>
      </SlideItem>
    </SlideHorizontal>

    <!-- 加号弹出菜单 -->
    <transition name="fade">
      <div class="plus-menu-mask" v-if="data.showPlusMenu" @click="data.showPlusMenu = false">
        <div class="plus-menu" @click.stop>
          <div
            class="menu-item"
            @click="((data.showPlusMenu = false), (data.createChatDialog = true))"
          >
            <Icon icon="fluent:people-team-20-filled" />
            <span>发起群聊</span>
          </div>
          <div class="menu-item" @click="((data.showPlusMenu = false), nav('/message/add-friend'))">
            <Icon icon="fluent:person-add-20-filled" />
            <span>添加朋友</span>
          </div>
        </div>
      </div>
    </transition>

    <!-- 发起群聊底部抽屉 -->
    <from-bottom-dialog page-id="Message" v-model="data.createChatDialog">
      <div class="create-chat-wrapper" v-show="!data.showJoinedChat">
        <div class="create-options">
          <div class="option-row" @click="createPublicGroup">
            <img class="option-icon" src="../../assets/img/icon/people-gray.png" alt="" />
            <div class="option-text">
              <span class="option-title">创建公开群</span>
              <span class="option-sub">支持更多群成员人员，可公开展示</span>
            </div>
            <dy-back direction="right" mode="light" scale="0.7" />
          </div>
          <div class="option-row" @click="createFaceToFaceGroup">
            <img class="option-icon" src="../../assets/img/icon/people-gray.png" alt="" />
            <div class="option-text">
              <span class="option-title">面对面建群</span>
              <span class="option-sub">与朋友面对面输入暗号建群</span>
            </div>
            <dy-back direction="right" mode="light" scale="0.7" />
          </div>
        </div>
        <div class="divider-label">邀请互相关注的人建群</div>
        <div class="invite-section">
          <Search
            :isShowRightText="data.isShowRightText"
            @click="data.isShowRightText = true"
            @notice="data.isShowRightText = false"
            @clear="data.isShowRightText = false"
            class="search-bar"
            placeholder="搜索"
            v-model="data.createChatSearchKey"
          />
          <div class="joined-chat" @click="data.showJoinedChat = true">
            <img class="left" src="../../assets/img/icon/people-gray.png" alt="" />
            <div class="right">
              <span>已加入的群聊</span>
              <dy-back direction="right" mode="light" scale="0.7" />
            </div>
          </div>
          <div class="friend-list">
            <div
              class="friend-item"
              :key="i"
              v-for="(item, i) in data.filteredInviteFriends"
              @click="toggleInviteSelect(item)"
            >
              <img class="left" :src="_checkImgUrl(item.avatar)" alt="" />
              <div class="right">
                <span>{{ item.name }}</span>
                <span class="account-tip" v-if="item.account">SeekFlow号: {{ item.account }}</span>
              </div>
              <Check mode="red" v-model="item._invite_selected" @change="syncSelect(item)" />
            </div>
            <div class="no-result" v-if="!data.filteredInviteFriends.length">
              <span class="notice-h2">暂无互相关注的好友</span>
            </div>
          </div>
          <div class="bottom-btn-bar">
            <div
              class="start-chat-btn"
              :class="{ active: data.createGroupSelected.length >= 2 }"
              @click="createGroupFromSelected"
            >
              发起聊天
            </div>
          </div>
        </div>
      </div>
      <div class="joined-chat-wrapper" v-show="data.showJoinedChat">
        <div class="nav">
          <dy-back @click="data.showJoinedChat = false" mode="light" scale="1.2"></dy-back>
          <span>已加入的群聊</span>
          <span>&nbsp;</span>
        </div>
        <div class="chat-list">
          <div
            class="chat-item"
            :key="'joined_' + i"
            v-for="(item, i) in data.groupConversations"
            @click="
              ((data.createChatDialog = false), (data.showJoinedChat = false), navToGroupChat(item))
            "
          >
            <GroupAvatar :avatars="item.member_avatars || []" :size="48" class="left-avatar" />
            <div class="right">
              <div class="title">
                <div class="name">
                  {{
                    item.group_name && item.group_name.length > 20
                      ? item.group_name.substr(0, 20) + '...'
                      : item.group_name
                  }}
                </div>
                <div class="num">({{ item.member_count || 1 }})</div>
              </div>
              <dy-back direction="right" mode="light"></dy-back>
            </div>
          </div>
          <div
            class="chat-item"
            @click="
              ((data.createChatDialog = false),
              (data.showJoinedChat = false),
              nav('/message/share-to-friend'))
            "
          >
            <img
              class="left"
              src="../../assets/img/icon/people-gray.png"
              alt=""
              style="
                padding: 12rem;
                box-sizing: border-box;
                background: var(--second-btn-color-tran);
              "
            />
            <div class="right">
              <div class="title">
                <div class="name" style="color: var(--primary-btn-color)">创建群聊</div>
              </div>
              <dy-back direction="right" mode="light"></dy-back>
            </div>
          </div>
        </div>
        <NoMore></NoMore>
      </div>
    </from-bottom-dialog>

    <!-- 朋友推荐弹窗 -->
    <transition name="fade">
      <div class="recommend-dialog" v-if="data.isShowRecommend">
        <div class="dialog-content">
          <div class="dialog-header">
            <img
              style="opacity: 0"
              src="../../assets/img/icon/components/gray-close-full2.png"
              alt=""
            />
            <div class="title">
              <span>朋友推荐</span>
              <img src="../../assets/img/icon/about-gray.png" alt="" />
            </div>
            <img
              @click="data.isShowRecommend = false"
              src="../../assets/img/icon/components/gray-close-full2.png"
              alt=""
            />
          </div>
          <div class="dialog-body">
            <Scroll ref="scroll" @pulldown="loadRecommendData">
              <Peoples v-model:list="data.recommend" :loading="data.loading" mode="recommend" />
              <Loading :is-full-screen="false" v-if="data.loading" />
              <NoMore v-else />
            </Scroll>
          </div>
        </div>
        <BaseMask />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import Search from '../../components/Search.vue'
import FromBottomDialog from '../../components/dialog/FromBottomDialog.vue'
import Check from '../../components/Check.vue'
import Peoples from '../people/components/Peoples.vue'
import People from '../people/components/Peoples.vue'
import Scroll from '../../components/Scroll.vue'
import SlideHorizontal from '@/components/slide/SlideHorizontal.vue'
import SlideItem from '@/components/slide/SlideItem.vue'
import { useBaseStore } from '@/store/pinia'

import { computed, onActivated, onMounted, onUnmounted, reactive, watch } from 'vue'
import { useNav } from '@/utils/hooks/useNav.js'
import { _checkImgUrl, _sleep, _no, cloneDeep, _notice } from '@/utils'
import { useScroll } from '@/utils/hooks/useScroll'
import {
  getConversations,
  getNotificationUnread,
  searchChats,
  searchNotifications,
  getGroupConversations,
  searchGroups,
  createGroup
} from '@/api/message'
import { searchUsers, getSystemNotices, getRecentAuthors } from '@/api/user'
import { connectSocket, disconnectSocket, onSocketMsg } from '@/utils/socket'
import GroupAvatar from '@/components/GroupAvatar.vue'
import bus from '@/utils/bus'

defineOptions({
  name: 'Message'
})

const mainScroll = useScroll()
const store = useBaseStore()
const nav = useNav()
const data = reactive({
  baseIndex: 1,
  recentAuthors: [] as any[],
  isShowRecommend: false,
  searching: false,
  searchKey: '',
  createChatSearchKey: '',
  showJoinedChat: false,
  loading: false,
  createChatDialog: false,
  isShowRightText: false,
  text: 'AAAAAAAAA、BBBBBBBBBBBBB、CCCCCCCC',
  searchFriends: [],
  searchResults: [] as any[],
  recommend: [],
  moreChat: [],
  conversations: [] as any[],
  unreadCounts: {} as Record<string, number>,
  // 搜索分类结果
  chatResults: [] as any[],
  contactResults: [] as any[],
  groupResults: [] as any[],
  sysMsgResults: [] as any[],
  suggestions: [] as any[],
  latestSystemNotice: null as any,
  sysNoticeUnread: 0,
  groupConversations: [] as any[],
  createGroupSelected: [] as any[],
  filteredInviteFriends: [] as any[],
  showPlusMenu: false
})

let unsubConv: (() => void) | null = null
let unsubNotify: (() => void) | null = null
let unsubGroupMsg: (() => void) | null = null

onMounted(() => {
  data.recommend = cloneDeep(store.friends.all)
  data.recommend.map((v) => {
    v.type = -2
  })
  data.moreChat = cloneDeep(store.friends.all.slice(0, 3))
  loadConversations()
  loadGroupConversations()
  loadUnreadCounts()
  loadRecentAuthors()
  // 建立 WebSocket，实时更新会话列表和未读数
  connectSocket()
  unsubConv = onSocketMsg('chat', (msg: any) => {
    loadConversations()
    if (msg && msg.from_user_id !== store.userinfo.uid) {
      _notice({
        title: msg.from_user?.nickname || '新消息',
        content: msg.content || '[消息]',
        avatar: msg.from_user?.avatar_168x168?.url_list?.[0] || '',
        msgType: msg.msg_type
      })
    }
  })
  unsubNotify = onSocketMsg('notification', () => loadUnreadCounts())
  unsubGroupMsg = onSocketMsg('group_message', (msg: any) => {
    if (msg && msg.group_id) {
      updateLocalGroupConversation(msg)
      if (msg.from_user_id !== store.userinfo.uid) {
        _notice({
          title: msg.from_user?.nickname || '群消息',
          content: msg.content || '[消息]',
          avatar: msg.from_user?.avatar_168x168?.url_list?.[0] || '',
          msgType: msg.msg_type
        })
      }
    }
  })
  // bus 事件兜底
  bus.on('CHAT_MESSAGE', (msg: any) => {
    loadConversations()
    if (msg && msg.from_user_id !== store.userinfo.uid) {
      _notice({
        title: msg.from_user?.nickname || '新消息',
        content: msg.content || '新消息',
        avatar: msg.from_user?.avatar_168x168?.url_list?.[0] || '',
        msgType: msg.msg_type
      })
    }
  })
  bus.on('NEW_NOTIFICATION', loadUnreadCounts)
  bus.on('GROUP_MESSAGE', (msg: any) => {
    if (msg && msg.group_id) {
      updateLocalGroupConversation(msg)
    }
  })
  bus.on('REFRESH_UNREAD', refreshAll)
  bus.on('READ_RECEIPT', loadConversations)
  loadSystemNoticePreview()
})

// keep-alive 激活时刷新
onActivated(() => {
  refreshAll()
})

onUnmounted(() => {
  if (unsubConv) {
    unsubConv()
    unsubConv = null
  }
  if (unsubNotify) {
    unsubNotify()
    unsubNotify = null
  }
  if (unsubGroupMsg) {
    unsubGroupMsg()
    unsubGroupMsg = null
  }
  bus.off('CHAT_MESSAGE', loadConversations)
  bus.off('NEW_NOTIFICATION', loadUnreadCounts)
  bus.off('GROUP_MESSAGE', loadGroupConversations)
  bus.off('REFRESH_UNREAD', refreshAll)
  bus.off('READ_RECEIPT', loadConversations)
})

function refreshAll() {
  loadConversations()
  loadGroupConversations()
  loadUnreadCounts()
  loadSystemNoticePreview()
}

async function loadConversations() {
  try {
    const res = await getConversations()
    if (res.success && res.data) {
      const myUid = store.userinfo.uid
      data.conversations = (res.data as any[]).map((item) => ({
        ...item,
        last_message: formatLastMsg(
          item.last_message,
          item.last_msg_type,
          item.last_msg_from_user_id === myUid && item.last_msg_is_read === 1
        )
      }))
    }
  } catch {
    /* ignore */
  }
}

async function loadGroupConversations() {
  try {
    const res = await getGroupConversations()
    if (res.success && res.data) {
      data.groupConversations = (res.data as any[]).map((item) => ({
        ...item,
        last_message: formatLastMsg(item.last_message, item.last_msg_type)
      }))
    }
  } catch {
    /* keep existing data on error */
  }
}

function updateLocalGroupConversation(msg: any) {
  const gid = msg.group_id
  const idx = data.groupConversations.findIndex((c: any) => c.group_id === gid)
  const formatted = formatLastMsg(msg.content || '', msg.msg_type)
  if (idx >= 0) {
    const conv = { ...data.groupConversations[idx] }
    conv.last_message = formatted
    conv.last_msg_type = msg.msg_type
    conv.last_time = msg.create_time
    if (msg.from_user_id !== store.userinfo.uid) {
      conv.unread_count = (conv.unread_count || 0) + 1
    }
    data.groupConversations[idx] = conv
  } else {
    // 新群，重新加载
    loadGroupConversations()
  }
}

function formatSysTime(timeStr: string): string {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr)
    return `${d.getMonth() + 1}`.padStart(2, '0') + '-' + `${d.getDate()}`.padStart(2, '0')
  } catch {
    return ''
  }
}

function formatLastMsg(content: string, msgType: number, readByThem?: boolean): string {
  const prefix = readByThem ? '已读·' : ''
  if (!content) return prefix
  let body: string
  switch (msgType) {
    case 2:
      body = '[图片]'
      break
    case 3:
      body = '[语音]'
      break
    case 4:
      body = '[视频]'
      break
    case 5:
      body = '[红包]'
      break
    case 9:
      body = '[分享视频]'
      break
    default:
      body = content.length > 20 ? content.slice(0, 20) + '...' : content
  }
  return prefix + body
}

async function loadSystemNoticePreview() {
  try {
    const res = await getSystemNotices({ pageNo: 1, pageSize: 1 })
    if (res.success && res.data) {
      data.latestSystemNotice = res.data.list?.[0] || null
      data.sysNoticeUnread = res.data.unread || 0
    }
  } catch {
    /* ignore */
  }
}

async function loadUnreadCounts() {
  try {
    const res = await getNotificationUnread()
    if (res.success && res.data) {
      data.unreadCounts = res.data
    }
  } catch {
    /* ignore */
  }
}

function navToChat(item: any) {
  const u = item.target_user || item
  const uid = u?.uid || u?.id
  console.log(
    '[Message] navToChat item:',
    JSON.stringify({
      hasTargetUser: !!item.target_user,
      u_uid: u?.uid,
      u_id: u?.id,
      u_nickname: u?.nickname
    })
  )
  if (u && uid && !isNaN(Number(uid))) {
    console.log('[Message] navToChat navigating with uid:', uid)
    nav('/message/chat', {
      user_id: uid,
      name: u.nickname || u.name || '',
      avatar: u.avatar_168x168?.url_list?.[0] || u.avatar || ''
    })
  } else {
    console.warn('[Message] navToChat: invalid uid', uid, 'u:', JSON.stringify(u))
  }
}

function navToGroupChat(group: any) {
  const gid = group.group_id || group.id
  if (gid) {
    data.searching = false
    nav('/message/group-chat', {
      group_id: gid,
      name: group.group_name || group.name || '',
      avatar: group.group_avatar || group.avatar || ''
    })
  }
}

const selectFriends = computed(() => {
  return store.friends.all.filter((v) => v.select).length
})

const searchFriendsAll = computed(() => {
  return data.searchResults
})

function localFilter(keyword: string): any[] {
  const kw = keyword.toLowerCase()
  const myUid = store.userinfo.uid
  return store.friends.all.filter((v: any) => {
    if ((v.uid || v.id) === myUid) return false
    return v.name.toLowerCase().includes(kw) || v.account.toLowerCase().includes(kw)
  })
}

// 系统消息列表（用于搜索）
const systemMessages = [
  {
    id: 'fans',
    name: '新朋友',
    detail: '新的关注消息',
    avatar: new URL('../../assets/img/icon/msg-icon1.png', import.meta.url).href,
    route: '/message/fans'
  },
  {
    id: 'all',
    name: '互动消息',
    detail: '赞、评论、@等互动消息',
    avatar: new URL('../../assets/img/icon/msg-icon2.png', import.meta.url).href,
    route: '/message/all'
  },
  {
    id: 'helper',
    name: 'SeekFlow小助手',
    detail: '#今天谁请客呢',
    avatar: new URL('../../assets/img/icon/msg-icon5.webp', import.meta.url).href,
    route: '/message/douyin-helper'
  },
  {
    id: 'system',
    name: '系统通知',
    detail: '协议修订通知',
    avatar: new URL('../../assets/img/icon/msg-icon4.png', import.meta.url).href,
    route: '/message/system-notice'
  },
  {
    id: 'update',
    name: '求更新',
    detail: '你收到过1次求更新',
    avatar: new URL('../../assets/img/icon/msg-icon7.webp', import.meta.url).href,
    route: '/me/request-update'
  },
  {
    id: 'task',
    name: '任务通知',
    detail: '发作品得流量',
    avatar: new URL('../../assets/img/icon/msg-icon6.webp', import.meta.url).href,
    route: '/message/task-notice'
  },
  {
    id: 'live',
    name: '直播通知',
    detail: '举报结果通知',
    avatar: new URL('../../assets/img/icon/msg-icon8.webp', import.meta.url).href,
    route: '/message/live-notice'
  },
  {
    id: 'money',
    name: '钱包通知',
    detail: '卡券发放提醒',
    avatar: new URL('../../assets/img/icon/msg-icon9.webp', import.meta.url).href,
    route: '/message/money-notice'
  }
]

// 群聊列表（从API加载）
const groupChats = [] as any[]

watch(
  () => data.searchKey,
  async (newVal) => {
    if (!newVal) {
      data.suggestions = []
      data.chatResults = []
      data.contactResults = []
      data.groupResults = []
      data.sysMsgResults = []
      return
    }
    const kw = newVal.toLowerCase()

    // ====== 本地即时搜索（先渲染，后端结果再增量补充） ======

    // 聊天记录：从已加载的会话中匹配
    const localChat: any[] = []
    data.conversations.forEach((c) => {
      const name = (c.target_user?.nickname || '').toLowerCase()
      const lastMsg = (c.last_message || '').toLowerCase()
      if (name.includes(kw) || lastMsg.includes(kw)) {
        localChat.push({
          uid: c.target_user?.uid,
          name: c.target_user?.nickname || '',
          avatar: c.target_user?.avatar_168x168?.url_list?.[0] || '',
          account: c.target_user?.unique_id || '',
          avatar_168x168: c.target_user?.avatar_168x168 || {}
        })
      }
    })
    data.chatResults = localChat

    // 联系人：从好友列表匹配
    data.contactResults = localFilter(newVal)

    // 群聊匹配：从已加载的群会话中本地匹配
    data.groupResults = data.groupConversations
      .filter((g: any) => g.group_name.toLowerCase().includes(kw))
      .map((g: any) => ({
        id: g.group_id,
        name: g.group_name,
        avatar: g.group_avatar || ''
      }))

    // 系统消息匹配
    data.sysMsgResults = systemMessages.filter(
      (m) => m.name.toLowerCase().includes(kw) || m.detail.toLowerCase().includes(kw)
    )

    // 建议列表：从上述本地结果聚合
    const allSuggestions: any[] = []
    localChat.forEach((c) => allSuggestions.push({ ...c, source: '聊天' }))
    data.contactResults.forEach((f: any) => {
      if (!allSuggestions.find((s) => s.uid === (f.uid || f.id))) {
        allSuggestions.push({ ...f, source: '联系人', uid: f.uid || f.id })
      }
    })
    data.groupResults.forEach((g) => allSuggestions.push({ ...g, source: '群聊' }))
    data.suggestions = allSuggestions.slice(0, 8)

    // ====== 后端异步补充（不覆盖本地已显示的结果） ======

    // 聊天记录：后端搜索消息内容，增量补充
    searchChats(newVal)
      .then((res) => {
        if (res.success && (res.data as any[])?.length) {
          const existingUids = new Set(data.chatResults.map((r) => r.uid))
          const newItems = (res.data as any[])
            .filter((u) => u.uid && !existingUids.has(u.uid))
            .map((u) => ({
              uid: u.uid,
              name: u.nickname || '',
              avatar: u.avatar_168x168?.url_list?.[0] || '',
              account: u.unique_id || '',
              avatar_168x168: u.avatar_168x168 || {}
            }))
          if (newItems.length) {
            data.chatResults = [...data.chatResults, ...newItems]
          }
        }
      })
      .catch(() => {})

    // 通知：后端搜索通知内容，增量补充到聊天记录
    searchNotifications(newVal)
      .then((res) => {
        if (res.success && (res.data as any[])?.length) {
          const existingUids = new Set(data.chatResults.map((r) => r.uid))
          const newItems = (res.data as any[])
            .filter((u) => u.uid && !existingUids.has(u.uid))
            .map((u) => ({
              uid: u.uid,
              name: u.nickname || '',
              avatar: u.avatar_168x168?.url_list?.[0] || '',
              account: u.unique_id || '',
              avatar_168x168: u.avatar_168x168 || {}
            }))
          if (newItems.length) {
            data.chatResults = [...data.chatResults, ...newItems]
          }
        }
      })
      .catch(() => {})

    // 群聊：后端搜索补充
    searchGroups(newVal)
      .then((res) => {
        if (res.success && (res.data as any[])?.length) {
          const existingIds = new Set(data.groupResults.map((g) => g.id))
          const newItems = (res.data as any[])
            .filter((g) => g.id && !existingIds.has(g.id))
            .map((g) => ({ id: g.id, name: g.name, avatar: g.avatar || '' }))
          if (newItems.length) {
            data.groupResults = [...data.groupResults, ...newItems]
            newItems.forEach((g) => {
              if (!data.suggestions.find((s) => s.id === g.id)) {
                data.suggestions.push({ ...g, source: '群聊' })
              }
            })
          }
        }
      })
      .catch(() => {})
  }
)

watch(
  () => data.createChatSearchKey,
  (newVal) => {
    if (newVal) {
      const friends = localFilter(newVal)
      data.searchFriends = friends
      data.filteredInviteFriends = friends.map((f: any) => ({
        ...f,
        _invite_selected: !!data.createGroupSelected.find(
          (s) => (s.uid || s.id) === (f.uid || f.id)
        )
      }))
    } else {
      data.searchFriends = []
      initInviteFriends()
    }
  }
)

watch(
  () => data.createChatDialog,
  (show) => {
    if (show) {
      data.createGroupSelected = []
      data.createChatSearchKey = ''
      initInviteFriends()
    }
  }
)

function normalizeSearchUser(u: any): any {
  const avatar168 = u.avatar_168x168?.url_list?.[0] || ''
  return {
    id: u.uid,
    uid: u.uid,
    name: u.nickname || '',
    avatar: avatar168,
    account: u.unique_id || '',
    avatar_168x168: u.avatar_168x168 || { url_list: [avatar168] }
  }
}

function handleClick(item) {
  data.createChatSearchKey = ''
  data.createChatDialog = false
  navToChat({ target_user: item })
}

async function loadRecommendData() {
  if (data.loading) return
  data.loading = true
  await _sleep(500)
  data.loading = false
  let temp = cloneDeep(store.friends.all)
  temp.map((v) => {
    v.type = -2
  })
  data.recommend = data.recommend.concat(temp)
}

function goHome() {
  data.baseIndex = 0
}

async function loadRecentAuthors() {
  try {
    const res = await getRecentAuthors()
    if (res.success && res.data) {
      data.recentAuthors = res.data as any[]
    }
  } catch {
    /* ignore */
  }
}

function createPublicGroup() {
  data.createChatDialog = false
  nav('/message/share-to-friend')
}

function createFaceToFaceGroup() {
  data.createChatDialog = false
  // 跳转到面对面建群页面（暂时用分享页面代替）
  nav('/message/share-to-friend')
}

function initInviteFriends() {
  const myUid = store.userinfo.uid
  const allFriends = (store.friends.all || []).filter((f: any) => (f.uid || f.id) !== myUid)
  data.filteredInviteFriends = allFriends.map((f) => ({
    ...f,
    _invite_selected: !!data.createGroupSelected.find((s) => (s.uid || s.id) === (f.uid || f.id))
  }))
}

function toggleInviteSelect(item: any) {
  item._invite_selected = !item._invite_selected
  syncSelect(item)
}

function syncSelect(item: any) {
  const fid = item.uid || item.id
  const idx = data.createGroupSelected.findIndex((s) => (s.uid || s.id) === fid)
  if (item._invite_selected) {
    if (idx === -1) data.createGroupSelected.push(item)
  } else {
    if (idx >= 0) data.createGroupSelected.splice(idx, 1)
  }
}

async function createGroupFromSelected() {
  if (data.createGroupSelected.length < 2) return
  const uids = data.createGroupSelected.map((f) => String(f.uid || f.id)).filter(Boolean)
  const name =
    data.createGroupSelected
      .map((f) => f.nickname || f.name)
      .join('、')
      .slice(0, 50) + '的群聊'
  try {
    const res = await createGroup({ name, member_uids: uids })
    if (res.success) {
      data.createChatDialog = false
      data.createGroupSelected = []
      data.createChatSearchKey = ''
      nav('/message/group-chat', {
        group_id: res.data?.id || res.data?.group_id || '',
        name: res.data?.name || name
      })
    } else {
      alert('创建失败: ' + (res.msg || ''))
    }
  } catch {
    alert('创建群聊失败')
  }
}
</script>
<style scoped lang="less">
@import '../../assets/less/index';

.message-slide-wrapper {
  font-size: 14rem;
  width: 100%;
  height: 100%;
  background: black;
  overflow: hidden;

  .sidebar {
    touch-action: pan-y;
    width: 80%;
    height: calc(var(--vh, 1vh) * 100);
    overflow: auto;
    background: rgb(22, 22, 22);
    padding: 10rem;
    padding-bottom: 20rem;
    box-sizing: border-box;
    color: white;

    .sidebar-header {
      font-size: 16rem;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .right {
        border-radius: 20rem;
        padding: 8rem 15rem;
        background: rgb(36, 36, 36);
        display: flex;
        align-items: center;
        font-size: 14rem;
        gap: 10rem;

        svg {
          font-size: 18rem;
        }
      }
    }

    .card {
      margin-top: 10rem;
      border-radius: 12rem;
      padding: 15rem;
      background: rgb(29, 29, 29);

      .card-header {
        margin-bottom: 8rem;
        font-size: 14rem;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .right {
          display: flex;
          align-items: center;
          color: gray;
          font-size: 12rem;
          gap: 3rem;

          svg {
            font-size: 14rem;
          }
        }
      }

      .card-content {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 12rem;

        .s-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 6rem;
          font-size: 12rem;
          cursor: pointer;

          img,
          svg {
            width: 40rem;
            height: 40rem;
            border-radius: 12rem;
            object-fit: cover;
          }

          svg {
            font-size: 28rem;
            padding: 6rem;
          }

          &.avatar img {
            border-radius: 50%;
          }
        }

        .no-data {
          font-size: 12rem;
        }
      }
    }
  }

  .plus-menu-mask {
    position: fixed;
    z-index: 20;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.4);

    .plus-menu {
      position: absolute;
      top: var(--common-header-height);
      right: 10rem;
      background: var(--main-bg);
      border-radius: 8rem;
      padding: 6rem 0;
      min-width: 140rem;
      box-shadow: 0 2rem 12rem rgba(0, 0, 0, 0.5);

      .menu-item {
        display: flex;
        align-items: center;
        gap: 12rem;
        padding: 14rem 20rem;
        font-size: 15rem;
        color: white;
        cursor: pointer;

        &:active {
          background: rgb(35, 41, 58);
        }

        svg {
          font-size: 20rem;
        }
      }
    }
  }

  .msg-content {
    height: 100%;
    background: var(--color-message);
    color: white;
    position: relative;
  }

  .no-search {
    height: calc(var(--vh, 1vh) * 100);
    display: flex;
    flex-direction: column;

    > header {
      padding: 0 20rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      height: var(--common-header-height);
      box-sizing: border-box;
      position: relative;
      font-size: 24rem;

      .header-title {
        font-size: 17rem;
        font-weight: 500;
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 12rem;
      }

      .menu-back-icon {
        transform: scaleX(-1);
      }
    }

    :deep(#BaseHeader .header) {
      border-bottom: none;

      .left {
        opacity: 0;
      }
    }

    .scroll {
      flex: 1;
      padding-top: 10rem;
      padding-bottom: var(--footer-height);
    }

    .friends {
      overflow-x: scroll;
      display: flex;
      font-size: 14rem;
      padding: 0 10rem;
      gap: 20rem;

      .friend {
        @width: 70rem;
        width: @width;
        min-width: @width;

        &:nth-last-child(1) {
          .avatar {
            height: @width;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--second-btn-color-tran);
            margin-bottom: 10rem;

            img {
              width: 35rem;
              height: 35rem;
            }
          }
        }

        .avatar {
          position: relative;
          margin-bottom: 6rem;
          width: 100%;

          img {
            width: 100%;
            height: 100%;
            border-radius: 50%;
          }

          &.on-line::before {
            content: ' ';
            border: 4rem solid black;
            width: 18rem;
            height: 18rem;
            background: rgb(115, 254, 73);
            border-radius: 50%;
            position: absolute;
            bottom: 0;
            right: 0;
          }
        }

        span {
          width: 64rem;
          font-size: 12rem;
          color: white;
          display: block;
          text-align: center;
          word-break: break-all;
          white-space: nowrap;
          text-overflow: ellipsis;
          overflow: hidden;
        }
      }
    }

    .messages {
      margin-top: 20rem;

      .message {
        display: flex;
        align-items: center;

        &.top {
          background: #353a4f;
        }

        &:active {
          background: rgb(57, 57, 57);
        }

        .avatar {
          position: relative;

          .head-image {
            margin-left: 20rem;
            margin-right: 15rem;
            @width: 55rem;
            width: @width;
            height: @width;
            border-radius: 50%;
          }

          &.on-line::before {
            content: ' ';
            border: 3rem solid black;
            width: 12rem;
            height: 12rem;
            background: rgb(115, 254, 73);
            border-radius: 50%;
            position: absolute;
            bottom: 0;
            right: 15rem;
          }
        }

        .content {
          flex: 1;
          display: flex;
          justify-content: space-between;
          @padding: 16rem;
          padding: @padding 0 @padding 0;

          .left {
            flex: 1;
            min-width: 0;
            overflow: hidden;

            .name {
              font-size: 16rem;
              color: white;
              display: flex;
              align-items: flex-start;

              > span {
                display: -webkit-box;
                -webkit-line-clamp: 1;
                -webkit-box-orient: vertical;
                overflow: hidden;
                text-overflow: ellipsis;
                word-break: break-all;
              }

              .tag {
                margin-left: 5rem;
                font-size: 10rem;
                background: var(--second-btn-color-tran);
                color: var(--second-text-color);
                padding: 2rem 5rem;
                border-radius: 2rem;
                flex-shrink: 0;
                white-space: nowrap;
              }
            }

            .detail {
              color: var(--second-text-color);
              font-size: 14rem;
              margin-top: 4rem;
              display: flex;
              align-items: center;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;

              .point {
                display: inline-block;
                margin-left: 8rem;
                margin-right: 8rem;
                border-radius: 50%;
                width: 1.5px;
                height: 1.5px;
                background: var(--second-text-color);
              }

              .sent {
                width: 10rem;
                height: 10rem;
              }
            }
          }

          .right {
            margin-right: 20rem;
            display: flex;
            align-items: center;
            flex-shrink: 0;

            .arrow {
              width: 15rem;
              height: 15rem;
            }

            .camera {
              width: 20rem;
              height: 20rem;
            }

            .not-read {
              margin-right: 5rem;
            }
          }
        }
      }

      .not-more {
        color: var(--second-text-color);
        text-align: center;
        padding: 20rem;
      }
    }

    .recommend-dialog {
      position: fixed;
      z-index: 11;
      top: 0;
      left: 0;
      width: 100vw;
      height: calc(var(--vh, 1vh) * 100);
      display: flex;
      align-items: center;
      justify-content: center;

      .dialog-content {
        position: relative;
        z-index: 4;
        background: white;
        width: 85vw;
        height: 80vh;
        border-radius: 12rem;
        overflow: hidden;

        .dialog-header {
          color: black;
          border-bottom: 1px solid whitesmoke;
          padding: var(--page-padding);
          display: flex;
          align-items: center;
          justify-content: space-between;

          .title {
            display: flex;
            align-items: center;

            & > img {
              margin-left: 3rem;
              width: 15rem;
            }
          }

          & > img {
            width: 20rem;
          }
        }

        .dialog-body {
          padding: var(--page-padding);
          padding-top: 0;
          height: calc(80vh - 50rem);
          overflow: auto;

          .scroll {
            height: calc(80vh - 50rem);
          }

          .l-button {
            color: white;
          }

          .name {
            color: black !important;
          }

          :deep(.People .content .left .name) {
            color: black !important;
          }
        }
      }
    }
  }

  .create-chat-wrapper {
    min-height: 70vh;
    padding-bottom: 60rem;
    margin-top: 6rem;

    .create-options {
      .option-row {
        display: flex;
        align-items: center;
        padding: 14rem 20rem;
        gap: 14rem;

        &:active {
          background: rgb(35, 41, 58);
        }

        .option-icon {
          width: 44rem;
          height: 44rem;
          border-radius: 8rem;
          background: var(--second-btn-color-tran);
          padding: 10rem;
          box-sizing: border-box;
        }

        .option-text {
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 3rem;

          .option-title {
            font-size: 15rem;
            color: white;
          }

          .option-sub {
            font-size: 12rem;
            color: var(--second-text-color);
          }
        }
      }
    }

    .divider-label {
      padding: 12rem 20rem;
      font-size: 12rem;
      color: var(--second-text-color);
      border-top: 1px solid var(--line-color);
      margin-top: 6rem;
    }

    .invite-section {
      .search-bar {
        margin: 0 20rem 8rem;
      }
    }

    .bottom-btn-bar {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      padding: 12rem 20rem 24rem;
      background: var(--main-bg);

      .start-chat-btn {
        width: 100%;
        height: 42rem;
        border-radius: 6rem;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 15rem;
        background: #555;
        color: #999;
        transition: all 0.2s;

        &.active {
          background: #fe4070;
          color: #fff;
        }
      }
    }

    .joined-chat {
      border-bottom: 1px solid var(--line-color);
      height: 50rem;
      padding: 0 20rem;
      display: flex;
      align-items: center;
      color: white;

      .left {
        width: 22rem;
        height: 22rem;
        margin-left: 10rem;
        margin-right: 20rem;
      }

      .right {
        font-size: 14rem;
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: white;

        img {
          height: 15rem;
        }
      }
    }

    .friend-list {
      padding: 0 20rem;

      .index {
        height: 60rem;
        line-height: 60rem;
        font-size: 14rem;
      }

      .friend-item {
        margin-bottom: 20rem;
        display: flex;
        align-items: center;
        color: white;

        &:nth-child(1) {
          margin-top: 10rem;
        }

        .left {
          width: 48rem;
          height: 48rem;
          border-radius: 50%;
          margin-right: 10rem;
          object-fit: cover;
        }

        .right {
          font-size: 14rem;
          flex: 1;
          display: flex;
          align-items: flex-start;
          justify-content: center;
          flex-direction: column;
          color: white;

          .account-tip {
            font-size: 11rem;
            color: var(--second-text-color);
            margin-top: 2rem;
          }

          img {
            height: 20rem;
          }
        }

        .check {
          width: 22rem;
          height: 22rem;
          margin-left: 10rem;
          flex-shrink: 0;
        }
      }
    }

    .btn-wrapper {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: var(--main-bg);
      display: flex;
      align-items: center;
      justify-content: center;
      padding-bottom: 20rem;

      .btn {
        width: calc(100% - 40rem);
        height: 40rem;
        display: flex;
        align-items: center;
        font-size: 14rem;
        justify-content: center;
        border-radius: 10rem;
        background: var(--primary-btn-color);
      }
    }

    .search-result {
      padding: 0 20rem;

      .search-result-item {
        margin-bottom: 20rem;
        display: flex;
        align-items: center;

        &:nth-child(1) {
          margin-top: 10rem;
        }

        .left {
          width: 48rem;
          height: 48rem;
          border-radius: 50%;
          margin-right: 10rem;
        }

        .right {
          font-size: 14rem;
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: space-between;

          .info {
            display: flex;
            flex-direction: column;

            .name {
              font-size: 14rem;
            }

            .account {
              font-size: 13rem;
              color: var(--second-text-color);
            }
          }

          img {
            height: 20rem;
          }
        }
      }
    }

    .no-result {
      height: 50vh;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;

      .notice-h1 {
        font-size: 16rem;
      }

      .notice-h2 {
        margin-top: 10rem;
        font-size: 14rem;
        color: var(--second-text-color);
      }
    }
  }

  .joined-chat-wrapper {
    min-height: 70vh;
    color: white;

    .nav {
      font-size: 17rem;
      padding: 20rem;
      display: flex;
      justify-content: space-between;

      img {
        height: 20rem;
      }
    }

    .chat-list {
      padding: 0 20rem;

      .chat-item {
        margin-bottom: 20rem;
        display: flex;
        align-items: center;
        position: relative;
        overflow: hidden;

        &:nth-last-child(1) {
          margin-bottom: 0;
        }

        &:nth-child(1) {
          margin-top: 10rem;
        }

        .left-avatar {
          margin-right: 10rem;
        }

        .right {
          font-size: 14rem;
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: space-between;

          .title {
            box-sizing: border-box;
            display: flex;
            align-items: center;

            .name {
            }

            .num {
              margin-left: 5rem;
              color: var(--second-text-color);
            }
          }

          img {
            height: 15rem;
          }
        }
      }
    }
  }

  .searching {
    padding: var(--page-padding);

    .sub-title {
      margin-top: var(--page-padding);
      color: var(--second-text-color);
      font-size: 12rem;
      display: flex;
      align-items: center;
      justify-content: space-between;

      .right {
        display: flex;
        align-items: center;
      }
    }

    .suggestions {
      margin-bottom: 10rem;

      .suggest-item {
        display: flex;
        align-items: center;
        padding: 12rem 0;
        gap: 12rem;
        border-bottom: 0.5px solid rgba(255, 255, 255, 0.05);

        &:active {
          opacity: 0.6;
        }

        .avatar {
          width: 40rem;
          height: 40rem;
          border-radius: 50%;
          object-fit: cover;
        }

        .info {
          display: flex;
          flex-direction: column;
          gap: 2rem;

          .name {
            font-size: 15rem;
            color: white;
          }

          .source {
            font-size: 11rem;
            color: var(--second-text-color);
          }
        }
      }
    }

    .category {
      margin-bottom: 10rem;
    }

    .search-item {
      display: flex;
      align-items: center;
      padding: 12rem 0;
      gap: 12rem;

      .avatar {
        width: 48rem;
        height: 48rem;
        border-radius: 50%;
        object-fit: cover;
      }

      .name {
        font-size: 15rem;
        color: white;
      }

      .info {
        display: flex;
        flex-direction: column;
        gap: 4rem;

        .detail {
          font-size: 12rem;
          color: var(--second-text-color);
        }
      }
    }

    .no-result {
      padding: 50rem 0;
      text-align: center;

      .notice-h1 {
        font-size: 16rem;
        color: white;
      }

      .notice-h2 {
        margin-top: 10rem;
        font-size: 14rem;
        color: var(--second-text-color);
      }
    }

    .goto-search-page {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: var(--page-padding);
      margin-top: var(--page-padding);
      border-top: 1px solid var(--line-color);

      .icon {
        border-radius: 50%;
        padding: 13rem;
        background: var(--second-btn-color-tran);
        width: 22rem;
        height: 22rem;
        margin-right: 10rem;
      }

      .right {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .left {
          display: flex;
          flex-direction: column;
          justify-content: space-between;

          .second-text-color {
            margin-top: 5rem;
          }
        }
      }
    }
  }
}
</style>
