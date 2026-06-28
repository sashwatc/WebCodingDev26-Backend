/*
 * chat.js - student <-> admin chat data layer (Appwrite Databases + Realtime).
 *
 * This module is the backend-of-the-frontend for the in-app messaging feature.
 * Each student has exactly one conversation document (keyed by their Appwrite
 * user id) plus many message documents. It exposes functions that app.js calls
 * to: load/create a student's conversation, list conversations for admins,
 * list/send messages, track per-side unread counts, mark threads read, and
 * subscribe to live updates over Appwrite Realtime. All Appwrite client/SDK
 * access is delegated to ./appwrite.js.
 */

// Pull the shared Appwrite client, service singletons, and SDK helpers from the
// wrapper module so this file never imports the CDN bundle directly.
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

// Collection-id accessors (read lazily so they pick up runtime config). One
// collection stores conversation summaries, the other stores individual messages.
const conversationsCollection = () => appwriteConfig.chatConversationsCollectionId;
const messagesCollection = () => appwriteConfig.chatMessagesCollectionId;

// True only when every id the chat feature needs is configured: base Appwrite
// config plus database id, both collection ids, and the admin team id.
export function hasChatConfig() {
  return Boolean(
    hasAppwriteConfig()
    && appwriteConfig.databaseId
    && conversationsCollection()
    && messagesCollection()
    && appwriteConfig.adminTeamId
  );
}

// Returns an empty string when chat is configured, otherwise a human-readable
// message naming the missing env vars. The UI renders this inline as a warning.
export function chatConfigError() {
  if (hasChatConfig()) {
    return "";
  }
  return "Appwrite chat is not configured. Set VITE_APPWRITE_DATABASE_ID, VITE_APPWRITE_CHAT_CONVERSATIONS_COLLECTION_ID, VITE_APPWRITE_CHAT_MESSAGES_COLLECTION_ID, and VITE_APPWRITE_ADMIN_TEAM_ID.";
}

// Guard that throws the configuration error message for write/read calls that
// cannot work without full chat config.
function requireChatConfig() {
  const message = chatConfigError();
  if (message) {
    throw new Error(message);
  }
}

// Current timestamp as an ISO-8601 string, used for createdAt/updatedAt fields.
function nowIso() {
  return new Date().toISOString();
}

// Appwrite document-level permissions for a student-owned row (their own
// conversation or message): the student can read and update it. Admin access is
// expected to be granted to the admin team at the collection level (see below).
function studentPermissions(studentId) {
  // Row security keeps this row private to the student. Admin access should be
  // granted at the collection/table level to the configured Appwrite admin team.
  return [
    Permission.read(Role.user(studentId)),
    Permission.update(Role.user(studentId))
  ];
}

// Permissions for an admin reply message: readable/updatable by the student,
// the replying admin, and (when configured) the whole admin team so any staff
// member can view the thread.
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

// Builds the conversation-summary document body for a student. `existing` lets
// callers preserve prior counters/timestamps when updating; defaults create a
// fresh conversation keyed and owned by the student's Appwrite user id.
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

// Builds a single message document body. Generates a unique id, stamps the
// sender's identity/role ("student" or "admin"), the text, a timestamp, and an
// unread (read:false) flag. Params destructured: { conversationId, sender,
// senderRole, messageText }.
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

// Wraps an Appwrite error in a new Error carrying a friendlier message so the
// UI can surface something readable.
function normalizeError(error) {
  return new Error(friendlyAuthError(error));
}

// Returns the student's single conversation document, creating it on first use.
// Tries to fetch the doc (keyed by user.$id); a 404 means none exists yet so it
// creates one, while any other error is normalized and rethrown. Returns the
// conversation document.
export async function getStudentConversation(user) {
  requireChatConfig();

  // Attempt to load the existing conversation; swallow only the 404 "not found".
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

  // No conversation existed: create one owned by the student.
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

// Lists conversations for the admin dashboard, newest activity first (capped at
// 100). Returns an array of conversation documents.
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

// Loads all messages for one conversation in chronological order (oldest first,
// capped at 200). Returns an array of message documents.
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

// After a message is sent, updates the conversation summary: stores a truncated
// preview + timestamp of the last message and bumps the *other* side's unread
// counter. `viewerRole` is the sender's role, so a student message increments
// the admin's unread count and vice versa. Returns the updated conversation.
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

// Sends a message as the student: ensures their conversation exists, creates
// the message document with student permissions, then refreshes the summary
// (bumping the admin unread count). Returns the saved message.
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

// Sends a reply as an admin into an existing conversation: creates the message
// with admin/team permissions and refreshes the summary (bumping the student's
// unread count). Returns the saved message.
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

// Marks the inbound (other party's) messages as read for whoever is viewing,
// and zeros that viewer's unread counter on the conversation. `viewerRole` is
// the current viewer ("admin" or "student"); the opposite role's unread
// messages are the ones flipped to read.
export async function markConversationRead(conversation, viewerRole) {
  requireChatConfig();
  // The viewer reads messages authored by the *other* role.
  const senderRole = viewerRole === "admin" ? "student" : "admin";
  // The counter to reset belongs to the viewer's own side.
  const unreadField = viewerRole === "admin" ? "adminUnreadCount" : "studentUnreadCount";

  try {
    // Find unread inbound messages for this conversation (up to 100).
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

    // Flip each unread message to read in parallel.
    await Promise.all((response.documents || []).map((message) => databases.updateDocument(
      appwriteConfig.databaseId,
      messagesCollection(),
      message.$id,
      { read: true }
    )));

    // Only write the conversation counter when it actually needs resetting.
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

// Reads the student's own unread count from their conversation document for the
// nav badge. Returns 0 when chat is unconfigured or the lookup fails (e.g. no
// conversation yet) so it never throws into the UI.
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

// Sums the admin-side unread counts across every conversation for the admin
// nav badge. Returns 0 when chat is unconfigured or the lookup fails.
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

// Subscribes (via Appwrite Realtime) to live message changes for one
// conversation and invokes onChange for events matching that conversationId.
// Returns an unsubscribe function (a no-op when chat is unconfigured).
export async function subscribeToConversation(conversationId, onChange) {
  if (!hasChatConfig()) {
    return () => {};
  }

  // Build the realtime channel string for any document in the messages collection.
  const channel = Channel
    .database(appwriteConfig.databaseId)
    .collection(messagesCollection())
    .document()
    .toString();
  // Filter events down to this conversation before notifying the caller.
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

// Subscribes the admin dashboard to live updates on both the conversations and
// messages collections, calling onChange for every event. Returns an
// unsubscribe function (a no-op when chat is unconfigured).
export async function subscribeToAdminChat(onChange) {
  if (!hasChatConfig()) {
    return () => {};
  }

  // Listen on both collections so new conversations and new replies both refresh.
  const channels = [
    Channel.database(appwriteConfig.databaseId).collection(conversationsCollection()).document().toString(),
    Channel.database(appwriteConfig.databaseId).collection(messagesCollection()).document().toString()
  ];
  const subscription = await realtime.subscribe(channels, onChange);
  return () => subscription.unsubscribe();
}
