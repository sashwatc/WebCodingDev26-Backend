import {
  appwriteConfig,
  Channel,
  databases,
  friendlyAuthError,
  hasAppwriteConfig,
  ID,
  Permission,
  Query,
  realtime,
  Role
} from "./appwrite.js";

const conversationsCollection = () => appwriteConfig.chatConversationsCollectionId;
const messagesCollection = () => appwriteConfig.chatMessagesCollectionId;

export function hasChatConfig() {
  return Boolean(
    hasAppwriteConfig()
    && appwriteConfig.databaseId
    && conversationsCollection()
    && messagesCollection()
    && appwriteConfig.adminTeamId
  );
}

export function chatConfigError() {
  if (hasChatConfig()) {
    return "";
  }
  return "Appwrite chat is not configured. Set VITE_APPWRITE_DATABASE_ID, VITE_APPWRITE_CHAT_CONVERSATIONS_COLLECTION_ID, VITE_APPWRITE_CHAT_MESSAGES_COLLECTION_ID, and VITE_APPWRITE_ADMIN_TEAM_ID.";
}

function requireChatConfig() {
  const message = chatConfigError();
  if (message) {
    throw new Error(message);
  }
}

function nowIso() {
  return new Date().toISOString();
}

function studentPermissions(studentId) {
  // Row security keeps this row private to the student. Admin access should be
  // granted at the collection/table level to the configured Appwrite admin team.
  return [
    Permission.read(Role.user(studentId)),
    Permission.update(Role.user(studentId))
  ];
}

function adminReplyPermissions(studentId, adminId) {
  const permissions = [
    Permission.read(Role.user(studentId)),
    Permission.update(Role.user(studentId)),
    Permission.read(Role.user(adminId)),
    Permission.update(Role.user(adminId))
  ];

  if (appwriteConfig.adminTeamId) {
    permissions.push(
      Permission.read(Role.team(appwriteConfig.adminTeamId)),
      Permission.update(Role.team(appwriteConfig.adminTeamId))
    );
  }

  return permissions;
}

function conversationPayload(user, existing = {}) {
  const timestamp = nowIso();
  return {
    conversationId: user.$id,
    studentId: user.$id,
    studentName: user.name || user.email || "Student",
    studentEmail: user.email || "",
    adminTeamId: appwriteConfig.adminTeamId,
    lastMessageText: existing.lastMessageText || "",
    lastMessageAt: existing.lastMessageAt || timestamp,
    studentUnreadCount: Number(existing.studentUnreadCount || 0),
    adminUnreadCount: Number(existing.adminUnreadCount || 0),
    createdAt: existing.createdAt || timestamp,
    updatedAt: timestamp
  };
}

function messagePayload({ conversationId, sender, senderRole, messageText }) {
  const messageId = ID.unique();
  return {
    messageId,
    conversationId,
    senderId: sender.$id,
    senderName: sender.name || sender.email || "User",
    senderRole,
    messageText,
    createdAt: nowIso(),
    read: false
  };
}

function normalizeError(error) {
  return new Error(friendlyAuthError(error));
}

export async function getStudentConversation(user) {
  requireChatConfig();

  try {
    return await databases.getDocument(
      appwriteConfig.databaseId,
      conversationsCollection(),
      user.$id
    );
  } catch (error) {
    if (error?.code !== 404) {
      throw normalizeError(error);
    }
  }

  try {
    return await databases.createDocument(
      appwriteConfig.databaseId,
      conversationsCollection(),
      user.$id,
      conversationPayload(user),
      studentPermissions(user.$id)
    );
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function listAdminConversations() {
  requireChatConfig();
  try {
    const response = await databases.listDocuments(
      appwriteConfig.databaseId,
      conversationsCollection(),
      [Query.orderDesc("updatedAt"), Query.limit(100)]
    );
    return response.documents || [];
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function listConversationMessages(conversationId) {
  requireChatConfig();
  try {
    const response = await databases.listDocuments(
      appwriteConfig.databaseId,
      messagesCollection(),
      [
        Query.equal("conversationId", conversationId),
        Query.orderAsc("createdAt"),
        Query.limit(200)
      ]
    );
    return response.documents || [];
  } catch (error) {
    throw normalizeError(error);
  }
}

async function updateConversationAfterMessage(conversation, message, viewerRole) {
  const adminUnreadCount = viewerRole === "student"
    ? Number(conversation.adminUnreadCount || 0) + 1
    : Number(conversation.adminUnreadCount || 0);
  const studentUnreadCount = viewerRole === "admin"
    ? Number(conversation.studentUnreadCount || 0) + 1
    : Number(conversation.studentUnreadCount || 0);

  return databases.updateDocument(
    appwriteConfig.databaseId,
    conversationsCollection(),
    conversation.conversationId,
    {
      lastMessageText: message.messageText.slice(0, 240),
      lastMessageAt: message.createdAt,
      updatedAt: message.createdAt,
      adminUnreadCount,
      studentUnreadCount
    }
  );
}

export async function sendStudentMessage(user, messageText) {
  requireChatConfig();
  const conversation = await getStudentConversation(user);
  const message = messagePayload({
    conversationId: conversation.conversationId,
    sender: user,
    senderRole: "student",
    messageText
  });

  try {
    const saved = await databases.createDocument(
      appwriteConfig.databaseId,
      messagesCollection(),
      message.messageId,
      message,
      studentPermissions(user.$id)
    );
    await updateConversationAfterMessage(conversation, saved, "student");
    return saved;
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function sendAdminMessage(adminUser, conversation, messageText) {
  requireChatConfig();
  const message = messagePayload({
    conversationId: conversation.conversationId,
    sender: adminUser,
    senderRole: "admin",
    messageText
  });

  try {
    const saved = await databases.createDocument(
      appwriteConfig.databaseId,
      messagesCollection(),
      message.messageId,
      message,
      adminReplyPermissions(conversation.studentId, adminUser.$id)
    );
    await updateConversationAfterMessage(conversation, saved, "admin");
    return saved;
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function markConversationRead(conversation, viewerRole) {
  requireChatConfig();
  const senderRole = viewerRole === "admin" ? "student" : "admin";
  const unreadField = viewerRole === "admin" ? "adminUnreadCount" : "studentUnreadCount";

  try {
    const response = await databases.listDocuments(
      appwriteConfig.databaseId,
      messagesCollection(),
      [
        Query.equal("conversationId", conversation.conversationId),
        Query.equal("senderRole", senderRole),
        Query.equal("read", false),
        Query.limit(100)
      ]
    );

    await Promise.all((response.documents || []).map((message) => databases.updateDocument(
      appwriteConfig.databaseId,
      messagesCollection(),
      message.$id,
      { read: true }
    )));

    if (Number(conversation[unreadField] || 0) > 0) {
      await databases.updateDocument(
        appwriteConfig.databaseId,
        conversationsCollection(),
        conversation.conversationId,
        { [unreadField]: 0 }
      );
    }
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function getStudentUnreadCount(user) {
  if (!hasChatConfig()) {
    return 0;
  }
  try {
    const conversation = await databases.getDocument(
      appwriteConfig.databaseId,
      conversationsCollection(),
      user.$id
    );
    return Number(conversation.studentUnreadCount || 0);
  } catch {
    return 0;
  }
}

export async function getAdminUnreadCount() {
  if (!hasChatConfig()) {
    return 0;
  }
  try {
    const conversations = await listAdminConversations();
    return conversations.reduce((total, conversation) => total + Number(conversation.adminUnreadCount || 0), 0);
  } catch {
    return 0;
  }
}

export async function subscribeToConversation(conversationId, onChange) {
  if (!hasChatConfig()) {
    return () => {};
  }

  const channel = Channel
    .database(appwriteConfig.databaseId)
    .collection(messagesCollection())
    .document()
    .toString();
  const subscription = await realtime.subscribe(
    channel,
    (event) => {
      if (event?.payload?.conversationId === conversationId) {
        onChange(event);
      }
    },
    [Query.equal("conversationId", conversationId)]
  );

  return () => subscription.unsubscribe();
}

export async function subscribeToAdminChat(onChange) {
  if (!hasChatConfig()) {
    return () => {};
  }

  const channels = [
    Channel.database(appwriteConfig.databaseId).collection(conversationsCollection()).document().toString(),
    Channel.database(appwriteConfig.databaseId).collection(messagesCollection()).document().toString()
  ];
  const subscription = await realtime.subscribe(channels, onChange);
  return () => subscription.unsubscribe();
}
