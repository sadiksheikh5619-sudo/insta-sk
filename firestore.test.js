const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 1000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users cannot read or write any STARLIGHT collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("public_profiles").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("stories").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("posts").doc("post_1").set({
      userId: ALICE_UID,
      authorUsername: "alice",
      authorAvatar: "",
      postType: "post",
      mediaType: "image",
      mediaUrl: "https://example.com/p.jpg",
      thumbnailUrl: "",
      caption: "Hi",
      category: "Art",
      hashtags: ["#starlight"],
      musicTitle: "",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Private user PII isolation & settings updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      username: "alice_star",
      displayName: "Alice Star",
      privateAccount: false,
      darkMode: true,
      pushNotifications: true,
      profileSetupComplete: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial settings update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkMode: false,
      privateAccount: true,
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot read or modify Alice's private user document
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ darkMode: true, updatedAt: pastDate(200) }));

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkMode: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Public profiles: creation, role lock, follow count update, and privacy visibility", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  // Cannot self-verify or spoof counters on create
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_star",
      displayName: "Alice",
      bio: "Hello",
      avatarUrl: "",
      visibility: "public",
      online: true,
      verified: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_star",
      displayName: "Alice",
      bio: "Hello",
      avatarUrl: "",
      visibility: "public",
      online: true,
      verified: false,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered public list succeeds; unfiltered list fails
  await assertSucceeds(aliceDb.collection("public_profiles").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("public_profiles").get());

  // Bob increments Alice's followersCount by 1
  await assertSucceeds(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      followersCount: 1,
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot tamper with Alice's bio or jump followersCount by 50
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      followersCount: 50,
      updatedAt: pastDate(400),
    })
  );
});

test("Posts & Reels: create, filtered feed queries, likes/comments counters, and comments subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(2000);

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorAvatar: "",
      postType: "post",
      mediaType: "image",
      mediaUrl: "data:image/jpeg;base64,/9j/4AAQ",
      thumbnailUrl: "",
      caption: "Cosmic night #starlight",
      category: "Photography",
      hashtags: ["#starlight"],
      musicTitle: "",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject extra @DocumentId 'id' key
  await assertFails(
    aliceDb.collection("posts").doc("post_bad").set({
      id: "post_bad",
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorAvatar: "",
      postType: "post",
      mediaType: "image",
      mediaUrl: "",
      thumbnailUrl: "",
      caption: "Bad",
      category: "Art",
      hashtags: [],
      musicTitle: "",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered feed query succeeds; unfiltered query fails
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("posts").get());

  // Bob likes Alice's post (likesCount + 1) and records in his own liked_posts
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: 1,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("liked_posts").doc("post_1").set({
      userId: BOB_UID,
      postId: "post_1",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Bob comments on Alice's public post
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("comment_1").set({
      postId: "post_1",
      postOwnerId: ALICE_UID,
      postVisibility: "public",
      userId: BOB_UID,
      authorUsername: "bob_nova",
      authorAvatar: "",
      text: "Glowing vibes!",
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );

  // Bob queries public comments
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").where("postVisibility", "==", "public").get()
  );

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_1").delete());
});

test("Stories & Highlights: create, view count increment, and owner highlight toggle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(2000);

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_star",
      authorAvatar: "",
      mediaType: "image",
      mediaUrl: "data:image/jpeg;base64,/9j/4AAQ",
      caption: "Midnight studio",
      highlight: false,
      highlightTitle: "",
      visibility: "public",
      viewsCount: 0,
      expiresAtEpochMs: Date.now() + 86400000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(bobDb.collection("stories").where("visibility", "==", "public").get());
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewsCount: 1,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").update({
      highlight: true,
      highlightTitle: "Studio",
      updatedAt: pastDate(400),
    })
  );
});

test("Conversations & Messages: participant isolation and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(2000);
  const convId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantUids: [ALICE_UID, BOB_UID],
      participantAId: ALICE_UID,
      participantBId: BOB_UID,
      participantAName: "Alice",
      participantBName: "Bob",
      participantAAvatar: "",
      participantBAvatar: "",
      lastMessageText: "Hey Bob!",
      lastMessageType: "text",
      lastSenderId: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      messageType: "text",
      text: "Hey Bob!",
      mediaUrl: "",
      durationMs: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query conversations & messages
  await assertSucceeds(
    bobDb.collection("conversations").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").where("participantUids", "array-contains", BOB_UID).get()
  );

  // Charlie cannot read or inject into Alice-Bob conversation
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_bad").set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      messageType: "text",
      text: "Intruder",
      mediaUrl: "",
      durationMs: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Notifications & Reports: actor-verified notification writes and owner-only reads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(1000);

  // Alice creates a like notification for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_1").set({
      recipientId: BOB_UID,
      actorId: ALICE_UID,
      actorUsername: "alice_star",
      actorAvatar: "",
      type: "like",
      targetId: "post_1",
      previewText: "liked your post",
      read: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot read Bob's notifications or spoof actorId as Charlie
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("notifications").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_spoof").set({
      recipientId: BOB_UID,
      actorId: CHARLIE_UID,
      actorUsername: "charlie",
      actorAvatar: "",
      type: "like",
      targetId: "post_1",
      previewText: "spoof",
      read: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob reads & marks notification read
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("notifications").get());
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("notifications").doc("notif_1").update({
      read: true,
      updatedAt: pastDate(200),
    })
  );
});
