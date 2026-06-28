/*
 * appwrite.js - Appwrite SDK wrapper for the Lost Then Found browser app.
 *
 * This is the single place that configures the Appwrite Web SDK client and
 * exposes thin, app-specific helpers for authentication: email/password signup
 * and login, Google OAuth, email verification, the current-user lookup, and
 * logout. app.js and lib/chat.js import from here so the rest of the frontend
 * never touches the raw SDK client directly.
 *
 * Configuration is injected at runtime by Spring Boot via /appwrite-config.js,
 * which sets window.__APPWRITE_CONFIG__ from the server's VITE_APPWRITE_* env
 * vars (see application.properties). The Appwrite SDK itself is pulled from a
 * pinned CDN ESM build (appwrite@26.0.0) below.
 */

// Pull the named SDK building blocks from the pinned Appwrite CDN ESM bundle.
// Account = auth, Databases = documents, Realtime/Channel = live updates,
// ID/Permission/Query/Role = helpers used when reading/writing documents.
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

// Runtime config injected by the server (see /appwrite-config.js). Falls back
// to an empty object so destructuring below never throws when it is absent.
const runtimeConfig = window.__APPWRITE_CONFIG__ || {};

// Normalized Appwrite configuration consumed across the frontend. Every value
// defaults to "" so callers can use Boolean()/truthiness checks to detect a
// missing/incomplete config. endpoint+projectId are required for auth; the
// database/collection/team ids are additionally required for chat.
export const appwriteConfig = {
  endpoint: runtimeConfig.endpoint || "",
  projectId: runtimeConfig.projectId || "",
  databaseId: runtimeConfig.databaseId || "",
  chatConversationsCollectionId: runtimeConfig.chatConversationsCollectionId || "",
  chatMessagesCollectionId: runtimeConfig.chatMessagesCollectionId || "",
  adminTeamId: runtimeConfig.adminTeamId || ""
};

// The single shared Appwrite SDK client used by every helper in this module.
const client = new Client();

// Appwrite needs both the API endpoint and project ID before any account
// method can work. The values are injected by Spring Boot from VITE_* env vars.
if (appwriteConfig.endpoint && appwriteConfig.projectId) {
  client.setEndpoint(appwriteConfig.endpoint).setProject(appwriteConfig.projectId);
}

// Service singletons bound to the configured client, re-exported for the app:
// account (auth), databases (chat documents), realtime (live subscriptions).
export const account = new Account(client);
export const databases = new Databases(client);
export const realtime = new Realtime(client);
// Re-export raw SDK helpers so chat.js can build permissions/queries/channels
// without importing the CDN bundle a second time.
export { Channel, ID, Permission, Query, Role };

// Returns true when the minimum auth config (endpoint + project id) is present.
// Used throughout the UI to disable auth controls when the server didn't
// provide Appwrite credentials.
export function hasAppwriteConfig() {
  return Boolean(appwriteConfig.endpoint && appwriteConfig.projectId);
}

// Guard used by every auth call: throws a clear, actionable error if the
// Appwrite client was never configured, rather than failing deep in the SDK.
function requireAppwriteConfig() {
  if (!hasAppwriteConfig()) {
    throw new Error("Appwrite is not configured. Set VITE_APPWRITE_ENDPOINT and VITE_APPWRITE_PROJECT_ID.");
  }
}

// Builds the absolute URL Appwrite should send the user back to after they
// click the verification link in their email (handled by the /verify-email SPA route).
function verificationRedirectUrl() {
  return `${window.location.origin}/verify-email`;
}

// True when the given Appwrite user object has a confirmed email address.
// Drives the "verify your email" warning banner in the UI.
export function isEmailVerified(user) {
  return user?.emailVerification === true;
}

// Maps raw Appwrite/SDK errors to friendlier, user-facing messages. Falls back
// to the original message for anything it doesn't specifically recognize.
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

// Fetches the currently signed-in Appwrite account. Throws if no active
// session exists (callers treat that rejection as "logged out").
export async function getCurrentUser() {
  requireAppwriteConfig();
  return account.get();
}

// Registers a new email/password account, signs the user in, and kicks off
// email verification. Params: { name, email, password }. Returns the freshly
// created/logged-in account object.
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

// Signs an existing user in with email/password and returns their account.
// Params: { email, password }.
export async function loginWithEmail({ email, password }) {
  requireAppwriteConfig();
  await account.createEmailPasswordSession(email, password);
  return account.get();
}

// Starts the Google OAuth2 flow. This redirects the whole browser away to
// Google; on success Appwrite returns the user to /auth/callback (carrying the
// `next` path to resume), or to /login?oauth=failed on failure. Does not return
// a value because navigation leaves the page.
export function signInWithGoogle(nextPath = "/") {
  requireAppwriteConfig();
  const successUrl = `${window.location.origin}/auth/callback?next=${encodeURIComponent(nextPath)}`;
  const failureUrl = `${window.location.origin}/login?oauth=failed`;

  // Google OAuth redirects away from this page. Appwrite creates the session
  // when Google sends the user back to successUrl.
  return account.createOAuth2Session("google", successUrl, failureUrl);
}

// Re-sends the verification email to the signed-in user (used by the warning
// banner's "Resend" button).
export function resendVerificationEmail() {
  requireAppwriteConfig();
  return account.createVerification(verificationRedirectUrl());
}

// Completes email verification using the userId + secret that Appwrite appended
// to the verification link. Called by the /verify-email page.
export function confirmEmailVerification(userId, secret) {
  requireAppwriteConfig();
  return account.updateVerification(userId, secret);
}

// Logs the current user out by deleting their active Appwrite session.
export function logoutCurrentSession() {
  requireAppwriteConfig();
  return account.deleteSession("current");
}
