package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.douyin.service.GroupChatService;
import com.douyin.service.UserService;
import com.douyin.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupChatServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupChatService {

    private final GroupMemberMapper groupMemberMapper;
    private final GroupMessageMapper groupMessageMapper;
    private final GroupReadCursorMapper readCursorMapper;
    private final UserService userService;

    public GroupChatServiceImpl(GroupMemberMapper groupMemberMapper,
                                GroupMessageMapper groupMessageMapper,
                                GroupReadCursorMapper readCursorMapper,
                                UserService userService) {
        this.groupMemberMapper = groupMemberMapper;
        this.groupMessageMapper = groupMessageMapper;
        this.readCursorMapper = readCursorMapper;
        this.userService = userService;
    }

    @Override
    @Transactional
    public Group createGroup(Long ownerUid, String name, List<Long> memberUids) {
        Group group = new Group();
        group.setName(name);
        group.setOwnerUid(ownerUid);
        group.setMemberCount(1 + (memberUids != null ? memberUids.size() : 0));
        save(group);

        // 添加群主
        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(ownerUid);
        owner.setRole(1); // 群主
        groupMemberMapper.insert(owner);

        // 添加其他成员
        if (memberUids != null && !memberUids.isEmpty()) {
            for (Long uid : memberUids) {
                if (uid.equals(ownerUid)) continue;
                GroupMember member = new GroupMember();
                member.setGroupId(group.getId());
                member.setUserId(uid);
                member.setRole(0);
                groupMemberMapper.insert(member);
            }
            // 更新 member_count
            int total = 1 + (int) memberUids.stream().filter(u -> !u.equals(ownerUid)).count();
            group.setMemberCount(total);
            updateById(group);
        }

        return group;
    }

    @Override
    public List<GroupVO> getGroupList(Long userId) {
        List<GroupMember> memberships = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();

        List<Long> groupIds = memberships.stream().map(GroupMember::getGroupId).collect(Collectors.toList());
        List<Group> groups = listByIds(groupIds);

        return groups.stream().map(g -> {
            GroupVO vo = GroupVO.from(g);
            vo.setMemberAvatars(getMemberAvatars(g.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public GroupVO getGroupInfo(Long groupId) {
        Group group = getById(groupId);
        if (group == null) return null;
        GroupVO vo = GroupVO.from(group);
        vo.setMemberAvatars(getMemberAvatars(groupId));
        return vo;
    }

    @Override
    public List<GroupMemberVO> getGroupMembers(Long groupId) {
        List<GroupMember> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        if (members.isEmpty()) return List.of();

        Set<Long> userIds = members.stream().map(GroupMember::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userMap = getUserVOMap(userIds);

        return members.stream()
                .map(m -> GroupMemberVO.from(m, userMap.get(m.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GroupMessage sendGroupMessage(Long groupId, Long fromUserId, String content, Integer msgType, String extra) {
        // 验证是否群成员
        Long count = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, fromUserId));
        if (count == null || count == 0) {
            throw new RuntimeException("不是群成员，无法发送消息");
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setFromUserId(fromUserId);
        msg.setContent(content != null ? content : "");
        msg.setMsgType(msgType != null ? msgType : 1);
        msg.setExtra(extra != null ? extra : "");
        groupMessageMapper.insert(msg);

        return msg;
    }

    @Override
    public List<GroupMessageVO> getGroupMessageHistory(Long groupId, Long userId, int pageSize, Long beforeId) {
        // 验证是否群成员
        Long count = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId));
        if (count == null || count == 0) {
            return List.of();
        }

        LambdaQueryWrapper<GroupMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMessage::getGroupId, groupId);
        if (beforeId != null && beforeId > 0) {
            wrapper.lt(GroupMessage::getId, beforeId);
        }
        wrapper.orderByDesc(GroupMessage::getId);
        wrapper.last("LIMIT " + pageSize);

        List<GroupMessage> messages = groupMessageMapper.selectList(wrapper);
        Collections.reverse(messages);

        Set<Long> userIds = messages.stream().map(GroupMessage::getFromUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userMap = getUserVOMap(userIds);

        return messages.stream()
                .map(m -> GroupMessageVO.from(m, userMap.get(m.getFromUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addMembers(Long groupId, Long operatorUid, List<Long> memberUids) {
        if (memberUids == null || memberUids.isEmpty()) return;

        Group group = getById(groupId);
        if (group == null) throw new RuntimeException("群聊不存在");

        // 获取现有成员ID
        Set<Long> existingIds = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId))
                .stream().map(GroupMember::getUserId).collect(Collectors.toSet());

        int added = 0;
        for (Long uid : memberUids) {
            if (!existingIds.contains(uid)) {
                GroupMember member = new GroupMember();
                member.setGroupId(groupId);
                member.setUserId(uid);
                member.setRole(0);
                groupMemberMapper.insert(member);
                added++;
            }
        }

        if (added > 0) {
            group.setMemberCount(group.getMemberCount() + added);
            updateById(group);
        }
    }

    @Override
    @Transactional
    public void removeMember(Long groupId, Long operatorUid, Long targetUid) {
        // 检查操作者权限
        GroupMember operator = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, operatorUid));
        if (operator == null || operator.getRole() == 0) {
            throw new RuntimeException("无权限移除成员");
        }

        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, targetUid));

        Group group = getById(groupId);
        group.setMemberCount(Math.max(0, group.getMemberCount() - 1));
        updateById(group);
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId));

        Group group = getById(groupId);

        // 如果群主退出，转让给第一个剩余成员或解散
        if (group.getOwnerUid().equals(userId)) {
            List<GroupMember> remaining = groupMemberMapper.selectList(
                    new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
            if (remaining.isEmpty()) {
                // 解散群
                groupMemberMapper.delete(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId));
                groupMessageMapper.delete(new LambdaQueryWrapper<GroupMessage>()
                        .eq(GroupMessage::getGroupId, groupId));
                removeById(groupId);
                return;
            } else {
                group.setOwnerUid(remaining.get(0).getUserId());
                // 升级新群主
                GroupMember newOwner = remaining.get(0);
                newOwner.setRole(1);
                groupMemberMapper.updateById(newOwner);
            }
        }

        group.setMemberCount(Math.max(0, group.getMemberCount() - 1));
        updateById(group);
    }

    @Override
    public List<GroupVO> searchGroups(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();
        List<Group> groups = groupMessageMapper.searchGroups(userId, keyword.trim());
        return groups.stream().map(g -> {
            GroupVO vo = GroupVO.from(g);
            vo.setMemberAvatars(getMemberAvatars(g.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<GroupConversationVO> getGroupConversations(Long userId) {
        // 获取用户加入的所有群
        List<GroupMember> memberships = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();

        List<GroupConversationVO> result = new ArrayList<>();
        for (GroupMember membership : memberships) {
            Long groupId = membership.getGroupId();
            Group group = getById(groupId);
            if (group == null) continue;

            // 最后一条消息
            GroupMessage lastMsg = groupMessageMapper.selectOne(
                    new LambdaQueryWrapper<GroupMessage>()
                            .eq(GroupMessage::getGroupId, groupId)
                            .orderByDesc(GroupMessage::getId)
                            .last("LIMIT 1"));

            // 未读数：从 read cursor 之后的消息数
            GroupReadCursor cursor = readCursorMapper.selectOne(
                    new LambdaQueryWrapper<GroupReadCursor>()
                            .eq(GroupReadCursor::getGroupId, groupId)
                            .eq(GroupReadCursor::getUserId, userId));
            long lastReadId = cursor != null ? cursor.getLastReadMsgId() : 0;
            long unread = groupMessageMapper.selectCount(
                    new LambdaQueryWrapper<GroupMessage>()
                            .eq(GroupMessage::getGroupId, groupId)
                            .gt(GroupMessage::getId, lastReadId)
                            .ne(GroupMessage::getFromUserId, userId));

            GroupConversationVO vo = new GroupConversationVO();
            vo.setGroupId(groupId);
            vo.setGroupName(group.getName());
            vo.setGroupAvatar(group.getAvatar());
            vo.setMemberCount(group.getMemberCount());
            vo.setMemberAvatars(getMemberAvatars(groupId));
            vo.setLastMessage(lastMsg != null ? lastMsg.getContent() : "");
            vo.setLastMsgType(lastMsg != null ? lastMsg.getMsgType() : 1);
            vo.setLastTime(lastMsg != null ? lastMsg.getCreateTime() : null);
            vo.setUnreadCount(unread);
            result.add(vo);
        }

        result.sort((a, b) -> {
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });
        return result;
    }

    @Override
    @Transactional
    public void markGroupRead(Long groupId, Long userId, Long lastMsgId) {
        GroupReadCursor cursor = readCursorMapper.selectOne(
                new LambdaQueryWrapper<GroupReadCursor>()
                        .eq(GroupReadCursor::getGroupId, groupId)
                        .eq(GroupReadCursor::getUserId, userId));
        if (cursor == null) {
            cursor = new GroupReadCursor();
            cursor.setGroupId(groupId);
            cursor.setUserId(userId);
            cursor.setLastReadMsgId(lastMsgId);
            readCursorMapper.insert(cursor);
        } else if (lastMsgId > cursor.getLastReadMsgId()) {
            cursor.setLastReadMsgId(lastMsgId);
            readCursorMapper.updateById(cursor);
        }
    }

    private Map<Long, UserVO> getUserVOMap(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<User> users = userService.listByIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getUid, UserVO::from, (a, b) -> a));
    }

    private List<String> getMemberAvatars(Long groupId) {
        List<GroupMember> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        if (members.isEmpty()) return List.of();

        List<Long> userIds = members.stream()
                .map(GroupMember::getUserId)
                .limit(9)
                .collect(Collectors.toList());

        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUid, u -> u, (a, b) -> a));

        return userIds.stream()
                .map(uid -> {
                    User u = userMap.get(uid);
                    return u != null ? u.getAvatar168Url() : null;
                })
                .filter(Objects::nonNull)
                .limit(9)
                .collect(Collectors.toList());
    }
}
