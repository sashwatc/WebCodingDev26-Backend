import {
  Account,
  Channel,
  Client,
  Databases,
  ID,
  Permission,
  Query,
  Realtime,
  Role
} from "https://cdn.jsdelivr.net/npm/appwrite@26.0.0/+esm";

const runtimeConfig = window.__APPWRITE_CONFIG__ || {};

export const appwriteConfig = {
  endpoint: runtimeConfig.endpoint || "",
  projectId: runtimeConfig.projectId || "",
  databaseId: runtimeConfig.databaseId || "",
  chatConversationsCollectionId: runtimeConfig.chatConversationsCollectionId || "",
  chatMessagesCollectionId: runtimeConfig.chatMessagesCollectionId || "",
  adminTeamId: runtimeConfig.adminTeamId || ""
};

const client = new Client();

// Appwrite needs both the API endpoint and project ID before any account
// method can work. The values are injected by Spring Boot from VITE_* env vars.
if (appwriteConfig.endpoint && appwriteConfig.projectId) {
  client.setEndpoint(appwriteConfig.endpoint).setProject(appwriteConfig.projectId);
}

export const account = new Account(client);
export const databases = new Databases(client);
export const realtime = new Realtime(client);
export { Channel, ID, Permission, Query, Role };

export function hasAppwriteConfig() {
  return Boolean(appwriteConfig.endpoint && appwriteConfig.projectId);
}

function requireAppwriteConfig() {
  if (!hasAppwriteConfig()) {
    throw new Error("Appwrite is not configured. Set VITE_APPWRITE_ENDPOINT and VITE_APPWRITE_PROJECT_ID.");
  }
}

function verificationRedirectUrl() {
  return `${window.location.origin}/verify-email`;
}

export function isEmailVerified(user) {
  return user?.emailVerification === true;
}

export function friendlyAuthError(error) {
  const message = error?.message || "Authentication request failed.";
  if (message.toLowerCase().includes("invalid credentials")) {
    return "The email or password is incorrect.";
  }
  if (message.toLowerCase().includes("user_already_exists")) {
    return "An account already exists for that email. Try signing in instead.";
  }
  return message;
}

export async function getCurrentUser() {
  requireAppwriteConfig();
  return account.get();
}

export async function signUpWithEmail({ name, email, password }) {
  requireAppwriteConfig();

  // 1. Create the Appwrite user record.
  await account.create(ID.unique(), email, password, name);

  // 2. Create a session right away so the user is logged in after signup.
  await account.createEmailPasswordSession(email, password);

  // 3. Send a verification email. Appwrite adds userId and secret to this URL.
  await account.createVerification(verificationRedirectUrl());

  return account.get();
}

export async function loginWithEmail({ email, password }) {
  requireAppwriteConfig();
  await account.createEmailPasswordSession(email, password);
  return account.get();
}

export function signInWithGoogle(nextPath = "/") {
  requireAppwriteConfig();
  const successUrl = `${window.location.origin}/auth/callback?next=${encodeURIComponent(nextPath)}`;
  const failureUrl = `${window.location.origin}/login?oauth=failed`;

  // Google OAuth redirects away from this page. Appwrite creates the session
  // when Google sends the user back to successUrl.
  return account.createOAuth2Session("google", successUrl, failureUrl);
}

export function resendVerificationEmail() {
  requireAppwriteConfig();
  return account.createVerification(verificationRedirectUrl());
}

export function confirmEmailVerification(userId, secret) {
  requireAppwriteConfig();
  return account.updateVerification(userId, secret);
}

export function logoutCurrentSession() {
  requireAppwriteConfig();
  return account.deleteSession("current");
}
