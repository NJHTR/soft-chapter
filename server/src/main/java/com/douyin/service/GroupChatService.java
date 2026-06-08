package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Group;
import com.douyin.entity.GroupMessage;
import com.douyin.vo.*;

import java.util.List;

public interface GroupChatService extends IService<Group> {

    /** 创建群聊 */
    Group createGroup(Long ownerUid, String name, List<Long> memberUids);

    /** 获取用户加入的群聊列表 */
    List<GroupVO> getGroupList(Long userId);

    /** 获取群聊信息 */
    GroupVO getGroupInfo(Long groupId);

    /** 获取群成员 */
    List<GroupMemberVO> getGroupMembers(Long groupId);

    /** 发送群消息 */
    GroupMessage sendGroupMessage(Long groupId, Long fromUserId, String content, Integer msgType, String extra);

    /** 获取群消息历史 */
    List<GroupMessageVO> getGroupMessageHistory(Long groupId, Long userId, int pageSize, Long beforeId);

    /** 邀请成员入群 */
    void addMembers(Long groupId, Long operatorUid, List<Long> memberUids);

    /** 移除成员(群主或管理员) */
    void removeMember(Long groupId, Long operatorUid, Long targetUid);

    /** 退出群聊 */
    void leaveGroup(Long groupId, Long userId);

    /** 搜索群聊 */
    List<GroupVO> searchGroups(Long userId, String keyword);

    /** 获取群聊会话列表（含最后消息和未读数） */
    List<GroupConversationVO> getGroupConversations(Long userId);

    /** 标记群消息为已读 */
    void markGroupRead(Long groupId, Long userId, Long lastMsgId);
}
