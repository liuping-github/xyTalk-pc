package xysoft.im.panels;

import xysoft.im.adapter.RoomMembersAdapter;
import xysoft.im.app.Launcher;
import xysoft.im.cache.UserCache;
import xysoft.im.components.*;
import xysoft.im.db.model.ContactsUser;
import xysoft.im.db.model.CurrentUser;
import xysoft.im.db.model.Room;
import xysoft.im.db.service.ContactsUserService;
import xysoft.im.db.service.CurrentUserService;
import xysoft.im.db.service.RoomService;
import xysoft.im.entity.SelectUserData;
import xysoft.im.frames.AddOrRemoveMemberDialog;
import xysoft.im.frames.MainFrame;
import xysoft.im.service.MucChatService;
import xysoft.im.utils.DebugUtil;
import xysoft.im.utils.JID;

import javax.swing.*;
import javax.swing.border.LineBorder;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;


public class RoomMembersPanel extends ParentAvailablePanel
{
    /**
	 * 右侧单聊群聊成员浮动窗
	 */
	private static final long serialVersionUID = -7968437757611971512L;
	public static final int ROOM_MEMBER_PANEL_WIDTH = 200;
    private static RoomMembersPanel roomMembersPanel;

    private RCListView listView = new RCListView();
    private JPanel operationPanel = new JPanel();
    private JButton leaveButton;

    private List<String> members = new ArrayList<>();
    private String roomId;
    private Room room;
    private RoomMembersAdapter adapter;
    private AddOrRemoveMemberDialog addOrRemoveMemberDialog;

    public RoomMembersPanel(JPanel parent)
    {
        super(parent);
        roomMembersPanel = this;

        initComponents();
        initView();
        setListeners();

    }

    private void initComponents()
    {
        setBorder(new LineBorder(Colors.LIGHT_GRAY));
        setBackground(Colors.FONT_WHITE);

        setPreferredSize(new Dimension(ROOM_MEMBER_PANEL_WIDTH, MainFrame.getContext().currentWindowHeight));
        setVisible(false);
        listView.setScrollBarColor(Colors.SCROLL_BAR_THUMB, Colors.WINDOW_BACKGROUND);
        listView.setContentPanelBackground(Colors.FONT_WHITE);
        listView.getContentPanel().setBackground(Colors.FONT_WHITE);

        operationPanel.setPreferredSize(new Dimension(60, 80));
        operationPanel.setBackground(Colors.FONT_WHITE);


        leaveButton = new RCButton("退出群聊", Colors.WINDOW_BACKGROUND_LIGHT, Colors.WINDOW_BACKGROUND, Colors.SCROLL_BAR_TRACK_LIGHT);
        leaveButton.setForeground(Colors.RED);
        leaveButton.setPreferredSize(new Dimension(180, 30));

    }

    private void initView()
    {
        operationPanel.add(leaveButton);

        setLayout(new GridBagLayout());
        add(listView, new GBC(0, 0).setFill(GBC.BOTH).setWeight(1, 1000));
        add(operationPanel, new GBC(0, 1).setFill(GBC.BOTH).setWeight(1, 1).setInsets(10, 0, 5, 0));

        adapter = new RoomMembersAdapter(members);
        listView.setAdapter(adapter);
    }

    public void setRoomId(String roomId)
    {
        this.roomId = roomId;
        room = Launcher.roomService.findById(roomId);
    }

    public void setVisibleAndUpdateUI(boolean aFlag)
    {
        if (aFlag)
        {
            updateUI();
            setVisible(aFlag);
        }

        setVisible(aFlag);
    }

    public void updateUI()
    {
        if (roomId != null)
        {
            try
            {
                room = Launcher.roomService.findById(roomId);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                room = Launcher.roomService.findById(roomId);
            }

            getRoomMembers();

            // 单独聊天，不显示退出按钮
            if (room.getType().equals("s"))
            {
                leaveButton.setVisible(false);
            }
            else
            {
                leaveButton.setVisible(true);
            }

            listView.notifyDataSetChanged(false);

            setLeaveButtonVisibility(true);

            if (isRoomCreator())
            {
                leaveButton.setText("解散群组");
            }
            else
            {
                leaveButton.setText("退出群组");
            }

        }
    }

    private void getRoomMembers()
    {
        members.clear();

        // 单独聊天，成员只显示两人
        if (room.getType().equals("s"))
        {
            members.add(UserCache.CurrentUserName);
            members.add(JID.usernameByJid(room.getRoomId()));
        }
        else //群聊
        {
            String roomMembers = room.getMember();
            String[] userArr = new String[]{};
            if (roomMembers != null)
            {
                userArr = roomMembers.split(",");
            }

            if (isRoomCreator())
            {
                members.remove("添加成员");
                members.add("添加成员");

                if (userArr.length > 1)
                {
                    members.remove("删除成员");
                    members.add("删除成员");
                }
            }

            if (room.getCreatorName() != null)
            {
                members.add(room.getCreatorName());
            }

            for (int i = 0; i < userArr.length; i++)
            {
                if (!members.contains(userArr[i]))
                {
                    members.add(userArr[i]);
                }
            }
        }
    }


    /**
     * 判断当前用户是否是房间创建者
     *
     * @return
     */
    private boolean isRoomCreator()
    {
        return room.getCreatorName() != null && room.getCreatorName().equals(UserCache.CurrentUserName);
    }


    public static RoomMembersPanel getContext()
    {
        return roomMembersPanel;
    }

    public void setLeaveButtonVisibility(boolean visible)
    {
        operationPanel.setVisible(visible);
    }

    private void setListeners()
    {
        adapter.setAddMemberButtonMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                selectAndAddRoomMember();
                super.mouseClicked(e);
            }
        });

        adapter.setRemoveMemberButtonMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                selectAndRemoveRoomMember();
                super.mouseClicked(e);
            }
        });

        leaveButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (isRoomCreator())
                {
                    int ret = JOptionPane.showConfirmDialog(MainFrame.getContext(), "确认解散群组？", "确认解散群组", JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION)
                    {
                        try {
							deleteGroup(room.getRoomId());
						} catch (XmppStringprepException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
                    }
                }
                else
                {
                    int ret = JOptionPane.showConfirmDialog(MainFrame.getContext(), "退出群聊，并从聊天列表中删除该群聊", "确认退出群聊", JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION)
                    {
                        leaveGroup(room.getRoomId());
                    }
                }
                super.mouseClicked(e);
            }
        });
    }


    /**
     * 选择并添加群成员
     */
    private void selectAndAddRoomMember()
    {
        List<ContactsUser> contactsUsers = Launcher.contactsUserService.findAll();
        List<SelectUserData> allusers = new ArrayList<>();
        List<SelectUserData> usersList = new ArrayList<>();
        
		List<Room> singleUser = Launcher.roomService.findByType("s");
		for (Room room : singleUser) {
			usersList.add(new SelectUserData(JID.usernameByJid(room.getRoomId()) + "--" + room.getName(), false));
		}

        for (ContactsUser con : contactsUsers)
        {
            if (!members.contains(con.getUsername()))
            {
                //selectUsers.add(new SelectUserData(con.getUsername(), false));
            	allusers.add(new SelectUserData(con.getUsername() + "--" + con.getName(), false));
            }
        }
        addOrRemoveMemberDialog = new AddOrRemoveMemberDialog(MainFrame.getContext(), true,usersList, allusers);
        addOrRemoveMemberDialog.getOkButton().setText("添加");
        addOrRemoveMemberDialog.getOkButton().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (((JButton) e.getSource()).isEnabled())
                {
                    ((JButton) e.getSource()).setEnabled(false);
                    List<SelectUserData> selectedUsers = addOrRemoveMemberDialog.getSelectedUser();
                    String[] userArr = new String[selectedUsers.size()];
                    for (int i = 0; i < selectedUsers.size(); i++)
                    {
                        userArr[i] = selectedUsers.get(i).getUserName();
                    }

                    try {
						inviteOrKick(userArr, "invite");//邀请
						addOrRemoveMemberDialog.setVisible(false);
					} catch (XmppStringprepException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
                }
                super.mouseClicked(e);
            }
        });
        addOrRemoveMemberDialog.setVisible(true);
    }

    /**
     * 选择并移除群成员
     */
    private void selectAndRemoveRoomMember()
    {
        List<SelectUserData> userDataList = new ArrayList<>();
        for (String member : members)
        {
            if (member.equals(room.getCreatorName()) || member.equals("添加成员") || member.equals("删除成员"))
            {
                continue;
            }
            DebugUtil.debug("MUC原成员："+ member);
            userDataList.add(new SelectUserData(member, false));
           
        }

        addOrRemoveMemberDialog = new AddOrRemoveMemberDialog(MainFrame.getContext(), true, userDataList);
        addOrRemoveMemberDialog.getOkButton().setText("移除");
        addOrRemoveMemberDialog.getOkButton().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (((JButton) e.getSource()).isEnabled())
                {
                    ((JButton) e.getSource()).setEnabled(false);
                    List<SelectUserData> selectedUsers = addOrRemoveMemberDialog.getSelectedUser();
                    String[] userArr = new String[selectedUsers.size()];
                    for (int i = 0; i < selectedUsers.size(); i++)
                    {
                        userArr[i] = selectedUsers.get(i).getUserName();
                    }

                    try {
						inviteOrKick(userArr, "kick");//踢人
						addOrRemoveMemberDialog.setVisible(false);
					} catch (XmppStringprepException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
                }

                super.mouseClicked(e);
            }
        });
        addOrRemoveMemberDialog.setVisible(true);
    }


    private void inviteOrKick(final String[] usernames, String type) throws XmppStringprepException
    {
    	if (usernames.length == 0)
    		return;
    	
    	MultiUserChat muc = 
				MultiUserChatManager.getInstanceFor(Launcher.connection).getMultiUserChat(JidCreate.entityBareFrom(roomId));
    	
    	//邀请成员
        if (type.equals("invite")){
        	addMan(usernames, muc);
        }
        
        //删除成员
        if (type.equals("kick")){       	
        	kickMan(usernames, muc);       	
        }
        
        //刷新群成员UI
        updateUI();
    }

	public void addMan(final String[] usernames, MultiUserChat muc) throws XmppStringprepException {
		JOptionPane.showMessageDialog(null, usernames, "完成邀请", JOptionPane.INFORMATION_MESSAGE);	
		
		List<Jid> members = new ArrayList<Jid>();
		for (String user : usernames){
			members.add(JidCreate.entityBareFrom(user.split("--")[0] + "@" + Launcher.DOMAIN ));
		} 
		//发送邀请消息
		MucChatService.sendInvitationMessage(members,room.getRoomId(),room.getName());
		
		if (room.getMember().length()>1){
			String[] oldMembers = room.getMember().split(",");
			for (String user : oldMembers){
				members.add(JidCreate.entityBareFrom(user + "@" + Launcher.DOMAIN ));
			} 
		}
		
		if (members.size()==0)
			return;
		
		StringBuilder memberforSave = new StringBuilder();
		for (Jid jid : members){
			memberforSave.append(jid.asUnescapedString().replace("@" + Launcher.DOMAIN, "") + ",");
		}
		room.setMember(memberforSave.toString().substring(0,memberforSave.toString().length()-1));
		Launcher.roomService.update(room);
		
		//对 xmpp muc 做邀请兼容处理
		for (int i = 0; i < members.size(); i++) {
			Jid userJid = members.get(i);
			try {
				muc.invite(userJid.asEntityBareJidIfPossible(), "邀请您进入群。");
			} catch (NotConnectedException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
		
		//保存全部成员
		try {
			muc.grantAdmin(members);
		} catch (XMPPErrorException | NoResponseException | NotConnectedException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void kickMan(final String[] usernames, MultiUserChat muc) throws XmppStringprepException {
		JOptionPane.showMessageDialog(null, usernames, "完成删除", JOptionPane.INFORMATION_MESSAGE);	
     	
		List<Jid> memberList = new ArrayList<Jid>();
		List<String> memberForKick = new ArrayList<String>();
		
		if (room.getMember().length()>1){
			String[] oldMembers = room.getMember().split(",");
			for (String user : oldMembers){
				memberList.add(JidCreate.entityBareFrom(user + "@" + Launcher.DOMAIN ));
			} 
		}
		
		for (String user : usernames){       		
			String jidStr = user.split("--")[0] + "@" + Launcher.DOMAIN;
			memberForKick.add(jidStr);
			memberList.remove(JidCreate.bareFrom(jidStr));
			
			//兼容XMPP
			try {
				muc.kickParticipant(Resourcepart.from(user), "您被管理员踢出");
			} catch (XMPPErrorException | NoResponseException | NotConnectedException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		StringBuilder memberforSave = new StringBuilder();
		for (Jid jid : memberList){
			memberforSave.append(jid.asUnescapedString().replace("@" + Launcher.DOMAIN, "") + ",");
		}
		room.setMember(memberforSave.toString().substring(0,memberforSave.toString().length()-1));
		Launcher.roomService.update(room);
		
		//发送删除消息
		MucChatService.sendKickMessage(memberForKick,room.getRoomId(),room.getName());
		
		//保存全部成员
		try {
			muc.grantAdmin(memberList);
		} catch (XMPPErrorException | NoResponseException | NotConnectedException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    /**
     * 删除MucRoom
     *
     * @param roomId
     * @throws XmppStringprepException 
     */
    private void deleteGroup(String roomId) throws XmppStringprepException
    {
        JOptionPane.showMessageDialog(null, "删除群聊：" + roomId, "删除群聊", JOptionPane.INFORMATION_MESSAGE);
        MultiUserChat muc = 
				MultiUserChatManager.getInstanceFor(Launcher.connection).getMultiUserChat(JidCreate.entityBareFrom(roomId));
        
        //发送删除消息
        List<String> memberForKick = new ArrayList<String>();
		
		if (room.getMember()!=null && !room.getMember().isEmpty()){
			String[] oldMembers = room.getMember().split(",");
			for (String user : oldMembers){
				memberForKick.add(user + "@" + Launcher.DOMAIN );
			} 
			MucChatService.sendKickMessage(memberForKick,room.getRoomId(),room.getName());
		}
      	      
        try {
			muc.destroy("解散群组", JidCreate.entityBareFrom(roomId));
		} catch (NoResponseException | XMPPErrorException | NotConnectedException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        //自我删除Room数据
        Launcher.roomService.delete(roomId);
        //更新左侧房间UI
        RoomsPanel.getContext().notifyDataSetChanged(false);
    }

    /**
     * 我退出MUC订阅
     *
     * @param roomId
     */
    private void leaveGroup(final String roomId)
    {
        JOptionPane.showMessageDialog(null, "退出群聊：" + roomId, "退出群聊", JOptionPane.INFORMATION_MESSAGE);
    }

}
